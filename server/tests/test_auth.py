from urllib.parse import parse_qs, urlparse


def _bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_email_account_register_login_profile_and_logout(client, register_device):
    _, legacy = register_device(name="게스트", fit=1, knee=True)

    res = client.post("/api/v1/auth/register", json={
        "email": "hiker@example.com",
        "password": "correct horse battery staple",
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
        "password": "correct horse battery staple",
        "name": "다른이름",
    })
    assert duplicate.status_code == 409

    logout = client.post("/api/v1/auth/logout", headers=account_auth)
    assert logout.status_code == 200
    assert client.get("/api/v1/auth/me", headers=account_auth).status_code == 401

    login = client.post("/api/v1/auth/login", json={
        "email": "hiker@example.com",
        "password": "correct horse battery staple",
        "device_token": legacy["token"],
    })
    assert login.status_code == 200
    assert login.json()["access_token"]
    assert login.json()["user"]["profile"]["name"] == "능선러"


def test_account_session_can_drive_hike_records(client, register_device):
    _, legacy = register_device(name="기록러")
    reg = client.post("/api/v1/auth/register", json={
        "email": "trail@example.com",
        "password": "very-secret-password",
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
        assert provider == "google"
        assert code == "provider-code"
        assert redirect_uri.endswith("/api/v1/auth/oauth/google/callback")
        return {
            "provider_user_id": "google-123",
            "email": "social@example.com",
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
