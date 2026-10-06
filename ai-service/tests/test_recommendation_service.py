from app.services.recommendation_service import RankingScores, weighted_score


def test_weighted_score_uses_configured_default_weights() -> None:
    score = weighted_score(
        RankingScores(
            visual_similarity=1.0,
            style_score=0.5,
            color_score=0.5,
            metadata_score=0.0,
        )
    )

    assert score == 0.75
