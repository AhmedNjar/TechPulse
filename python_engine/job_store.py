"""
job_store.py
Simple in-memory job/result store for MVP development.

In production, replace with:
  - Redis (via redis-py / aioredis) for persistence across restarts
  - PostgreSQL with async SQLAlchemy for full relational storage

Usage:
  from job_store import job_store
  job_store["my-result-id"] = {"status": "RUNNING", ...}
"""

# Shared mutable dictionary — thread-safe for asyncio (single-threaded event loop)
# For multi-worker deployments, use Redis instead.
job_store: dict = {}
