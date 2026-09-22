import numpy as np

from app.models import fashion_clip_loader
from app.models.schemas import StylePrediction


def classify_style(image_embedding: np.ndarray) -> StylePrediction:
    components = fashion_clip_loader.get_fashion_clip_components()
    if components is None:
        raise RuntimeError("Fashion model is currently unavailable.")

    _, _, _, cached_text_embeddings = components
    text_embeddings = cached_text_embeddings.detach().cpu().numpy()
    normalized_image = image_embedding / max(float(np.linalg.norm(image_embedding)), 1e-12)
    similarities = text_embeddings @ normalized_image
    best_index = int(np.argmax(similarities))
    style = tuple(fashion_clip_loader.STYLE_PROMPTS)[best_index]
    confidence = float(np.clip(similarities[best_index], 0.0, 1.0))
    return StylePrediction(style=style, confidence=round(confidence, 6))
