"""구워넣은 카탈로그(server/data/catalog.json) → DB 적재.

재배포/슬립으로 임시 DB가 비어도 시작 시 즉시 전량 복원(영속). API ETL과 달리
외부 호출이 없어 빠르고 결정적이다. 파일이 없으면 API ETL로 폴백.
"""
import json
import os
from pathlib import Path

from sqlalchemy import func, select

from ..db import SessionLocal, init_db
from ..models import Mountain

CATALOG = Path(__file__).resolve().parents[1] / "data" / "catalog.json"


def available() -> bool:
    # 테스트는 자체 시드를 쓰므로 베이크 적재를 건너뛴다(결정적).
    return CATALOG.exists() and not os.getenv("FORESTMATE_SKIP_CATALOG")


def load_from_file() -> dict:
    """catalog.json을 DB로 적재. 이미 같은 수 이상이면 생략."""
    if not CATALOG.exists():
        return {"ok": False, "reason": "catalog.json 없음", "loaded": 0}
    rows = json.loads(CATALOG.read_text(encoding="utf-8"))
    init_db()
    db = SessionLocal()
    try:
        existing = db.scalar(select(func.count()).select_from(Mountain)) or 0
        if existing >= len(rows):
            return {"ok": True, "loaded": 0, "in_db": existing, "source": "file(skip)"}
        objs = [Mountain(
            list_no=r["list_no"], name=r["name"].replace("_", " "), addr=r.get("addr", ""),
            sido=r.get("sido", ""), height=r.get("height", 0),
            summary=r.get("summary", ""), is_top100=r.get("top100", False),
            lat=r.get("lat"), lon=r.get("lon"), sgg=r.get("sgg", ""),
            facilities=json.dumps(r.get("facilities", {}), ensure_ascii=False),
        ) for r in rows]
        if existing == 0:
            db.bulk_save_objects(objs)          # 빈 DB(대부분의 시작) — 빠른 대량삽입
        else:
            for o in objs:
                db.merge(o)
        db.commit()
        return {"ok": True, "loaded": len(objs), "in_db": len(objs), "source": "file"}
    finally:
        db.close()
