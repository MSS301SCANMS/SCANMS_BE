import logging
from threading import Lock
from typing import Any

from app.core.config import get_settings

logger = logging.getLogger(__name__)

STYLE_PROMPTS: dict[str, str] = {
    "CASUAL": "a casual fashion item",
    "FORMAL": "a formal fashion item",
    "STREETWEAR": "a streetwear fashion item",
    "BUSINESS": "a business fashion item",
    "SPORT": "a sport fashion item",
}

_model: Any | None = None
_processor: Any | None = None
_device = "cpu"
_style_embeddings: Any | None = None
_load_error: str | None = None
_load_attempted = False
_lock = Lock()


def load_fashion_clip() -> bool:
    """Load FashionCLIP and cache normalized style prompt embeddings once."""
    global _model, _processor, _device, _style_embeddings, _load_error, _load_attempted
    settings = get_settings()
    if not settings.fashion_clip_enabled:
        _load_attempted = True
        _load_error = "FashionCLIP is disabled."
        return False
    if _model is not None:
        return True

    with _lock:
        if _model is not None:
            return True
        if _load_attempted:
            return False
        _load_attempted = True
        try:
            import torch
            from transformers import AutoModelForZeroShotImageClassification, AutoProcessor

            requested_device = settings.fashion_clip_device.lower()
            _device = (
                "cuda"
                if requested_device == "auto" and torch.cuda.is_available()
                else "cpu" if requested_device == "auto" else requested_device
            )
            _processor = AutoProcessor.from_pretrained(
                settings.fashion_clip_model,
                local_files_only=settings.fashion_clip_local_files_only,
            )
            _model = AutoModelForZeroShotImageClassification.from_pretrained(
                settings.fashion_clip_model,
                local_files_only=settings.fashion_clip_local_files_only,
            ).to(_device)
            _model.eval()
            text_inputs = _processor(
                text=list(STYLE_PROMPTS.values()),
                return_tensors="pt",
                padding=True,
            )
            text_inputs = {key: value.to(_device) for key, value in text_inputs.items()}
            with torch.inference_mode():
                text_features = _model.get_text_features(**text_inputs)
                if hasattr(text_features, "pooler_output"):
                    text_features = text_features.pooler_output
                _style_embeddings = torch.nn.functional.normalize(text_features, dim=-1)
            _load_error = None
            logger.info("FashionCLIP model loaded successfully on %s", _device)
            return True
        except Exception as exception:
            _model = None
            _processor = None
            _style_embeddings = None
            _load_error = "FashionCLIP could not be loaded."
            logger.warning("FashionCLIP is unavailable: %s", type(exception).__name__)
            return False


def is_fashion_clip_ready() -> bool:
    return _model is not None and _processor is not None and _style_embeddings is not None


def get_fashion_clip_components() -> tuple[Any, Any, str, Any] | None:
    if not is_fashion_clip_ready():
        return None
    return _model, _processor, _device, _style_embeddings


def get_fashion_clip_error() -> str | None:
    return _load_error
