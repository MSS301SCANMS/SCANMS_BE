from io import BytesIO

from fastapi import UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError

from app.core.config import get_settings

ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp"}


class InvalidImageError(ValueError):
    pass


class UnsupportedImageTypeError(InvalidImageError):
    pass


class ImageTooLargeError(InvalidImageError):
    pass


async def decode_upload(upload: UploadFile) -> Image.Image:
    settings = get_settings()
    content_type = (upload.content_type or "").lower().split(";", maxsplit=1)[0]
    if content_type not in ALLOWED_IMAGE_TYPES:
        raise UnsupportedImageTypeError(
            "Unsupported image format. Use JPEG, PNG, or WebP."
        )

    content = await upload.read(settings.max_image_size_bytes + 1)
    await upload.close()

    if not content:
        raise InvalidImageError("Image file is empty.")
    if len(content) > settings.max_image_size_bytes:
        raise ImageTooLargeError(
            f"Image exceeds the {settings.max_image_size_mb} MB size limit."
        )

    try:
        with Image.open(BytesIO(content)) as source:
            source.verify()
        with Image.open(BytesIO(content)) as source:
            return ImageOps.exif_transpose(source).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError) as exception:
        raise InvalidImageError("Image file is corrupted or cannot be decoded.") from exception
