import numpy as np


def rgb_to_lab(rgb: np.ndarray) -> np.ndarray:
    """Convert sRGB values in [0, 255] to CIE L*a*b* (D65)."""
    values = np.asarray(rgb, dtype=np.float64) / 255.0
    linear = np.where(
        values <= 0.04045,
        values / 12.92,
        ((values + 0.055) / 1.055) ** 2.4,
    )
    xyz = linear @ np.array(
        [
            [0.4124564, 0.2126729, 0.0193339],
            [0.3575761, 0.7151522, 0.1191920],
            [0.1804375, 0.0721750, 0.9503041],
        ]
    )
    xyz /= np.array([0.95047, 1.0, 1.08883])
    delta = 6 / 29
    transformed = np.where(
        xyz > delta**3,
        np.cbrt(xyz),
        xyz / (3 * delta**2) + 4 / 29,
    )
    x, y, z = np.moveaxis(transformed, -1, 0)
    return np.stack((116 * y - 16, 500 * (x - y), 200 * (y - z)), axis=-1)


COLOR_PALETTE_RGB: dict[str, tuple[int, int, int]] = {
    "black": (15, 15, 15),
    "white": (245, 245, 245),
    "gray": (128, 128, 128),
    "red": (200, 35, 45),
    "orange": (230, 120, 35),
    "yellow": (235, 210, 45),
    "green": (45, 145, 75),
    "blue": (45, 105, 200),
    "navy": (25, 45, 95),
    "purple": (125, 70, 155),
    "pink": (225, 135, 165),
    "brown": (115, 75, 45),
    "beige": (215, 195, 155),
}

_PALETTE_NAMES = tuple(COLOR_PALETTE_RGB)
_PALETTE_LAB = rgb_to_lab(np.array(list(COLOR_PALETTE_RGB.values())))


def nearest_color_name(lab_color: np.ndarray) -> str:
    distances = np.linalg.norm(_PALETTE_LAB - lab_color, axis=1)
    return _PALETTE_NAMES[int(np.argmin(distances))]


def kmeans_lab(
    pixels: np.ndarray, cluster_count: int, max_iterations: int = 30
) -> tuple[np.ndarray, np.ndarray]:
    """Small deterministic K-means implementation for image color extraction."""
    if pixels.size == 0:
        raise ValueError("Cannot cluster an empty image.")

    unique = np.unique(pixels, axis=0)
    k = min(cluster_count, len(unique))
    first_index = int(np.argmax(np.linalg.norm(unique - unique.mean(axis=0), axis=1)))
    centroids = [unique[first_index]]
    while len(centroids) < k:
        distances = np.min(
            np.linalg.norm(unique[:, None, :] - np.array(centroids)[None, :, :], axis=2),
            axis=1,
        )
        centroids.append(unique[int(np.argmax(distances))])
    centers = np.asarray(centroids, dtype=np.float64)

    labels = np.zeros(len(pixels), dtype=np.int64)
    for _ in range(max_iterations):
        distances = np.linalg.norm(pixels[:, None, :] - centers[None, :, :], axis=2)
        new_labels = np.argmin(distances, axis=1)
        new_centers = np.array(
            [
                pixels[new_labels == index].mean(axis=0)
                if np.any(new_labels == index)
                else centers[index]
                for index in range(k)
            ]
        )
        labels = new_labels
        if np.allclose(centers, new_centers, atol=1e-3):
            centers = new_centers
            break
        centers = new_centers
    return centers, labels
