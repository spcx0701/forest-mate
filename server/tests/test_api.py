"""API 통합 테스트 — 기기 등록 → 산행 → 위험경고 → SOS → 관제 반영 전체 흐름."""

import asyncio
from datetime import timedelta

import pytest


def _csp_directives(csp):
    directives = {}
    for directive in csp.split(";"):
        parts = directive.strip().split()
        if parts:
            directives[parts[0]] = set(parts[1:])
    return directives


def test_healthz_snapshot_mode(client):
    res = client.get("/api/v1/healthz")
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "ok"
    assert body["live_data"] is False  # 테스트는 스냅샷 모드


@pytest.mark.parametrize("path", ["/index.html", "/docs", "/api/v1/healthz"])
def test_security_headers_present(client, path):
    res = client.get(path)
    assert res.status_code == 200
    assert res.headers["x-frame-options"] == "DENY"
    assert res.headers["x-content-type-options"] == "nosniff"
    assert res.headers["referrer-policy"] == "strict-origin-when-cross-origin"
    csp = res.headers["content-security-policy"]
    assert "frame-ancestors 'none'" in csp
    assert "'unsafe-inline'" not in csp
    assert "*" not in csp


def test_root_serves_landing_page_before_static_app(client):
    res = client.get("/")
    assert res.status_code == 200
    assert res.headers["content-type"].startswith("text/html")
    assert "산림 공공데이터·AI 산행 안전 플랫폼" in res.text
    assert 'id="download"' in res.text
    assert "AI 산행 안전 동반자" not in res.text


@pytest.mark.no_db
def test_root_falls_back_to_index_when_landing_page_is_missing(tmp_path, monkeypatch):
    from fastapi.testclient import TestClient

    from server import main

    app_dir = tmp_path / "app"
    app_dir.mkdir()
    (app_dir / "index.html").write_text("<!doctype html><title>web app</title>", encoding="utf-8")

    main._inline_csp_hashes.cache_clear()
    monkeypatch.setattr(main, "APP_DIR", app_dir)
    try:
        with TestClient(main.create_app()) as local_client:
            res = local_client.get("/")
    finally:
        main._inline_csp_hashes.cache_clear()

    assert res.status_code == 200
    assert res.headers["content-type"].startswith("text/html")
    assert "<title>web app</title>" in res.text


def test_csp_keeps_wikipedia_thumbnail_hosts_out_of_browser(client):
    res = client.get("/index.html")
    directives = _csp_directives(res.headers["content-security-policy"])

    assert "'self'" in directives["connect-src"]
    assert directives["connect-src"].isdisjoint({"https://ko.wikipedia.org"})
    assert "'self'" in directives["img-src"]
    assert directives["img-src"].isdisjoint({"https://upload.wikimedia.org"})


def test_csp_does_not_allow_external_leaflet_cdn_when_map_assets_are_local(client):
    res = client.get("/index.html")
    directives = _csp_directives(res.headers["content-security-policy"])

    assert "https://unpkg.com" not in directives["script-src"]
    assert "https://unpkg.com" not in directives["style-src"]


def test_mountain_hero_proxy_serves_validated_same_origin_image(client, monkeypatch):
    from server.routers import public

    public._HERO_CACHE.clear()

    async def fake_thumbnail_url(name):
        await asyncio.sleep(0)
        assert name == "북한산"
        return "https://upload.wikimedia.org/wikipedia/commons/example.jpg"

    async def fake_image(url):
        await asyncio.sleep(0)
        assert url == "https://upload.wikimedia.org/wikipedia/commons/example.jpg"
        return b"jpeg-bytes", "image/jpeg"

    monkeypatch.setattr(public, "_fetch_wikipedia_thumbnail_url", fake_thumbnail_url)
    monkeypatch.setattr(public, "_fetch_allowed_hero_image", fake_image)

    res = client.get("/api/v1/mountain-hero", params={"name": "북한산", "height": 836})

    assert res.status_code == 200
    assert res.headers["content-type"].startswith("image/jpeg")
    assert res.headers["cache-control"] == "public, max-age=86400"
    assert res.content == b"jpeg-bytes"


def test_mountain_hero_proxy_uses_svg_fallback_when_remote_fails(client, monkeypatch):
    from server.routers import public

    public._HERO_CACHE.clear()

    async def missing_thumbnail_url(name):
        await asyncio.sleep(0)
        return None

    monkeypatch.setattr(public, "_fetch_wikipedia_thumbnail_url", missing_thumbnail_url)

    res = client.get("/api/v1/mountain-hero", params={"name": "북한산", "height": 836})

    assert res.status_code == 200
    assert res.headers["content-type"].startswith("image/svg+xml")
    assert res.headers["cache-control"] == "public, max-age=3600"
    assert b"<svg" in res.content
    assert "upload.wikimedia.org" not in res.text


def test_mountain_hero_proxy_reuses_cached_payload(client):
    from server.routers import public

    public._HERO_CACHE.clear()
    public._HERO_CACHE["북한산:836"] = (b"cached-image", "image/webp")

    res = client.get("/api/v1/mountain-hero", params={"name": "북한산", "height": 836})

    assert res.status_code == 200
    assert res.headers["content-type"].startswith("image/webp")
    assert res.headers["cache-control"] == "public, max-age=86400"
    assert res.content == b"cached-image"


def test_mountain_hero_url_allowlist_rejects_non_wikimedia_hosts():
    from server.routers import public

    assert b"#2D6A4F" in public._fallback_hero_svg("한라산", 1950)
    assert public._is_allowed_hero_image_url(
        "https://upload.wikimedia.org/wikipedia/commons/example.jpg"
    )
    assert not public._is_allowed_hero_image_url("https://evil.example/example.jpg")
    assert not public._is_allowed_hero_image_url("http://upload.wikimedia.org/example.jpg")


def test_mountain_hero_cache_evicts_oldest_entry():
    from server.routers import public

    public._HERO_CACHE.clear()
    for idx in range(public._HERO_CACHE_MAX_ENTRIES):
        public._HERO_CACHE[f"old-{idx}"] = (b"old", "image/jpeg")

    public._cache_hero_image("new", b"new", "image/png")

    assert "old-0" not in public._HERO_CACHE
    assert public._HERO_CACHE["new"] == (b"new", "image/png")
    public._HERO_CACHE.clear()


def test_mountain_hero_fetches_allowed_wikipedia_thumbnail(monkeypatch):
    from server.routers import public

    image_url = "https://upload.wikimedia.org/wikipedia/commons/example.jpg"

    class FakeResponse:
        status_code = 200

        def json(self):
            return {"thumbnail": {"source": image_url}}

    class FakeClient:
        def __init__(self, *args, **kwargs):
            self.request_url = None

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        async def get(self, url, headers):
            await asyncio.sleep(0)
            self.request_url = url
            assert headers["accept"] == "application/json"
            assert headers["user-agent"].startswith("ForestMate/")
            return FakeResponse()

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_wikipedia_thumbnail_url("북한산 둘레길")) == image_url


def test_mountain_hero_thumbnail_fetch_rejects_invalid_source(monkeypatch):
    from server.routers import public

    class FakeResponse:
        status_code = 200

        def json(self):
            return {"thumbnail": {"source": "https://evil.example/image.jpg"}}

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        async def get(self, url, headers):
            await asyncio.sleep(0)
            return FakeResponse()

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_wikipedia_thumbnail_url("북한산")) is None


def test_mountain_hero_thumbnail_fetch_handles_non_success(monkeypatch):
    from server.routers import public

    class FakeResponse:
        status_code = 404

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        async def get(self, url, headers):
            await asyncio.sleep(0)
            return FakeResponse()

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_wikipedia_thumbnail_url("북한산")) is None


def test_mountain_hero_thumbnail_fetch_handles_http_errors(monkeypatch):
    from server.routers import public

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        async def get(self, url, headers):
            await asyncio.sleep(0)
            raise public.httpx.HTTPError("summary failed")

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_wikipedia_thumbnail_url("북한산")) is None


def test_mountain_hero_fetches_allowed_image_stream(monkeypatch):
    from server.routers import public

    image_url = "https://upload.wikimedia.org/wikipedia/commons/example.jpg"

    class FakeStreamResponse:
        status_code = 200
        url = image_url
        headers = {"content-type": "image/jpeg; charset=binary", "content-length": "10"}

        async def aiter_bytes(self):
            yield b"jpeg-"
            yield b"bytes"

    class FakeStream:
        async def __aenter__(self):
            await asyncio.sleep(0)
            return FakeStreamResponse()

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        def stream(self, method, url, headers):
            assert method == "GET"
            assert url == image_url
            assert "image/webp" in headers["accept"]
            return FakeStream()

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_allowed_hero_image(image_url)) == (b"jpeg-bytes", "image/jpeg")


@pytest.mark.parametrize(
    ("status_code", "headers"),
    [
        (404, {"content-type": "image/jpeg"}),
        (200, {"content-type": "text/html"}),
        (200, {"content-type": "image/png", "content-length": str(2_000_001)}),
    ],
)
def test_mountain_hero_image_fetch_rejects_bad_responses(monkeypatch, status_code, headers):
    from server.routers import public

    image_url = "https://upload.wikimedia.org/wikipedia/commons/example.jpg"

    class FakeStreamResponse:
        url = image_url

        def __init__(self):
            self.status_code = status_code
            self.headers = headers

        async def aiter_bytes(self):
            yield b"ignored"

    class FakeStream:
        async def __aenter__(self):
            await asyncio.sleep(0)
            return FakeStreamResponse()

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        def stream(self, method, url, headers):
            return FakeStream()

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_allowed_hero_image(image_url)) is None


def test_mountain_hero_image_fetch_rejects_invalid_urls_before_client(monkeypatch):
    from server.routers import public

    def fail_async_client(*args, **kwargs):
        raise AssertionError("invalid URLs must not open an HTTP client")

    monkeypatch.setattr(public.httpx, "AsyncClient", fail_async_client)

    assert asyncio.run(public._fetch_allowed_hero_image("https://evil.example/image.jpg")) is None


def test_mountain_hero_image_fetch_rejects_oversized_stream(monkeypatch):
    from server.routers import public

    image_url = "https://upload.wikimedia.org/wikipedia/commons/example.jpg"

    class FakeStreamResponse:
        status_code = 200
        url = image_url
        headers = {"content-type": "image/png"}

        async def aiter_bytes(self):
            yield b"x" * (public._HERO_MAX_BYTES + 1)

    class FakeStream:
        async def __aenter__(self):
            await asyncio.sleep(0)
            return FakeStreamResponse()

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        async def __aenter__(self):
            await asyncio.sleep(0)
            return self

        async def __aexit__(self, *exc_info):
            await asyncio.sleep(0)
            return None

        def stream(self, method, url, headers):
            return FakeStream()

    monkeypatch.setattr(public.httpx, "AsyncClient", FakeClient)

    assert asyncio.run(public._fetch_allowed_hero_image(image_url)) is None


def test_api_docs_avoid_external_swagger_assets(client):
    res = client.get("/docs")
    assert res.status_code == 200
    assert 'href="/openapi.json"' in res.text
    assert "swagger-ui-dist" not in res.text
    assert "cdn.jsdelivr.net" not in res.text
    assert "<script" not in res.text
    assert "<style" not in res.text
    assert client.head("/docs").status_code == 200


def test_inline_csp_hash_collector_handles_inline_html():
    from server import main

    collector = main._InlineCspHashCollector()
    collector.feed(
        '<style>.flag::before{content:"&amp;";}</style>'
        '<script>const marker = "&amp;&#35;";</script>'
        '<script src="/app.js"></script>'
        '<p style="color:red"></p>'
    )

    assert main._csp_hash('.flag::before{content:"&amp;";}') in collector.style_hashes
    assert main._csp_hash('const marker = "&amp;&#35;";') in collector.script_hashes
    assert main._csp_hash("color:red") in collector.style_attr_hashes
    assert len(collector.script_hashes) == 1

    entity_collector = main._InlineCspHashCollector()
    entity_collector._start_collecting("script")
    entity_collector.handle_entityref("amp")
    entity_collector.handle_charref("35")
    entity_collector.handle_endtag("script")
    assert main._csp_hash("&amp;&#35;") in entity_collector.script_hashes


def test_inline_csp_hashes_tolerate_missing_and_unreadable_app_dir(tmp_path, monkeypatch):
    from pathlib import Path

    from server import main

    main._inline_csp_hashes.cache_clear()
    monkeypatch.setattr(main, "APP_DIR", tmp_path / "missing")
    assert main._inline_csp_hashes() == ((), (), ())
    assert "style-src-attr 'none'" in main._content_security_policy()

    app_dir = tmp_path / "app"
    app_dir.mkdir()
    (app_dir / "ok.html").write_text('<style>.ok{color:green}</style>', encoding="utf-8")
    (app_dir / "broken.html").write_text("<script>ignored()</script>", encoding="utf-8")
    original_read_text = Path.read_text

    def fake_read_text(self, *args, **kwargs):
        if self.name == "broken.html":
            raise OSError("cannot read test file")
        return original_read_text(self, *args, **kwargs)

    main._inline_csp_hashes.cache_clear()
    monkeypatch.setattr(main, "APP_DIR", app_dir)
    monkeypatch.setattr(Path, "read_text", fake_read_text)
    script_hashes, style_hashes, style_attr_hashes = main._inline_csp_hashes()

    assert script_hashes == ()
    assert style_hashes == (main._csp_hash(".ok{color:green}"),)
    assert style_attr_hashes == ()
    main._inline_csp_hashes.cache_clear()


@pytest.mark.parametrize(("region", "status", "score"), [
    ("eunpyeong", 200, 82),
    ("nowhere", 404, None),
])
def test_index_endpoint(client, region, status, score):
    res = client.get("/api/v1/index", params={"region": region})
    assert res.status_code == status
    if score is not None:
        body = res.json()
        assert body["score"] == score
        assert body["conditions"]["weather"]["source"] == "snapshot"


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


@pytest.mark.parametrize(("sample_id", "status", "name"), [
    ("mushroom", 200, "개나리광대버섯"),
    ("NOPE", 404, None),
])
def test_species_identify(client, sample_id, status, name):
    res = client.post("/api/v1/species/identify", json={"sample_id": sample_id})
    assert res.status_code == status
    if name is not None:
        body = res.json()
        assert body["toxic"] is True
        assert name in body["name"]


def test_mountains_search_empty(client):
    # 키 없는 테스트 환경 — 카탈로그 비어있어도 검색 API는 정상 스키마 반환
    res = client.get("/api/v1/mountains?q=절대없는산")
    assert res.status_code == 200
    body = res.json()
    assert body["total"] == 0 and body["items"] == []
    assert client.get("/api/v1/mountains/sido").status_code == 200


def test_public_catalog_routes(client):
    assert len(client.get("/api/v1/regions").json()) >= 3
    assert client.get("/api/v1/courses").json()[0]["id"]
    assert client.get("/api/v1/courses/bukhansan").json()["risks"]
    assert client.get("/api/v1/courses/NOPE").status_code == 404


def test_well_known_routes(client, tmp_path, monkeypatch):
    from server import main
    well_known = tmp_path / ".well-known"
    well_known.mkdir()
    (well_known / "assetlinks.json").write_text('[{"ok": true}]', encoding="utf-8")
    (well_known / "apple-app-site-association").write_text('{"applinks": {}}', encoding="utf-8")
    monkeypatch.setattr(main, "APP_DIR", tmp_path)
    assert client.get("/.well-known/assetlinks.json").json() == [{"ok": True}]
    assert client.get("/.well-known/apple-app-site-association").json() == {"applinks": {}}


def test_mountains_filters_forecast_and_trails(client, seed_mountain, tmp_path, monkeypatch):
    seed_mountain(list_no="SEOUL1", name="북한산", sido="서울특별시", addr="서울특별시 은평구",
                  is_top100=True, lat=37.65, lon=126.98, sgg="11140")
    seed_mountain(list_no="JEJU1", name="한라산", sido="제주특별자치도", addr="제주시",
                  lat=33.36, lon=126.53, sgg="50110")

    res = client.get("/api/v1/mountains", params={"q": "산", "sido": "서울", "size": 999})
    body = res.json()
    assert body["size"] == 100
    assert body["total"] == 1 and body["items"][0]["name"] == "북한산"
    assert client.get("/api/v1/mountains/sido").json()[0]["count"] >= 1

    forecast = client.get("/api/v1/forecast", params={"lat": 37.66, "lon": 126.99}).json()
    assert len(forecast["days"]) == 3
    assert {"date", "label", "dow", "fire", "score"} <= set(forecast["days"][0])

    from server.routers import public
    (tmp_path / "SEOUL1.json").write_text('{"name": "북한산", "segs": [[[1, 2], [3, 4]]]}',
                                          encoding="utf-8")
    monkeypatch.setattr(public, "_TRAILS_DIR", tmp_path)
    assert client.get("/api/v1/mountains/SEOUL1/trails").json()["segs"]
    assert client.get("/api/v1/mountains/MISSING/trails").json() == {"name": "", "segs": []}


def test_mountain_index_resolves_region(client, seed_mountain):
    # 산 1개 시드 후, 선택한 산의 산행지수가 시도 대표지점으로 계산되는지
    seed_mountain(list_no="GEO1", name="지리산", sido="경상남도",
                  addr="경상남도 산청군 시천면", height=1915, is_top100=True)

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


def test_gps_nearby_and_precise_index(client, seed_mountain):
    # 실측 좌표 보유 산 → GPS 주변검색·현재위치지수·정밀 산행지수
    seed_mountain(list_no="GPS1", name="청계산", sido="경기도",
                  addr="경기도 과천시 막계동", height=618,
                  lat=37.4449, lon=127.0539, sgg="41290")

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
    assert pi["mountain"]["lat"] == pytest.approx(37.4449)


def test_full_hike_flow(client, register_device):
    # 1) 기기 등록(익명)
    auth, _ = register_device(knee=True)

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
    # 배지 — 실집계 기반(첫 산행 달성, 10회 미달성, 진척 포함)
    assert rep["distinct_courses"] >= 1 and rep["regions"] >= 1
    badges = {b["id"]: b for b in rep["badges"]}
    assert badges["first"]["earned"] is True       # 1회 완주 → 첫 산행 달성
    assert badges["ten"]["earned"] is False        # 10회 미달성
    assert badges["ten"]["progress"] == 1 and badges["ten"]["goal"] == 10

    # 산행 기록 목록 — 산별 거리·칼로리
    log = client.get("/api/v1/hikes", headers=auth).json()
    assert len(log["items"]) == 1
    assert log["items"][0]["km"] > 0 and log["items"][0]["kcal"] > 0 and log["items"][0]["course"]


def test_watch_pairing_and_sensor_ingest(client, register_device):
    auth, _ = register_device(name="워치러")
    hike = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth).json()
    hike_id = hike["hike_id"]

    pair = client.post("/api/v1/watch/pair/start", json={"hike_id": hike_id}, headers=auth)
    assert pair.status_code == 201
    code = pair.json()["code"]
    assert len(code) == 6 and code.isdigit()

    claimed = client.post("/api/v1/watch/pair/claim", json={"code": code})
    assert claimed.status_code == 200
    watch_token = claimed.json()["watch_token"]
    assert claimed.json()["hike_id"] == hike_id
    assert claimed.json()["course_id"] == "bukhansan"
    assert claimed.json()["course_name"] == "북한산 백운대 코스"
    assert claimed.json()["course_km"] > 0
    assert claimed.json()["course_elev"] == 836
    assert claimed.json()["route"]

    latest = client.get("/api/v1/watch/latest", params={"hike_id": hike_id}, headers=auth).json()
    assert latest == {"connected": False, "hike_id": hike_id}

    watch_auth = {"Authorization": f"Bearer {watch_token}"}
    tracked = client.post("/api/v1/watch/track",
                          json={"hr": 151, "lat": 37.6584, "lon": 126.9778, "acc": 8, "battery": 74},
                          headers=watch_auth)
    assert tracked.status_code == 200
    assert tracked.json()["progress"] == pytest.approx(0.0)

    latest = client.get("/api/v1/watch/latest", params={"hike_id": hike_id}, headers=auth).json()
    assert latest["connected"] is True
    assert latest["hr"] == 151
    assert latest["lat"] == pytest.approx(37.6584)
    assert latest["lon"] == pytest.approx(126.9778)
    assert latest["battery"] == 74
    assert latest["age_sec"] >= 0


def test_watch_pairing_can_connect_before_hike_and_attach_later(client, register_device):
    auth, _ = register_device(name="상시워치")

    pair = client.post("/api/v1/watch/pair/start", json={}, headers=auth)
    assert pair.status_code == 201
    code = pair.json()["code"]
    assert pair.json()["hike_id"] is None

    claimed = client.post("/api/v1/watch/pair/claim", json={"code": code})
    assert claimed.status_code == 200
    assert claimed.json()["hike_id"] is None
    assert claimed.json()["course_id"] is None
    watch_auth = {"Authorization": f"Bearer {claimed.json()['watch_token']}"}

    pre_hike = client.post("/api/v1/watch/track",
                           json={"hr": 118, "lat": 37.5, "lon": 127.0, "acc": 7, "battery": 81},
                           headers=watch_auth)
    assert pre_hike.status_code == 200
    assert pre_hike.json()["progress"] == pytest.approx(0.0)

    latest = client.get("/api/v1/watch/latest", headers=auth).json()
    assert latest["connected"] is True
    assert latest["hike_id"] is None
    assert latest["hr"] == 118

    hike_id = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth).json()["hike_id"]
    tracked = client.post("/api/v1/watch/track",
                          json={"progress": 0.12, "hr": 126, "lat": 37.6584, "lon": 126.9778},
                          headers=watch_auth)
    assert tracked.status_code == 200
    assert tracked.json()["progress"] == pytest.approx(0.12)

    latest = client.get("/api/v1/watch/latest", params={"hike_id": hike_id}, headers=auth).json()
    assert latest["connected"] is True
    assert latest["hike_id"] == hike_id
    assert latest["hr"] == 126


def test_watch_pairing_rejects_invalid_or_foreign_sessions(client, register_device):
    auth_a, _ = register_device(name="A")
    hike_id = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth_a).json()["hike_id"]
    auth_b, _ = register_device(name="B")

    assert client.post("/api/v1/watch/pair/start", json={"hike_id": hike_id}, headers=auth_b).status_code == 404
    assert client.post("/api/v1/watch/pair/claim", json={"code": "000000"}).status_code == 404
    assert client.post("/api/v1/watch/track", json={"hr": 90}).status_code == 401
    assert client.post("/api/v1/watch/track", json={"hr": 90},
                       headers={"Authorization": "Bearer nope"}).status_code == 401


def test_watch_pair_claim_rate_limits_repeated_guesses(client):
    from server.routers import watch as watch_router

    watch_router._pair_claim_attempts.clear()
    try:
        headers = {"X-Forwarded-For": "203.0.113.17"}
        for _ in range(10):
            assert client.post("/api/v1/watch/pair/claim", json={"code": "999999"},
                               headers=headers).status_code == 404
        assert client.post("/api/v1/watch/pair/claim", json={"code": "999999"},
                           headers=headers).status_code == 429
    finally:
        watch_router._pair_claim_attempts.clear()


def test_watch_pairing_rejects_inactive_hikes(client, register_device):
    auth, _ = register_device(name="종료산행")
    hike_id = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth).json()["hike_id"]
    assert client.post(f"/api/v1/hikes/{hike_id}/end", headers=auth).status_code == 200
    assert client.post("/api/v1/watch/pair/start", json={"hike_id": hike_id},
                       headers=auth).status_code == 409

    active_hike = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth).json()["hike_id"]
    pair = client.post("/api/v1/watch/pair/start", json={"hike_id": active_hike}, headers=auth).json()
    assert client.post(f"/api/v1/hikes/{active_hike}/end", headers=auth).status_code == 200
    assert client.post("/api/v1/watch/pair/claim", json={"code": pair["code"]}).status_code == 409


def test_watch_pair_code_exhaustion_is_reported(register_device, monkeypatch):
    from fastapi import HTTPException

    from server.db import SessionLocal
    from server.models import WatchPair, utcnow
    from server.routers import watch as watch_router

    _, legacy = register_device(name="코드충돌")
    db = SessionLocal()
    try:
        monkeypatch.setattr(watch_router.secrets, "randbelow", lambda limit: 0)
        db.add(WatchPair(code="000000", device_id=legacy["device_id"],
                         expires_at=utcnow() + timedelta(minutes=10)))
        db.flush()
        with pytest.raises(HTTPException) as unavailable:
            watch_router._new_code(db)
        assert unavailable.value.status_code == 503
    finally:
        db.rollback()
        db.close()


def test_watch_latest_can_fallback_to_unattached_session(client, register_device):
    auth, _ = register_device(name="사전페어링")
    pair = client.post("/api/v1/watch/pair/start", json={}, headers=auth).json()
    assert client.post("/api/v1/watch/pair/claim", json={"code": pair["code"]}).status_code == 200

    hike_id = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth).json()["hike_id"]
    latest = client.get("/api/v1/watch/latest", params={"hike_id": hike_id}, headers=auth).json()
    assert latest == {"connected": False, "hike_id": hike_id}


def test_watch_track_publishes_distress_alert_for_stalled_hr_anomaly(client, register_device):
    from server.db import SessionLocal
    from server.models import AlertEvent, TrackPoint, utcnow

    auth, _ = register_device(name="조난워치")
    hike_id = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth).json()["hike_id"]
    code = client.post("/api/v1/watch/pair/start", json={"hike_id": hike_id}, headers=auth).json()["code"]
    watch_token = client.post("/api/v1/watch/pair/claim", json={"code": code}).json()["watch_token"]

    old = utcnow() - timedelta(minutes=31)
    db = SessionLocal()
    try:
        db.add_all([
            TrackPoint(hike_id=hike_id, progress=0.25, hr=142, created_at=old),
            TrackPoint(hike_id=hike_id, progress=0.25, hr=143, created_at=old + timedelta(minutes=1)),
        ])
        db.commit()
    finally:
        db.close()

    tracked = client.post("/api/v1/watch/track", json={"progress": 0.25, "hr": 151},
                          headers={"Authorization": f"Bearer {watch_token}"})
    assert tracked.status_code == 200
    assert tracked.json()["distress"]["level"] == 2

    db = SessionLocal()
    try:
        alert = db.query(AlertEvent).filter(AlertEvent.kind == "distress").one()
        assert "워치 조난위험" in alert.title
    finally:
        db.close()


def test_push_subscription_flow(client, register_device):
    auth, _ = register_device(name="Push")
    assert client.get("/api/v1/push/vapid").json() == {"enabled": False, "publicKey": ""}
    assert client.post("/api/v1/push/subscribe", json={}, headers=auth).json() == {
        "ok": False, "reason": "endpoint 없음",
    }
    sub = {"endpoint": "https://push.example/sub", "keys": {"p256dh": "p256", "auth": "auth"}}
    assert client.post("/api/v1/push/subscribe", json=sub, headers=auth).json() == {"ok": True}
    assert client.post("/api/v1/push/test", headers=auth).json() == {
        "sent": 0, "subs": 1, "enabled": False,
    }


def test_dashboard_websocket_receives_sos(client, register_device):
    auth, _ = register_device(name="WS")
    with client.websocket_connect("/api/v1/ws/dashboard") as ws:
        client.post("/api/v1/sos", json={"note": "테스트"}, headers=auth)
        msg = ws.receive_json()
        assert msg["type"] == "sos"
        assert "SOS" in msg["title"]
