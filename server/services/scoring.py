"""산행지수·코스 매칭·위험도 융합 — 앱과 동일한 규칙의 단일 출처(서버)."""
from ..seed import COURSES

CROWD_PENALTY = {"높음": 8, "보통": 3, "낮음": 1}


def hike_index(cond: dict) -> dict:
    """오늘의 산행지수 0~100 — 산불(0.3) + 산사태(0.25) + 기상(0.25) + 일몰(0.2)."""
    score = int(
        cond["fire"]["score"] * 0.30
        + cond["landslide"]["score"] * 0.25
        + cond["weather"]["score"] * 0.25
        + cond["sunset_score"] * 0.20
    )
    if score >= 80:
        label = "좋음 — 산행하기 좋은 날"
    elif score >= 60:
        label = "보통 — 기상 변화에 유의"
    else:
        label = "주의 — 무리한 산행은 피하세요"
    return {"score": score, "label": label}


def match_score(course: dict, fit: int, knee: bool, heart: bool, weather_score: float) -> int:
    """프로필 기반 코스 매칭률. 규칙은 단순·설명가능해야 한다(심사·CS 대응)."""
    s = 100.0
    s -= abs(course["level_n"] - fit) * 9
    if course["steep"] and knee:
        s -= 4
    if heart and course["level_n"] >= 3:
        s -= 6
    s -= CROWD_PENALTY.get(course["crowd"], 3)
    s -= (100 - weather_score) / 12
    return round(s)


def recommend(fit: int, knee: bool, heart: bool, conditions: dict[str, dict]) -> list[dict]:
    """전 코스 매칭 점수 산출 후 내림차순. conditions: region_id → 지역 조건."""
    out = []
    for c in COURSES:
        wx = conditions[c["region"]]["weather"]["score"]
        score = match_score(c, fit, knee, heart, wx)
        reasons = []
        if abs(c["level_n"] - fit) == 0:
            reasons.append("체력 수준과 난이도 일치")
        if c["steep"] and knee:
            reasons.append("급경사 구간 — 무릎 주의 반영(-4)")
        if c["crowd"] == "낮음":
            reasons.append("혼잡도 낮음")
        out.append({"course": c, "score": score, "reasons": reasons})
    return sorted(out, key=lambda x: -x["score"])


def fused_risk(course: dict, rain_prob: int, wind: float) -> list[dict]:
    """구간 위험도 융합 — 정적 위험(산사태등급·사고이력)에 실시간 기상을 가중.

    운영에서는 XGBoost 모델 서빙으로 교체한다(피처 스토어 동일). 규칙 기반
    베이스라인을 남겨 두는 이유: 모델 장애 시 폴백 + 설명가능성.
    """
    out = []
    for hz in course["hazards"]:
        base = 70 if "사고다발" in hz["grade"] or "1등급" in hz["grade"] else 50
        score = base + (10 if rain_prob >= 30 else 0) + (8 if wind >= 8 else 0)
        score = min(99, score)
        if score >= 75:
            action = "진입 경고"
        elif score >= 60:
            action = "우회 권고"
        else:
            action = "주의"
        out.append({**hz, "risk": score, "action": action})
    return sorted(out, key=lambda x: -x["risk"])
