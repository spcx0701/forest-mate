"""영속 모델 — 기기(익명 인증)·산행·트랙·SOS·경보."""
import secrets
import uuid
from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text
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
