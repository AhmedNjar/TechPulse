# TechPulse Spindle Engine — Python Simulation Core

FastAPI backend serving the **TechPulse Spindle R&D Suite** Compose Desktop app.

## Prerequisites

| Requirement | Version |
|---|---|
| Python | 3.11+ |
| ANSYS Mechanical APDL *(optional)* | 2022 R2+ |
| pip | 23+ |

## Quick Start

```bash
# 1. Create virtual environment
python -m venv .venv
source .venv/bin/activate        # Linux/macOS
.venv\Scripts\activate.bat       # Windows

# 2. Install dependencies
pip install -r requirements.txt

# 3. Start the engine
uvicorn main:app --host 127.0.0.1 --port 8000 --reload
```

The Kotlin app will connect to `http://127.0.0.1:8000` automatically.
Interactive API docs: http://127.0.0.1:8000/docs

## Architecture

```
python_engine/
├── main.py                  ← FastAPI app, routes, lifespan
├── schemas.py               ← Pydantic request/response models
├── job_store.py             ← In-memory job result store
├── core/
│   ├── monte_carlo_runner.py   ← Probabilistic structural analysis
│   ├── genetic_algorithm.py    ← NSGA-II multi-objective GA (DEAP)
│   ├── ml_surrogate_model.py   ← Scikit-learn surrogate model
│   └── ansys_connector.py      ← PyMAPDL interface to ANSYS
└── models/
    └── surrogate_v1.pkl        ← Pre-trained surrogate (add after training)
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/simulate` | Submit simulation job (Monte Carlo / Static / ML) |
| `GET`  | `/api/results/{id}` | Poll for job result |
| `GET`  | `/api/optimisation/stream?config_id=...` | SSE stream of GA generations |
| `GET`  | `/api/health` | Engine health check |

## ANSYS Configuration

Set the environment variable before starting:
```bash
export ANSYS_MAPDL_EXEC=/usr/ansys_inc/v222/ansys/bin/ansys222
```

## ML Surrogate Training

```python
from core.ml_surrogate_model import MLSurrogateModel
import numpy as np

model = MLSurrogateModel()
X = np.load("data/features.npy")   # Collected from FEA runs
y = np.load("data/targets.npy")
model.train(X, y)
model.save("models/surrogate_v1.pkl")
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `TECHPULSE_ENGINE_URL` | `http://127.0.0.1:8000` | Engine URL (used by Kotlin app) |
| `ANSYS_MAPDL_EXEC` | `/usr/ansys_inc/v222/...` | Path to ANSYS executable |
