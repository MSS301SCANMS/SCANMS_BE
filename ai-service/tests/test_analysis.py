from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.models.schemas import DetectionResponse
from app.services import analysis_service

from io import BytesIO


def _png_bytes() -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (16, 16), color="white").save(buffer, format="PNG")
    return buffer.getvalue()


def test_analyze_falls_back_to_full_image_when_no_detection(monkeypatch) -> None:
    monkeypatch.setattr(
        analysis_service,
        "detect_clothing",
        lambda _: DetectionResponse(detections=[], top_prediction=None, inference_time_ms=1.0),
    )
    monkeypatch.setattr(
        analysis_service,
        "generate_image_embedding",
        lambda _: (_ for _ in ()).throw(
            analysis_service.EmbeddingUnavailableError("unavailable")
        ),
    )

    with TestClient(app) as client:
        response = client.post(
            "/api/v1/ai/analyze",
            files={"image": ("sample.png", _png_bytes(), "image/png")},
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["detectionFallback"] is True
    assert payload["embeddingGenerated"] is False
    assert payload["dominantColors"][0] == {"name": "white", "ratio": 1.0}


def test_analyze_uses_422_for_unsupported_content() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/ai/analyze",
            files={"image": ("sample.txt", b"text", "text/plain")},
        )

    assert response.status_code == 422
    assert response.json()["code"] == "UNSUPPORTED_IMAGE_FORMAT"
