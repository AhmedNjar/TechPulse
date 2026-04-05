"""
pyvista_renderer.py  v2.0
Server-side 3D rendering of the stepped hollow spindle using PyVista.

Renders offscreen to PNG (base64) and returns it to the Kotlin client
which displays it in an Image composable.

Render modes:
  GEOMETRY   — Bare stepped shaft with segment colour coding
  STRESS     — Von Mises stress heatmap (from static FEA or analytical)
  DEFLECTION — Deflection magnitude heatmap
  MODAL      — Animated mode-shape visualisation (static frame at max amplitude)

Coordinate system matches ANSYS: Z = spindle axis, tool tip at Z=0.

Offline / no-display safe: pyvista uses offscreen rendering (OSMesa / EGL)
so no display server is required — works on headless Linux demo machines.
"""

from __future__ import annotations
import base64
import io
import math
import logging
from typing import Optional

logger = logging.getLogger(__name__)

try:
    import pyvista as pv
    import numpy as np
    PYVISTA_AVAILABLE = True
    pv.global_theme.background = "black"
    pv.global_theme.font.color = "white"
except ImportError:
    PYVISTA_AVAILABLE = False
    logger.warning("PyVista not installed — renderer will return placeholder PNG")


# ── Colour maps ───────────────────────────────────────────────────

CMAP_STRESS     = "hot"           # Red = high stress
CMAP_DEFLECTION = "coolwarm"      # Blue = min, Red = max
CMAP_MODAL      = "RdBu"          # Blue/Red for mode shape displacement
SEGMENT_COLOURS = [               # Distinct colours per shaft segment
    "#00D4B1", "#5A8FFF", "#FFB830", "#FF4D6D",
    "#9B59FF", "#2ECC8A", "#FF8C42", "#4ECDC4",
]


class PyVistaRenderer:

    @staticmethod
    def is_available() -> bool:
        return PYVISTA_AVAILABLE

    def render(
        self,
        project,
        result: Optional[dict] = None,
        render_mode: str = "GEOMETRY",
        modal_mode_number: int = 1,
        width_px: int = 1200,
        height_px: int = 600,
    ) -> dict:
        """
        Main entry point. Returns dict with image_b64, metadata.
        Falls back to a placeholder SVG if PyVista unavailable.
        """
        if not PYVISTA_AVAILABLE:
            return self._placeholder_response(width_px, height_px, render_mode)

        try:
            return self._render_pyvista(
                project, result, render_mode, modal_mode_number, width_px, height_px
            )
        except Exception as exc:
            logger.error(f"PyVista render failed: {exc}", exc_info=True)
            return self._placeholder_response(width_px, height_px, render_mode)

    # ── PyVista rendering ─────────────────────────────────────────

    def _render_pyvista(self, project, result, render_mode, modal_mode, w, h):
        geom = project.geometry
        brgs = project.bearing_system.bearings

        # Build the stepped shaft mesh
        shaft_mesh, segment_scalar = self._build_shaft_mesh(geom)

        # Determine scalar field and colour map
        scalar_name, cmap, s_min, s_max, unit = self._setup_scalars(
            shaft_mesh, segment_scalar, result, render_mode, modal_mode, geom
        )

        # Plotter (offscreen)
        pl = pv.Plotter(off_screen=True, window_size=[w, h])
        pl.set_background("#060A14")   # Match app NavyDeepest

        # Shaft body
        pl.add_mesh(
            shaft_mesh,
            scalars=scalar_name,
            cmap=cmap,
            clim=[s_min, s_max],
            show_scalar_bar=True,
            scalar_bar_args={
                "title": f"{render_mode}\n[{unit}]",
                "color": "white",
                "fmt": "%.3f",
                "n_labels": 5,
                "position_x": 0.82,
                "position_y": 0.15,
            }
        )

        # Bearing positions — rendered as rings
        for brg in brgs:
            self._add_bearing_ring(pl, brg)

        # Shaft axis line
        L = geom.total_length_mm
        axis_pts = pv.Line(
            pointa=[0, 0, 0],
            pointb=[0, 0, L],
            resolution=2
        )
        pl.add_mesh(axis_pts, color="#2A3554", line_width=1)

        # Tool tip marker
        pl.add_point_labels(
            [[0, 0, 0]],
            ["Tool Tip"],
            font_size=10,
            text_color="#00D4B1",
            point_color="#00D4B1",
            point_size=12,
        )

        # Segment labels
        z_bounds = geom.z_boundaries_mm
        for i, seg in enumerate(geom.segments):
            z_mid = (z_bounds[i] + z_bounds[i+1]) / 2
            r_mid = seg.outer_diameter_mm / 2 * 1.3
            pl.add_point_labels(
                [[0, r_mid, z_mid]],
                [seg.name],
                font_size=8,
                text_color="#8896B3",
            )

        # Camera: isometric side view
        pl.view_isometric()
        pl.camera.zoom(1.2)

        # Render to PNG in memory
        img_bytes = pl.screenshot(return_img=False, transparent_background=False)
        pl.close()

        img_b64 = base64.b64encode(
            pv.image_to_string(img_bytes) if isinstance(img_bytes, np.ndarray)
            else img_bytes
        ).decode("utf-8")

        return {
            "image_b64": img_b64,
            "width_px": w,
            "height_px": h,
            "colorbar_min": s_min,
            "colorbar_max": s_max,
            "colorbar_unit": unit,
        }

    def _build_shaft_mesh(self, geom):
        """Build a pyvista mesh of the stepped hollow spindle."""
        import numpy as np

        meshes = []
        scalars_all = []
        z_bounds = geom.z_boundaries_mm

        for i, seg in enumerate(geom.segments):
            z0 = z_bounds[i]
            z1 = z_bounds[i + 1]
            R_o = seg.outer_diameter_mm / 2
            R_i = seg.inner_diameter_mm / 2

            # Outer cylinder
            outer = pv.CylinderStructured(
                radius=np.linspace(R_i if R_i > 0 else R_o * 0.01, R_o, 2),
                height=seg.length_mm,
                center=[0, 0, z0 + seg.length_mm / 2],
                direction=[0, 0, 1],
                theta_resolution=32,
                z_resolution=max(4, int(seg.length_mm / 10)),
            )
            # Add segment index as scalar for colour-coded geometry view
            outer["segment_id"] = np.full(outer.n_points, float(i))
            meshes.append(outer)

        combined = meshes[0].copy() if len(meshes) == 1 else pv.merge(meshes)
        return combined, "segment_id"

    def _setup_scalars(self, mesh, default_scalar, result, render_mode, modal_mode, geom):
        """Determine which scalar field to visualise and its colour range."""
        import numpy as np

        if render_mode == "GEOMETRY" or result is None:
            return default_scalar, "tab10", 0, len(geom.segments) - 1, "Segment"

        if render_mode == "STRESS":
            static = result.get("static", {})
            s_max = static.get("max_von_mises_mpa", 300.0)
            # Interpolate stress linearly along shaft (placeholder heuristic)
            z_arr = mesh.points[:, 2]
            L = geom.total_length_mm
            stress_field = s_max * (1 - z_arr / L) * 0.8
            mesh["stress_mpa"] = stress_field.clip(0, s_max)
            return "stress_mpa", CMAP_STRESS, 0.0, s_max, "MPa"

        if render_mode == "DEFLECTION":
            static = result.get("static", {})
            d_max = static.get("max_deflection_mm", 0.05)
            z_arr = mesh.points[:, 2]
            L = geom.total_length_mm
            front_z = L * 0.2
            # Cantilever-like deflection profile
            defl_field = d_max * np.maximum(0, 1 - z_arr / front_z) * 0.8
            mesh["deflection_mm"] = defl_field.clip(0, d_max)
            return "deflection_mm", CMAP_DEFLECTION, 0.0, d_max, "mm"

        if render_mode == "MODAL":
            modal = result.get("modal", {})
            modes = modal.get("mode_shapes", [])
            mode_data = next((m for m in modes if m["mode_number"] == modal_mode), None)
            amp = mode_data["max_displacement_mm"] if mode_data else 1.0
            z_arr = mesh.points[:, 2]
            L = geom.total_length_mm
            n = modal_mode
            modal_field = amp * np.sin(n * math.pi * z_arr / L)
            mesh["modal_disp"] = modal_field
            return "modal_disp", CMAP_MODAL, -amp, amp, "mm (normalised)"

        return default_scalar, "tab10", 0, len(geom.segments) - 1, "Segment"

    def _add_bearing_ring(self, pl, brg):
        """Add a coloured torus at the bearing Z-position."""
        try:
            R_o = brg.outer_diameter_mm / 2
            R_i = brg.bore_diameter_mm / 2
            ring = pv.Disc(
                center=[0, 0, brg.z_position_mm],
                normal=[0, 0, 1],
                inner=R_i,
                outer=R_o,
                r_res=2,
                c_res=32,
            )
            color = "#00D4B1" if brg.role == "LOCATING" else "#5A8FFF"
            pl.add_mesh(ring, color=color, opacity=0.6, lighting=True)
        except Exception:
            pass   # Non-critical — continue without the ring

    def _placeholder_response(self, w, h, mode):
        """Return a minimal base64-encoded 1×1 transparent PNG as placeholder."""
        # 1×1 grey PNG (smallest valid PNG)
        placeholder = (
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
            "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
        )
        return {
            "image_b64": placeholder,
            "width_px": w,
            "height_px": h,
            "colorbar_min": 0.0,
            "colorbar_max": 1.0,
            "colorbar_unit": "N/A",
        }
