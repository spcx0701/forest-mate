"""공개 API — 산행지수·코스·추천·AI 챗·종 판별·전국 산 검색."""
import asyncio

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..adapters import mountains as mnt_api
from ..adapters.base import last_errors
from ..adapters.public_data import get_fire_risk, get_region_conditions, get_weather
from ..config import get_settings
from ..db import get_db
from ..models import Mountain
from ..schemas import ChatIn, SpeciesIn
from ..seed import COURSES, REGIONS, SPECIES
from ..services import chat as chat_service
from ..services.scoring import fused_risk, hike_index, recommend

router = APIRouter()


@router.get("/healthz")
async def healthz():
    settings = get_settings()
    return {"status": "ok", "service": settings.app_name, "env": settings.env,
            "live_data": settings.live_data, "llm": settings.llm_enabled}


@router.get("/regions")
async def regions():
    return [{"id": rid, "name": r["name"]} for rid, r in REGIONS.items()]


@router.get("/mountains")
async def mountains(q: str = "", sido: str = "", page: int = 1, size: int = 30,
                    db: Session = Depends(get_db)):
    """전국 산 검색 — 산림청 산정보 ETL 적재분(Mountain). 이름·시도로 필터."""
    size = max(1, min(size, 100))
    stmt = select(Mountain)
    if q:
        stmt = stmt.where(Mountain.name.contains(q))
    if sido:
        stmt = stmt.where(Mountain.sido.contains(sido))
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    rows = db.execute(
        stmt.order_by(Mountain.is_top100.desc(), Mountain.name)
        .limit(size).offset((page - 1) * size)
    ).scalars().all()
    return {
        "total": total, "page": page, "size": size,
        "items": [{"list_no": m.list_no, "name": m.name, "addr": m.addr,
                   "sido": m.sido, "height": m.height, "top100": m.is_top100,
                   "summary": m.summary} for m in rows],
    }


@router.get("/mountains/sido")
async def mountains_by_sido(db: Session = Depends(get_db)):
    """시·도별 산 개수(전국 분포 표출용)."""
    rows = db.execute(
        select(Mountain.sido, func.count()).group_by(Mountain.sido).order_by(func.count().desc())
    ).all()
    return [{"sido": s or "(미상)", "count": n} for s, n in rows]


@router.get("/_diag", include_in_schema=False)
async def _diag(db: Session = Depends(get_db)):
    """라이브 진단 — 기상·산불·산정보 어댑터 동작과 적재 현황(키 값은 미노출)."""
    s = get_settings()
    w = await get_weather("eunpyeong")
    f = await get_fire_risk("eunpyeong")
    mnt_probe: dict
    try:
        items, total = await mnt_api.fetch_mountains(page=1, rows=3)
        mnt_probe = {"ok": True, "total_available": total,
                     "sample": [i["mntiname"] for i in items]}
    except Exception as exc:  # noqa: BLE001
        mnt_probe = {"ok": False, "error": str(exc)[:200]}
    return {
        "live_data": s.live_data,
        "key_form": ("encoded" if "%" in s.data_go_kr_key else "raw") if s.data_go_kr_key else "none",
        "weather": {"source": w.get("source"), "temp": w.get("temp"), "station": w.get("station")},
        "fire": {"source": f.get("source"), "level": f.get("level")},
        "mountains_api": mnt_probe,
        "mountains_in_db": db.scalar(select(func.count()).select_from(Mountain)) or 0,
        "errors": dict(last_errors),
    }


@router.get("/index")
async def index(region: str = "eunpyeong"):
    try:
        cond = await get_region_conditions(region)
    except KeyError:
        raise HTTPException(404, f"unknown region: {region}")
    idx = hike_index(cond)
    return {**idx, "conditions": cond}


@router.get("/courses")
async def courses():
    return COURSES


@router.get("/courses/{course_id}")
async def course_detail(course_id: str):
    course = next((c for c in COURSES if c["id"] == course_id), None)
    if not course:
        raise HTTPException(404, "course not found")
    cond = await get_region_conditions(course["region"])
    risks = fused_risk(course, cond["weather"]["rain_prob"], cond["weather"]["wind"])
    return {**course, "risks": risks, "conditions": cond}


@router.get("/recommend")
async def recommend_courses(fit: int = 2, knee: bool = False, heart: bool = False):
    region_ids = {c["region"] for c in COURSES}
    conds = dict(zip(region_ids,
                     await asyncio.gather(*(get_region_conditions(r) for r in region_ids))))
    items = recommend(fit, knee, heart, conds)
    return [{"course_id": i["course"]["id"], "name": i["course"]["name"],
             "score": i["score"], "reasons": i["reasons"]} for i in items]


@router.post("/chat")
async def chat(body: ChatIn):
    cond = await get_region_conditions(body.region_id)
    return await chat_service.answer(body.message, body.lang, body.region_id,
                                     body.course_id, body.progress, cond)


@router.post("/species/identify")
async def identify(body: SpeciesIn):
    """운영: multipart 이미지 → 온디바이스 1차 + 서버 비전 모델 2차 검증.
    데모 빌드는 샘플 ID로 도감 스냅샷을 반환한다(스키마 동일)."""
    sp = next((s for s in SPECIES if s["id"] == body.sample_id), None)
    if not sp:
        raise HTTPException(404, "unknown sample")
    return {**sp, "source": "국가생물종지식정보(국립수목원) 대조", "model": "demo-lookup"}
