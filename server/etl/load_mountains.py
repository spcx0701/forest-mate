"""전국 산 카탈로그 ETL — 산림청 산정보 서비스(3,368개)를 Mountain 테이블에 적재.

실행:
    python -m server.etl.load_mountains          # 전체 적재(DATA_GO_KR_KEY 필요)
    python -m server.etl.load_mountains --pages 2 --rows 100   # 일부만(점검용)

키가 없으면(live_data=False) 즉시 종료한다. 멱등 — list_no 기준 upsert.
"""
import argparse
import asyncio
import re

from sqlalchemy import func, select

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


def _g(row: dict, *keys: str) -> str:
    """후보 키 중 처음으로 값이 있는 것(필드명 변형 흡수)."""
    for k in keys:
        if row.get(k):
            return row[k]
    return ""


def _to_model(row: dict) -> Mountain:
    name = _g(row, "mntiname", "mntnnm", "mntn_nm", "name")
    addr = _g(row, "mntiadd", "addr", "mntn_lctn", "lctn")
    return Mountain(
        list_no=_g(row, "mntilistno", "mntnno", "no") or name,
        name=name, sub_name=_g(row, "mntisname"), addr=addr,
        sido=_sido(addr), height=_height(_g(row, "mntihigh", "mntn_hg", "height")),
        summary=_g(row, "mntisummary")[:4000], details=_g(row, "mntidetails", "mntn_dtl")[:8000],
        admin=_g(row, "mntiadmin"), admin_tel=_g(row, "mntiadminnum"),
        is_top100=_is_top100(_g(row, "mntitop")),
    )


async def _fetch_retry(page: int, rows: int, attempts: int = 3) -> tuple[list[dict], int]:
    """느린 프록시의 간헐적 ReadTimeout 대비 — 페이지별 재시도(지수 백오프)."""
    last: Exception | None = None
    for n in range(attempts):
        try:
            return await fetch_mountains(page=page, rows=rows)
        except AdapterError as exc:
            last = exc
            await asyncio.sleep(1.5 * (n + 1))
    raise last  # type: ignore[misc]


async def run(max_pages: int | None = None, rows: int = 100) -> dict:
    if not get_settings().live_data:
        return {"ok": False, "reason": "DATA_GO_KR_KEY 미설정 — 적재 생략", "loaded": 0}

    init_db()
    db = SessionLocal()
    try:
        # 이어받기 — 이미 적재된 수만큼 페이지를 건너뛰고 재개(재시작·부분실패 대비, 멱등).
        existing = db.scalar(select(func.count()).select_from(Mountain)) or 0
        page = existing // rows + 1
        loaded, total = 0, None
        while True:
            try:
                items, total = await _fetch_retry(page, rows)
            except AdapterError as exc:
                # 재시도 후에도 실패 → 부분 적재 상태로 종료(다음 시작 시 이어받기).
                return {"ok": False, "reason": str(exc), "loaded": loaded,
                        "page": page, "in_db": existing + loaded}
            if not items:
                break
            for row in items:
                db.merge(_to_model(row))   # upsert (멱등)
            db.commit()
            loaded += len(items)
            if (total and existing + loaded >= total) or (max_pages and page >= max_pages):
                break
            page += 1
        return {"ok": True, "loaded": loaded, "total": total,
                "in_db": existing + loaded, "pages": page}
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
