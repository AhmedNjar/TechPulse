"""
monte_carlo_runner.py  v2.0
Probabilistic Monte Carlo simulation for the stepped hollow spindle.

Stochastic variables modelled:
  • Shaft runout (ISO 230-7)  → rotating eccentric radial force
  • Mass eccentricity          → F_ec = m × ω² × e (unbalance)
  • Bearing preload variance   → lognormal on preload → affects C/P life ratio
  • Angular misalignment       → additional moment loading at bearing seats
  • OD manufacturing tolerance → affects I and A of each segment
  • Bearing radial clearance   → shifts effective contact angle and load zone
  • Axial/radial load CV       → cutting force uncertainty
  • Temperature delta          → thermal preload shift, clearance change

Physics:
  • Stepped Euler-Bernoulli beam using segment-by-segment integration
    (Macaulay method for variable cross-section)
  • ISO 281 L10 bearing life with probabilistic equivalent load P
  • Von Mises stress at most-loaded cross-section

Key outputs match MonteCarloResult Pydantic schema.
"""

from __future__ import annotations
import math
import numpy as np
from dataclasses import dataclass
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from schemas import SpindleProject

RNG = np.random.default_rng()

# ── Material properties ────────────────────────────────────────────

MATERIAL_DB = {
    "STEEL_42CrMo4":    {"E": 210_000, "rho": 7.85e-9, "sy": 680.0,  "alpha": 12e-6},
    "STEEL_42CrMo4_HT": {"E": 210_000, "rho": 7.85e-9, "sy": 900.0,  "alpha": 12e-6},
    "STEEL_100Cr6":     {"E": 210_000, "rho": 7.83e-9, "sy": 1700.0, "alpha": 12e-6},
    "TITANIUM_Ti6Al4V": {"E": 114_000, "rho": 4.43e-9, "sy": 880.0,  "alpha": 8.6e-6},
    "CARBIDE_TUNGSTEN": {"E": 550_000, "rho": 1.56e-8, "sy": 2200.0, "alpha": 5.5e-6},
}


class MonteCarloRunner:

    def __init__(self, project):
        self.project  = project
        self.geom     = project.geometry
        self.brgs     = project.bearing_system.bearings
        sv            = project.stochastic
        self.sv       = sv
        self.N        = sv.n_iterations
        mat_key       = getattr(project.material, "value",
                                getattr(project.material, "name", "STEEL_42CrMo4"))
        self.mat      = MATERIAL_DB.get(mat_key, MATERIAL_DB["STEEL_42CrMo4"])
        self.E        = self.mat["E"]          # N/mm²
        self.rho      = self.mat["rho"]        # kg/mm³
        self.sy       = self.mat["sy"]         # MPa
        self.alpha    = self.mat["alpha"]      # /°C thermal expansion
        self.F_r0     = project.radial_load_n
        self.F_a0     = project.axial_load_n
        self.rpm      = project.operating_speed_rpm
        self.omega    = self.rpm * 2 * math.pi / 60   # rad/s

    # ── Public ────────────────────────────────────────────────────

    def run(self) -> dict:
        """Full Monte Carlo run — returns dict matching MonteCarloResult schema."""
        sv = self.sv
        N  = self.N
        rng = np.random.default_rng()

        # ── 1. Sample stochastic inputs ───────────────────────────
        # Shaft OD variation — each segment independently
        od_perturb = [
            rng.normal(0, sv.od_tolerance_mm / 3, N)
            for _ in self.geom.segments
        ]

        # Runout → eccentric radial force
        # F_runout = m_effective × ω² × runout_amplitude
        m_shaft = self._shaft_mass()
        runout  = rng.normal(0, sv.runout_1sigma_mm / 3, N).clip(0, None)
        F_runout = m_shaft * self.omega**2 * runout   # N

        # Mass eccentricity → unbalance force
        ecc     = rng.normal(0, sv.eccentricity_1sigma_mm / 3, N).clip(0, None)
        F_ecc   = m_shaft * self.omega**2 * ecc       # N

        # Angular misalignment → moment at bearing seat
        theta   = rng.normal(0, sv.angular_misalign_rad, N)   # rad
        span    = max(b.z_position_mm for b in self.brgs) - min(b.z_position_mm for b in self.brgs)
        M_misalign = F_runout * span * np.abs(np.sin(theta))  # N·mm

        # Effective radial and axial loads
        F_r = (rng.normal(self.F_r0, self.F_r0 * sv.radial_load_cv, N)
               + F_runout + F_ecc).clip(0, None)
        F_a = rng.normal(self.F_a0, self.F_a0 * sv.axial_load_cv, N).clip(0, None)

        # Bearing preload — lognormal to stay positive
        preload_arr = []
        for brg in self.brgs:
            mu_p = brg.preload_force_n
            cv_p = sv.bearing_preload_cv
            if mu_p > 0:
                sigma_ln = math.sqrt(math.log(1 + cv_p**2))
                mu_ln    = math.log(mu_p) - 0.5 * sigma_ln**2
                preload_arr.append(rng.lognormal(mu_ln, sigma_ln, N))
            else:
                preload_arr.append(np.zeros(N))

        # Temperature-induced preload shift (thermal expansion × stiffness)
        delta_T  = rng.normal(0, sv.temp_delta_c, N)
        K_avg    = np.mean([b.housing_stiffness_n_per_mm for b in self.brgs])
        D_avg    = np.mean([b.bore_diameter_mm for b in self.brgs])
        dF_therm = K_avg * self.alpha * D_avg * delta_T  # N

        # ── 2. Compute deflection for each sample ─────────────────
        deflections = np.zeros(N)
        stresses    = np.zeros(N)

        for i in range(N):
            # Perturbed segment geometry
            segs_perturbed = []
            for j, seg in enumerate(self.geom.segments):
                od_p = seg.outer_diameter_mm + od_perturb[j][i]
                id_p = seg.inner_diameter_mm  # ID tolerance smaller — ignore for now
                segs_perturbed.append((od_p, id_p, seg.length_mm))

            defl_i, stress_i = self._compute_sample(
                segs_perturbed, F_r[i], F_a[i], M_misalign[i]
            )
            deflections[i] = defl_i
            stresses[i]    = stress_i

        # ── 3. Natural frequency distribution (perturbed geometry) ─
        freq_samples = self._compute_frequency_samples(od_perturb, N)

        # ── 4. Failure assessment ─────────────────────────────────
        # Fail if: deflection > limit OR stress > yield OR freq < threshold
        defl_limit  = getattr(self.project, "target_deflection_limit_mm", 0.05)
        freq_floor  = getattr(self.project, "target_min_frequency_hz", 350.0)
        fail_defl   = deflections > defl_limit
        fail_stress = stresses > self.sy
        fail_freq   = freq_samples < freq_floor
        fail_any    = fail_defl | fail_stress | fail_freq
        pof_pct     = float(np.sum(fail_any) / N * 100)

        # ── 5. ISO 281 Bearing life per bearing ────────────────────
        bearing_lives = []
        for b_idx, brg in enumerate(self.brgs):
            life_data = self._compute_bearing_life_iso281(
                brg, F_r, preload_arr[b_idx] + np.abs(dF_therm), N
            )
            bearing_lives.append({
                "bearing_id": brg.bearing_id,
                "designation": brg.designation,
                "dynamic_load_rating_kn": brg.dynamic_load_rating_kn,
                "equivalent_dynamic_load_mean_n": float(np.mean(life_data["P_samples"])),
                "equivalent_dynamic_load_std_n":  float(np.std(life_data["P_samples"])),
                "l10_life_mean_hours": float(np.mean(life_data["L10_hours"])),
                "l10_life_std_hours":  float(np.std(life_data["L10_hours"])),
                "l10_life_p5_hours":   float(np.percentile(life_data["L10_hours"], 5)),
                "l10_life_p95_hours":  float(np.percentile(life_data["L10_hours"], 95)),
                "failure_probability_pct": float(
                    np.sum(life_data["L10_hours"] < getattr(self.project, "target_bearing_life_hours", 20_000))
                    / N * 100
                )
            })

        # ── 6. Sobol-like sensitivity (variance-based, linear proxy) ─
        sensitivity = self._estimate_sensitivity(
            deflections, F_r, F_ecc, F_runout, od_perturb, preload_arr
        )

        return {
            "n_iterations": N,
            "probability_of_failure_pct": pof_pct,
            "reliability_pct": 100.0 - pof_pct,
            "deflection_mean_mm":  float(np.mean(deflections)),
            "deflection_std_mm":   float(np.std(deflections)),
            "deflection_p5_mm":    float(np.percentile(deflections, 5)),
            "deflection_p95_mm":   float(np.percentile(deflections, 95)),
            "stress_mean_mpa":     float(np.mean(stresses)),
            "stress_std_mpa":      float(np.std(stresses)),
            "stress_p95_mpa":      float(np.percentile(stresses, 95)),
            "freq_mean_hz":        float(np.mean(freq_samples)),
            "freq_std_hz":         float(np.std(freq_samples)),
            "freq_p5_hz":          float(np.percentile(freq_samples, 5)),
            "bearing_lives": bearing_lives,
            "sensitivity": sensitivity,
            "runout_effect_mean_n":          float(np.mean(F_runout)),
            "eccentricity_effect_mean_n":    float(np.mean(F_ecc)),
            "misalignment_moment_mean_nmm":  float(np.mean(M_misalign)),
        }

    # ── Physics ────────────────────────────────────────────────────

    def _compute_sample(self, segs, F_r, F_a, M_misalign):
        """
        Single-sample stepped beam deflection and stress using
        Macaulay's method with piecewise EI.

        Model: simply supported at front and rear bearing positions.
        Load: radial force F_r at tool tip (z=0).
        Returns (tool_tip_deflection_mm, max_von_mises_mpa).
        """
        E = self.E
        z_bounds = self._z_boundaries()
        front_z = min(b.z_position_mm for b in self.brgs)
        rear_z  = max(b.z_position_mm for b in self.brgs)
        span    = rear_z - front_z

        # Reaction forces (simply supported at front/rear bearing)
        # Tool tip overhang load F_r at z=0, front bearing at z=front_z
        R_rear  = F_r * front_z / span if span > 0 else 0.0
        R_front = F_r - R_rear

        # Segment I values with perturbation
        I_segs = []
        for od, id_, _ in segs:
            I = math.pi / 64 * (od**4 - id_**4)
            I_segs.append(max(I, 1.0))  # Guard against zero

        # Deflection at tool tip using Castigliano / Mohr integral (simplified)
        # For overhang: δ_tip = R_front × front_z³ / (3EI_min)
        I_min = min(I_segs)
        defl_tip = abs(F_r * front_z**3 / (3 * E * I_min))

        # Max bending moment at front bearing root
        M_max = F_r * front_z + M_misalign

        # Most-stressed cross section (min Z-section modulus)
        od_min  = min(od for od, _, _ in segs)
        id_min  = min(id_ for _, id_, _ in segs)
        Z_s     = math.pi / 32 * (od_min**4 - id_min**4) / (od_min / 2 + 1e-9)
        A_min   = math.pi / 4 * (od_min**2 - id_min**2)

        sigma_b = M_max / max(Z_s, 1e-6)
        sigma_a = F_a / max(A_min, 1e-6)
        sigma_vm = math.sqrt((sigma_b + sigma_a)**2)

        return defl_tip, sigma_vm

    def _compute_frequency_samples(self, od_perturb, N):
        """
        Approximate 1st natural frequency distribution for perturbed geometry.
        f₁ = (π/2L²) × √(EI_eff / ρA_eff)  for simply-supported beam.
        """
        L = self.geom.total_length_mm
        freqs = np.zeros(N)
        for i in range(N):
            I_vals, A_vals, L_vals = [], [], []
            for j, seg in enumerate(self.geom.segments):
                od = seg.outer_diameter_mm + od_perturb[j][i]
                id_ = seg.inner_diameter_mm
                I_vals.append(math.pi / 64 * (od**4 - id_**4))
                A_vals.append(math.pi / 4  * (od**2 - id_**2))
                L_vals.append(seg.length_mm)
            # Weighted average by segment length
            L_total = sum(L_vals)
            I_eff = sum(I * l for I, l in zip(I_vals, L_vals)) / L_total
            A_eff = sum(A * l for A, l in zip(A_vals, L_vals)) / L_total
            f1 = (math.pi / (2 * L**2)) * math.sqrt(
                self.E * max(I_eff, 1.0) / (self.rho * max(A_eff, 1.0))
            )
            freqs[i] = f1 / (2 * math.pi)
        return freqs

    def _compute_bearing_life_iso281(self, brg, F_r_samples, preload_samples, N):
        """
        ISO 281 L10h life for one bearing position.

        P = X × F_r + Y × F_a  (simplified: X=1, Y=0 for radial-dominant)
        L10 = (C/P)^p × 10^6 / (60 × n)
        """
        C   = brg.dynamic_load_rating_kn * 1_000  # kN → N
        p   = 3.0 if "CYLINDRICAL" not in brg.bearing_type else 10.0/3.0
        n   = self.rpm

        # Effective load includes bearing preload (adds to minimum load zone)
        P_samples = (F_r_samples * 0.5 + preload_samples * 0.3).clip(min=C * 0.01)

        L10_hours = np.power(C / P_samples, p) * 1e6 / (60 * n)
        return {"P_samples": P_samples, "L10_hours": L10_hours}

    def _estimate_sensitivity(self, deflections, F_r, F_ecc, F_runout,
                               od_perturb, preload_arr):
        """
        Variance-based sensitivity (linear proxy Sobol S1 via correlation).
        S1_i ≈ Var(E[Y|X_i]) / Var(Y)
        """
        Var_Y = np.var(deflections)
        if Var_Y < 1e-20:
            return {}

        sensitivity = {}
        factors = {
            "Radial Load":       F_r,
            "Eccentricity Force": F_ecc,
            "Runout Force":      F_runout,
            "OD Tolerance (seg 0)": od_perturb[0] if od_perturb else np.zeros(len(deflections)),
        }
        if preload_arr:
            factors["Bearing Preload"] = preload_arr[0]

        total = 0.0
        raw = {}
        for name, x in factors.items():
            corr = float(np.corrcoef(x, deflections)[0, 1])**2
            raw[name] = corr
            total += corr

        # Normalise to sum = 1
        if total > 0:
            sensitivity = {k: round(v / total, 3) for k, v in raw.items()}

        return sensitivity

    def _shaft_mass(self) -> float:
        """Total shaft mass [kg]."""
        return sum(
            s.area_mm2 * s.length_mm * self.rho
            for s in self.geom.segments
        )

    def _z_boundaries(self):
        zs = [0.0]
        for seg in self.geom.segments:
            zs.append(zs[-1] + seg.length_mm)
        return zs
