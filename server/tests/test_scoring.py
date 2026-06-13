from server.seed import COURSES, REGIONS
from server.services.scoring import fused_risk, hike_index, match_score, recommend


def _cond(region_id: str) -> dict:
    region = REGIONS[region_id]
    return {**region["snapshot"], "name": region["name"],
            "sunset_at": region["sunset_at"],
            "sunset_score": region["snapshot"]["sunset_score"]}


def test_hike_index_eunpyeong_is_82():
    assert hike_index(_cond("eunpyeong"))["score"] == 82


def test_hike_index_labels():
    assert "좋음" in hike_index(_cond("eunpyeong"))["label"]
    assert hike_index(_cond("guri"))["score"] < 80


def test_match_score_default_profile_prefers_bukhansan():
    bukhansan = next(c for c in COURSES if c["id"] == "bukhansan")
    score = match_score(bukhansan, fit=2, knee=True, heart=False, weather_score=88)
    assert score == 92


def test_recommend_orders_by_fit():
    conds = {rid: _cond(rid) for rid in REGIONS}
    advanced = recommend(fit=3, knee=False, heart=False, conditions=conds)
    assert advanced[0]["course"]["id"] == "dobong"
    beginner = recommend(fit=1, knee=False, heart=False, conditions=conds)
    assert beginner[0]["course"]["level_n"] == 1


def test_fused_risk_rain_raises_score():
    course = next(c for c in COURSES if c["id"] == "bukhansan")
    dry = fused_risk(course, rain_prob=0, wind=2)
    wet = fused_risk(course, rain_prob=60, wind=9)
    assert wet[0]["risk"] > dry[0]["risk"]
    assert all(r["risk"] <= 99 for r in wet)
