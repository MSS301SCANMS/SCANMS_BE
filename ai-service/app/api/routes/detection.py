from fastapi import APIRouter, File, HTTPException, UploadFile, status

from app.models.schemas import DetectionResponse, ErrorResponse
from app.services.detection_service import (
    InferenceError,
    ModelUnavailableError,
    detect_clothing,
)
from app.utils.image_utils import (
    ImageTooLargeError,
    InvalidImageError,
    UnsupportedImageTypeError,
    decode_upload,
)

router = APIRouter(prefix="/api/v1/ai", tags=["AI Detection"])

ERROR_RESPONSES = {
    400: {"model": ErrorResponse},
    413: {"model": ErrorResponse},
    500: {"model": ErrorResponse},
    503: {"model": ErrorResponse},
}


@router.post("/detect", response_model=DetectionResponse, responses=ERROR_RESPONSES)
async def detect(image: UploadFile = File(...)) -> DetectionResponse:
    try:
        decoded_image = await decode_upload(image)
        return detect_clothing(decoded_image)
    except UnsupportedImageTypeError as exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "UNSUPPORTED_IMAGE_FORMAT", "message": str(exception)},
        ) from exception
    except ImageTooLargeError as exception:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail={"code": "IMAGE_TOO_LARGE", "message": str(exception)},
        ) from exception
    except InvalidImageError as exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={"code": "INVALID_IMAGE", "message": str(exception)},
        ) from exception
    except ModelUnavailableError as exception:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "MODEL_UNAVAILABLE", "message": str(exception)},
        ) from exception
    except InferenceError as exception:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail={"code": "INFERENCE_ERROR", "message": str(exception)},
        ) from exception
