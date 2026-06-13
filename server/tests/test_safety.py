from datetime import datetime, timedelta, timezone

from server.services.safety import assess_distress, k_anonymize

NOW = datetime(2026, 6, 13, 12, 0, tzinfo=timezone.utc)


def _pt(minutes_ago: float, progress: float, hr: int | None = 95) -> dict:
    return {"progress": progress, "hr": hr,
            "created_at": NOW - timedelta(minutes=minutes_ago)}


def test_moving_is_not_distress():
    pts = [_pt(40, 0.30), _pt(20, 0.40), _pt(1, 0.50)]
    assert assess_distress(pts, now=NOW)["level"] == 0


def test_short_stall_below_threshold():
    pts = [_pt(10, 0.50), _pt(5, 0.50), _pt(1, 0.50)]
    res = assess_distress(pts, now=NOW)
    assert res["level"] == 0
    assert res["reason"] == "stall_below_threshold"


def test_long_stall_triggers_level1():
    pts = [_pt(45, 0.50), _pt(30, 0.5005), _pt(1, 0.501)]
    res = assess_distress(pts, now=NOW)
    assert res["level"] == 1


def test_stall_with_hr_anomaly_is_level2():
    pts = [_pt(45, 0.50), _pt(30, 0.50), _pt(1, 0.50, hr=152)]
    res = assess_distress(pts, now=NOW)
    assert res["level"] == 2


def test_insufficient_data():
    assert assess_distress([_pt(1, 0.1)], now=NOW)["level"] == 0


def test_k_anonymize_hides_small_clusters():
    progresses = [0.11, 0.12, 0.13, 0.95]  # bin1=3명, bin9=1명
    cells = k_anonymize(progresses, k=2)
    assert len(cells) == 1
    assert cells[0]["bin"] == 1 and cells[0]["count"] == 3


def test_k_anonymize_k1_shows_all():
    cells = k_anonymize([0.1, 0.9], k=1)
    assert sum(c["count"] for c in cells) == 2
