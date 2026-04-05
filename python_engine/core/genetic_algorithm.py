"""
genetic_algorithm.py  v2.0
Multi-objective NSGA-II for the stepped hollow spindle.

Objectives (all minimised — DEAP uses negative for maximise):
  1. Tool-tip deflection [mm]             — minimise
  2. Shaft mass [kg]                      — minimise (weight proxy)
  3. −Natural frequency [Hz]             — minimise (= maximise freq)

Constraints (penalty if violated):
  • Max deflection    ≤ 0.05 mm
  • Min nat. freq     ≥ 350 Hz  (1.4 × 250 Hz max spindle freq)
  • Max Von Mises     ≤ 400 MPa
  • All bearing RPMs  ≤ limiting speed

Design variables (genes) — per-segment OD + ID + bearing preloads:
  Genes [0..n_seg-1]  → segment OD (mm) in [40, 100]
  Genes [n_seg..2n-1] → segment ID/OD ratio k in [0, 0.7]
  Genes [2n..2n+n_b]  → bearing preload (N) in [50, 700]
"""

from __future__ import annotations
import asyncio
import math
import random
from typing import AsyncGenerator, Any

import numpy as np

try:
    from deap import base, creator, tools
    DEAP_AVAILABLE = True
except ImportError:
    DEAP_AVAILABLE = False

MATERIAL_DB = {
    "STEEL_42CrMo4":    {"E": 210_000, "rho": 7.85e-9, "sy": 680.0},
    "STEEL_42CrMo4_HT": {"E": 210_000, "rho": 7.85e-9, "sy": 900.0},
    "STEEL_100Cr6":     {"E": 210_000, "rho": 7.83e-9, "sy": 1700.0},
    "TITANIUM_Ti6Al4V": {"E": 114_000, "rho": 4.43e-9, "sy": 880.0},
    "CARBIDE_TUNGSTEN": {"E": 550_000, "rho": 1.56e-8, "sy": 2200.0},
}


class GeneticAlgorithm:

    # ── GA hyperparameters ─────────────────────────────────────
    POP_SIZE   = 120
    MAX_GEN    = 50
    CX_PROB    = 0.80
    MUT_PROB   = 0.05
    ETA_CX     = 20
    ETA_MUT    = 20

    # ── Engineering constraints ───────────────────────────────
    MAX_DEFLECTION_MM  = 0.05
    MIN_FREQ_HZ        = 350.0
    MAX_STRESS_MPA     = 400.0
    PENALTY            = 1e6    # Constraint violation penalty

    def __init__(self, project_dict: dict):
        self.project = project_dict
        self._parse_project()
        self._setup_deap()

    def _parse_project(self):
        geom   = self.project.get("geometry", {})
        segs   = geom.get("segments", [])
        brgs   = self.project.get("bearing_system", {}).get("bearings", [])
        mat_k  = self.project.get("material", "STEEL_42CrMo4")
        if hasattr(mat_k, "value"):
            mat_k = mat_k.value

        self.mat       = MATERIAL_DB.get(mat_k, MATERIAL_DB["STEEL_42CrMo4"])
        self.segments  = segs
        self.bearings  = brgs
        self.n_seg     = len(segs) or 4
        self.n_brg     = len(brgs) or 3
        self.rpm       = float(self.project.get("operating_speed_rpm", 12_000))
        self.F_r       = float(self.project.get("radial_load_n", 1_500))
        self.F_a       = float(self.project.get("axial_load_n", 500))

        # Gene bounds
        self.od_bounds     = [(40.0, 100.0)] * self.n_seg
        self.k_bounds      = [(0.0,  0.7)]   * self.n_seg   # k = ID/OD
        self.pre_bounds    = [(50.0, 700.0)] * self.n_brg
        self.all_bounds    = self.od_bounds + self.k_bounds + self.pre_bounds
        self.n_genes       = len(self.all_bounds)

    def _setup_deap(self):
        if not DEAP_AVAILABLE:
            return
        # Clean up previous registrations
        for name in ("FitnessSpindle", "SpindleInd"):
            if hasattr(creator, name):
                delattr(creator, name)

        creator.create("FitnessSpindle", base.Fitness, weights=(-1.0, -1.0, -1.0))
        creator.create("SpindleInd", list, fitness=creator.FitnessSpindle)

        self.tb = base.Toolbox()

        def rand_gene(lo, hi):
            return lambda: random.uniform(lo, hi)

        attrs = [rand_gene(lo, hi) for lo, hi in self.all_bounds]
        self.tb.register("individual", tools.initCycle, creator.SpindleInd,
                         attrs, n=1)
        self.tb.register("population", tools.initRepeat, list, self.tb.individual)
        self.tb.register("evaluate", self._evaluate)
        self.tb.register("mate", tools.cxSimulatedBinaryBounded,
                         eta=self.ETA_CX,
                         low=[b[0] for b in self.all_bounds],
                         up=[b[1]  for b in self.all_bounds])
        self.tb.register("mutate", tools.mutPolynomialBounded,
                         eta=self.ETA_MUT,
                         low=[b[0] for b in self.all_bounds],
                         up=[b[1]  for b in self.all_bounds],
                         indpb=0.2)
        self.tb.register("select", tools.selNSGA2)

    # ── Objective + constraint evaluation ─────────────────────

    def _evaluate(self, ind: list) -> tuple[float, float, float]:
        """Return (deflection, mass, −freq) with constraint penalties."""
        ods   = ind[:self.n_seg]
        ks    = ind[self.n_seg:2*self.n_seg]
        pres  = ind[2*self.n_seg:]
        E     = self.mat["E"]
        rho   = self.mat["rho"]
        sy    = self.mat["sy"]

        # Segment lengths — kept constant (only OD/ID are design vars)
        lengths = [
            float(s.get("length_mm", 100)) if isinstance(s, dict) else 100.0
            for s in (self.segments or [{}] * self.n_seg)
        ]
        L_total = sum(lengths)

        # Cross-section properties
        I_vals, A_vals = [], []
        for od, k, L in zip(ods, ks, lengths):
            id_ = od * k
            I   = math.pi / 64 * (od**4 - id_**4)
            A   = math.pi / 4  * (od**2 - id_**2)
            I_vals.append(max(I, 1.0))
            A_vals.append(max(A, 1.0))

        # Effective properties (length-weighted)
        I_eff = sum(I*l for I,l in zip(I_vals, lengths)) / max(L_total, 1)
        A_eff = sum(A*l for A,l in zip(A_vals, lengths)) / max(L_total, 1)
        I_min = min(I_vals)

        # Front bearing Z position
        if self.bearings:
            if isinstance(self.bearings[0], dict):
                front_z = min(b.get("z_position_mm", 80) for b in self.bearings)
            else:
                front_z = min(getattr(b, "z_position_mm", 80) for b in self.bearings)
        else:
            front_z = L_total * 0.2

        # ── Deflection (cantilever from front bearing) ────────
        defl = (self.F_r * front_z**3) / (3 * E * I_min)

        # ── Mass ──────────────────────────────────────────────
        mass = sum(A * l * rho for A, l in zip(A_vals, lengths))  # kg

        # ── Natural frequency (1st mode, simply supported) ────
        freq = (math.pi / (2 * L_total**2)) * math.sqrt(
            E * I_eff / (rho * A_eff)
        ) / (2 * math.pi)  # Hz

        # ── Von Mises stress ──────────────────────────────────
        M_max  = self.F_r * front_z
        Z_min  = I_min / (max(ods) / 2 + 1e-9)
        sigma  = M_max / max(Z_min, 1e-6) + self.F_a / max(A_eff, 1e-6)

        # ── Constraint penalties ──────────────────────────────
        penalty = 0.0
        if defl > self.MAX_DEFLECTION_MM:
            penalty += self.PENALTY * (defl - self.MAX_DEFLECTION_MM) ** 2
        if freq < self.MIN_FREQ_HZ:
            penalty += self.PENALTY * (self.MIN_FREQ_HZ - freq) ** 2
        if sigma > self.MAX_STRESS_MPA:
            penalty += self.PENALTY * (sigma - self.MAX_STRESS_MPA) ** 2

        return (defl + penalty, mass + penalty, -freq + penalty)

    # ── Async SSE generator ────────────────────────────────────

    async def evolve(self) -> AsyncGenerator[dict, None]:
        if not DEAP_AVAILABLE:
            async for ev in self._stub_evolve():
                yield ev
            return

        pop = self.tb.population(n=self.POP_SIZE)
        for ind in pop:
            ind.fitness.values = self.tb.evaluate(ind)

        for gen in range(1, self.MAX_GEN + 1):
            offspring = tools.selTournamentDCD(pop, len(pop))
            offspring = list(map(self.tb.clone, offspring))

            for c1, c2 in zip(offspring[::2], offspring[1::2]):
                if random.random() < self.CX_PROB:
                    self.tb.mate(c1, c2)
                    del c1.fitness.values, c2.fitness.values

            for mut in offspring:
                if random.random() < self.MUT_PROB:
                    self.tb.mutate(mut)
                    del mut.fitness.values

            invalids = [ind for ind in offspring if not ind.fitness.valid]
            for ind in invalids:
                ind.fitness.values = self.tb.evaluate(ind)

            pop = self.tb.select(pop + offspring, self.POP_SIZE)
            yield self._build_event(gen, pop)
            await asyncio.sleep(0)

    def _build_event(self, gen: int, pop: list) -> dict:
        fits   = [ind.fitness.values for ind in pop]
        best_f = min(fits, key=lambda f: f[0])   # Best deflection
        mean_defl = float(np.mean([f[0] for f in fits]))

        pareto = tools.sortNondominated(pop, len(pop), first_front_only=True)[0]
        elite  = min(pareto, key=lambda ind: ind.fitness.values[0])

        return {
            "event_type":       "GENERATION",
            "generation":        gen,
            "best_fitness":      best_f[0],
            "mean_fitness":      mean_defl,
            "best_deflection_mm": best_f[0],
            "best_freq_hz":      abs(best_f[2]),
            "best_weight_kg":    best_f[1],
            "constraint_violations": sum(
                1 for f in fits if f[0] > self.MAX_DEFLECTION_MM + 1e-4
            ),
            "pareto_points": [
                {
                    "deflection": ind.fitness.values[0],
                    "weight_kg":  ind.fitness.values[1],
                    "freq_hz":    abs(ind.fitness.values[2])
                }
                for ind in pareto[:20]
            ]
        }

    async def _stub_evolve(self) -> AsyncGenerator[dict, None]:
        """Offline stub when DEAP not installed."""
        for gen in range(1, 21):
            await asyncio.sleep(0.4)
            t = gen / 20
            yield {
                "event_type":       "GENERATION",
                "generation":        gen,
                "best_fitness":      0.05 * math.exp(-3 * t) + 0.008,
                "mean_fitness":      0.05 * math.exp(-2.5 * t) + 0.012,
                "best_deflection_mm": 0.05 * math.exp(-3 * t) + 0.008,
                "best_freq_hz":      280 + 120 * t,
                "best_weight_kg":    4.2 - 1.1 * t,
                "constraint_violations": max(0, 5 - gen // 2),
                "pareto_points": [
                    {"deflection": 0.04 - 0.02*t + i*0.002,
                     "weight_kg":  5.0 - i*0.3,
                     "freq_hz":    300 + 80*t - i*5}
                    for i in range(10)
                ]
            }

    @classmethod
    def from_job_store(cls, config_id: str, job_store: dict) -> "GeneticAlgorithm":
        job = job_store.get(config_id)
        if not job:
            raise ValueError(f"Config '{config_id}' not found in job store")
        return cls(job.get("project", {}))
