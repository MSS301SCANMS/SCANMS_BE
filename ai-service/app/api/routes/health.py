from pathlib import Path

from fastapi import APIRouter

from app.core.config import get_settings
from app.models.model_loader import is_model_ready
from app.models.schemas import HealthResponse

router = APIRouter(prefix="/api/v1/ai", tags=["AI Health"])


def _public_model_path() -> str:
    configured_path = get_settings().model_path
    if configured_path.is_absolute():
        return Path("weights", configured_path.name).as_posix()
    return configured_path.as_posix()


@router.get("/health", response_model=HealthResponse, response_model_exclude_none=True)
def health() -> HealthResponse:
    ready = is_model_ready()
    return HealthResponse(
        status="UP" if ready else "DEGRADED",
        model_loaded=ready,
        model_path=_public_model_path() if ready else None,
    )
