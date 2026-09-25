from dataclasses import dataclass

from app.core.config import get_settings


@dataclass(frozen=True)
class RankingScores:
    visual_similarity: float
    style_score: float
    color_score: float
    metadata_score: float


def weighted_score(scores: RankingScores) -> float:
    """Combine normalized candidate scores using environment-configured weights."""
    settings = get_settings()
    total_weight = settings.ranking_weight_sum
    if total_weight <= 0:
        raise ValueError("At least one recommendation weight must be positive.")
    values = (
        max(0.0, min(1.0, scores.visual_similarity)),
        max(0.0, min(1.0, scores.style_score)),
        max(0.0, min(1.0, scores.color_score)),
        max(0.0, min(1.0, scores.metadata_score)),
    )
    result = (
        settings.ranking_visual_weight * values[0]
        + settings.ranking_style_weight * values[1]
        + settings.ranking_color_weight * values[2]
        + settings.ranking_metadata_weight * values[3]
    ) / total_weight
    return round(result, 6)
