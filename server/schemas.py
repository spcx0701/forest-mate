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


class ProfileIn(BaseModel):
    name: str = Field(default="산친구", max_length=16)
    fit: int = Field(default=2, ge=1, le=3)
    knee: bool = False
    heart: bool = False


class AuthRegisterIn(ProfileIn):
    email: str = Field(max_length=255)
    password: str = Field(min_length=8, max_length=128)
    device_token: str | None = None


class AuthLoginIn(BaseModel):
    email: str = Field(max_length=255)
    password: str = Field(min_length=1, max_length=128)
    device_token: str | None = None


class AuthProfileOut(BaseModel):
    name: str
    fit: int
    knee: bool
    heart: bool


class AuthUserOut(BaseModel):
    id: str
    email: str | None = None
    providers: list[str]
    avatar_url: str = ""
    profile: AuthProfileOut


class AuthOut(BaseModel):
    access_token: str
    token_type: str = "Bearer"
    expires_in: int
    user: AuthUserOut
    device_token: str


class AuthMeOut(BaseModel):
    user: AuthUserOut
    device_token: str


class HikeCreate(BaseModel):
    course_id: str


class TrackIn(BaseModel):
    progress: float = Field(ge=0.0, le=1.0)
    alt: int = 0
    hr: int | None = Field(default=None, ge=20, le=250)
    lat: float | None = None   # 실제 GPS 위경도(있으면 동기화)
    lon: float | None = None


class TrackOut(BaseModel):
    alerts: list[dict]
    distress: dict
    progress: float


class WatchPairStartIn(BaseModel):
    hike_id: str | None = None


class WatchPairOut(BaseModel):
    code: str
    expires_in: int
    hike_id: str | None = None


class WatchPairClaimIn(BaseModel):
    code: str = Field(min_length=6, max_length=6)


class WatchPairClaimOut(BaseModel):
    watch_token: str
    hike_id: str | None = None
    course_id: str | None = None
    course_name: str | None = None
    course_km: float | None = None
    course_elev: int | None = None
    route: str | None = None


class WatchTrackIn(BaseModel):
    progress: float | None = Field(default=None, ge=0.0, le=1.0)
    alt: int = 0
    hr: int | None = Field(default=None, ge=20, le=250)
    lat: float | None = None
    lon: float | None = None
    acc: int | None = Field(default=None, ge=0, le=5000)
    battery: int | None = Field(default=None, ge=0, le=100)


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


class HikeSummaryOut(BaseModel):
    """마이 리포트 — 기기(익명)별 실제 산행 기록 집계."""
    total_hikes: int
    total_km: float
    total_kcal: int
    co2_kg: float
    active_days: int           # 가입(기기 등록)일로부터 경과 일수
    level: int
    monthly: list[dict]        # 최근 6개월 [{month, km, count}]
    distinct_courses: int = 0  # 완등한 서로 다른 코스 수
    regions: int = 0           # 방문한 서로 다른 지역(시·도) 수
    badges: list[dict] = []    # 배지 진척 [{id,icon,label,earned,progress,goal}]


class ChatIn(BaseModel):
    message: str = Field(min_length=1, max_length=500)
    lang: str = Field(default="ko", pattern="^(ko|en|zh|ja)$")
    region_id: str = "eunpyeong"
    course_id: str | None = None
    progress: float = Field(default=0.0, ge=0.0, le=1.0)


class SpeciesIn(BaseModel):
    sample_id: str  # 데모 식별. 운영: multipart 이미지 업로드 → 비전 모델 추론
