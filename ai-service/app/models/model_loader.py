import logging
from pathlib import Path
from threading import Lock
from typing import Any

from app.core.config import get_settings

logger = logging.getLogger(__name__)

_model: Any | None = None
_load_error: str | None = None
_lock = Lock()


def _resolved_model_path() -> Path:
    configured_path = get_settings().model_path.expanduser()
    if configured_path.is_absolute():
        return configured_path
    return Path.cwd() / configured_path


def load_model() -> bool:
    """Load YOLO once and return whether the model is ready."""
    global _model, _load_error

    if _model is not None:
        return True

    with _lock:
        if _model is not None:
            return True

        model_path = _resolved_model_path()
        if not model_path.is_file():
            _load_error = "Configured model file was not found."
            logger.warning("AI model is unavailable: configured model file was not found")
            return False

        try:
            from ultralytics import YOLO

            _model = YOLO(str(model_path))
            _load_error = None
            logger.info("AI model loaded successfully")
            return True
        except Exception as exception:  # Ultralytics can raise several backend errors.
            _model = None
            _load_error = "The configured model could not be loaded."
            logger.error("AI model is unavailable: %s", type(exception).__name__)
            return False


def get_model() -> Any | None:
    return _model


def is_model_ready() -> bool:
    return _model is not None


def get_load_error() -> str | None:
    return _load_error
