from datetime import datetime, timedelta, timezone

import pytest

from server.services.safety import assess_distress, k_anonymize

NOW = datetime(2026, 6, 13, 12, 0, tzinfo=timezone.utc)


def _pt(minutes_ago: float, progress: float, hr: int | None = 95) -> dict:
    return {"progress": progress, "hr": hr,
            "created_at": NOW - timedelta(minutes=minutes_ago)}


@pytest.mark.parametrize(("points", "expected"), [
    ([_pt(40, 0.30), _pt(20, 0.40), _pt(1, 0.50)], (0, "moving")),
    ([_pt(10, 0.50), _pt(5, 0.50), _pt(1, 0.50)], (0, "stall_below_threshold")),
    ([_pt(45, 0.50), _pt(30, 0.5005), _pt(1, 0.501)], (1, "stalled")),
    ([_pt(45, 0.50), _pt(30, 0.50), _pt(1, 0.50, hr=152)],
     (2, "stalled_with_hr_anomaly")),
    ([_pt(1, 0.1)], (0, "insufficient_data")),
], ids=["moving", "short-stall", "long-stall", "hr-anomaly", "insufficient"])
def test_assess_distress_cases(points, expected):
    res = assess_distress(points, now=NOW)
    assert (res["level"], res["reason"]) == expected


@pytest.mark.parametrize(("progresses", "k", "expected"), [
    ([0.11, 0.12, 0.13, 0.95], 2, [(1, 3)]),
    ([0.1, 0.9], 1, [(1, 1), (9, 1)]),
], ids=["hide-small-clusters", "k1-shows-all"])
def test_k_anonymize_cases(progresses, k, expected):
    assert [(c["bin"], c["count"]) for c in k_anonymize(progresses, k=k)] == expected
