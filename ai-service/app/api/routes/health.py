from pathlib import Path

from fastapi import APIRouter

from app.core.config import get_settings
from app.models.model_loader import is_model_ready
from app.models.fashion_clip_loader import is_fashion_clip_ready
from app.models.schemas import HealthResponse
from app.services.kafka_service import is_kafka_connected, kafka_bootstrap

router = APIRouter(prefix="/api/v1/ai", tags=["AI Health"])


def _public_model_path() -> str:
    configured_path = get_settings().model_path
    if configured_path.is_absolute():
        return Path("weights", configured_path.name).as_posix()
    return configured_path.as_posix()


@router.get("/health", response_model=HealthResponse, response_model_exclude_none=True)
def health() -> HealthResponse:
    settings = get_settings()
    ready = is_model_ready()
    fashion_ready = is_fashion_clip_ready()
    kafka_ok = is_kafka_connected()
    return HealthResponse(
        status="UP" if ready and fashion_ready else "DEGRADED",
        model_loaded=ready,
        model_path=_public_model_path() if ready else None,
        fashion_clip_loaded=fashion_ready,
        kafka_connected=kafka_ok,
        kafka_bootstrap=kafka_bootstrap(),
        kafka_group_id=settings.kafka_group_id if settings.kafka_enabled else None,
        kafka_topic=settings.kafka_analysis_topic if settings.kafka_enabled else None,
    )
