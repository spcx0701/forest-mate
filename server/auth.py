"""계정 인증 헬퍼 — 자체 세션과 기존 익명 기기 토큰을 함께 처리."""
from __future__ import annotations

import hashlib
import hmac
import secrets
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from fastapi import Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .db import get_db
from .models import AuthSession, Device, User, UserDevice, utcnow

DUMMY_PASSWORD_HASH = (
    "pbkdf2_sha256$390000$00000000000000000000000000000000$"
    "0000000000000000000000000000000000000000000000000000000000000000"
)


@dataclass(frozen=True)
class AuthContext:
    user: User | None
    device: Device
    token: str
    account_session: AuthSession | None = None


def normalize_email(email: str) -> str:
    return email.strip().lower()


def validate_email(email: str) -> str:
    value = normalize_email(email)
    parts = value.split("@")
    if len(parts) != 2:
        raise HTTPException(422, "invalid email")
    local, domain = parts
    if (
        not local
        or not domain
        or "." not in domain
        or domain.startswith(".")
        or domain.endswith(".")
        or any(ch.isspace() for ch in value)
    ):
        raise HTTPException(422, "invalid email")
    return value


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    rounds = 390_000
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"),
                                 salt.encode("utf-8"), rounds).hex()
    return f"pbkdf2_sha256${rounds}${salt}${digest}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        algo, rounds_raw, salt, expected = encoded.split("$", 3)
        if algo != "pbkdf2_sha256":
            return False
        digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"),
                                     salt.encode("utf-8"), int(rounds_raw)).hex()
        return hmac.compare_digest(digest, expected)
    except Exception:  # noqa: BLE001
        return False


def bearer_token(authorization: str) -> str:
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    token = authorization[7:].strip()
    if not token:
        raise HTTPException(401, "missing bearer token")
    return token


def _aware(value: datetime) -> datetime:
    return value if value.tzinfo else value.replace(tzinfo=timezone.utc)


def is_active_session(session: AuthSession) -> bool:
    return session.revoked_at is None and _aware(session.expires_at) > datetime.now(timezone.utc)


def create_session(db: Session, user: User, user_agent: str = "", ip: str = "") -> tuple[str, AuthSession]:
    raw = secrets.token_urlsafe(48)
    session = AuthSession(
        user_id=user.id,
        token_hash=token_hash(raw),
        expires_at=utcnow() + timedelta(days=get_settings().auth_session_days),
        user_agent=user_agent[:255],
        ip=ip[:64],
    )
    db.add(session)
    db.flush()
    return raw, session


def session_for_token(db: Session, token: str) -> AuthSession | None:
    session = db.scalar(select(AuthSession).where(AuthSession.token_hash == token_hash(token)))
    return session if session and is_active_session(session) else None


def linked_device_ids(db: Session, user: User) -> list[str]:
    return list(db.scalars(select(UserDevice.device_id).where(UserDevice.user_id == user.id)))


def link_device_to_user(db: Session, user: User, device: Device) -> None:
    existing = db.get(UserDevice, {"user_id": user.id, "device_id": device.id})
    if existing:
        return
    owner = db.scalar(select(UserDevice).where(UserDevice.device_id == device.id))
    if owner and owner.user_id != user.id:
        raise HTTPException(409, "device already linked")
    db.add(UserDevice(user_id=user.id, device_id=device.id))


def link_device_token_to_user(db: Session, user: User, device_token: str | None) -> Device | None:
    if not device_token:
        return None
    device = db.scalar(select(Device).where(Device.token == device_token))
    if not device:
        return None
    link_device_to_user(db, user, device)
    return device


def ensure_user_device(db: Session, user: User) -> Device:
    device_id = db.scalar(select(UserDevice.device_id).where(UserDevice.user_id == user.id))
    if device_id:
        device = db.get(Device, device_id)
        if device:
            return device
    device = Device(name=user.name, fit=user.fit, knee=user.knee, heart=user.heart)
    db.add(device)
    db.flush()
    link_device_to_user(db, user, device)
    return device


def context_from_authorization(db: Session, authorization: str) -> AuthContext:
    token = bearer_token(authorization)
    session = session_for_token(db, token)
    if session:
        user = db.get(User, session.user_id)
        if not user:
            raise HTTPException(401, "invalid token")
        device = ensure_user_device(db, user)
        return AuthContext(user=user, device=device, token=token, account_session=session)

    device = db.scalar(select(Device).where(Device.token == token))
    if not device:
        raise HTTPException(401, "invalid token")
    link = db.scalar(select(UserDevice).where(UserDevice.device_id == device.id))
    user = db.get(User, link.user_id) if link else None
    return AuthContext(user=user, device=device, token=token)


def get_auth_context(authorization: str = Header(default=""),
                     db: Session = Depends(get_db)) -> AuthContext:
    return context_from_authorization(db, authorization)


def get_current_user(ctx: AuthContext = Depends(get_auth_context)) -> User:
    if not ctx.user or not ctx.account_session:
        raise HTTPException(401, "account login required")
    return ctx.user
