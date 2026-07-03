"""AI 챗 서비스 — 의도 엔진(기본) + LLM RAG(키 설정 시).

LLM 장애·미설정 시에도 핵심 안전 답변이 항상 동작해야 하므로
규칙 기반 의도 엔진을 1급 폴백으로 유지한다.
"""
import logging
import re

import anthropic

from ..config import get_settings
from ..seed import COURSES, REGIONS
from .scoring import fused_risk

log = logging.getLogger(__name__)

I18N = {
    "en": {
        "hello": "Hi! I'm Soop-i, your AI forest guide. Ask me about trails, weather, or plants!",
        "summit": "About {km}km to the summit — roughly {min} min at your pace. Watch the wind near the top. 🧥",
    },
    "zh": {
        "hello": "你好！我是AI森林向导。可以问我路线、天气或植物！",
        "summit": "距离山顶约{km}公里，按您的速度约{min}分钟。山顶风大，请注意保暖！🧥",
    },
    "ja": {
        "hello": "こんにちは！AI森ガイドです。コース・天気・植物について聞いてください！",
        "summit": "山頂まで約{km}km、今のペースで約{min}分です。山頂は風が強いのでご注意を！🧥",
    },
}


def _course(course_id: str | None) -> dict:
    return next((c for c in COURSES if c["id"] == course_id), COURSES[0])


def _context_block(cond: dict, course: dict, progress: float) -> str:
    """LLM에 주입할 실시간 컨텍스트(공공데이터 조회 결과)."""
    left_km = round(course["km"] * (1 - progress), 1)
    left_min = round(course["minutes"] * (1 - progress) * 0.9)
    risks = fused_risk(course, cond["weather"]["rain_prob"], cond["weather"]["wind"])
    risk_txt = "; ".join(f"{r['type']}({r['grade']}, 위험도 {r['risk']})" for r in risks)
    return (
        f"지역: {cond['name']} / 기상: {cond['weather']['temp']}°C {cond['weather']['label']}, "
        f"풍속 {cond['weather']['wind']}m/s, 강수확률 {cond['weather']['rain_prob']}% "
        f"(출처: {cond['weather'].get('station', '')})\n"
        f"산불위험: {cond['fire']['level']} / 일몰: {cond['sunset_at']}\n"
        f"코스: {course['name']} — 진행률 {round(progress * 100)}%, 남은 거리 {left_km}km, "
        f"예상 {left_min}분\n위험구간: {risk_txt}"
    )


def rule_reply(message: str, lang: str, cond: dict, course: dict, progress: float) -> dict:
    """규칙 기반 의도 엔진 — LLM 폴백이자 오프라인 기본기."""
    t = message.lower()
    left_km = round(course["km"] * (1 - progress), 1)
    left_min = round(course["minutes"] * (1 - progress) * 0.9)

    if re.search(r"(남았|얼마나|거리|도착|시간|summit|远|頂)", t):
        if lang in I18N:
            return {"reply": I18N[lang]["summit"].format(km=left_km, min=left_min),
                    "intent": "summit"}
        return {"reply": f"남은 거리 {left_km}km, 현재 페이스라면 약 {left_min}분 뒤 도착해요. "
                         f"일몰({cond['sunset_at']})까지 하산 여유를 꼭 확인하세요.",
                "intent": "summit"}
    if re.search(r"(날씨|기상|바람|비|기온|weather)", t):
        w = cond["weather"]
        return {"reply": f"{cond['name']} 산악기상: {w['temp']}°C {w['label']}, 풍속 {w['wind']}m/s, "
                         f"강수확률 {w['rain_prob']}%. 고지대는 도심보다 5~8℃ 낮아요.",
                "intent": "weather"}
    if re.search(r"(위험|낙석|산사태|사고|조심)", t):
        risks = fused_risk(course, cond["weather"]["rain_prob"], cond["weather"]["wind"])
        txt = ", ".join(f"{r['type']}({r['action']})" for r in risks)
        return {"reply": f"{course['name']} 위험 구간 {len(risks)}곳: {txt}. "
                         "접근 300m 전에 미리 알려드릴게요.", "intent": "hazard"}
    if re.search(r"(휴양림|치유|예약)", t):
        return {"reply": "이번 주 토 10시 축령산 치유의숲 프로그램에 잔여석이 있어요. "
                         "마이 탭 안심 서비스에서 예약을 도와드릴게요.", "intent": "healing"}
    if re.search(r"(보험)", t):
        return {"reply": "1일 안심보험은 산행지수 연동으로 990원부터예요. 상해·구조비용이 보장돼요.",
                "intent": "insurance"}
    if re.search(r"(안녕|hello|hi|你好|こんにちは)", t):
        reply = I18N.get(lang, {}).get("hello",
                                       "안녕하세요! 코스·날씨·위험 정보, 식물 사진까지 무엇이든 물어보세요.")
        return {"reply": reply, "intent": "greeting"}
    return {"reply": "산림 공공데이터 기반으로 코스 안내·산악 날씨·위험 구간·식물 식별을 도와드려요. "
                     "어떤 게 궁금하세요?", "intent": "fallback"}


async def answer(message: str, lang: str, course_id: str | None, progress: float, cond: dict) -> dict:
    settings = get_settings()
    course = _course(course_id)
    sources = [
        "기상청 단기예보" if cond["weather"].get("source") == "live" else "기상 스냅샷",
        cond["fire"].get("src", "산불위험예보"),
        "산사태정보시스템", "소방청 산악사고 통계",
    ]

    if settings.llm_enabled:
        from .llm import ask_llm
        try:
            reply = await ask_llm(message, lang, _context_block(cond, course, progress))
            return {"reply": reply, "intent": "llm", "engine": "claude", "sources": sources}
        except anthropic.APIError as exc:
            log.warning("LLM fallback to rules: %s", exc)

    result = rule_reply(message, lang, cond, course, progress)
    return {**result, "engine": "rules", "sources": sources}
