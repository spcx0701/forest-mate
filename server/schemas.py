"""API 입출력 스키마."""
from datetime import datetime

from pydantic import BaseModel, Field


class DeviceCreate(BaseModel):
    name: str = Field(default="동무", max_length=16)
    fit: int = Field(default=2, ge=1, le=3)
    knee: bool = False
    heart: bool = False


class DeviceOut(BaseModel):
    device_id: str
    token: str
    name: str


class HikeCreate(BaseModel):
    course_id: str


class TrackIn(BaseModel):
    progress: float = Field(ge=0.0, le=1.0)
    alt: int = 0
    hr: int | None = Field(default=None, ge=20, le=250)


class TrackOut(BaseModel):
    alerts: list[dict]
    distress: dict
    progress: float


class HikeEndOut(BaseModel):
    hike_id: str
    distance_km: float
    kcal: int
    duration_min: int


class SosCreate(BaseModel):
    hike_id: str | None = None
    note: str = Field(default="", max_length=500)


class SosOut(BaseModel):
    sos_id: str
    status: str
    grid_no: str
    gps: str
    station: str
    eta_min: int
    created_at: datetime


class ChatIn(BaseModel):
    message: str = Field(min_length=1, max_length=500)
    lang: str = Field(default="ko", pattern="^(ko|en|zh|ja)$")
    region_id: str = "eunpyeong"
    course_id: str | None = None
    progress: float = Field(default=0.0, ge=0.0, le=1.0)


class SpeciesIn(BaseModel):
    sample_id: str  # 데모 식별. 운영: multipart 이미지 업로드 → 비전 모델 추론
