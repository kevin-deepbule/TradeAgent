"""Health-check route for service readiness and refresh settings."""

from typing import Any

from fastapi import APIRouter

from backend.config import REFRESH_SECONDS


router = APIRouter()


@router.get("/api/health")
async def health() -> dict[str, Any]:
    """Return a lightweight status payload for frontend and smoke checks."""
    return {"ok": True, "refreshSeconds": REFRESH_SECONDS}
