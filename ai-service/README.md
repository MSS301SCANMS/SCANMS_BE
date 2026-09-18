# SCANMS AI Service

Stateless FastAPI service that loads an existing Ultralytics YOLO clothing model and returns product-classification suggestions. Predictions are advisory only; Catalog Service remains responsible for seller confirmation and product persistence.

## Architecture

```text
Frontend -> SCANMS Gateway -> Catalog Service -> AI Service -> YOLO model
```

The service has no database, does not train models, and does not persist product data.

## Requirements

- Python 3.11+
- A trained Ultralytics model at `weights/best.pt`, or another location configured by `MODEL_PATH`

## Environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `APP_NAME` | `SCANMS AI Service` | OpenAPI application name |
| `APP_VERSION` | `1.0.0` | API version |
| `HOST` | `0.0.0.0` | Uvicorn bind host |
| `PORT` | `8000` | Uvicorn port |
| `MODEL_PATH` | `weights/best.pt` | YOLO weight file |
| `CONFIDENCE_THRESHOLD` | `0.5` | Minimum detection confidence |
| `MAX_IMAGE_SIZE_MB` | `10` | Upload size limit |
| `ALLOWED_ORIGINS` | Local frontend/gateway origins | Comma-separated CORS allowlist |

Copy `.env.example` to `.env` when local overrides are needed.

## Local setup

```bash
cd ai-service
python -m venv .venv
```

Activate the environment, then install and run:

```bash
python -m pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Place the trained file at `weights/best.pt`. The service still starts without it; health becomes `DEGRADED` and detection returns HTTP `503`.

## Endpoints

- `GET /api/v1/ai/health`
- `POST /api/v1/ai/detect` with multipart field `image`
- Swagger UI: `GET /docs`

Example request:

```bash
curl -X POST http://localhost:8000/api/v1/ai/detect \
  -F "image=@shirt.jpg"
```

Example response:

```json
{
  "detections": [
    {
      "classId": 0,
      "className": "short_sleeve_top",
      "confidence": 0.93,
      "boundingBox": {"x1": 120.2, "y1": 80.4, "x2": 420.7, "y2": 560.8}
    }
  ],
  "topPrediction": {"className": "short_sleeve_top", "confidence": 0.93},
  "inferenceTimeMs": 48.2
}
```

## Tests

Tests do not require real model weights:

```bash
pytest
```

## Docker

Build the image:

```bash
docker build -t scanms-ai-service ./ai-service
```

Run it with model weights mounted at runtime:

```bash
docker run --rm -p 8000:8000 \
  -v "$(pwd)/ai-service/weights/best.pt:/models/best.pt:ro" \
  -e MODEL_PATH=/models/best.pt \
  scanms-ai-service
```

## Catalog Service integration

Catalog Service exposes `POST /api/v1/products/ai-analyze`. It forwards the multipart image to this service through OpenFeign and wraps the suggestion in the standard SCANMS `ApiResponse`. Catalog Service does not persist the suggestion automatically.

Future service-to-service authentication can use `X-Internal-Api-Key` or JWT. No authentication secret is stored by this baseline implementation.
