"""
ansys_connector.py  v2.0
ANSYS MAPDL interface for the TechPulse Spindle R&D Suite.

Performs two analysis types on a stepped hollow shaft:
  1. Static Structural  — deflection, bending stress, Von Mises
  2. Modal Analysis     — natural frequencies + mode shapes (Block Lanczos)

Element strategy:
  • BEAM188 with cubic interpolation — 2-node 3D beam
  • Each shaft segment gets a distinct circular hollow cross-section (SECTYPE)
  • Bearing positions modelled as COMBIN14 spring elements to ground
  • Preload forces applied as nodal forces in ±Y direction

Coordinate system:
  • Z = spindle axis (tool → rear)
  • X, Y = radial directions
  • Origin at tool tip (Z = 0)

Offline / Dry-Run mode:
  Set ANSYS_DRY_RUN=1 in environment (or if ANSYS is not installed)
  to return analytically-computed mock data matching the exact dict schema.
"""

from __future__ import annotations
import os
import math
import logging
from typing import Optional

logger = logging.getLogger(__name__)

try:
    from ansys.mapdl.core import launch_mapdl
    ANSYS_AVAILABLE = True
except ImportError:
    ANSYS_AVAILABLE = False

DRY_RUN = os.getenv("ANSYS_DRY_RUN", "0") == "1" or not ANSYS_AVAILABLE


# ── Material database ─────────────────────────────────────────────

MATERIAL_DB = {
    "STEEL_42CrMo4":    {"E": 210_000, "nu": 0.3, "rho": 7.85e-9, "sy": 680.0,  "sf": 320.0},
    "STEEL_42CrMo4_HT": {"E": 210_000, "nu": 0.3, "rho": 7.85e-9, "sy": 900.0,  "sf": 430.0},
    "STEEL_100Cr6":     {"E": 210_000, "nu": 0.3, "rho": 7.83e-9, "sy": 1700.0, "sf": 750.0},
    "TITANIUM_Ti6Al4V": {"E": 114_000, "nu": 0.33,"rho": 4.43e-9, "sy": 880.0,  "sf": 480.0},
    "CARBIDE_TUNGSTEN": {"E": 550_000, "nu": 0.22,"rho": 1.56e-8, "sy": 2200.0, "sf": 1000.0},
}


class AnsysConnector:

    ANSYS_PATH = os.getenv("ANSYS_MAPDL_EXEC",
                           "/usr/ansys_inc/v222/ansys/bin/ansys222")

    @staticmethod
    def is_available() -> bool:
        return ANSYS_AVAILABLE and os.path.exists(AnsysConnector.ANSYS_PATH)

    # ── Public API ────────────────────────────────────────────────

    def run_full_analysis(self, project) -> dict:
        """
        Run Static Structural + Modal Analysis on the stepped hollow shaft.
        Returns a dict with keys 'static' and 'modal' matching SimulationResult schema.
        """
        if DRY_RUN:
            logger.info("ANSYS Dry-Run mode — returning analytical mock data")
            return self._dry_run_full(project)
        return self._ansys_full(project)

    # ── ANSYS MAPDL execution ─────────────────────────────────────

    def _ansys_full(self, project) -> dict:
        """Full ANSYS MAPDL analysis — stepped BEAM188 + Block Lanczos modal."""
        geom    = project.geometry
        brgs    = project.bearing_system
        mat_key = project.material.value if hasattr(project.material, "value") else str(project.material)
        mat     = MATERIAL_DB.get(mat_key, MATERIAL_DB["STEEL_42CrMo4"])
        speed   = project.operating_speed_rpm
        F_r     = project.radial_load_n
        F_a     = project.axial_load_n
        n_modes = project.n_modal_modes if hasattr(project, "n_modal_modes") else 6

        mapdl = launch_mapdl(
            exec_file    = self.ANSYS_PATH,
            run_location = "/tmp/techpulse_spindle",
            override     = True,
            loglevel     = "WARNING"
        )

        try:
            # ── 1. Preprocessor setup ─────────────────────────────
            mapdl.finish()
            mapdl.clear()
            mapdl.prep7()

            # Element type: BEAM188 with cubic interpolation
            mapdl.et(1, "BEAM188")
            mapdl.keyopt(1, 3, 3)     # Cubic interpolation
            mapdl.keyopt(1, 4, 1)     # Include shear deformation

            # Spring element for bearing supports
            mapdl.et(2, "COMBIN14")
            mapdl.keyopt(2, 2, 1)     # DOF set = UY (radial)
            mapdl.et(3, "COMBIN14")
            mapdl.keyopt(3, 2, 2)     # DOF set = UZ (axial, for locating bearings)

            # ── 2. Material definition ────────────────────────────
            mapdl.mp("EX",   1, mat["E"])
            mapdl.mp("PRXY", 1, mat["nu"])
            mapdl.mp("DENS", 1, mat["rho"])

            # ── 3. Cross-sections — one per shaft segment ─────────
            z_bounds = geom.z_boundaries_mm
            for i, seg in enumerate(geom.segments):
                sec_id = i + 1
                R_o = seg.outer_diameter_mm / 2.0
                R_i = seg.inner_diameter_mm / 2.0
                mapdl.sectype(sec_id, "BEAM", "CTUBE")   # Circular tube (hollow)
                mapdl.secdata(R_i, R_o, 16)              # ri, ro, n_cells

            # ── 4. Geometry: keypoints at segment boundaries ──────
            for kp_id, z in enumerate(z_bounds):
                mapdl.k(kp_id + 1, 0, 0, z)

            # ── 5. Lines → mesh each segment ─────────────────────
            for i in range(len(geom.segments)):
                line_id = i + 1
                mapdl.l(i + 1, i + 2)
                mapdl.latt(1, None, 1, None, None, None, line_id)  # material=1, section=line_id
                # Mesh density: at least 5 elements per segment
                n_div = max(5, int(geom.segments[i].length_mm / 10))
                mapdl.lesize(line_id, "", "", n_div)

            mapdl.lmesh("ALL")
            n_elem = int(mapdl.get("N_ELEM", "ELEM", 0, "COUNT"))
            n_node = int(mapdl.get("N_NODE", "NODE", 0, "COUNT"))

            # ── 6. Bearing supports as spring elements ────────────
            bearing_node_ids = []
            for brg in brgs.bearings:
                # Find nearest mesh node at bearing Z-position
                bn = int(mapdl.queries.node(0, 0, brg.z_position_mm))
                bearing_node_ids.append(bn)

                # Ground node at bearing location (UY constrained)
                gn = mapdl.get("MAX_NODE", "NODE", 0, "NUM", "MAX") + 1
                mapdl.n(gn, 999, 0, brg.z_position_mm)   # Ground node far away
                mapdl.d(gn, "ALL")

                # Radial spring Y
                elem_id = mapdl.get("MAX_ELEM", "ELEM", 0, "NUM", "MAX") + 1
                mapdl.type(2)
                mapdl.r(elem_id, brg.housing_stiffness_n_per_mm * 1000)  # N/mm → N/m? keep in mm
                mapdl.e(bn, gn)

                # Axial spring Z for locating bearings only
                if brg.role == "LOCATING":
                    axial_k = brg.housing_stiffness_n_per_mm * 1000 * 0.5
                    gn2 = mapdl.get("MAX_NODE", "NODE", 0, "NUM", "MAX") + 1
                    mapdl.n(gn2, 0, 999, brg.z_position_mm)
                    mapdl.d(gn2, "ALL")
                    mapdl.type(3)
                    mapdl.r(elem_id + 1, axial_k)
                    mapdl.e(bn, gn2)

            # ── 7. Loads: cutting forces at tool tip ──────────────
            tool_tip_node = int(mapdl.queries.node(0, 0, 0))
            mapdl.f(tool_tip_node, "FY", -F_r)    # Radial cutting force
            mapdl.f(tool_tip_node, "FZ", -F_a)    # Axial thrust

            # Preload forces at each bearing pair
            for i, brg in enumerate(brgs.bearings):
                if brg.preload_force_n > 0:
                    bn = bearing_node_ids[i]
                    mapdl.f(bn, "FY", brg.preload_force_n / 2)

            # ── 8. STATIC SOLVE ───────────────────────────────────
            mapdl.finish()
            mapdl.slashsolu()
            mapdl.antype("STATIC")
            mapdl.solve()
            mapdl.finish()

            # ── 9. Post-process static results ────────────────────
            mapdl.post1()
            mapdl.set(1, 1)

            max_defl = float(mapdl.get("MAX_UY", "NODE", 0, "U", "Y"))
            max_defl_node = int(mapdl.get("MAX_UY_NODE", "NODE", 0, "U", "Y", "MAX"))
            max_defl_z = float(mapdl.queries.node_coordinates(max_defl_node)[2])
            tip_defl = abs(float(mapdl.get_value("NODE", tool_tip_node, "U", "Y")))
            max_stress = float(mapdl.get("MAX_SEQV", "NODE", 0, "S", "EQV"))
            max_s_node = int(mapdl.get("MAX_SEQV_NODE", "NODE", 0, "S", "EQV", "MAX"))
            max_s_z = float(mapdl.queries.node_coordinates(max_s_node)[2])
            sy = mat["sy"]
            sf = mat["sf"]
            mapdl.finish()

            static_result = {
                "solver_version": str(mapdl.version),
                "element_type": "BEAM188",
                "n_elements": n_elem,
                "n_nodes": n_node,
                "max_deflection_mm": abs(max_defl),
                "max_deflection_location_mm": max_defl_z,
                "tool_tip_deflection_mm": tip_defl,
                "max_bending_stress_mpa": max_stress * 0.6,   # approx bending fraction
                "max_bending_stress_location_mm": max_s_z,
                "max_von_mises_mpa": max_stress,
                "safety_factor_yield": sy / max(max_stress, 0.1),
                "safety_factor_fatigue": sf / max(max_stress * 0.7, 0.1),
            }

            # ── 10. MODAL SOLVE (Block Lanczos) ────────────────────
            mapdl.slashsolu()
            mapdl.antype("MODAL")
            mapdl.modopt("LANB", n_modes, 0, 10_000)   # Extract n_modes, 0–10 kHz
            mapdl.mxpand(n_modes, "", "", "YES")        # Expand + calculate stresses
            mapdl.solve()
            mapdl.finish()

            # ── 11. Post-process modal results ─────────────────────
            mapdl.post1()
            spindle_freq = speed / 60.0
            mode_shapes = []
            first_critical_rpm = None

            for mode in range(1, n_modes + 1):
                mapdl.set(mode, 1)
                freq_hz = float(mapdl.get("FREQ", "ACTIVE", 0, "SOLU", "FREQ"))
                max_amp = float(mapdl.get("MAX_UY", "NODE", 0, "U", "Y"))
                crit_rpm = freq_hz * 60.0
                if first_critical_rpm is None:
                    first_critical_rpm = crit_rpm
                safety = (freq_hz - spindle_freq) / spindle_freq * 100 if spindle_freq > 0 else 0.0

                # Classify mode type heuristically
                mode_type = _classify_mode(mode, freq_hz, speed)

                mode_shapes.append({
                    "mode_number": mode,
                    "natural_frequency_hz": freq_hz,
                    "damping_ratio": 0.02,
                    "mode_type": mode_type,
                    "max_displacement_mm": abs(max_amp),
                    "critical_speed_rpm": crit_rpm,
                    "safety_margin_pct": safety
                })

            mapdl.finish()

            modal_result = {
                "solver": "ANSYS Block Lanczos",
                "n_modes_extracted": n_modes,
                "mode_shapes": mode_shapes,
                "spindle_frequency_hz": spindle_freq,
                "first_critical_speed_rpm": first_critical_rpm or 0.0,
                "safety_factor_to_first_critical": (
                    (first_critical_rpm / speed) if speed > 0 and first_critical_rpm else 0.0
                )
            }

            return {"static": static_result, "modal": modal_result}

        finally:
            mapdl.exit()

    # ── Dry-run (analytical fallback) ────────────────────────────

    def _dry_run_full(self, project) -> dict:
        """
        Euler-Bernoulli beam + analytical modal estimate for offline demos.
        Models the stepped shaft as an equivalent uniform beam using the
        minimum second moment of area for conservative deflection estimate.
        """
        geom    = project.geometry
        brgs    = project.bearing_system
        mat_key = getattr(project.material, "value",
                          getattr(project.material, "name", "STEEL_42CrMo4"))
        mat     = MATERIAL_DB.get(mat_key, MATERIAL_DB["STEEL_42CrMo4"])
        E       = mat["E"]          # N/mm²
        rho     = mat["rho"]        # kg/mm³
        speed   = project.operating_speed_rpm
        F_r     = project.radial_load_n
        F_a     = project.axial_load_n

        # Effective beam properties (minimum I = conservative)
        L_total = geom.total_length_mm
        I_min   = min(s.second_moment_mm4 for s in geom.segments)
        A_mean  = sum(s.area_mm2 * s.length_mm for s in geom.segments) / L_total

        # Tool-tip deflection: cantilever approximation
        # Front bearing acts as root → overhang = z_front_bearing
        front_z = min(b.z_position_mm for b in brgs.bearings)
        defl_tip = (F_r * front_z**3) / (3 * E * I_min)

        # Max deflection at midspan between bearings
        rear_z   = max(b.z_position_mm for b in brgs.bearings)
        span     = rear_z - front_z
        defl_mid = (F_r * span**3) / (48 * E * I_min)
        max_defl = max(defl_tip, defl_mid)

        # Bending stress at critical section
        M_max = F_r * front_z                            # N·mm
        Z_min = I_min / (max(s.outer_diameter_mm for s in geom.segments) / 2)
        sigma_b = M_max / Z_min
        sigma_a = F_a / A_mean
        sigma_vm = math.sqrt((sigma_b + sigma_a)**2)

        sy = mat["sy"]; sf = mat["sf"]

        static_result = {
            "solver_version": "Dry-Run v2.0 (Euler-Bernoulli)",
            "element_type": "BEAM188 (simulated)",
            "n_elements": sum(max(5, int(s.length_mm / 10)) for s in geom.segments),
            "n_nodes":    sum(max(5, int(s.length_mm / 10)) for s in geom.segments) + 1,
            "max_deflection_mm": max_defl,
            "max_deflection_location_mm": front_z * 0.5,
            "tool_tip_deflection_mm": defl_tip,
            "max_bending_stress_mpa": sigma_b,
            "max_bending_stress_location_mm": front_z,
            "max_von_mises_mpa": sigma_vm,
            "safety_factor_yield": sy / max(sigma_vm, 0.1),
            "safety_factor_fatigue": sf / max(sigma_vm * 0.7, 0.1),
        }

        # Modal: simply-supported beam natural frequencies
        # f_n = (n²π/2L²) × √(EI/ρA)   [Hz]
        spindle_freq = speed / 60.0
        mode_shapes  = []
        first_crit   = None

        for n in range(1, 7):
            f_n = (n**2 * math.pi) / (2 * L_total**2) * math.sqrt(E * I_min / (rho * A_mean))
            f_n /= (2 * math.pi)     # Convert rad/s → Hz
            crit_rpm = f_n * 60.0
            if first_crit is None:
                first_crit = crit_rpm
            safety = (f_n - spindle_freq) / spindle_freq * 100 if spindle_freq > 0 else 0.0
            mode_shapes.append({
                "mode_number": n,
                "natural_frequency_hz": f_n,
                "damping_ratio": 0.02,
                "mode_type": _classify_mode(n, f_n, speed),
                "max_displacement_mm": 1.0 / n,   # Normalised
                "critical_speed_rpm": crit_rpm,
                "safety_margin_pct": safety
            })

        modal_result = {
            "solver": "Dry-Run (Analytical — Simply Supported Beam)",
            "n_modes_extracted": 6,
            "mode_shapes": mode_shapes,
            "spindle_frequency_hz": spindle_freq,
            "first_critical_speed_rpm": first_crit or 0.0,
            "safety_factor_to_first_critical": (
                (first_crit / speed) if speed > 0 and first_crit else 0.0
            )
        }

        return {"static": static_result, "modal": modal_result}


def _classify_mode(mode_number: int, freq_hz: float, speed_rpm: float) -> str:
    """Heuristic mode type labelling based on mode number."""
    labels = {
        1: "1st Bending (XY-plane)",
        2: "1st Bending (XZ-plane)",
        3: "2nd Bending (XY-plane)",
        4: "2nd Bending (XZ-plane)",
        5: "1st Torsional",
        6: "1st Axial"
    }
    return labels.get(mode_number, f"Mode {mode_number}")
