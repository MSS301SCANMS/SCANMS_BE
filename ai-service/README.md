# SCANMS AI Service

Stateless FastAPI service that combines the existing Ultralytics YOLO clothing model, CIELAB/K-means dominant-color extraction, FashionCLIP embeddings, and zero-shot style classification. Predictions are advisory only; Product Service remains responsible for seller confirmation and product persistence.

## Architecture

```text
Frontend -> SCANMS Gateway -> Product Service -> AI Service
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
| `CONFIDENCE_THRESHOLD` | `0.28` | Primary YOLO confidence; a second pass at ~0.22 runs when only one garment family is found |
| `MAX_IMAGE_SIZE_MB` | `10` | Upload size limit |
| `COLOR_CLUSTERS` | `3` | Number of Lab K-means clusters |
| `COLOR_RESIZE_PIXELS` | `128` | Maximum color-analysis width/height |
| `FASHION_CLIP_ENABLED` | `true` | Enable optional FashionCLIP inference |
| `FASHION_CLIP_MODEL` | `patrickjohncyh/fashion-clip` | Hugging Face model id or local directory |
| `FASHION_CLIP_LOCAL_FILES_ONLY` | `true` | Avoid downloads; set `false` once to populate the cache |
| `FASHION_CLIP_DEVICE` | `auto` | `auto`, `cpu`, or a Torch device such as `cuda` |
| `RANKING_*_WEIGHT` | `0.60/0.20/0.10/0.10` | Visual/style/color/metadata ranking weights |
| `ALLOWED_ORIGINS` | Local frontend/gateway origins | Comma-separated CORS allowlist |
| `KAFKA_ENABLED` | `true` | Connect to SCANMS Kafka (docker-compose `kafka`) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker address |
| `KAFKA_GROUP_ID` | `ai-service` | Consumer group shown on Admin → Giám sát |
| `KAFKA_ANALYSIS_TOPIC` | `ai.analysis.completed.v1` | Topic phát sau `/analyze` |

Copy `.env.example` to `.env` when local overrides are needed.

Kafka is optional at runtime: if the broker is down, the HTTP API still serves detection/analyze and health reports `kafkaConnected=false`.

## Local setup

Use **Python 3.11**. Dependencies are pinned in `requirements.txt` to a known-good stack (`torch==2.14.1`, `torchvision==0.29.1`, `torchaudio==2.11.0`, `transformers==5.20.2`). That set satisfies `transformers` requiring **PyTorch ≥ 2.6** and avoids mixing leftover `torch==2.5.1+cu121` wheels.

```bash
cd ai-service
python -m venv venv
```

Activate the environment, then install and run:

```bash
# Windows: venv\Scripts\activate
# Linux/macOS: source venv/bin/activate
python -m pip install -U pip
python -m pip install -r requirements.txt
python -c "import torch, torchvision; print(torch.__version__, torchvision.__version__, torch.cuda.is_available())"
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
# or: python main.py
```

If you previously installed CUDA-pinned Torch 2.5.x packages, uninstall them before reinstalling:

```bash
pip uninstall -y torch torchvision torchaudio
pip install -r requirements.txt
```

Optional GPU: install a matching `torch` / `torchvision` / `torchaudio` trio from [pytorch.org](https://pytorch.org) (same CUDA build), then keep the rest of `requirements.txt`. Do not mix PyPI CPU Torch 2.14 with old `+cu121` vision/audio wheels.

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

Alternatively, create a regular **Python** run configuration using the included entrypoint:

```text
Script path: <repository>/ai-service/main.py
Working directory: <repository>/ai-service
Python interpreter: <repository>/ai-service/.venv/Scripts/python.exe
```

If the Python menus are absent, install/enable JetBrains' Python plugin and restart the IDE.

## Endpoints

- `GET /api/v1/ai/health` (includes `kafkaConnected`, group, topic)
- `POST /api/v1/ai/detect` with multipart field `image`
- `POST /api/v1/ai/analyze` with multipart field `image` (recommended full pipeline; publishes Kafka event when connected)
- Swagger UI: `GET /docs`

### Kafka

With `KAFKA_ENABLED=true` and broker at `KAFKA_BOOTSTRAP_SERVERS`:

1. Creates topic `ai.analysis.completed.v1` (3 partitions) if missing
2. Joins consumer group `ai-service` (visible on Admin system health)
3. After each successful `/analyze`, produces a compact JSON event (class, colors, style, latency)

Start broker first: `docker compose up -d kafka` from `SCANMS_BE`.

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

## Product Service integration

Product Service exposes `POST /api/v1/products/ai-analyze`. It forwards the multipart image to `/api/v1/ai/analyze` through OpenFeign and wraps the suggestion in the standard SCANMS `ApiResponse`. Product Service does not persist the suggestion automatically. The legacy `/detect` endpoint remains available for backward compatibility.

PGVector retrieval is not enabled in the current Product Service, so no database or fake similar-product endpoint is created here. `recommendation_service.py` contains the configurable weighted-score calculation ready for the Product-owned vector retrieval phase.

Future service-to-service authentication can use `X-Internal-Api-Key` or JWT. No authentication secret is stored by this baseline implementation.
