"""공개 API — 산행지수·코스·추천·AI 챗·종 판별."""
import asyncio

from fastapi import APIRouter, HTTPException

from ..adapters.public_data import get_region_conditions
from ..config import get_settings
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
