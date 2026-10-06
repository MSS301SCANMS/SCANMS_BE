from typing import Any

import numpy as np
from PIL import Image

from app.models import fashion_clip_loader


class EmbeddingUnavailableError(RuntimeError):
    pass


def generate_image_embedding(image: Image.Image) -> np.ndarray:
    if not fashion_clip_loader.is_fashion_clip_ready():
        fashion_clip_loader.load_fashion_clip()
    components = fashion_clip_loader.get_fashion_clip_components()
    if components is None:
        raise EmbeddingUnavailableError("Fashion model is currently unavailable.")

    model, processor, device, _ = components
    try:
        import torch

        inputs: dict[str, Any] = processor(images=image, return_tensors="pt")
        inputs = {key: value.to(device) for key, value in inputs.items()}
        with torch.inference_mode():
            features = model.get_image_features(**inputs)
            if hasattr(features, "pooler_output"):
                features = features.pooler_output
            features = torch.nn.functional.normalize(features, dim=-1)
        return features[0].detach().cpu().numpy().astype(np.float32)
    except Exception as exception:
        raise EmbeddingUnavailableError(
            "Fashion embedding could not be generated."
        ) from exception
