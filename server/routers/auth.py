"""계정 생성·로그인·소셜 OAuth 연결."""
from __future__ import annotations

import json
import secrets
from datetime import timedelta
from typing import Annotated
from urllib.parse import urlencode

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.orm import Session
from starlette.datastructures import URL

from ..auth import (
    DUMMY_PASSWORD_HASH,
    AuthContext,
    _aware,
    create_session,
    get_auth_context,
    hash_password,
    link_device_token_to_user,
    normalize_email,
    token_hash,
    validate_email,
    verify_password,
)
from ..config import get_settings
from ..db import get_db
from ..models import AuthIdentity, OAuthState, User, UserDevice, utcnow
from ..schemas import (AuthLoginIn, AuthMeOut, AuthOut, AuthRegisterIn,
                       AuthUserOut, ProfileIn)

router = APIRouter()
ACCOUNT_LOGIN_REQUIRED = "account login required"
UNKNOWN_OAUTH_PROVIDER = "unknown oauth provider"
OAUTH_REDIRECT_PATHS = frozenset({"/index.html", "/home.html"})
OAUTH_ERROR_CODES = frozenset({"access_denied", "invalid_oauth_callback", "oauth_failed"})

OAUTH_PROVIDERS = {
    "google": {
        "authorize_url": "https://accounts.google.com/o/oauth2/v2/auth",
        "token_url": "https://oauth2.googleapis.com/token",
        "userinfo_url": "https://openidconnect.googleapis.com/v1/userinfo",
        "scope": "openid email profile",
    },
    "kakao": {
        "authorize_url": "https://kauth.kakao.com/oauth/authorize",
        "token_url": "https://kauth.kakao.com/oauth/token",
        "userinfo_url": "https://kapi.kakao.com/v2/user/me",
        "scope": "profile_nickname account_email",
    },
    "naver": {
        "authorize_url": "https://nid.naver.com/oauth2.0/authorize",
        "token_url": "https://nid.naver.com/oauth2.0/token",
        "userinfo_url": "https://openapi.naver.com/v1/nid/me",
        "scope": "",
    },
}


def _provider_credentials(provider: str) -> tuple[str, str]:
    settings = get_settings()
    pairs = {
        "google": (settings.google_client_id, settings.google_client_secret),
        "kakao": (settings.kakao_client_id, settings.kakao_client_secret),
        "naver": (settings.naver_client_id, settings.naver_client_secret),
    }
    if provider not in pairs:
        raise ValueError(UNKNOWN_OAUTH_PROVIDER)
    return pairs[provider]


def _provider_config(provider: str) -> dict[str, str]:
    if provider == "google":
        return OAUTH_PROVIDERS["google"]
    if provider == "kakao":
        return OAUTH_PROVIDERS["kakao"]
    if provider == "naver":
        return OAUTH_PROVIDERS["naver"]
    raise HTTPException(404, UNKNOWN_OAUTH_PROVIDER)


def _provider_configured(provider: str) -> bool:
    client_id, client_secret = _provider_credentials(provider)
    if provider == "kakao":
        return bool(client_id)  # Kakao client secret is optional unless enabled in console.
    return bool(client_id and client_secret)


def _callback_url(request: Request, provider: str) -> str:
    settings = get_settings()
    base = settings.public_base_url.rstrip("/") if settings.public_base_url else str(request.base_url).rstrip("/")
    return f"{base}/api/v1/auth/oauth/{provider}/callback"


def _session_ttl_seconds() -> int:
    return get_settings().auth_session_days * 24 * 60 * 60


def _profile_dict(user: User) -> dict:
    return {"name": user.name, "fit": user.fit, "knee": user.knee, "heart": user.heart}


def _user_out(db: Session, user: User) -> AuthUserOut:
    providers = list(db.scalars(
        select(AuthIdentity.provider).where(AuthIdentity.user_id == user.id).order_by(AuthIdentity.provider)
    ))
    return AuthUserOut(id=user.id, email=user.email, providers=providers,
                       avatar_url=user.avatar_url, profile=_profile_dict(user))


def _sync_profile(user: User, profile: ProfileIn | dict) -> None:
    data = profile.model_dump() if isinstance(profile, ProfileIn) else profile
    user.name = (data.get("name") or "산친구")[:16]
    user.fit = int(data.get("fit") or 2)
    user.knee = bool(data.get("knee", False))
    user.heart = bool(data.get("heart", False))
    user.updated_at = utcnow()


def _sync_linked_device(db: Session, user: User) -> str:
    device_id = db.scalar(select(UserDevice.device_id).where(UserDevice.user_id == user.id))
    if not device_id:
        from ..auth import ensure_user_device
        return ensure_user_device(db, user).token
    from ..models import Device
    device = db.get(Device, device_id)
    if not device:
        from ..auth import ensure_user_device
        return ensure_user_device(db, user).token
    device.name = user.name
    device.fit = user.fit
    device.knee = user.knee
    device.heart = user.heart
    return device.token


def _auth_response(db: Session, user: User, access_token: str) -> AuthOut:
    device_token = _sync_linked_device(db, user)
    return AuthOut(access_token=access_token, expires_in=_session_ttl_seconds(),
                   user=_user_out(db, user), device_token=device_token)


def _create_user(db: Session, *, email: str | None, profile: ProfileIn | dict,
                 avatar_url: str = "") -> User:
    data = profile.model_dump() if isinstance(profile, ProfileIn) else profile
    user = User(email=email, avatar_url=avatar_url[:512])
    _sync_profile(user, data)
    db.add(user)
    db.flush()
    return user


def _safe_oauth_redirect_path(path: str | None) -> str:
    return path if path in OAUTH_REDIRECT_PATHS else "/index.html"


def _oauth_error(error: str) -> RedirectResponse:
    safe_error = error if error in OAUTH_ERROR_CODES else "oauth_failed"
    fragment = urlencode({"auth_error": safe_error})
    url = URL("/index.html").replace(fragment=fragment)
    return RedirectResponse(str(url), status_code=302)


def _oauth_authorization_redirect(provider: str, params: dict[str, str]) -> RedirectResponse:
    if provider == "google":
        url = URL("https://accounts.google.com/o/oauth2/v2/auth").include_query_params(**params)
        return RedirectResponse(str(url), status_code=302)
    if provider == "kakao":
        url = URL("https://kauth.kakao.com/oauth/authorize").include_query_params(**params)
        return RedirectResponse(str(url), status_code=302)
    if provider == "naver":
        url = URL("https://nid.naver.com/oauth2.0/authorize").include_query_params(**params)
        return RedirectResponse(str(url), status_code=302)
    raise HTTPException(404, UNKNOWN_OAUTH_PROVIDER)


def _oauth_success_redirect(path: str | None, token: str, provider: str) -> RedirectResponse:
    fragment = urlencode({"auth_token": token, "provider": provider})
    if _safe_oauth_redirect_path(path) == "/home.html":
        url = URL("/home.html").replace(fragment=fragment)
        return RedirectResponse(str(url), status_code=302)
    url = URL("/index.html").replace(fragment=fragment)
    return RedirectResponse(str(url), status_code=302)


@router.get("/auth/providers")
async def auth_providers():
    return {
        "email": True,
        "oauth": {provider: _provider_configured(provider) for provider in OAUTH_PROVIDERS},
    }


@router.post("/auth/register", response_model=AuthOut, status_code=201,
             responses={409: {"description": "Email already registered"}})
async def register(body: AuthRegisterIn, request: Request, db: Annotated[Session, Depends(get_db)]):
    email = validate_email(body.email)
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "email already registered")
    user = _create_user(db, email=email, profile=body)
    identity = AuthIdentity(user_id=user.id, provider="password", provider_user_id=email,
                            email=email, credential_hash=hash_password(body.password))
    db.add(identity)
    link_device_token_to_user(db, user, body.device_token)
    token, _ = create_session(db, user, request.headers.get("user-agent", ""),
                              request.client.host if request.client else "")
    response = _auth_response(db, user, token)
    db.commit()
    return response


@router.post("/auth/login", response_model=AuthOut,
             responses={401: {"description": "Invalid email, password, or account"}})
async def login(body: AuthLoginIn, request: Request, db: Annotated[Session, Depends(get_db)]):
    email = validate_email(body.email)
    identity = db.scalar(select(AuthIdentity).where(
        AuthIdentity.provider == "password", AuthIdentity.provider_user_id == email
    ))
    credential_hash = identity.credential_hash if identity else DUMMY_PASSWORD_HASH
    if not identity or not verify_password(body.password, credential_hash):
        raise HTTPException(401, "invalid email or password")
    user = db.get(User, identity.user_id)
    if not user:
        raise HTTPException(401, "invalid account")
    link_device_token_to_user(db, user, body.device_token)
    token, _ = create_session(db, user, request.headers.get("user-agent", ""),
                              request.client.host if request.client else "")
    response = _auth_response(db, user, token)
    db.commit()
    return response


@router.get("/auth/me", response_model=AuthMeOut,
            responses={401: {"description": "Account login required"}})
async def me(ctx: Annotated[AuthContext, Depends(get_auth_context)],
             db: Annotated[Session, Depends(get_db)]):
    if not ctx.user or not ctx.account_session:
        raise HTTPException(401, ACCOUNT_LOGIN_REQUIRED)
    response = AuthMeOut(user=_user_out(db, ctx.user), device_token=_sync_linked_device(db, ctx.user))
    db.commit()
    return response


@router.patch("/auth/me/profile", response_model=AuthMeOut,
              responses={401: {"description": "Account login required"}})
async def update_profile(body: ProfileIn, ctx: Annotated[AuthContext, Depends(get_auth_context)],
                         db: Annotated[Session, Depends(get_db)]):
    if not ctx.user or not ctx.account_session:
        raise HTTPException(401, ACCOUNT_LOGIN_REQUIRED)
    _sync_profile(ctx.user, body)
    response = AuthMeOut(user=_user_out(db, ctx.user), device_token=_sync_linked_device(db, ctx.user))
    db.commit()
    return response


@router.post("/auth/logout", responses={401: {"description": "Account login required"}})
async def logout(ctx: Annotated[AuthContext, Depends(get_auth_context)],
                 db: Annotated[Session, Depends(get_db)]):
    if not ctx.account_session:
        raise HTTPException(401, ACCOUNT_LOGIN_REQUIRED)
    ctx.account_session.revoked_at = utcnow()
    db.commit()
    return {"ok": True}


@router.get("/auth/oauth/{provider}/start",
            responses={
                404: {"description": "Unknown OAuth provider"},
                503: {"description": "OAuth provider is not configured"},
            })
async def oauth_start(provider: str, request: Request, db: Annotated[Session, Depends(get_db)],
                      device_token: str = "", name: str = "산친구", fit: int = 2,
                      knee: bool = False, heart: bool = False,
                      redirect_path: Annotated[str, Query()] = "/index.html"):
    if provider not in OAUTH_PROVIDERS:
        raise HTTPException(404, UNKNOWN_OAUTH_PROVIDER)
    if not _provider_configured(provider):
        raise HTTPException(503, f"{provider} login is not configured")
    client_id, _ = _provider_credentials(provider)
    cfg = _provider_config(provider)
    state = secrets.token_urlsafe(32)
    profile = {"name": name[:16] or "산친구", "fit": fit, "knee": knee, "heart": heart}
    db.add(OAuthState(
        state_hash=token_hash(state), provider=provider, device_token=device_token[:128],
        profile_json=json.dumps(profile, ensure_ascii=False),
        redirect_path=_safe_oauth_redirect_path(redirect_path),
        expires_at=utcnow() + timedelta(minutes=get_settings().auth_state_ttl_minutes),
    ))
    db.commit()
    params = {
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": _callback_url(request, provider),
        "state": state,
    }
    if cfg["scope"]:
        params["scope"] = cfg["scope"]
    return _oauth_authorization_redirect(provider, params)


async def fetch_oauth_profile(provider: str, code: str, redirect_uri: str) -> dict:
    client_id, client_secret = _provider_credentials(provider)
    cfg = _provider_config(provider)
    data = {
        "grant_type": "authorization_code",
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "code": code,
    }
    if client_secret:
        data["client_secret"] = client_secret
    async with httpx.AsyncClient(timeout=8.0) as client:
        token_res = await client.post(cfg["token_url"], data=data, headers={"Accept": "application/json"})
        token_res.raise_for_status()
        access_token = token_res.json().get("access_token")
        if not access_token:
            raise HTTPException(502, "oauth token missing")
        user_res = await client.get(cfg["userinfo_url"], headers={"Authorization": f"Bearer {access_token}"})
        user_res.raise_for_status()
        raw = user_res.json()

    if provider == "google":
        return {
            "provider_user_id": str(raw["sub"]),
            "email": normalize_email(raw.get("email", "")) or None,
            "email_verified": raw.get("email_verified") is True,
            "name": raw.get("name") or raw.get("given_name") or "산친구",
            "avatar_url": raw.get("picture") or "",
        }
    if provider == "kakao":
        account = raw.get("kakao_account", {})
        profile = account.get("profile", {})
        return {
            "provider_user_id": str(raw["id"]),
            "email": normalize_email(account.get("email", "")) or None,
            "email_verified": account.get("is_email_verified") is True,
            "name": profile.get("nickname") or "카카오 산친구",
            "avatar_url": profile.get("profile_image_url") or profile.get("thumbnail_image_url") or "",
        }
    response = raw.get("response", raw)
    return {
        "provider_user_id": str(response["id"]),
        "email": normalize_email(response.get("email", "")) or None,
        "email_verified": False,
        "name": response.get("nickname") or response.get("name") or "네이버 산친구",
        "avatar_url": response.get("profile_image") or "",
    }


def _consume_oauth_state(db: Session, provider: str, state: str) -> OAuthState:
    saved = db.get(OAuthState, token_hash(state))
    if not saved or saved.provider != provider:
        raise HTTPException(401, "invalid oauth state")
    if _aware(saved.expires_at) < utcnow():
        db.delete(saved)
        db.commit()
        raise HTTPException(401, "expired oauth state")
    db.delete(saved)
    db.flush()
    return saved


def _social_user(db: Session, provider: str, profile: dict, requested_profile: dict) -> User:
    provider_user_id = str(profile["provider_user_id"])
    identity = db.scalar(select(AuthIdentity).where(
        AuthIdentity.provider == provider, AuthIdentity.provider_user_id == provider_user_id
    ))
    if identity:
        user = db.get(User, identity.user_id)
        if not user:
            raise HTTPException(401, "invalid account")
        return user

    email = normalize_email(profile.get("email") or "") or None
    verified_email = email if profile.get("email_verified") else None
    user = db.scalar(select(User).where(User.email == verified_email)) if verified_email else None
    if not user:
        chosen = requested_profile if requested_profile.get("name") else {
            "name": profile.get("name") or "산친구", "fit": 2, "knee": False, "heart": False,
        }
        user = _create_user(db, email=verified_email, profile=chosen, avatar_url=profile.get("avatar_url", ""))
    elif requested_profile.get("name"):
        _sync_profile(user, requested_profile)
    if profile.get("avatar_url"):
        user.avatar_url = profile["avatar_url"][:512]
    db.add(AuthIdentity(user_id=user.id, provider=provider, provider_user_id=provider_user_id,
                        email=email, credential_hash=""))
    db.flush()
    return user


@router.get("/auth/oauth/{provider}/callback",
            responses={
                404: {"description": "Unknown OAuth provider"},
                401: {"description": "Invalid or expired OAuth state"},
                502: {"description": "OAuth token is missing"},
            })
async def oauth_callback(provider: str, request: Request, db: Annotated[Session, Depends(get_db)],
                         code: str = "", state: str = "", error: str = ""):
    if error:
        return _oauth_error(error)
    if provider not in OAUTH_PROVIDERS:
        raise HTTPException(404, UNKNOWN_OAUTH_PROVIDER)
    if not code or not state:
        return _oauth_error("invalid_oauth_callback")
    try:
        saved = _consume_oauth_state(db, provider, state)
        requested_profile = json.loads(saved.profile_json or "{}")
        profile = await fetch_oauth_profile(provider, code, _callback_url(request, provider))
        user = _social_user(db, provider, profile, requested_profile)
        link_device_token_to_user(db, user, saved.device_token)
        token, _ = create_session(db, user, request.headers.get("user-agent", ""),
                                  request.client.host if request.client else "")
        _sync_linked_device(db, user)
        db.commit()
        return _oauth_success_redirect(saved.redirect_path, token, provider)
    except HTTPException:
        raise
    except Exception:  # noqa: BLE001
        db.rollback()
        return _oauth_error("oauth_failed")
