"""숲길동무 API 서버.

  uvicorn server.main:app --port 5181
정적 프런트(app/)도 같은 오리진에서 서빙한다(CORS 불필요·배포 단순화).
"""
import asyncio
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import func, select

from .config import get_settings
from .db import SessionLocal, init_db
from .routers import dashboard, hikes, public

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("forestmate")

APP_DIR = Path(__file__).resolve().parent.parent / "app"


async def _autoload_mountains() -> None:
    """키가 있고 카탈로그가 비어 있으면 전국 산을 백그라운드로 적재(시작 비차단)."""
    from .models import Mountain
    db = SessionLocal()
    try:
        count = db.scalar(select(func.count()).select_from(Mountain)) or 0
    finally:
        db.close()
    if count:
        return
    from .etl.load_mountains import run
    try:
        log.info("mountain catalog empty — ETL 시작")
        log.info("mountain ETL 완료: %s", await run())
    except Exception as exc:  # noqa: BLE001
        log.warning("mountain ETL 실패(스냅샷/검색은 계속 동작): %s", exc)


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    settings = get_settings()
    log.info("started env=%s live_data=%s llm=%s",
             settings.env, settings.live_data, settings.llm_enabled)
    if settings.live_data:
        asyncio.create_task(_autoload_mountains())  # 비차단 — 헬스체크 즉시 통과
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="숲길동무 ForestMate API", version="1.0.0", lifespan=lifespan)

    origins = [o.strip() for o in settings.cors_origins.split(",")]
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_methods=["*"],
                       allow_headers=["*"])

    app.include_router(public.router, prefix="/api/v1", tags=["public"])
    app.include_router(hikes.router, prefix="/api/v1", tags=["hikes"])
    app.include_router(dashboard.router, prefix="/api/v1", tags=["dashboard"])

    # TWA(Android) Digital Asset Links — 정적 마운트가 .well-known 점(.) 경로를
    # 막는 프록시도 있어 명시 라우트로 보장. Play 앱 서명키 지문을 채워 배포한다.
    @app.get("/.well-known/assetlinks.json", include_in_schema=False)
    async def assetlinks():
        f = APP_DIR / ".well-known" / "assetlinks.json"
        if f.exists():
            return FileResponse(f, media_type="application/json")
        return JSONResponse([], status_code=404)

    # iOS Universal Links(선택) — Capacitor 앱의 apple-app-site-association
    @app.get("/.well-known/apple-app-site-association", include_in_schema=False)
    async def aasa():
        f = APP_DIR / ".well-known" / "apple-app-site-association"
        if f.exists():
            return FileResponse(f, media_type="application/json")
        return JSONResponse({}, status_code=404)

    # 정적 프런트 (모바일 PWA·랜딩·관제) — API 라우트 뒤에 마운트
    if APP_DIR.exists():
        app.mount("/", StaticFiles(directory=APP_DIR, html=True), name="app")
    return app


app = create_app()
