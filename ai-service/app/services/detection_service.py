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


def detect_clothing(image: Image.Image) -> DetectionResponse:
    settings = get_settings()

    if not model_loader.is_model_ready() and not model_loader.load_model():
        raise ModelUnavailableError("AI model is not available.")

    model = model_loader.get_model()
    if model is None:
        raise ModelUnavailableError("AI model is not available.")

    started_at = perf_counter()
    try:
        results = model.predict(
            source=image,
            conf=settings.confidence_threshold,
            verbose=False,
        )
        detections: list[DetectionItem] = []

        if results:
            result = results[0]
            boxes = getattr(result, "boxes", None)
            names = getattr(result, "names", getattr(model, "names", {}))

            if boxes is not None:
                coordinates = boxes.xyxy.cpu().tolist()
                confidences = boxes.conf.cpu().tolist()
                class_ids = boxes.cls.cpu().tolist()

                for coordinate, confidence, raw_class_id in zip(
                    coordinates, confidences, class_ids, strict=True
                ):
                    if float(confidence) < settings.confidence_threshold:
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
    except ModelUnavailableError:
        raise
    except Exception as exception:
        raise InferenceError("The image could not be analyzed.") from exception

    inference_time_ms = round((perf_counter() - started_at) * 1000, 3)
    detections.sort(
        key=lambda item: (
            item.confidence,
            (item.bounding_box.x2 - item.bounding_box.x1)
            * (item.bounding_box.y2 - item.bounding_box.y1),
        ),
        reverse=True,
    )
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
