"""Galaxy Watch / Wear OS 페어링과 산행 센서 업로드."""
import secrets
from datetime import datetime, timedelta, timezone
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..models import (
    AlertEvent,
    Device,
    Hike,
    TrackPoint,
    WatchPair,
    WatchSample,
    WatchSession,
)
from ..schemas import (TrackOut, WatchPairClaimIn, WatchPairClaimOut,
                       WatchPairOut, WatchPairStartIn, WatchTrackIn)
from ..services.bus import bus
from ..services.safety import assess_distress
from .hikes import _course, get_device

router = APIRouter()

PAIR_EXPIRES_SECONDS = 600
WATCH_CONNECTED_SECONDS = 60
_pair_claim_attempts: dict[str, list[datetime]] = {}


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _new_code(db: Session) -> str:
    for _ in range(20):
        code = f"{secrets.randbelow(1_000_000):06d}"
        if not db.get(WatchPair, code):
            return code
    raise HTTPException(503, "pair code unavailable")


def _claim_client_key(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "").split(",", 1)[0].strip()
    if forwarded:
        return forwarded
    return request.client.host if request.client else "unknown"


def _check_pair_claim_rate_limit(request: Request) -> None:
    settings = get_settings()
    now = _utcnow()
    window = max(1, settings.watch_pair_claim_window_s)
    max_attempts = max(1, settings.watch_pair_claim_max_attempts)
    cutoff = now - timedelta(seconds=window)
    key = _claim_client_key(request)
    attempts = [ts for ts in _pair_claim_attempts.get(key, []) if ts > cutoff]
    if len(attempts) >= max_attempts:
        _pair_claim_attempts[key] = attempts
        raise HTTPException(429, "too many pair attempts")
    attempts.append(now)
    _pair_claim_attempts[key] = attempts


def get_watch_session(db: Annotated[Session, Depends(get_db)],
                      authorization: Annotated[str, Header()] = "") -> WatchSession:
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    session = db.scalar(select(WatchSession).where(WatchSession.token == authorization.removeprefix("Bearer ")))
    if not session:
        raise HTTPException(401, "invalid watch token")
    return session


@router.post("/watch/pair/start", response_model=WatchPairOut, status_code=201,
             responses={
                 404: {"description": "Hike not found"},
                 409: {"description": "Hike is not active"},
                 503: {"description": "Pair code unavailable"},
             })
async def start_watch_pairing(body: WatchPairStartIn,
                              device: Annotated[Device, Depends(get_device)],
                              db: Annotated[Session, Depends(get_db)]):
    hike_id = None
    if body.hike_id:
        hike = db.get(Hike, body.hike_id)
        if not hike or hike.device_id != device.id:
            raise HTTPException(404, "hike not found")
        if hike.status != "active":
            raise HTTPException(409, "hike is not active")
        hike_id = hike.id

    now = _utcnow()
    code = _new_code(db)
    db.add(WatchPair(code=code, device_id=device.id, hike_id=hike_id,
                     expires_at=now + timedelta(seconds=PAIR_EXPIRES_SECONDS)))
    db.commit()
    return WatchPairOut(code=code, expires_in=PAIR_EXPIRES_SECONDS, hike_id=hike_id)


@router.post("/watch/pair/claim", response_model=WatchPairClaimOut,
             responses={
                 404: {"description": "Pair code not found"},
                 409: {"description": "Hike is not active"},
                 429: {"description": "Too many pair attempts"},
             })
async def claim_watch_pairing(body: WatchPairClaimIn, request: Request,
                              db: Annotated[Session, Depends(get_db)]):
    _check_pair_claim_rate_limit(request)
    pair = db.get(WatchPair, body.code)
    if not pair or _aware(pair.expires_at) < _utcnow():
        raise HTTPException(404, "pair code not found")

    course = None
    course_id = None
    if pair.hike_id:
        hike = db.get(Hike, pair.hike_id)
        if not hike or hike.status != "active":
            raise HTTPException(409, "hike is not active")
        course = _course(hike.course_id)
        course_id = course["id"]

    session = WatchSession(device_id=pair.device_id, hike_id=pair.hike_id)
    db.add(session)
    db.delete(pair)
    db.commit()
    return WatchPairClaimOut(watch_token=session.token, hike_id=session.hike_id,
                             course_id=course_id,
                             course_name=course["name"] if course else None,
                             course_km=course["km"] if course else None,
                             course_elev=_course_peak_elev(course),
                             route=course["route"] if course else None)


def _latest_active_hike(device_id: str, db: Session) -> Hike | None:
    return db.execute(
        select(Hike)
        .where(Hike.device_id == device_id, Hike.status == "active")
        .order_by(Hike.started_at.desc())
    ).scalars().first()


def _active_hike_for_watch(watch: WatchSession, db: Session) -> Hike | None:
    if watch.hike_id:
        hike = db.get(Hike, watch.hike_id)
        if hike and hike.device_id == watch.device_id and hike.status == "active":
            return hike
    hike = _latest_active_hike(watch.device_id, db)
    if hike:
        watch.hike_id = hike.id
    return hike


def _course_peak_elev(course: dict | None) -> int | None:
    if not course:
        return None
    elev = course.get("elev")
    if isinstance(elev, list) and elev:
        return max(elev)
    if isinstance(elev, int):
        return elev
    return None


@router.post("/watch/track", response_model=TrackOut)
async def track_from_watch(body: WatchTrackIn,
                           watch: Annotated[WatchSession, Depends(get_watch_session)],
                           db: Annotated[Session, Depends(get_db)]):
    now = _utcnow()
    watch.last_seen_at = now
    watch.last_hr = body.hr
    watch.last_lat = body.lat
    watch.last_lon = body.lon
    watch.last_acc = body.acc
    watch.battery = body.battery

    hike = _active_hike_for_watch(watch, db)
    if not hike:
        db.commit()
        return TrackOut(alerts=[], distress={"level": 0, "reasons": ["no active hike"]}, progress=0.0)

    course = _course(hike.course_id)
    progress = hike.progress if body.progress is None else body.progress
    hike.progress = progress
    hike.distance_km = round(course["km"] * progress, 2)

    point = TrackPoint(hike_id=hike.id, progress=progress, alt=body.alt, hr=body.hr,
                       lat=body.lat, lon=body.lon, created_at=now)
    if body.hr is not None:
        db.add(WatchSample(hike_id=hike.id, hr=body.hr, lat=body.lat, lon=body.lon,
                           acc=body.acc, battery=body.battery, created_at=now))
    db.add(point)
    db.commit()

    rows = db.execute(
        select(TrackPoint).where(TrackPoint.hike_id == hike.id).order_by(TrackPoint.created_at)
    ).scalars().all()
    points = [{"progress": p.progress, "hr": p.hr, "created_at": p.created_at} for p in rows]
    distress = assess_distress(points)
    if distress["level"] >= 1:
        db.add(AlertEvent(kind="distress", title=f"워치 조난위험 — 이동 정지 감지 ({course['name']})",
                          body=str(distress), course_id=course["id"]))
        db.commit()
        bus.publish({"type": "distress", "level": distress["level"],
                     "title": "워치 조난위험 — 이동 정지 감지", "course_id": course["id"]})

    return TrackOut(alerts=[], distress=distress, progress=progress)


@router.get("/watch/latest")
async def latest_watch_status(device: Annotated[Device, Depends(get_device)],
                              db: Annotated[Session, Depends(get_db)],
                              hike_id: str | None = None):
    query = select(WatchSession).where(WatchSession.device_id == device.id)
    if hike_id:
        query = query.where(WatchSession.hike_id == hike_id)
    session = db.execute(query.order_by(WatchSession.created_at.desc())).scalars().first()
    if not session and hike_id:
        session = db.execute(
            select(WatchSession)
            .where(WatchSession.device_id == device.id, WatchSession.hike_id.is_(None))
            .order_by(WatchSession.created_at.desc())
        ).scalars().first()
    if not session or not session.last_seen_at:
        return {"connected": False, "hike_id": hike_id}

    seen = _aware(session.last_seen_at)
    age = max(0, int((_utcnow() - seen).total_seconds()))
    return {
        "connected": age <= WATCH_CONNECTED_SECONDS,
        "hike_id": session.hike_id,
        "hr": session.last_hr,
        "lat": session.last_lat,
        "lon": session.last_lon,
        "acc": session.last_acc,
        "battery": session.battery,
        "age_sec": age,
        "seen_at": seen.isoformat(),
    }
