"""API 통합 테스트 — 기기 등록 → 산행 → 위험경고 → SOS → 관제 반영 전체 흐름."""


def test_healthz(client):
    res = client.get("/api/v1/healthz")
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "ok"
    assert body["live_data"] is False  # 테스트는 스냅샷 모드


def test_index_snapshot_score(client):
    res = client.get("/api/v1/index", params={"region": "eunpyeong"})
    assert res.status_code == 200
    body = res.json()
    assert body["score"] == 82
    assert body["conditions"]["weather"]["source"] == "snapshot"


def test_index_unknown_region_404(client):
    assert client.get("/api/v1/index", params={"region": "nowhere"}).status_code == 404


def test_recommend_personalization(client):
    res = client.get("/api/v1/recommend", params={"fit": 3})
    assert res.json()[0]["course_id"] == "dobong"
    res = client.get("/api/v1/recommend", params={"fit": 2, "knee": True})
    assert res.json()[0]["course_id"] == "bukhansan"
    assert res.json()[0]["score"] == 92


def test_chat_rule_engine(client):
    res = client.post("/api/v1/chat", json={"message": "오늘 산 날씨 어때?"})
    body = res.json()
    assert body["engine"] == "rules"
    assert body["intent"] == "weather"
    assert "18" in body["reply"]  # 은평 스냅샷 기온


def test_species_identify(client):
    res = client.post("/api/v1/species/identify", json={"sample_id": "mushroom"})
    body = res.json()
    assert body["toxic"] is True
    assert "개나리광대버섯" in body["name"]


def test_mountains_search_empty(client):
    # 키 없는 테스트 환경 — 카탈로그 비어있어도 검색 API는 정상 스키마 반환
    res = client.get("/api/v1/mountains?q=북한")
    assert res.status_code == 200
    body = res.json()
    assert body["total"] == 0 and body["items"] == []
    assert client.get("/api/v1/mountains/sido").status_code == 200


def test_mountain_index_resolves_region(client):
    # 산 1개 시드 후, 선택한 산의 산행지수가 시도 대표지점으로 계산되는지
    from server.db import SessionLocal
    from server.models import Mountain
    db = SessionLocal()
    db.merge(Mountain(list_no="GEO1", name="지리산", sido="경상남도",
                      addr="경상남도 산청군 시천면", height=1915, is_top100=True))
    db.commit()
    db.close()

    res = client.get("/api/v1/mountains/GEO1/index")
    assert res.status_code == 200
    body = res.json()
    assert body["mountain"]["name"] == "지리산"
    assert "경상남도" in body["place"]
    assert "weather" in body["conditions"] and "fire" in body["conditions"]
    # 키 없는 환경이므로 스냅샷으로 폴백(엔드포인트·해석 경로는 정상 동작)
    assert body["conditions"]["weather"]["source"] == "snapshot"
    assert isinstance(body["score"], int) and 0 <= body["score"] <= 100

    assert client.get("/api/v1/mountains/NOPE/index").status_code == 404


def test_gps_nearby_and_precise_index(client):
    # 실측 좌표 보유 산 → GPS 주변검색·현재위치지수·정밀 산행지수
    from server.db import SessionLocal
    from server.models import Mountain
    db = SessionLocal()
    db.merge(Mountain(list_no="GPS1", name="청계산", sido="경기도",
                      addr="경기도 과천시 막계동", height=618,
                      lat=37.4449, lon=127.0539, sgg="41290"))
    db.commit()
    db.close()

    # 주변 검색 — 과천 인근 좌표
    res = client.get("/api/v1/mountains/nearby", params={"lat": 37.45, "lon": 127.05, "radius": 10})
    assert res.status_code == 200
    body = res.json()
    assert body["count"] >= 1
    assert body["items"][0]["name"] == "청계산"
    assert body["items"][0]["dist_km"] < 5

    # 현재 위치(GPS) 지수
    g = client.get("/api/v1/index/gps", params={"lat": 37.45, "lon": 127.05}).json()
    assert g["place"] == "현재 위치" and 0 <= g["score"] <= 100

    # 정밀 산행지수 — 좌표 있으면 place가 시도근사가 아닌 실주소
    pi = client.get("/api/v1/mountains/GPS1/index").json()
    assert "대표지점 추정" not in pi["place"]
    assert pi["mountain"]["lat"] == 37.4449


def test_full_hike_flow(client):
    # 1) 기기 등록(익명)
    res = client.post("/api/v1/devices", json={"name": "테스터", "fit": 2, "knee": True})
    assert res.status_code == 201
    token = res.json()["token"]
    auth = {"Authorization": f"Bearer {token}"}

    # 2) 인증 없는 호출 거부
    assert client.post("/api/v1/hikes", json={"course_id": "bukhansan"}).status_code == 401

    # 3) 산행 시작
    res = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth)
    assert res.status_code == 201
    hike_id = res.json()["hike_id"]

    # 4) 트랙 업데이트 — 위험구간(0.62) 접근 창(0.54~0.62) 진입 시 경고
    #    0.20은 어느 위험구간 창(0.30~0.38, 0.54~0.62)에도 안 걸린다
    res = client.post(f"/api/v1/hikes/{hike_id}/track",
                      json={"progress": 0.20, "alt": 400, "hr": 96}, headers=auth)
    assert res.json()["alerts"] == []
    res = client.post(f"/api/v1/hikes/{hike_id}/track",
                      json={"progress": 0.56, "alt": 520, "hr": 101}, headers=auth)
    alerts = res.json()["alerts"]
    assert len(alerts) == 1 and "낙석주의" in alerts[0]["title"]

    # 5) SOS 접수
    res = client.post("/api/v1/sos", json={"hike_id": hike_id, "note": "발목 부상"},
                      headers=auth)
    assert res.status_code == 201
    body = res.json()
    assert body["status"] == "dispatched"
    assert body["grid_no"] == "다사 5683 2741"

    # 6) 산행 종료 → 기록 반환
    res = client.post(f"/api/v1/hikes/{hike_id}/end", headers=auth)
    assert res.json()["distance_km"] > 0

    # 7) 관제 요약에 사건 반영
    res = client.get("/api/v1/dashboard/summary")
    body = res.json()
    assert body["kpi"]["open_sos"] >= 1
    kinds = [e["kind"] for e in body["events"]]
    assert "sos" in kinds and "checkin" in kinds
    assert len(body["risk_table"]) > 0

    # 8) 마이 리포트 — 하드코딩이 아니라 이 기기의 실제 완료 산행을 집계
    res = client.get("/api/v1/hikes/summary", headers=auth)
    assert res.status_code == 200
    rep = res.json()
    assert rep["total_hikes"] == 1
    assert rep["total_km"] > 0 and rep["total_kcal"] > 0
    assert rep["active_days"] >= 1
    assert sum(m["count"] for m in rep["monthly"]) == 1  # 완료 1건이 월별에 반영


def test_dashboard_websocket_receives_sos(client):
    res = client.post("/api/v1/devices", json={"name": "WS", "fit": 2})
    auth = {"Authorization": f"Bearer {res.json()['token']}"}
    with client.websocket_connect("/api/v1/ws/dashboard") as ws:
        client.post("/api/v1/sos", json={"note": "테스트"}, headers=auth)
        msg = ws.receive_json()
        assert msg["type"] == "sos"
        assert "SOS" in msg["title"]
