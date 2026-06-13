"""안전 도메인 — 조난 감지 규칙 + 위치 k-익명화."""
from collections import Counter
from datetime import datetime, timezone

from ..config import get_settings


def assess_distress(points: list[dict], now: datetime | None = None) -> dict:
    """이동 정지 기반 조난 의심 판정.

    points: [{progress, hr, created_at}] 시간순.
    규칙: 최근 N개 포인트에서 진행률 변화가 0.2%p 미만이고, 그 구간이
    distress_stall_minutes 이상 지속되면 level 1(확인 필요).
    심박 이상(>140 또는 <45)이 동반되면 level 2(즉시 전파).
    """
    settings = get_settings()
    now = now or datetime.now(timezone.utc)

    if len(points) < settings.distress_min_points:
        return {"level": 0, "reason": "insufficient_data"}

    recent = points[-settings.distress_min_points:]
    moved = abs(recent[-1]["progress"] - recent[0]["progress"])
    first_t = recent[0]["created_at"]
    if first_t.tzinfo is None:
        first_t = first_t.replace(tzinfo=timezone.utc)
    stalled_min = (now - first_t).total_seconds() / 60

    if moved >= 0.002:
        return {"level": 0, "reason": "moving"}
    if stalled_min < settings.distress_stall_minutes:
        return {"level": 0, "reason": "stall_below_threshold", "stalled_min": round(stalled_min, 1)}

    hr = recent[-1].get("hr")
    if hr is not None and (hr > 140 or hr < 45):
        return {"level": 2, "reason": "stalled_with_hr_anomaly",
                "stalled_min": round(stalled_min, 1), "hr": hr}
    return {"level": 1, "reason": "stalled", "stalled_min": round(stalled_min, 1)}


def k_anonymize(progresses: list[float], k: int | None = None, bins: int = 10) -> list[dict]:
    """코스 진행률을 구간(bin)으로 집계하고 k 미만 군집은 표출하지 않는다.

    위치정보법 준수 — 관제 화면에는 개인 위치가 아닌 군집 통계만 노출.
    """
    settings = get_settings()
    k = k if k is not None else settings.k_anonymity
    counts = Counter(min(bins - 1, int(p * bins)) for p in progresses)
    return [
        {"bin": b, "range": [round(b / bins, 2), round((b + 1) / bins, 2)], "count": n}
        for b, n in sorted(counts.items())
        if n >= k
    ]
