"""TechPulse Spindle Engine — FastAPI Backend v5"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from routers import fea_router, mc_router, sobol_router

app = FastAPI(title="TechPulse Spindle Engine", version="5.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"],
                   allow_methods=["*"], allow_headers=["*"])
app.include_router(fea_router.router)
app.include_router(mc_router.router)
app.include_router(sobol_router.router)

@app.get("/health")
def health(): return {"status": "ok", "version": "5.0.0"}
