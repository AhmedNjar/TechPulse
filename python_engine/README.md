# TechPulse Spindle Engine — python_engine/

## Run
```bash
cd python_engine && pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## Endpoints
| Method | Path | Module |
|--------|------|--------|
| POST | /api/fea/solve | SpindleFEA v5 |
| POST | /api/mc/run | Monte Carlo v2 |
| POST | /api/sobol/train | Train surrogate |
| POST | /api/sobol/run | Sobol indices |
