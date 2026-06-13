"""B2G 관제 — 요약 통계 + 실시간 WebSocket 피드."""
import asyncio
import contextlib

from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..adapters.public_data import get_region_conditions
from ..db import get_db
from ..models import AlertEvent, Hike, SosEvent
from ..seed import COURSES
from ..services.bus import bus
from ..services.safety import k_anonymize
from ..services.scoring import fused_risk

router = APIRouter()


@router.get("/dashboard/summary")
async def summary(db: Session = Depends(get_db)):
    active = db.scalar(select(func.count()).select_from(Hike).where(Hike.status == "active")) or 0
    sos_open = db.scalar(select(func.count()).select_from(SosEvent)
                         .where(SosEvent.status != "closed")) or 0
    alerts_today = db.scalar(select(func.count()).select_from(AlertEvent)) or 0

    # 코스별 익명 분포(k-익명화) — 개인 위치는 절대 표출하지 않는다
    heat = {}
    for course in COURSES:
        progresses = [h.progress for h in db.scalars(
            select(Hike).where(Hike.course_id == course["id"], Hike.status == "active"))]
        cells = k_anonymize(progresses)
        if cells:
            heat[course["id"]] = cells

    cond = await get_region_conditions("eunpyeong")
    risk_table = []
    for course in COURSES:
        for r in fused_risk(course, cond["weather"]["rain_prob"], cond["weather"]["wind"]):
            risk_table.append({"course": course["name"], **r})
    risk_table.sort(key=lambda x: -x["risk"])

    events = [{"kind": e.kind, "title": e.title, "body": e.body,
               "at": e.created_at.isoformat()}
              for e in db.scalars(select(AlertEvent)
                                  .order_by(AlertEvent.created_at.desc()).limit(8))]

    return {"kpi": {"active_hikers": active, "open_sos": sos_open,
                    "alerts_today": alerts_today},
            "heat": heat, "risk_table": risk_table[:6], "events": events}


@router.websocket("/ws/dashboard")
async def ws_dashboard(ws: WebSocket):
    await ws.accept()
    q = bus.subscribe()
    try:
        while True:
            # 30초 무이벤트 시 ping — 프록시 idle timeout 방지
            with contextlib.suppress(asyncio.TimeoutError):
                payload = await asyncio.wait_for(q.get(), timeout=30)
                await ws.send_text(payload)
                continue
            await ws.send_text('{"type":"ping"}')
    except WebSocketDisconnect:
        pass
    finally:
        bus.unsubscribe(q)
