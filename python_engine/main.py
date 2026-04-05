"""
main.py  v2.0 — TechPulse Spindle R&D Suite FastAPI Engine
Offline-safe: all endpoints work without ANSYS if ANSYS_DRY_RUN=1.

Endpoints:
  POST /api/simulate              — Full analysis (Static + Modal + MC)
  GET  /api/results/{id}          — Poll result
  POST /api/render                — Request PyVista render image
  GET  /api/optimisation/stream   — SSE: GA generation events
  POST /api/taguchi               — Process Taguchi experiment
  POST /api/project/save          — Persist .spindle project JSON
  GET  /api/project/load/{name}   — Load .spindle project
  GET  /api/project/list          — List saved projects
  GET  /api/health                — Engine health
  GET  /docs                      — Swagger UI
"""

import asyncio
import json
import os
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from schemas import (
    SimulateRequest, SimulateResponse,
    ResultResponse, RenderRequest, RenderResponse,
    HealthResponse, OptimisationStreamEvent, SpindleProject,
)
from core.ansys_connector import AnsysConnector
from core.monte_carlo_runner import MonteCarloRunner
from core.pyvista_renderer import PyVistaRenderer
from core.taguchi_analyzer import TaguchiAnalyzer
from core.genetic_algorithm import GeneticAlgorithm
from core.ml_surrogate_model import MLSurrogateModel
from job_store import job_store

# ── Directories ────────────────────────────────────────────────────
PROJECTS_DIR = Path(os.getenv("TECHPULSE_PROJECTS_DIR", "./projects"))
PROJECTS_DIR.mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    print("[Engine v2.0] Starting up…")
    app.state.ml_model  = MLSurrogateModel()
    app.state.renderer  = PyVistaRenderer()
    app.state.ansys     = AnsysConnector()
    try:
        app.state.ml_model.load("models/surrogate_v2.pkl")
        print("[Engine] ML surrogate loaded")
    except Exception:
        print("[Engine] ML surrogate not found — analytical fallback active")
    print("[Engine] Ready ✓")
    yield
    print("[Engine] Shutdown")


app = FastAPI(
    title="TechPulse Spindle Engine",
    version="2.0.0",
    description="Multi-physics simulation engine: Static FEA + Modal + Monte Carlo + GA",
    lifespan=lifespan,
    docs_url="/docs",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://127.0.0.1:*", "http://localhost:*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


# ══════════════════════════════════════════════════════════════════
#  SIMULATION
# ══════════════════════════════════════════════════════════════════

@app.post("/api/simulate", response_model=SimulateResponse)
async def submit_simulation(request: SimulateRequest, bg: BackgroundTasks):
    result_id = str(uuid.uuid4())
    job_store[result_id] = {
        "status": "PENDING",
        "config_id": request.config_id,
        "mode": request.mode.value,
    }
    bg.add_task(_run_full_analysis, result_id, request)
    est = 60 if AnsysConnector.is_available() else 15
    return SimulateResponse(
        result_id=result_id,
        status="PENDING",
        message=f"Analysis queued — mode: {request.mode.value}",
        estimated_duration_sec=est,
    )


async def _run_full_analysis(result_id: str, req: SimulateRequest):
    job_store[result_id]["status"] = "RUNNING"
    t0 = time.time()
    try:
        project = req.project

        # ── Static + Modal (ANSYS or dry-run) ──────────────────
        fea = app.state.ansys.run_full_analysis(project)

        # ── Monte Carlo ─────────────────────────────────────────
        mc_result = None
        if req.mode.value in ("MONTE_CARLO", "FULL_ANALYSIS"):
            runner = MonteCarloRunner(project)
            mc_result = runner.run()

        # ── Render geometry (GEOMETRY mode — always fast) ───────
        render = app.state.renderer.render(project, fea, "GEOMETRY")

        # Attach render image to static result
        if fea.get("static") and render.get("image_b64"):
            fea["static"]["render_image_b64"] = render["image_b64"]

        job_store[result_id].update({
            "status": "COMPLETED",
            "timestamp_ms": int(time.time() * 1000),
            "duration_sec": round(time.time() - t0, 2),
            "static": fea.get("static"),
            "modal": fea.get("modal"),
            "monte_carlo": mc_result,
        })
    except Exception as exc:
        import traceback
        job_store[result_id] = {
            "status": "FAILED",
            "error_message": str(exc),
            "traceback": traceback.format_exc(),
        }


@app.get("/api/results/{result_id}", response_model=ResultResponse)
async def get_result(result_id: str):
    job = job_store.get(result_id)
    if not job:
        raise HTTPException(status_code=404, detail=f"Result '{result_id}' not found")
    return ResultResponse(
        result_id=result_id,
        config_id=job.get("config_id", ""),
        status=job.get("status", "UNKNOWN"),
        error_message=job.get("error_message"),
        result=job if job.get("status") == "COMPLETED" else None,
    )


# ══════════════════════════════════════════════════════════════════
#  PYVISTA RENDER
# ══════════════════════════════════════════════════════════════════

@app.post("/api/render", response_model=RenderResponse)
async def render_image(req: RenderRequest):
    job = job_store.get(req.result_id) if req.result_id else None
    project_data = job.get("project") if job else None

    if not project_data:
        raise HTTPException(status_code=404, detail="No project data for this result")

    project = SpindleProject(**project_data)
    result  = {k: job.get(k) for k in ("static", "modal", "monte_carlo")}

    render = app.state.renderer.render(
        project    = project,
        result     = result,
        render_mode= req.render_mode,
        modal_mode_number = req.modal_mode_number,
        width_px   = req.width_px,
        height_px  = req.height_px,
    )
    return RenderResponse(**render)


# ══════════════════════════════════════════════════════════════════
#  GA OPTIMISATION — SSE STREAM
# ══════════════════════════════════════════════════════════════════

@app.get("/api/optimisation/stream")
async def stream_optimisation(config_id: str):
    job = job_store.get(config_id)
    if not job:
        raise HTTPException(status_code=404, detail=f"Config '{config_id}' not found")

    async def generator():
        ga = GeneticAlgorithm(job.get("project", {}))
        async for event in ga.evolve():
            payload = json.dumps(event)
            yield f"data: {payload}\n\n"
            await asyncio.sleep(0)
        yield 'data: {"event_type":"COMPLETE","generation":-1,"best_fitness":0,' \
              '"mean_fitness":0,"best_deflection_mm":0,"best_freq_hz":0,' \
              '"best_weight_kg":0,"pareto_points":[],"constraint_violations":0}\n\n'

    return StreamingResponse(generator(), media_type="text/event-stream")


# ══════════════════════════════════════════════════════════════════
#  TAGUCHI
# ══════════════════════════════════════════════════════════════════

@app.post("/api/taguchi")
async def process_taguchi(body: dict):
    """
    Process Taguchi experiment data.
    Body keys: array_type, control_factors, responses (or 'demo': true)
    """
    if body.get("demo"):
        result = TaguchiAnalyzer.generate_demo_experiment()
        return result

    analyzer = TaguchiAnalyzer(body.get("array_type", "L9"))
    result   = analyzer.process_experiment(
        control_factors = body["control_factors"],
        responses       = body["responses"],
    )
    return result


@app.get("/api/taguchi/demo")
async def taguchi_demo():
    return TaguchiAnalyzer.generate_demo_experiment()


# ══════════════════════════════════════════════════════════════════
#  PROJECT SAVE / LOAD
# ══════════════════════════════════════════════════════════════════

@app.post("/api/project/save")
async def save_project(project: SpindleProject):
    filename = PROJECTS_DIR / f"{project.metadata.project_id}.spindle"
    with open(filename, "w", encoding="utf-8") as f:
        f.write(project.model_dump_json(indent=2))
    return {"saved": True, "path": str(filename), "project_id": project.metadata.project_id}


@app.get("/api/project/load/{project_id}")
async def load_project(project_id: str):
    filename = PROJECTS_DIR / f"{project_id}.spindle"
    if not filename.exists():
        raise HTTPException(status_code=404, detail=f"Project '{project_id}' not found")
    with open(filename, "r", encoding="utf-8") as f:
        return json.load(f)


@app.get("/api/project/list")
async def list_projects():
    files = sorted(PROJECTS_DIR.glob("*.spindle"))
    projects = []
    for f in files:
        try:
            data = json.loads(f.read_text())
            meta = data.get("metadata", {})
            projects.append({
                "project_id": meta.get("project_id", f.stem),
                "name": meta.get("name", f.stem),
                "last_modified_at": meta.get("last_modified_at", ""),
                "version": meta.get("version", "2.0"),
                "file_size_kb": round(f.stat().st_size / 1024, 1),
            })
        except Exception:
            continue
    return projects


# ══════════════════════════════════════════════════════════════════
#  HEALTH
# ══════════════════════════════════════════════════════════════════

@app.get("/api/health", response_model=HealthResponse)
async def health():
    return HealthResponse(
        status            = "ok",
        ansys_available   = AnsysConnector.is_available(),
        pyvista_available = PyVistaRenderer.is_available(),
        ml_model_loaded   = getattr(app.state.ml_model, "is_loaded", False),
        version           = "2.0.0",
    )
