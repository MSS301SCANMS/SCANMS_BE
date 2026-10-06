from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_name: str = "SCANMS AI Service"
    app_version: str = "1.0.0"
    host: str = "0.0.0.0"
    port: int = 8000
    model_path: Path = Path("weights/best.pt")
    confidence_threshold: float = Field(default=0.5, ge=0.0, le=1.0)
    max_image_size_mb: int = Field(default=10, gt=0)
    color_clusters: int = Field(default=3, ge=1, le=8)
    color_resize_pixels: int = Field(default=128, ge=32, le=512)
    fashion_clip_enabled: bool = True
    fashion_clip_model: str = "patrickjohncyh/fashion-clip"
    fashion_clip_local_files_only: bool = True
    fashion_clip_device: str = "auto"
    ranking_visual_weight: float = Field(default=0.60, ge=0.0)
    ranking_style_weight: float = Field(default=0.20, ge=0.0)
    ranking_color_weight: float = Field(default=0.10, ge=0.0)
    ranking_metadata_weight: float = Field(default=0.10, ge=0.0)
    allowed_origins: str = (
        "http://localhost:3000,http://localhost:5173,http://localhost:8080"
    )

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.allowed_origins.split(",") if origin.strip()]

    @property
    def max_image_size_bytes(self) -> int:
        return self.max_image_size_mb * 1024 * 1024

    @property
    def ranking_weight_sum(self) -> float:
        return (
            self.ranking_visual_weight
            + self.ranking_style_weight
            + self.ranking_color_weight
            + self.ranking_metadata_weight
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
