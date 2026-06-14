"""Web Push 구독·발송 — 인증(기기 토큰) 구간. 비우면 인앱 알림만."""
from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..db import get_db
from ..models import Device, PushSub
from ..services.push import send_to
from .hikes import get_device

router = APIRouter()


@router.get("/push/vapid")
async def push_vapid():
    """프런트 구독용 공개키 + 활성 여부."""
    s = get_settings()
    return {"enabled": s.push_enabled, "publicKey": s.vapid_public_key}


@router.post("/push/subscribe")
async def push_subscribe(body: dict, device: Device = Depends(get_device),
                         db: Session = Depends(get_db)):
    """브라우저 PushSubscription 저장(멱등)."""
    if not body.get("endpoint"):
        return {"ok": False, "reason": "endpoint 없음"}
    keys = body.get("keys", {})
    db.merge(PushSub(endpoint=body["endpoint"], device_id=device.id,
                     p256dh=keys.get("p256dh", ""), auth=keys.get("auth", "")))
    db.commit()
    return {"ok": True}


@router.post("/push/test")
async def push_test(device: Device = Depends(get_device), db: Session = Depends(get_db)):
    """이 기기 구독으로 테스트 푸시 발송."""
    subs = db.execute(select(PushSub).where(PushSub.device_id == device.id)).scalars().all()
    sent = sum(1 for s in subs if send_to(
        s, "숲길동무 알림", "푸시 알림이 켜졌어요! 등록한 일정·즐겨찾기 산 소식을 보내드릴게요 🏔", "/"))
    return {"sent": sent, "subs": len(subs), "enabled": get_settings().push_enabled}
