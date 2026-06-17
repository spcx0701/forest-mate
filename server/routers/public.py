"""공개 API — 산행지수·코스·추천·AI 챗·종 판별·전국 산 검색·GPS 주변."""
import asyncio
import json
import math
from pathlib import Path
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..adapters.public_data import conditions_for_region, get_region_conditions
from ..config import get_settings
from ..db import get_db
from ..geo import region_for_coords, region_for_mountain
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
async def mountains(db: Annotated[Session, Depends(get_db)],
                    q: str = "", sido: str = "", page: int = 1, size: int = 30):
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
                   "lat": m.lat, "lon": m.lon, "summary": m.summary} for m in rows],
    }


def _haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """두 좌표 간 거리(km)."""
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


@router.get("/mountains/nearby")
async def mountains_nearby(lat: float, lon: float, db: Annotated[Session, Depends(get_db)],
                           radius: float = 30, limit: int = 20):
    """현재 위치(GPS) 주변 산 — 좌표 보유 산 중 거리순. radius km 이내."""
    rows = db.execute(
        select(Mountain).where(Mountain.lat.is_not(None))
    ).scalars().all()
    out = []
    for m in rows:
        d = _haversine(lat, lon, m.lat, m.lon)
        if d <= radius:
            out.append((d, m))
    out.sort(key=lambda x: x[0])
    return {
        "origin": {"lat": lat, "lon": lon}, "radius_km": radius,
        "count": len(out),
        "items": [{"list_no": m.list_no, "name": m.name, "addr": m.addr,
                   "sido": m.sido, "height": m.height, "top100": m.is_top100,
                   "lat": m.lat, "lon": m.lon, "dist_km": round(d, 1)}
                  for d, m in out[:limit]],
    }


@router.get("/mountains/sido")
async def mountains_by_sido(db: Annotated[Session, Depends(get_db)]):
    """시·도별 산 개수(전국 분포 표출용)."""
    rows = db.execute(
        select(Mountain.sido, func.count()).group_by(Mountain.sido).order_by(func.count().desc())
    ).all()
    return [{"sido": s or "(미상)", "count": n} for s, n in rows]


@router.get("/mountains/{list_no}/index",
            responses={404: {"description": "Mountain not found"}})
async def mountain_index(list_no: str, db: Annotated[Session, Depends(get_db)]):
    """선택한 산의 산행지수 — 실측 좌표가 있으면 정밀 격자·시군구로 계산."""
    m = db.get(Mountain, list_no)
    if not m:
        raise HTTPException(404, "mountain not found")
    region = region_for_mountain(m)
    cond = await conditions_for_region(region)
    idx = hike_index(cond)
    place = (m.addr or m.name) if region.get("precise") else f"{m.sido} 대표지점 추정"
    return {
        **idx, "conditions": cond,
        "mountain": {"list_no": m.list_no, "name": m.name, "sido": m.sido,
                     "addr": m.addr, "height": m.height, "top100": m.is_top100,
                     "lat": m.lat, "lon": m.lon,
                     "facilities": json.loads(m.facilities or "{}")},
        "place": place,
    }


@router.get("/index", responses={404: {"description": "Unknown region"}})
async def index(region: str = "eunpyeong"):
    try:
        cond = await get_region_conditions(region)
    except KeyError:
        raise HTTPException(404, f"unknown region: {region}")
    idx = hike_index(cond)
    return {**idx, "conditions": cond}


@router.get("/forecast")
async def forecast(lat: float, lon: float, db: Annotated[Session, Depends(get_db)]):
    """산행 일정용 일자별 예보 — 날씨·산불 적합도(현 위치 최근접 산의 시군구로 산불)."""
    from datetime import datetime, timedelta

    from ..adapters.public_data import get_fire_risk, get_forecast
    rows = db.execute(select(Mountain).where(Mountain.lat.is_not(None))).scalars().all()
    sgg = min(((_haversine(lat, lon, m.lat, m.lon), m) for m in rows),
              key=lambda x: x[0])[1].sgg if rows else ""
    region = region_for_coords(lat, lon, sgg)
    fire = await get_fire_risk(region)
    days_raw = await get_forecast(region)
    dows = ["월", "화", "수", "목", "금", "토", "일"]
    out = []
    for i, d in enumerate(days_raw):
        dt = datetime.strptime(d["date"], "%Y%m%d")
        # 적합도 = 날씨점수·산불점수 결합
        score = int(d["score"] * 0.6 + fire["score"] * 0.4)
        if i == 0:
            label = "오늘"
        elif i == 1:
            label = "내일"
        else:
            label = f"{dt.month}/{dt.day}"
        out.append({"date": d["date"], "label": label,
                    "dow": dows[dt.weekday()], "temp": d["temp"], "rain_prob": d["rain_prob"],
                    "fire": fire["level"], "score": score})
    return {"days": out}


_TRAILS_DIR = Path(__file__).resolve().parents[1] / "data" / "trails"


@router.get("/mountains/{list_no}/trails")
async def mountain_trails(list_no: str):
    """등산로 선(line) — 산림청 등산로 공간정보(FRT000801) 산별 경로. 없으면 빈 목록."""
    f = _TRAILS_DIR / f"{list_no}.json"
    if not f.exists():
        return {"name": "", "segs": []}
    return json.loads(f.read_text(encoding="utf-8"))


@router.get("/index/gps")
async def index_gps(lat: float, lon: float, db: Annotated[Session, Depends(get_db)]):
    """현재 위치(GPS) 산행지수 — 정밀 날씨격자 + 최근접 산의 시군구로 산불."""
    rows = db.execute(select(Mountain).where(Mountain.lat.is_not(None))).scalars().all()
    sgg, nearest_name = "", ""
    if rows:
        _, nm = min(((_haversine(lat, lon, m.lat, m.lon), m) for m in rows), key=lambda x: x[0])
        sgg, nearest_name = nm.sgg, nm.name
    cond = await conditions_for_region(region_for_coords(lat, lon, sgg))
    idx = hike_index(cond)
    return {**idx, "conditions": cond, "place": "현재 위치", "nearest": nearest_name}


@router.get("/courses")
async def courses():
    return COURSES


@router.get("/courses/{course_id}",
            responses={404: {"description": "Course not found"}})
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
    return await chat_service.answer(body.message, body.lang, body.course_id, body.progress, cond)


@router.post("/species/identify",
             responses={404: {"description": "Unknown sample"}})
async def identify(body: SpeciesIn):
    """운영: multipart 이미지 → 온디바이스 1차 + 서버 비전 모델 2차 검증.
    데모 빌드는 샘플 ID로 도감 스냅샷을 반환한다(스키마 동일)."""
    sp = next((s for s in SPECIES if s["id"] == body.sample_id), None)
    if not sp:
        raise HTTPException(404, "unknown sample")
    return {**sp, "source": "국가생물종지식정보(국립수목원) 대조", "model": "demo-lookup"}
