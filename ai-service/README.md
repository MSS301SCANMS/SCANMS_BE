# SCANMS AI Service

Stateless FastAPI service that combines the existing Ultralytics YOLO clothing model, CIELAB/K-means dominant-color extraction, FashionCLIP embeddings, and zero-shot style classification. Predictions are advisory only; Catalog Service remains responsible for seller confirmation and product persistence.

## Architecture

```text
Frontend -> SCANMS Gateway -> Catalog Service -> AI Service
                                                    |-> YOLO
                                                    |-> Lab + K-means
                                                    `-> FashionCLIP
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
| `COLOR_CLUSTERS` | `3` | Number of Lab K-means clusters |
| `COLOR_RESIZE_PIXELS` | `128` | Maximum color-analysis width/height |
| `FASHION_CLIP_ENABLED` | `true` | Enable optional FashionCLIP inference |
| `FASHION_CLIP_MODEL` | `patrickjohncyh/fashion-clip` | Hugging Face model id or local directory |
| `FASHION_CLIP_LOCAL_FILES_ONLY` | `true` | Avoid downloads; set `false` once to populate the cache |
| `FASHION_CLIP_DEVICE` | `auto` | `auto`, `cpu`, or a Torch device such as `cuda` |
| `RANKING_*_WEIGHT` | `0.60/0.20/0.10/0.10` | Visual/style/color/metadata ranking weights |
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

FashionCLIP is approximately 605 MB. For the first online run, set `FASHION_CLIP_LOCAL_FILES_ONLY=false`; Transformers downloads it into the Hugging Face cache. For offline/production deployments, pre-populate that cache or point `FASHION_CLIP_MODEL` at a mounted local model directory and keep local-only mode enabled. If FashionCLIP is unavailable, `/analyze` deliberately returns YOLO and color results with `embeddingGenerated=false` and `stylePrediction=null`.

## IntelliJ IDEA / PyCharm interpreter

The repository root is a Java/Maven project using JDK 21, while `ai-service` is a separate Python module. Configure the Python module with:

- Interpreter: `<repository>/ai-service/.venv/Scripts/python.exe`
- Content/source root: `<repository>/ai-service`
- Working directory: `<repository>/ai-service`

In IntelliJ IDEA/PyCharm, open **Settings → Python → Interpreter** (the exact label can include the project name), choose **Add Interpreter → Add Local Interpreter → Existing**, and select the interpreter above. If `ai-service` is not listed as a separate module, add/import the `ai-service` directory in **File → Project Structure → Modules**, then assign this Python SDK to that module. Keep the root Maven modules on JDK 21.

For an Uvicorn run configuration use:

```text
Module name: uvicorn
Parameters: app.main:app --reload --host 0.0.0.0 --port 8000
Working directory: <repository>/ai-service
Python interpreter: <repository>/ai-service/.venv/Scripts/python.exe
```

If the Python menus are absent, install/enable JetBrains' Python plugin and restart the IDE.

## Endpoints

- `GET /api/v1/ai/health`
- `POST /api/v1/ai/detect` with multipart field `image`
- `POST /api/v1/ai/analyze` with multipart field `image` (recommended full pipeline)
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

The full analysis response adds `dominantColors`, `stylePrediction`, `detectionFallback`, and `embeddingGenerated`. Raw embedding vectors are intentionally not returned to the frontend-facing API.

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

Catalog Service exposes `POST /api/v1/products/ai-analyze`. It forwards the multipart image to `/api/v1/ai/analyze` through OpenFeign and wraps the suggestion in the standard SCANMS `ApiResponse`. Catalog Service does not persist the suggestion automatically. The legacy `/detect` endpoint remains available for backward compatibility.

PGVector retrieval is not enabled in the current Catalog Service, so no database or fake similar-product endpoint is created here. `recommendation_service.py` contains the configurable weighted-score calculation ready for the Catalog-owned vector retrieval phase.

Future service-to-service authentication can use `X-Internal-Api-Key` or JWT. No authentication secret is stored by this baseline implementation.
