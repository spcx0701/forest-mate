"""DB 세션 — dev: SQLite / prod: PostgreSQL(영속). 연결 실패 시 SQLite 폴백."""
import logging

from sqlalchemy import create_engine, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings

log = logging.getLogger("forestmate.db")
_FALLBACK = "sqlite:////tmp/forestmate.db"


class Base(DeclarativeBase):
    pass


def _engine_for(url: str):
    if url.startswith("postgres://"):  # Render는 postgres://, SQLAlchemy 2.0은 postgresql://
        url = url.replace("postgres://", "postgresql://", 1)
    kwargs: dict = {"pool_pre_ping": True}
    if url.startswith("sqlite"):
        kwargs["connect_args"] = {"check_same_thread": False}
    return create_engine(url, **kwargs)


def _make_engine():
    url = get_settings().database_url
    eng = _engine_for(url)
    if url.startswith("sqlite"):
        return eng
    try:  # Postgres 등 — 잘못 설정돼도 라이브가 죽지 않도록 연결 확인 후 폴백
        with eng.connect() as c:
            c.execute(text("SELECT 1"))
        log.info("DB 연결: %s", url.split("@")[-1])
        return eng
    except Exception as exc:  # noqa: BLE001
        log.warning("DB(%s) 연결 실패 → SQLite 폴백: %s", url.split("@")[-1], exc)
        return _engine_for(_FALLBACK)


engine = _make_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


def get_db():
    db: Session = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    from . import models  # noqa: F401 — 모델 등록

    Base.metadata.create_all(engine)
