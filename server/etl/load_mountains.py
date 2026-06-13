"""전국 산 카탈로그 ETL — 산림청 산정보 서비스(3,368개)를 Mountain 테이블에 적재.

실행:
    python -m server.etl.load_mountains          # 전체 적재(DATA_GO_KR_KEY 필요)
    python -m server.etl.load_mountains --pages 2 --rows 100   # 일부만(점검용)

키가 없으면(live_data=False) 즉시 종료한다. 멱등 — list_no 기준 upsert.
"""
import argparse
import asyncio
import re

from ..adapters.base import AdapterError
from ..adapters.mountains import fetch_mountains
from ..config import get_settings
from ..db import SessionLocal, init_db
from ..models import Mountain

_NUM = re.compile(r"\d+")


def _height(raw: str) -> int:
    m = _NUM.search(raw or "")
    return int(m.group()) if m else 0


def _sido(addr: str) -> str:
    """소재지 첫 토큰(시·도). 예: '서울특별시 은평구 …' → '서울특별시'."""
    return (addr or "").split()[0] if addr else ""


def _is_top100(raw: str) -> bool:
    v = (raw or "").strip()
    return bool(v) and ("100" in v or v.upper() in ("Y", "1", "O", "TRUE"))


def _to_model(row: dict) -> Mountain:
    return Mountain(
        list_no=row["mntilistno"] or row["mntiname"],
        name=row["mntiname"], sub_name=row["mntisname"], addr=row["mntiadd"],
        sido=_sido(row["mntiadd"]), height=_height(row["mntihigh"]),
        summary=row["mntisummary"][:4000], details=row["mntidetails"][:8000],
        admin=row["mntiadmin"], admin_tel=row["mntiadminnum"],
        is_top100=_is_top100(row["mntitop"]),
    )


async def run(max_pages: int | None = None, rows: int = 100) -> dict:
    if not get_settings().live_data:
        return {"ok": False, "reason": "DATA_GO_KR_KEY 미설정 — 적재 생략", "loaded": 0}

    init_db()
    loaded, page, total = 0, 1, None
    db = SessionLocal()
    try:
        while True:
            try:
                items, total = await fetch_mountains(page=page, rows=rows)
            except AdapterError as exc:
                return {"ok": False, "reason": str(exc), "loaded": loaded, "page": page}
            if not items:
                break
            for row in items:
                db.merge(_to_model(row))   # upsert (멱등)
            db.commit()
            loaded += len(items)
            if (total and loaded >= total) or (max_pages and page >= max_pages):
                break
            page += 1
        return {"ok": True, "loaded": loaded, "total": total, "pages": page}
    finally:
        db.close()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", type=int, default=None, help="최대 페이지 수(점검용)")
    ap.add_argument("--rows", type=int, default=100, help="페이지당 행 수")
    args = ap.parse_args()
    result = asyncio.run(run(max_pages=args.pages, rows=args.rows))
    print(result)


if __name__ == "__main__":
    main()
