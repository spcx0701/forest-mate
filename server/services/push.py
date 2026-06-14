"""Web Push 발송 — VAPID. 키 미설정(push_enabled=False)이면 무동작."""
import json
import logging

from ..config import get_settings

log = logging.getLogger("forestmate.push")


def send_to(sub, title: str, body: str, url: str = "/") -> bool:
    s = get_settings()
    if not s.push_enabled:
        return False
    try:
        from pywebpush import webpush  # 지연 import — 라이브러리 없거나 미설정 시 영향 최소화
        priv = s.vapid_private_key.replace("\\n", "\n")  # 환경변수 한 줄 PEM의 \n 복원
        webpush(
            subscription_info={"endpoint": sub.endpoint,
                               "keys": {"p256dh": sub.p256dh, "auth": sub.auth}},
            data=json.dumps({"title": title, "body": body, "url": url}),
            vapid_private_key=priv,
            vapid_claims={"sub": s.vapid_subject},
            timeout=8,
        )
        return True
    except Exception as exc:  # noqa: BLE001 — 만료 구독 등은 조용히 실패
        log.info("push 발송 실패(%s): %s", getattr(sub, "endpoint", "")[:40], exc)
        return False
