"""영속 모델 — 계정·기기·산행·트랙·SOS·경보."""
import secrets
import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def _id() -> str:
    return uuid.uuid4().hex


def _token() -> str:
    return secrets.token_urlsafe(32)


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Device(Base):
    """익명 기기 등록 — 회원가입 없이 토큰 기반 인증(개인정보 최소수집)."""

    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    token: Mapped[str] = mapped_column(String(64), unique=True, index=True, default=_token)
    name: Mapped[str] = mapped_column(String(32), default="동무")
    fit: Mapped[int] = mapped_column(Integer, default=2)  # 1 초급 / 2 중급 / 3 상급
    knee: Mapped[bool] = mapped_column(Boolean, default=False)
    heart: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    hikes: Mapped[list["Hike"]] = relationship(back_populates="device")


class User(Base):
    """서비스 계정 — 이메일/소셜 로그인 뒤 서버가 소유하는 canonical profile."""

    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    email: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    name: Mapped[str] = mapped_column(String(32), default="산친구")
    fit: Mapped[int] = mapped_column(Integer, default=2)
    knee: Mapped[bool] = mapped_column(Boolean, default=False)
    heart: Mapped[bool] = mapped_column(Boolean, default=False)
    avatar_url: Mapped[str] = mapped_column(String(512), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class AuthIdentity(Base):
    """로그인 수단 — password/google/kakao/naver를 같은 사용자에 연결."""

    __tablename__ = "auth_identities"
    __table_args__ = (UniqueConstraint("provider", "provider_user_id", name="uq_auth_provider_user"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    provider: Mapped[str] = mapped_column(String(24), index=True)
    provider_user_id: Mapped[str] = mapped_column(String(255))
    email: Mapped[str | None] = mapped_column(String(255), nullable=True)
    credential_hash: Mapped[str] = mapped_column(String(255), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class AuthSession(Base):
    """자체 로그인 세션 — 원문 토큰은 DB에 저장하지 않고 해시만 보관."""

    __tablename__ = "auth_sessions"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    user_agent: Mapped[str] = mapped_column(String(255), default="")
    ip: Mapped[str] = mapped_column(String(64), default="")


class UserDevice(Base):
    """계정과 익명 기기 토큰의 연결 — 기존 산행 기록을 계정으로 승격한다."""

    __tablename__ = "user_devices"
    __table_args__ = (UniqueConstraint("device_id", name="uq_user_device_device"),)

    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), primary_key=True)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.id"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class OAuthState(Base):
    """소셜 로그인 CSRF state — OAuth 콜백에서 1회 소비한다."""

    __tablename__ = "oauth_states"

    state_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    provider: Mapped[str] = mapped_column(String(24), index=True)
    device_token: Mapped[str] = mapped_column(String(128), default="")
    profile_json: Mapped[str] = mapped_column(Text, default="{}")
    redirect_path: Mapped[str] = mapped_column(String(255), default="/index.html")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class Hike(Base):
    __tablename__ = "hikes"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.id"), index=True)
    course_id: Mapped[str] = mapped_column(String(32), index=True)
    status: Mapped[str] = mapped_column(String(16), default="active")  # active|done|aborted
    progress: Mapped[float] = mapped_column(Float, default=0.0)
    distance_km: Mapped[float] = mapped_column(Float, default=0.0)
    kcal: Mapped[int] = mapped_column(Integer, default=0)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    device: Mapped[Device] = relationship(back_populates="hikes")
    points: Mapped[list["TrackPoint"]] = relationship(back_populates="hike",
                                                      order_by="TrackPoint.created_at")


class TrackPoint(Base):
    """이동 궤적 — 조난감지 입력. 원본은 산행 종료 후 통계 익명화 뒤 보존기간 내 파기."""

    __tablename__ = "track_points"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    hike_id: Mapped[str] = mapped_column(ForeignKey("hikes.id"), index=True)
    progress: Mapped[float] = mapped_column(Float)
    alt: Mapped[int] = mapped_column(Integer, default=0)
    hr: Mapped[int | None] = mapped_column(Integer, nullable=True)
    lat: Mapped[float | None] = mapped_column(Float, nullable=True)  # 실제 GPS
    lon: Mapped[float | None] = mapped_column(Float, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)

    hike: Mapped[Hike] = relationship(back_populates="points")


class WatchPair(Base):
    """워치 페어링 코드 — 폰/PWA가 만들고 워치가 1회 사용한다."""

    __tablename__ = "watch_pairs"

    code: Mapped[str] = mapped_column(String(6), primary_key=True)
    hike_id: Mapped[str | None] = mapped_column(ForeignKey("hikes.id"), index=True, nullable=True)
    device_id: Mapped[str] = mapped_column(ForeignKey("devices.id"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), default="", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    claimed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class WatchSample(Base):
    """워치 최신 센서 샘플 — 심박/GPS/배터리."""

    __tablename__ = "watch_samples"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    hike_id: Mapped[str] = mapped_column(ForeignKey("hikes.id"), index=True)
    hr: Mapped[int] = mapped_column(Integer)
    lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    lon: Mapped[float | None] = mapped_column(Float, nullable=True)
    acc: Mapped[float | None] = mapped_column(Float, nullable=True)
    battery: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class SosEvent(Base):
    __tablename__ = "sos_events"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    device_id: Mapped[str] = mapped_column(String(32), index=True)
    hike_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    grid_no: Mapped[str] = mapped_column(String(32))
    gps: Mapped[str] = mapped_column(String(64))
    note: Mapped[str] = mapped_column(Text, default="")
    status: Mapped[str] = mapped_column(String(16), default="received")  # received|dispatched|closed
    station: Mapped[str] = mapped_column(String(64), default="")
    eta_min: Mapped[int] = mapped_column(Integer, default=9)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class PushSub(Base):
    """Web Push 구독 — 기기별 푸시 발송 대상(VAPID)."""
    __tablename__ = "push_subs"

    endpoint: Mapped[str] = mapped_column(String(512), primary_key=True)
    device_id: Mapped[str] = mapped_column(String(32), index=True)
    p256dh: Mapped[str] = mapped_column(String(255))
    auth: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class WatchSession(Base):
    """페어링된 워치 세션 — 산행 중 센서 업로드 토큰과 최신 상태."""

    __tablename__ = "watch_sessions"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    device_id: Mapped[str] = mapped_column(String(32), index=True)
    hike_id: Mapped[str | None] = mapped_column(String(32), index=True, nullable=True)
    token: Mapped[str] = mapped_column(String(64), unique=True, index=True, default=_token)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_hr: Mapped[int | None] = mapped_column(Integer, nullable=True)
    last_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    last_lon: Mapped[float | None] = mapped_column(Float, nullable=True)
    last_acc: Mapped[int | None] = mapped_column(Integer, nullable=True)
    battery: Mapped[int | None] = mapped_column(Integer, nullable=True)


class Mountain(Base):
    """전국 산 카탈로그 — 산림청 산정보 서비스(3,368개) ETL 적재 결과.

    상세 안전 코스(고도·위험구간·대피소)는 별도 큐레이션(seed COURSES)이며,
    이 테이블은 전국 산 검색·소개용 카탈로그다.
    """

    __tablename__ = "mountains"

    list_no: Mapped[str] = mapped_column(String(16), primary_key=True)  # mntilistno
    name: Mapped[str] = mapped_column(String(64), index=True)           # mntiname
    sub_name: Mapped[str] = mapped_column(String(64), default="")       # mntisname
    addr: Mapped[str] = mapped_column(String(128), default="")          # mntiadd(소재지)
    sido: Mapped[str] = mapped_column(String(24), index=True, default="")  # 소재지 첫 토큰(시·도)
    height: Mapped[int] = mapped_column(Integer, default=0)             # mntihigh
    summary: Mapped[str] = mapped_column(Text, default="")              # mntisummary
    details: Mapped[str] = mapped_column(Text, default="")             # mntidetails
    admin: Mapped[str] = mapped_column(String(64), default="")          # mntiadmin
    admin_tel: Mapped[str] = mapped_column(String(32), default="")      # mntiadminnum
    is_top100: Mapped[bool] = mapped_column(Boolean, default=False)     # mntitop(100대명산 여부)
    # VWorld 지오코딩(주소→좌표·법정동 시군구코드) — 정밀 날씨격자·산불·주변검색용
    lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    lon: Mapped[float | None] = mapped_column(Float, nullable=True)
    sgg: Mapped[str] = mapped_column(String(5), default="")             # 시군구 5자리(산불 localAreas)
    facilities: Mapped[str] = mapped_column(Text, default="")           # 등산로 주요지점 시설 JSON


class AlertEvent(Base):
    """관제 피드 이벤트(위험구간 경고·조난의심·해제 등)."""

    __tablename__ = "alert_events"

    id: Mapped[str] = mapped_column(String(32), primary_key=True, default=_id)
    kind: Mapped[str] = mapped_column(String(24))  # sos|distress|hazard|checkin|clear
    title: Mapped[str] = mapped_column(String(128))
    body: Mapped[str] = mapped_column(Text, default="")
    course_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
