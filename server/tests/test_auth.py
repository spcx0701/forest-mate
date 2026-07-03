import asyncio
import json
from datetime import timedelta
from urllib.parse import parse_qs, urlparse

import pytest


def _bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


PRIMARY_AUTH_VALUE = " ".join(("correct", "horse", "battery", "staple"))
SECONDARY_AUTH_VALUE = "-".join(("very", "secret", "test", "phrase"))
GHOST_AUTH_VALUE = "-".join(("ghost", "test", "phrase"))
WRONG_AUTH_VALUE = "invalid"


def test_email_account_register_login_profile_and_logout(client, register_device):
    _, legacy = register_device(name="게스트", fit=1, knee=True)

    res = client.post("/api/v1/auth/register", json={
        "email": "hiker@example.com",
        "password": PRIMARY_AUTH_VALUE,
        "name": "산계정",
        "fit": 3,
        "knee": False,
        "heart": True,
        "device_token": legacy["token"],
    })
    assert res.status_code == 201
    body = res.json()
    assert body["token_type"] == "Bearer"
    assert body["access_token"]
    assert body["device_token"] == legacy["token"]
    assert body["user"]["email"] == "hiker@example.com"
    assert body["user"]["profile"]["name"] == "산계정"
    assert body["user"]["profile"]["fit"] == 3
    assert body["user"]["profile"]["heart"] is True

    account_auth = _bearer(body["access_token"])
    me = client.get("/api/v1/auth/me", headers=account_auth)
    assert me.status_code == 200
    assert me.json()["user"]["email"] == "hiker@example.com"

    patched = client.patch("/api/v1/auth/me/profile", json={
        "name": "능선러",
        "fit": 2,
        "knee": True,
        "heart": False,
    }, headers=account_auth)
    assert patched.status_code == 200
    assert patched.json()["user"]["profile"] == {
        "name": "능선러",
        "fit": 2,
        "knee": True,
        "heart": False,
    }

    duplicate = client.post("/api/v1/auth/register", json={
        "email": "hiker@example.com",
        "password": PRIMARY_AUTH_VALUE,
        "name": "다른이름",
    })
    assert duplicate.status_code == 409

    logout = client.post("/api/v1/auth/logout", headers=account_auth)
    assert logout.status_code == 200
    assert client.get("/api/v1/auth/me", headers=account_auth).status_code == 401

    login = client.post("/api/v1/auth/login", json={
        "email": "hiker@example.com",
        "password": PRIMARY_AUTH_VALUE,
        "device_token": legacy["token"],
    })
    assert login.status_code == 200
    assert login.json()["access_token"]
    assert login.json()["user"]["profile"]["name"] == "능선러"


def test_account_session_can_drive_hike_records(client, register_device):
    _, legacy = register_device(name="기록러")
    reg = client.post("/api/v1/auth/register", json={
        "email": "trail@example.com",
        "password": SECONDARY_AUTH_VALUE,
        "name": "기록러",
        "device_token": legacy["token"],
    }).json()
    auth = _bearer(reg["access_token"])

    started = client.post("/api/v1/hikes", json={"course_id": "bukhansan"}, headers=auth)
    assert started.status_code == 201
    hike_id = started.json()["hike_id"]
    client.post(f"/api/v1/hikes/{hike_id}/track",
                json={"progress": 0.7, "alt": 620, "hr": 103}, headers=auth)
    ended = client.post(f"/api/v1/hikes/{hike_id}/end", headers=auth)
    assert ended.status_code == 200

    summary = client.get("/api/v1/hikes/summary", headers=auth)
    assert summary.status_code == 200
    assert summary.json()["total_hikes"] == 1

    rows = client.get("/api/v1/hikes", headers=auth)
    assert rows.status_code == 200
    assert len(rows.json()["items"]) == 1


def test_oauth_start_and_callback_create_account_session(client, register_device, monkeypatch):
    _, legacy = register_device(name="소셜러")
    monkeypatch.setenv("GOOGLE_CLIENT_ID", "google-client-id")
    monkeypatch.setenv("GOOGLE_CLIENT_SECRET", "google-client-secret")
    monkeypatch.setenv("PUBLIC_BASE_URL", "https://forestmate.example")

    from server.config import get_settings
    get_settings.cache_clear()

    start = client.get("/api/v1/auth/oauth/google/start", params={
        "device_token": legacy["token"],
        "name": "소셜러",
        "fit": 2,
        "knee": "false",
        "heart": "true",
    }, follow_redirects=False)
    assert start.status_code in (302, 307)
    loc = start.headers["location"]
    parsed = urlparse(loc)
    assert parsed.netloc == "accounts.google.com"
    state = parse_qs(parsed.query)["state"][0]

    from server.routers import auth as auth_router

    async def fake_fetch_oauth_profile(provider, code, redirect_uri):
        await asyncio.sleep(0)
        assert provider == "google"
        assert code == "provider-code"
        assert redirect_uri.endswith("/api/v1/auth/oauth/google/callback")
        return {
            "provider_user_id": "google-123",
            "email": "social@example.com",
            "email_verified": True,
            "name": "구글산친구",
            "avatar_url": "https://example.com/avatar.png",
        }

    monkeypatch.setattr(auth_router, "fetch_oauth_profile", fake_fetch_oauth_profile)
    callback = client.get("/api/v1/auth/oauth/google/callback", params={
        "code": "provider-code",
        "state": state,
    }, follow_redirects=False)
    assert callback.status_code in (302, 307)
    redirect = callback.headers["location"]
    assert redirect.startswith("/index.html#auth_token=")

    token = redirect.split("auth_token=", 1)[1].split("&", 1)[0]
    me = client.get("/api/v1/auth/me", headers=_bearer(token))
    assert me.status_code == 200
    body = me.json()
    assert body["user"]["email"] == "social@example.com"
    assert body["user"]["profile"]["name"] == "소셜러"
    assert body["user"]["profile"]["heart"] is True


def test_oauth_redirect_path_is_allow_listed(client, register_device, monkeypatch):
    monkeypatch.setenv("GOOGLE_CLIENT_ID", "google-client-id")
    monkeypatch.setenv("GOOGLE_CLIENT_SECRET", "google-client-secret")
    monkeypatch.setenv("PUBLIC_BASE_URL", "https://forestmate.example")

    from server.config import get_settings
    from server.routers import auth as auth_router

    get_settings.cache_clear()
    issued = 0

    async def fake_fetch_oauth_profile(provider, code, redirect_uri):
        nonlocal issued
        issued += 1
        await asyncio.sleep(0)
        return {
            "provider_user_id": f"google-redirect-{issued}",
            "email": f"redirect-{issued}@example.com",
            "email_verified": True,
            "name": "리다이렉트",
            "avatar_url": "",
        }

    monkeypatch.setattr(auth_router, "fetch_oauth_profile", fake_fetch_oauth_profile)

    def roundtrip(redirect_path: str) -> str:
        _, legacy = register_device(name="소셜러")
        start = client.get("/api/v1/auth/oauth/google/start", params={
            "device_token": legacy["token"],
            "redirect_path": redirect_path,
        }, follow_redirects=False)
        assert start.status_code in (302, 307)
        state = parse_qs(urlparse(start.headers["location"]).query)["state"][0]
        callback = client.get("/api/v1/auth/oauth/google/callback", params={
            "code": "provider-code",
            "state": state,
        }, follow_redirects=False)
        assert callback.status_code in (302, 307)
        return callback.headers["location"]

    assert roundtrip("https://evil.example/callback").startswith("/index.html#auth_token=")
    assert roundtrip("//evil.example/callback").startswith("/index.html#auth_token=")
    assert roundtrip("/admin").startswith("/index.html#auth_token=")
    assert roundtrip("/home.html").startswith("/home.html#auth_token=")


def test_oauth_redirect_helpers_cover_provider_and_error_edges():
    from fastapi import HTTPException

    from server.routers import auth as auth_router

    assert auth_router._safe_oauth_redirect_path(None) == "/index.html"
    assert auth_router._safe_oauth_redirect_path("/index.html") == "/index.html"
    assert auth_router._safe_oauth_redirect_path("/home.html") == "/home.html"
    assert auth_router._safe_oauth_redirect_path("/admin") == "/index.html"

    assert auth_router._oauth_error("access_denied").headers["location"] == "/index.html#auth_error=access_denied"
    invalid = auth_router._oauth_error("invalid_oauth_callback")
    assert invalid.headers["location"] == "/index.html#auth_error=invalid_oauth_callback"
    assert auth_router._oauth_error("unexpected").headers["location"] == "/index.html#auth_error=oauth_failed"

    assert auth_router._provider_config("google")["authorize_url"] == (
        "https://accounts.google.com/o/oauth2/v2/auth"
    )
    assert auth_router._provider_config("kakao")["authorize_url"] == "https://kauth.kakao.com/oauth/authorize"
    assert auth_router._provider_config("naver")["authorize_url"] == "https://nid.naver.com/oauth2.0/authorize"
    with pytest.raises(HTTPException) as unknown_config:
        auth_router._provider_config("github")
    assert unknown_config.value.status_code == 404

    redirect_uri = "https://forestmate.example/api/v1/auth/oauth/google/callback"
    google = auth_router._oauth_authorization_redirect("google", "google-client", redirect_uri, "abc")
    google_url = urlparse(google.headers["location"])
    assert google_url.scheme == "https"
    assert google_url.netloc == "accounts.google.com"
    assert google_url.path == "/o/oauth2/v2/auth"
    assert parse_qs(google_url.query) == {
        "response_type": ["code"],
        "client_id": ["google-client"],
        "redirect_uri": [redirect_uri],
        "state": ["abc"],
        "scope": ["openid email profile"],
    }
    kakao = auth_router._oauth_authorization_redirect("kakao", "kakao-client", redirect_uri, "abc")
    kakao_url = urlparse(kakao.headers["location"])
    assert kakao_url.scheme == "https"
    assert kakao_url.netloc == "kauth.kakao.com"
    assert kakao_url.path == "/oauth/authorize"
    assert parse_qs(kakao_url.query) == {
        "response_type": ["code"],
        "client_id": ["kakao-client"],
        "redirect_uri": [redirect_uri],
        "state": ["abc"],
        "scope": ["profile_nickname account_email"],
    }
    naver = auth_router._oauth_authorization_redirect("naver", "naver-client", redirect_uri, "abc")
    naver_url = urlparse(naver.headers["location"])
    assert naver_url.scheme == "https"
    assert naver_url.netloc == "nid.naver.com"
    assert naver_url.path == "/oauth2.0/authorize"
    assert parse_qs(naver_url.query) == {
        "response_type": ["code"],
        "client_id": ["naver-client"],
        "redirect_uri": [redirect_uri],
        "state": ["abc"],
    }
    with pytest.raises(HTTPException) as unknown_redirect:
        auth_router._oauth_authorization_redirect("github", "client", redirect_uri, "abc")
    assert unknown_redirect.value.status_code == 404

    home = auth_router._oauth_success_redirect("/home.html", "token value")
    assert home.headers["location"] == "/home.html#auth_token=token+value"
    fallback = auth_router._oauth_success_redirect("/admin", "token")
    assert fallback.headers["location"] == "/index.html#auth_token=token"


def test_auth_helpers_cover_invalid_and_guest_edges(register_device):
    from fastapi import HTTPException
    from sqlalchemy import select

    from server import auth
    from server.db import SessionLocal
    from server.models import AuthSession, Device, User, UserDevice, utcnow

    _, legacy = register_device(name="연결기기")
    db = SessionLocal()
    try:
        with pytest.raises(HTTPException) as bad_email:
            auth.validate_email("@missing-local")
        assert bad_email.value.status_code == 422
        with pytest.raises(HTTPException) as loose_email:
            auth.validate_email("a@b")
        assert loose_email.value.status_code == 422
        with pytest.raises(HTTPException) as missing_at:
            auth.validate_email("missing-at.example.com")
        assert missing_at.value.status_code == 422
        assert auth.verify_password("pw", "legacy$1$salt$digest") is False
        assert auth.verify_password("pw", "not-a-passphrase-hash") is False
        with pytest.raises(HTTPException) as blank_bearer:
            auth.bearer_token("Bearer    ")
        assert blank_bearer.value.status_code == 401

        user = User(email="owner@example.com", name="소유자")
        other = User(email="other@example.com", name="타인")
        db.add_all([user, other])
        db.flush()
        linked = db.scalar(select(Device).where(Device.token == legacy["token"]))
        db.add(UserDevice(user_id=user.id, device_id=linked.id))
        db.flush()
        assert auth.link_device_token_to_user(db, user, None) is None
        assert auth.link_device_token_to_user(db, user, "missing-token") is None
        with pytest.raises(HTTPException) as conflict:
            auth.link_device_to_user(db, other, linked)
        assert conflict.value.status_code == 409

        fresh = User(email="fresh@example.com", name="새계정", fit=3, knee=True, heart=True)
        db.add(fresh)
        db.flush()
        created = auth.ensure_user_device(db, fresh)
        db.flush()
        assert created.name == "새계정"
        assert created.fit == 3
        assert db.get(UserDevice, {"user_id": fresh.id, "device_id": created.id})

        raw = "orphan-session-token"
        db.add(AuthSession(user_id="missing-user", token_hash=auth.token_hash(raw),
                           expires_at=utcnow() + timedelta(days=1)))
        db.flush()
        with pytest.raises(HTTPException) as orphan:
            auth.context_from_authorization(db, f"Bearer {raw}")
        assert orphan.value.status_code == 401

        guest_ctx = auth.AuthContext(user=None, device=created, token="guest")
        with pytest.raises(HTTPException) as guest:
            auth.get_current_user(guest_ctx)
        assert guest.value.status_code == 401
        account_session = AuthSession(user_id=fresh.id, token_hash=auth.token_hash("account"),
                                      expires_at=utcnow() + timedelta(days=1))
        assert auth.get_current_user(auth.AuthContext(
            user=fresh, device=created, token="account", account_session=account_session,
        )) is fresh
    finally:
        db.rollback()
        db.close()


def test_auth_routes_reject_guest_and_invalid_login(client, register_device):
    auth_header, _ = register_device(name="게스트")

    assert client.get("/api/v1/auth/me", headers=auth_header).status_code == 401
    assert client.patch("/api/v1/auth/me/profile", json={"name": "게스트", "fit": 2},
                        headers=auth_header).status_code == 401
    assert client.post("/api/v1/auth/logout", headers=auth_header).status_code == 401

    registered = client.post("/api/v1/auth/register", json={
        "email": "new@example.com",
        "password": PRIMARY_AUTH_VALUE,
        "name": "새계정",
    })
    assert registered.status_code == 201
    assert registered.json()["device_token"]

    bad = client.post("/api/v1/auth/login", json={"email": "new@example.com", "password": WRONG_AUTH_VALUE})
    assert bad.status_code == 401

    from server.auth import hash_password
    from server.db import SessionLocal
    from server.models import AuthIdentity

    db = SessionLocal()
    try:
        db.add(AuthIdentity(user_id="missing-user", provider="password",
                            provider_user_id="ghost@example.com",
                            email="ghost@example.com",
                            credential_hash=hash_password(GHOST_AUTH_VALUE)))
        db.commit()
    finally:
        db.close()
    ghost = client.post("/api/v1/auth/login", json={
        "email": "ghost@example.com",
        "password": GHOST_AUTH_VALUE,
    })
    assert ghost.status_code == 401


def test_auth_provider_configuration_and_oauth_error_routes(client, monkeypatch):
    monkeypatch.setenv("KAKAO_CLIENT_ID", "kakao-rest-api-key")
    monkeypatch.delenv("KAKAO_CLIENT_SECRET", raising=False)
    monkeypatch.delenv("GOOGLE_CLIENT_ID", raising=False)
    monkeypatch.delenv("GOOGLE_CLIENT_SECRET", raising=False)

    from server.config import get_settings
    from server.routers import auth as auth_router

    get_settings.cache_clear()
    providers = client.get("/api/v1/auth/providers").json()
    assert providers["oauth"]["kakao"] is True
    assert providers["oauth"]["google"] is False

    with pytest.raises(ValueError) as unknown:
        auth_router._provider_credentials("unknown")
    assert "unknown oauth provider" in str(unknown.value)
    assert client.get("/api/v1/auth/oauth/unknown/start").status_code == 404
    assert client.get("/api/v1/auth/oauth/google/start").status_code == 503

    err = client.get("/api/v1/auth/oauth/google/callback", params={"error": "access_denied"},
                     follow_redirects=False)
    assert err.status_code in (302, 307)
    assert err.headers["location"] == "/index.html#auth_error=access_denied"
    invalid = client.get("/api/v1/auth/oauth/google/callback", follow_redirects=False)
    assert invalid.headers["location"] == "/index.html#auth_error=invalid_oauth_callback"


def test_oauth_profile_fetch_maps_all_supported_providers(monkeypatch):
    monkeypatch.setenv("GOOGLE_CLIENT_ID", "google-id")
    monkeypatch.setenv("GOOGLE_CLIENT_SECRET", "google-secret")
    monkeypatch.setenv("KAKAO_CLIENT_ID", "kakao-id")
    monkeypatch.setenv("NAVER_CLIENT_ID", "naver-id")
    monkeypatch.setenv("NAVER_CLIENT_SECRET", "naver-secret")

    from server.config import get_settings
    from server.routers import auth as auth_router

    get_settings.cache_clear()

    class FakeResponse:
        def __init__(self, payload):
            self._payload = payload

        def json(self):
            return self._payload

        def raise_for_status(self):
            return None

    class FakeClient:
        def __init__(self, *args, **kwargs):
            self.provider = None

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def post(self, url, data, headers):
            await asyncio.sleep(0)
            self.provider = next(p for p, cfg in auth_router.OAUTH_PROVIDERS.items()
                                 if cfg["token_url"] == url)
            return FakeResponse({"access_token": f"{self.provider}-token"})

        async def get(self, url, headers):
            await asyncio.sleep(0)
            provider = self.provider
            payloads = {
                "google": {"sub": "g-1", "email": "GOOGLE@EXAMPLE.COM",
                           "email_verified": True,
                           "name": "Google Hiker", "picture": "https://example.com/g.png"},
                "kakao": {"id": 2, "kakao_account": {
                    "email": "KAKAO@EXAMPLE.COM",
                    "is_email_verified": True,
                    "profile": {"nickname": "카카오러", "thumbnail_image_url": "https://example.com/k.png"},
                }},
                "naver": {"response": {"id": "n-3", "email": "NAVER@EXAMPLE.COM",
                                        "nickname": "네이버러", "profile_image": "https://example.com/n.png"}},
            }
            return FakeResponse(payloads[provider])

    monkeypatch.setattr(auth_router.httpx, "AsyncClient", FakeClient)

    google = asyncio.run(auth_router.fetch_oauth_profile("google", "code", "https://cb"))
    kakao = asyncio.run(auth_router.fetch_oauth_profile("kakao", "code", "https://cb"))
    naver = asyncio.run(auth_router.fetch_oauth_profile("naver", "code", "https://cb"))

    assert google == {
        "provider_user_id": "g-1",
        "email": "google@example.com",
        "email_verified": True,
        "name": "Google Hiker",
        "avatar_url": "https://example.com/g.png",
    }
    assert kakao["provider_user_id"] == "2"
    assert kakao["email"] == "kakao@example.com"
    assert kakao["email_verified"] is True
    assert kakao["name"] == "카카오러"
    assert naver["provider_user_id"] == "n-3"
    assert naver["email"] == "naver@example.com"
    assert naver["email_verified"] is False
    assert naver["name"] == "네이버러"


def test_oauth_profile_fetch_rejects_missing_access_token(monkeypatch):
    monkeypatch.setenv("GOOGLE_CLIENT_ID", "google-id")
    monkeypatch.setenv("GOOGLE_CLIENT_SECRET", "google-secret")

    from fastapi import HTTPException

    from server.config import get_settings
    from server.routers import auth as auth_router

    get_settings.cache_clear()

    class FakeResponse:
        def json(self):
            return {}

        def raise_for_status(self):
            return None

    class FakeClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

        async def post(self, url, data, headers):
            await asyncio.sleep(0)
            return FakeResponse()

    monkeypatch.setattr(auth_router.httpx, "AsyncClient", lambda *args, **kwargs: FakeClient())
    with pytest.raises(HTTPException) as missing:
        asyncio.run(auth_router.fetch_oauth_profile("google", "code", "https://cb"))
    assert missing.value.status_code == 502


def test_oauth_state_and_social_account_edge_cases(client, monkeypatch):
    from fastapi import HTTPException

    from server.auth import token_hash
    from server.config import get_settings
    from server.db import SessionLocal
    from server.models import AuthIdentity, OAuthState, User, UserDevice, utcnow
    from server.routers import auth as auth_router

    monkeypatch.setenv("PUBLIC_BASE_URL", "https://forestmate.example")
    get_settings.cache_clear()

    db = SessionLocal()
    try:
        with pytest.raises(HTTPException) as invalid_state:
            auth_router._consume_oauth_state(db, "google", "missing-state")
        assert invalid_state.value.status_code == 401

        db.add(OAuthState(state_hash=token_hash("expired-state"), provider="google",
                          expires_at=utcnow() - timedelta(minutes=1)))
        db.flush()
        with pytest.raises(HTTPException) as expired_state:
            auth_router._consume_oauth_state(db, "google", "expired-state")
        assert expired_state.value.status_code == 401

        db.add(AuthIdentity(user_id="missing-user", provider="google",
                            provider_user_id="ghost", email="ghost@example.com"))
        db.flush()
        with pytest.raises(HTTPException) as missing_user:
            auth_router._social_user(db, "google", {"provider_user_id": "ghost"},
                                     {"name": "", "fit": 2, "knee": False, "heart": False})
        assert missing_user.value.status_code == 401
    finally:
        db.rollback()
        db.close()

    db = SessionLocal()
    try:
        existing = User(email="social-merge@example.com", name="기존")
        db.add(existing)
        db.flush()
        user = auth_router._social_user(
            db, "google",
            {"provider_user_id": "new-google-id", "email": "SOCIAL-MERGE@EXAMPLE.COM",
             "email_verified": True, "avatar_url": "https://example.com/avatar.png"},
            {"name": "요청프로필", "fit": 3, "knee": True, "heart": False},
        )
        assert user.id == existing.id
        assert user.name == "요청프로필"
        assert user.fit == 3
        assert user.avatar_url == "https://example.com/avatar.png"
        same_user = auth_router._social_user(db, "google", {"provider_user_id": "new-google-id"}, {})
        assert same_user.id == existing.id

        db.add(UserDevice(user_id=user.id, device_id="missing-device"))
        db.flush()
        token = auth_router._sync_linked_device(db, user)
        assert token
    finally:
        db.rollback()
        db.close()

    db = SessionLocal()
    try:
        existing = User(email="unverified@example.com", name="기존")
        db.add(existing)
        db.flush()
        user = auth_router._social_user(
            db, "kakao",
            {"provider_user_id": "kakao-unverified", "email": "UNVERIFIED@EXAMPLE.COM",
             "email_verified": False, "name": "검증안됨"},
            {},
        )
        assert user.id != existing.id
        assert user.email is None
        assert user.name == "검증안됨"
    finally:
        db.rollback()
        db.close()

    db = SessionLocal()
    try:
        db.add(OAuthState(state_hash=token_hash("runtime-error-state"), provider="google",
                          profile_json=json.dumps({"name": "소셜", "fit": 2}),
                          redirect_path="/index.html",
                          expires_at=utcnow() + timedelta(minutes=5)))
        db.commit()
    finally:
        db.close()

    async def boom(provider, code, redirect_uri):
        raise RuntimeError("provider unavailable")

    monkeypatch.setattr(auth_router, "fetch_oauth_profile", boom)
    callback = client.get("/api/v1/auth/oauth/google/callback", params={
        "code": "provider-code",
        "state": "runtime-error-state",
    }, follow_redirects=False)
    assert callback.status_code in (302, 307)
    assert callback.headers["location"] == "/index.html#auth_error=oauth_failed"

    db = SessionLocal()
    try:
        db.add(OAuthState(state_hash=token_hash("http-error-state"), provider="google",
                          profile_json=json.dumps({"name": "소셜", "fit": 2}),
                          redirect_path="/index.html",
                          expires_at=utcnow() + timedelta(minutes=5)))
        db.commit()
    finally:
        db.close()

    async def http_error(provider, code, redirect_uri):
        raise HTTPException(502, "provider rejected")

    monkeypatch.setattr(auth_router, "fetch_oauth_profile", http_error)
    rejected = client.get("/api/v1/auth/oauth/google/callback", params={
        "code": "provider-code",
        "state": "http-error-state",
    }, follow_redirects=False)
    assert rejected.status_code == 502
