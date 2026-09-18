from fastapi.testclient import TestClient

from app.main import app


def test_health_returns_valid_state() -> None:
    with TestClient(app) as client:
        response = client.get("/api/v1/ai/health")

    assert response.status_code == 200
    payload = response.json()
    assert payload["service"] == "ai-service"
    assert payload["status"] in {"UP", "DEGRADED"}
    assert isinstance(payload["modelLoaded"], bool)
