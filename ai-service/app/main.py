import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.routes import analyze, detection, health
from app.core.config import get_settings
from app.models.model_loader import load_model
from app.models.fashion_clip_loader import load_fashion_clip
from app.models.schemas import ErrorResponse

logger = logging.getLogger(__name__)
settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    load_model()
    load_fashion_clip()
    yield


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Internal-Api-Key"],
)


@app.exception_handler(HTTPException)
async def http_exception_handler(_: Request, exception: HTTPException) -> JSONResponse:
    detail = exception.detail
    if isinstance(detail, dict):
        error = ErrorResponse(
            code=str(detail.get("code", "REQUEST_ERROR")),
            message=str(detail.get("message", "Request failed.")),
        )
    else:
        error = ErrorResponse(code="REQUEST_ERROR", message=str(detail))
    return JSONResponse(status_code=exception.status_code, content=error.model_dump())


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    _: Request, exception: RequestValidationError
) -> JSONResponse:
    missing_image = any(error.get("loc", ())[-1:] == ("image",) for error in exception.errors())
    message = "Image file is required." if missing_image else "Invalid request."
    error = ErrorResponse(code="INVALID_REQUEST", message=message)
    return JSONResponse(status_code=400, content=error.model_dump())


@app.exception_handler(Exception)
async def unexpected_exception_handler(_: Request, exception: Exception) -> JSONResponse:
    logger.error("Unexpected request failure: %s", type(exception).__name__)
    error = ErrorResponse(
        code="INTERNAL_SERVER_ERROR",
        message="An unexpected error occurred.",
    )
    return JSONResponse(status_code=500, content=error.model_dump())


app.include_router(health.router)
app.include_router(detection.router)
app.include_router(analyze.router)
