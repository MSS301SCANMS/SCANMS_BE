import numpy as np
from PIL import Image

from app.core.config import get_settings
from app.models.schemas import DominantColor
from app.utils.color_utils import kmeans_lab, nearest_color_name, rgb_to_lab


def extract_dominant_colors(image: Image.Image) -> list[DominantColor]:
    settings = get_settings()
    resized = image.copy()
    resized.thumbnail(
        (settings.color_resize_pixels, settings.color_resize_pixels),
        Image.Resampling.LANCZOS,
    )
    rgb_pixels = np.asarray(resized.convert("RGB"), dtype=np.float64).reshape(-1, 3)
    lab_pixels = rgb_to_lab(rgb_pixels)
    centers, labels = kmeans_lab(lab_pixels, settings.color_clusters)
    counts = np.bincount(labels, minlength=len(centers))

    # Merge clusters that map to the same public color name.
    ratios_by_name: dict[str, float] = {}
    for index, count in enumerate(counts):
        name = nearest_color_name(centers[index])
        ratios_by_name[name] = ratios_by_name.get(name, 0.0) + count / len(labels)

    return [
        DominantColor(name=name, ratio=round(ratio, 6))
        for name, ratio in sorted(ratios_by_name.items(), key=lambda item: item[1], reverse=True)
    ]
