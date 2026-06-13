"""기기 등록·산행 추적·SOS — 토큰 인증 구간."""
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..db import get_db
from ..models import AlertEvent, Device, Hike, SosEvent, TrackPoint
from ..schemas import (DeviceCreate, DeviceOut, HikeCreate, HikeEndOut,
                       HikeSummaryOut, SosCreate, SosOut, TrackIn, TrackOut)
from ..seed import COURSES
from ..services.bus import bus
from ..services.safety import assess_distress

router = APIRouter()


def get_device(authorization: str = Header(default=""),
               db: Session = Depends(get_db)) -> Device:
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    device = db.scalar(select(Device).where(Device.token == authorization[7:]))
    if not device:
        raise HTTPException(401, "invalid token")
    return device


def _course(course_id: str) -> dict:
    course = next((c for c in COURSES if c["id"] == course_id), None)
    if not course:
        raise HTTPException(404, "course not found")
    return course


@router.post("/devices", response_model=DeviceOut, status_code=201)
async def register_device(body: DeviceCreate, db: Session = Depends(get_db)):
    device = Device(name=body.name, fit=body.fit, knee=body.knee, heart=body.heart)
    db.add(device)
    db.commit()
    return DeviceOut(device_id=device.id, token=device.token, name=device.name)


@router.post("/hikes", status_code=201)
async def start_hike(body: HikeCreate, device: Device = Depends(get_device),
                     db: Session = Depends(get_db)):
    course = _course(body.course_id)
    hike = Hike(device_id=device.id, course_id=course["id"])
    db.add(hike)
    db.add(AlertEvent(kind="checkin", title=f"입산 체크인 — {course['name']}",
                      body=f"{device.name}님, 예상 {course['minutes']}분", course_id=course["id"]))
    db.commit()
    bus.publish({"type": "checkin", "title": f"입산 체크인 — {course['name']}",
                 "course_id": course["id"], "at": datetime.now(timezone.utc).isoformat()})
    return {"hike_id": hike.id, "course_id": course["id"], "started_at": hike.started_at}


@router.post("/hikes/{hike_id}/track", response_model=TrackOut)
async def track(hike_id: str, body: TrackIn, device: Device = Depends(get_device),
                db: Session = Depends(get_db)):
    hike = db.get(Hike, hike_id)
    if not hike or hike.device_id != device.id:
        raise HTTPException(404, "hike not found")
    if hike.status != "active":
        raise HTTPException(409, "hike is not active")

    course = _course(hike.course_id)
    prev = hike.progress
    hike.progress = body.progress
    hike.distance_km = round(course["km"] * body.progress, 2)
    db.add(TrackPoint(hike_id=hike.id, progress=body.progress, alt=body.alt, hr=body.hr))
    db.commit()

    # 위험구간 접근 경고 (진행률 창 통과 시 1회)
    alerts = []
    for hz in course["hazards"]:
        if prev < hz["at"] - 0.08 <= body.progress < hz["at"]:
            alerts.append({"type": "hazard", "title": f"300m 앞 {hz['type']} 구간",
                           "body": f"{hz['grade']} — {hz['note']}"})
            db.add(AlertEvent(kind="hazard", title=f"{hz['type']} 구간 접근 — {course['name']}",
                              body=hz["note"], course_id=course["id"]))
            db.commit()
            bus.publish({"type": "hazard", "title": f"{hz['type']} 구간 접근", "course_id": course["id"]})

    # 조난 감지 — 서버측 판정(앱 종료·통신 두절 시에도 마지막 데이터로 평가 가능)
    points = [{"progress": p.progress, "hr": p.hr, "created_at": p.created_at}
              for p in hike.points]
    distress = assess_distress(points)
    if distress["level"] >= 1:
        db.add(AlertEvent(kind="distress", title=f"AI 조난위험 — 이동 정지 감지 ({course['name']})",
                          body=str(distress), course_id=course["id"]))
        db.commit()
        bus.publish({"type": "distress", "level": distress["level"],
                     "title": "AI 조난위험 — 이동 정지 감지", "course_id": course["id"]})

    return TrackOut(alerts=alerts, distress=distress, progress=body.progress)


@router.post("/hikes/{hike_id}/end", response_model=HikeEndOut)
async def end_hike(hike_id: str, device: Device = Depends(get_device),
                   db: Session = Depends(get_db)):
    hike = db.get(Hike, hike_id)
    if not hike or hike.device_id != device.id:
        raise HTTPException(404, "hike not found")
    hike.status = "done"
    hike.ended_at = datetime.now(timezone.utc)
    hike.kcal = int(hike.distance_km * 260)
    db.commit()
    started = hike.started_at if hike.started_at.tzinfo else hike.started_at.replace(tzinfo=timezone.utc)
    duration = int((hike.ended_at - started).total_seconds() / 60)
    return HikeEndOut(hike_id=hike.id, distance_km=hike.distance_km,
                      kcal=hike.kcal, duration_min=duration)


@router.get("/hikes/summary", response_model=HikeSummaryOut)
async def hike_summary(device: Device = Depends(get_device), db: Session = Depends(get_db)):
    """마이 리포트 — 이 기기의 완료된 산행을 DB에서 집계(하드코딩 아님)."""
    hikes = db.execute(
        select(Hike).where(Hike.device_id == device.id, Hike.status == "done")
    ).scalars().all()
    total = len(hikes)
    total_km = round(sum(h.distance_km for h in hikes), 1)
    total_kcal = sum(h.kcal for h in hikes)
    created = device.created_at if device.created_at.tzinfo else device.created_at.replace(tzinfo=timezone.utc)
    active_days = (datetime.now(timezone.utc) - created).days + 1

    # 최근 6개월 월별 집계(완료 산행 기준)
    now = datetime.now(timezone.utc)
    months: dict[str, dict] = {}
    for i in range(5, -1, -1):
        y, m = now.year, now.month - i
        while m <= 0:
            m += 12
            y -= 1
        months[f"{y:04d}-{m:02d}"] = {"month": f"{y:04d}-{m:02d}", "km": 0.0, "count": 0}
    for h in hikes:
        ref = h.ended_at or h.started_at
        ref = ref if ref.tzinfo else ref.replace(tzinfo=timezone.utc)
        key = f"{ref.year:04d}-{ref.month:02d}"
        if key in months:
            months[key]["km"] = round(months[key]["km"] + h.distance_km, 1)
            months[key]["count"] += 1

    return HikeSummaryOut(
        total_hikes=total, total_km=total_km, total_kcal=total_kcal,
        co2_kg=round(total_km * 0.38, 1), active_days=active_days,
        level=1 + total // 3, monthly=list(months.values()),
    )


@router.post("/sos", response_model=SosOut, status_code=201)
async def sos(body: SosCreate, device: Device = Depends(get_device),
              db: Session = Depends(get_db)):
    course = None
    if body.hike_id:
        hike = db.get(Hike, body.hike_id)
        if hike and hike.device_id == device.id:
            course = next((c for c in COURSES if c["id"] == hike.course_id), None)
    course = course or COURSES[0]

    event = SosEvent(device_id=device.id, hike_id=body.hike_id, note=body.note,
                     grid_no=course["grid_no"], gps=course["gps"],
                     station=course["fire_station"], status="dispatched")
    db.add(event)
    db.add(AlertEvent(kind="sos", title=f"SOS 접수 — {course['name']}",
                      body=f"국가지점번호 {course['grid_no']} · {course['fire_station']} 인계",
                      course_id=course["id"]))
    db.commit()
    bus.publish({"type": "sos", "title": f"SOS 접수 — {course['name']}",
                 "grid_no": course["grid_no"], "station": course["fire_station"],
                 "eta_min": event.eta_min, "at": event.created_at.isoformat()})
    # 운영 연계 지점: 소방청 119 신고 API + 보호자 푸시(FCM) 발송 워커로 팬아웃
    return SosOut(sos_id=event.id, status=event.status, grid_no=event.grid_no,
                  gps=event.gps, station=event.station, eta_min=event.eta_min,
                  created_at=event.created_at)
