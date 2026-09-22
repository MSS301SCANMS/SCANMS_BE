import logging
from time import perf_counter

from PIL import Image

from app.models.schemas import AnalysisResponse
from app.services.color_service import extract_dominant_colors
from app.services.detection_service import crop_top_detection, detect_clothing
from app.services.embedding_service import EmbeddingUnavailableError, generate_image_embedding
from app.services.style_service import classify_style

logger = logging.getLogger(__name__)


def analyze_product_image(image: Image.Image) -> AnalysisResponse:
    started_at = perf_counter()
    detection = detect_clothing(image)
    best_detection = detection.detections[0] if detection.detections else None
    crop = crop_top_detection(image, best_detection)

    try:
        colors = extract_dominant_colors(crop)
    except Exception as exception:
        logger.warning("Color extraction failed: %s", type(exception).__name__)
        colors = []

    embedding_generated = False
    style_prediction = None
    try:
        embedding = generate_image_embedding(crop)
        embedding_generated = True
        style_prediction = classify_style(embedding)
    except EmbeddingUnavailableError as exception:
        logger.info("FashionCLIP fallback used: %s", exception)
    except Exception as exception:
        logger.warning("Style classification failed: %s", type(exception).__name__)

    return AnalysisResponse(
        detections=detection.detections,
        top_prediction=detection.top_prediction,
        dominant_colors=colors,
        style_prediction=style_prediction,
        detection_fallback=best_detection is None,
        embedding_generated=embedding_generated,
        inference_time_ms=round((perf_counter() - started_at) * 1000, 3),
    )
