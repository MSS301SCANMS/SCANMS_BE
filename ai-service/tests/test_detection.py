from io import BytesIO

from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.services import detection_service


def _png_bytes() -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (8, 8), color="white").save(buffer, format="PNG")
    return buffer.getvalue()


def test_detection_rejects_unsupported_file_type() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/ai/detect",
            files={"image": ("sample.txt", b"not an image", "text/plain")},
        )

    assert response.status_code == 400
    assert response.json()["code"] == "UNSUPPORTED_IMAGE_FORMAT"


def test_detection_returns_503_when_model_is_unavailable(monkeypatch) -> None:
    monkeypatch.setattr(detection_service.model_loader, "is_model_ready", lambda: False)
    monkeypatch.setattr(detection_service.model_loader, "load_model", lambda: False)

    with TestClient(app) as client:
        response = client.post(
            "/api/v1/ai/detect",
            files={"image": ("sample.png", _png_bytes(), "image/png")},
        )

    assert response.status_code == 503
    assert response.json() == {
        "code": "MODEL_UNAVAILABLE",
        "message": "AI model is not available.",
    }
