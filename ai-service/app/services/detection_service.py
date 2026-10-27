from time import perf_counter

from PIL import Image

from app.core.config import get_settings
from app.models import model_loader
from app.models.schemas import BoundingBox, DetectionItem, DetectionResponse, TopPrediction


class ModelUnavailableError(RuntimeError):
    pass


class InferenceError(RuntimeError):
    pass


def _class_name(names: object, class_id: int) -> str:
    if isinstance(names, dict):
        return str(names.get(class_id, class_id))
    if isinstance(names, (list, tuple)) and 0 <= class_id < len(names):
        return str(names[class_id])
    return str(class_id)


def _garment_family(class_name: str) -> str:
    """Group YOLO classes so one outfit photo can keep top + bottom + outerwear."""
    key = (class_name or "").strip().lower().replace("-", "_").replace(" ", "_")
    if any(token in key for token in ("trouser", "pants", "jean", "skirt")) or key == "shorts":
        return "BOTTOM"
    if "outwear" in key or "outerwear" in key or "jacket" in key or "coat" in key or key == "vest":
        return "OUTERWEAR"
    if key.endswith("_top") or key == "top" or "shirt" in key or (
        "sling" in key and "dress" not in key
    ):
        return "TOP"
    if "dress" in key:
        return "DRESS"
    return f"OTHER:{key}"


def _box_area(item: DetectionItem) -> float:
    box = item.bounding_box
    return max(0.0, box.x2 - box.x1) * max(0.0, box.y2 - box.y1)


def _prefer_diverse_detections(detections: list[DetectionItem]) -> list[DetectionItem]:
    """Keep the strongest box per garment family (áo / quần / áo khoác / váy)."""
    if not detections:
        return []
    ordered = sorted(detections, key=lambda item: (item.confidence, _box_area(item)), reverse=True)
    selected: list[DetectionItem] = []
    seen_families: set[str] = set()
    for item in ordered:
        family = _garment_family(item.class_name)
        if family in seen_families:
            continue
        selected.append(item)
        seen_families.add(family)
    return selected


def _run_predict(image: Image.Image, conf: float) -> list[DetectionItem]:
    model = model_loader.get_model()
    if model is None:
        raise ModelUnavailableError("AI model is not available.")

    results = model.predict(
        source=image,
        conf=conf,
        iou=0.5,
        max_det=30,
        imgsz=960,
        verbose=False,
    )
    detections: list[DetectionItem] = []
    if not results:
        return detections

    result = results[0]
    boxes = getattr(result, "boxes", None)
    names = getattr(result, "names", getattr(model, "names", {}))
    if boxes is None:
        return detections

    coordinates = boxes.xyxy.cpu().tolist()
    confidences = boxes.conf.cpu().tolist()
    class_ids = boxes.cls.cpu().tolist()
    for coordinate, confidence, raw_class_id in zip(
        coordinates, confidences, class_ids, strict=True
    ):
        if float(confidence) < conf:
            continue
        class_id = int(raw_class_id)
        detections.append(
            DetectionItem(
                class_id=class_id,
                class_name=_class_name(names, class_id),
                confidence=round(float(confidence), 6),
                bounding_box=BoundingBox(
                    x1=float(coordinate[0]),
                    y1=float(coordinate[1]),
                    x2=float(coordinate[2]),
                    y2=float(coordinate[3]),
                ),
            )
        )
    return detections


def detect_clothing(image: Image.Image) -> DetectionResponse:
    settings = get_settings()

    if not model_loader.is_model_ready() and not model_loader.load_model():
        raise ModelUnavailableError("AI model is not available.")

    if model_loader.get_model() is None:
        raise ModelUnavailableError("AI model is not available.")

    started_at = perf_counter()
    try:
        primary_conf = settings.confidence_threshold
        detections = _prefer_diverse_detections(_run_predict(image, primary_conf))
        families = {_garment_family(item.class_name) for item in detections}
        # Second pass at a lower score so secondary pieces (skirt/top under jacket) can appear.
        if len(families) < 2:
            rescue_conf = min(primary_conf, 0.22)
            rescued = _run_predict(image, rescue_conf)
            detections = _prefer_diverse_detections(detections + rescued)
    except ModelUnavailableError:
        raise
    except Exception as exception:
        raise InferenceError("The image could not be analyzed.") from exception

    inference_time_ms = round((perf_counter() - started_at) * 1000, 3)
    best = detections[0] if detections else None
    top_prediction = (
        TopPrediction(class_name=best.class_name, confidence=best.confidence)
        if best is not None
        else None
    )
    return DetectionResponse(
        detections=detections,
        top_prediction=top_prediction,
        inference_time_ms=inference_time_ms,
    )


def crop_top_detection(image: Image.Image, detection: DetectionItem | None) -> Image.Image:
    """Crop the best detected garment, or return a copy of the full image."""
    if detection is None:
        return image.copy()

    box = detection.bounding_box
    left = max(0, min(image.width, int(box.x1)))
    top = max(0, min(image.height, int(box.y1)))
    right = max(0, min(image.width, int(round(box.x2))))
    bottom = max(0, min(image.height, int(round(box.y2))))
    if right <= left or bottom <= top:
        return image.copy()
    return image.crop((left, top, right, bottom))


def crop_union_detections(image: Image.Image, detections: list[DetectionItem]) -> Image.Image:
    """Crop the union of all detection boxes for multi-garment outfit color/style."""
    if not detections:
        return image.copy()
    if len(detections) == 1:
        return crop_top_detection(image, detections[0])

    left = min(int(item.bounding_box.x1) for item in detections)
    top = min(int(item.bounding_box.y1) for item in detections)
    right = max(int(round(item.bounding_box.x2)) for item in detections)
    bottom = max(int(round(item.bounding_box.y2)) for item in detections)
    left = max(0, min(image.width, left))
    top = max(0, min(image.height, top))
    right = max(0, min(image.width, right))
    bottom = max(0, min(image.height, bottom))
    if right <= left or bottom <= top:
        return image.copy()
    return image.crop((left, top, right, bottom))
