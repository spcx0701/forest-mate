import pytest

from server.seed import COURSES, REGIONS
from server.services.scoring import fused_risk, hike_index, match_score, recommend


def _cond(region_id: str) -> dict:
    region = REGIONS[region_id]
    return {**region["snapshot"], "name": region["name"],
            "sunset_at": region["sunset_at"],
            "sunset_score": region["snapshot"]["sunset_score"]}


@pytest.mark.parametrize(("region_id", "score", "label"), [
    ("eunpyeong", 82, "좋음"),
    ("guri", None, "보통"),
])
def test_hike_index_cases(region_id, score, label):
    got = hike_index(_cond(region_id))
    if score is not None:
        assert got["score"] == score
    assert label in got["label"]


def test_match_score_default_profile_prefers_bukhansan():
    bukhansan = next(c for c in COURSES if c["id"] == "bukhansan")
    score = match_score(bukhansan, fit=2, knee=True, heart=False, weather_score=88)
    assert score == 92


@pytest.mark.parametrize(("fit", "knee", "expected"), [
    (3, False, "dobong"),
    (1, False, 1),
    (2, True, "bukhansan"),
], ids=["advanced", "beginner", "knee-friendly"])
def test_recommend_orders_by_profile(fit, knee, expected):
    conds = {rid: _cond(rid) for rid in REGIONS}
    top = recommend(fit=fit, knee=knee, heart=False, conditions=conds)[0]["course"]
    assert (top["level_n"] if isinstance(expected, int) else top["id"]) == expected


def test_fused_risk_rain_raises_score():
    course = next(c for c in COURSES if c["id"] == "bukhansan")
    dry = fused_risk(course, rain_prob=0, wind=2)
    wet = fused_risk(course, rain_prob=60, wind=9)
    assert wet[0]["risk"] > dry[0]["risk"]
    assert all(r["risk"] <= 99 for r in wet)
