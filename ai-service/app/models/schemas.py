from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    parts = value.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class BoundingBox(ApiModel):
    x1: float
    y1: float
    x2: float
    y2: float


class DetectionItem(ApiModel):
    class_id: int
    class_name: str
    confidence: float
    bounding_box: BoundingBox


class TopPrediction(ApiModel):
    class_name: str
    confidence: float


class DominantColor(ApiModel):
    name: str
    ratio: float = Field(ge=0.0, le=1.0)


class StylePrediction(ApiModel):
    style: str
    confidence: float = Field(ge=0.0, le=1.0)


class DetectionResponse(ApiModel):
    detections: list[DetectionItem]
    top_prediction: TopPrediction | None
    inference_time_ms: float


class AnalysisResponse(ApiModel):
    detections: list[DetectionItem]
    top_prediction: TopPrediction | None
    dominant_colors: list[DominantColor]
    style_prediction: StylePrediction | None
    detection_fallback: bool
    embedding_generated: bool
    inference_time_ms: float


class HealthResponse(ApiModel):
    service: str = "ai-service"
    status: str
    model_loaded: bool
    model_path: str | None = None
    fashion_clip_loaded: bool = False


class ErrorResponse(BaseModel):
    code: str
    message: str
