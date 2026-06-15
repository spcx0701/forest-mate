import os
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

# 테스트 전용 환경 — import 전에 주입
os.environ["DATABASE_URL"] = "sqlite:///./test_forestmate.db"
os.environ["K_ANONYMITY"] = "1"
os.environ["DISTRESS_STALL_MINUTES"] = "30"
os.environ["DATA_GO_KR_KEY"] = ""       # 스냅샷 모드 강제
os.environ["ANTHROPIC_API_KEY"] = ""    # 규칙 엔진 강제
os.environ["FORESTMATE_SKIP_CATALOG"] = "1"  # 베이크 카탈로그 적재 생략(테스트 자체 시드)


@pytest.fixture(scope="session")
def client():
    from fastapi.testclient import TestClient

    from server.config import get_settings
    get_settings.cache_clear()
    from server.main import app

    with TestClient(app) as c:
        yield c

    from server.db import engine
    engine.dispose()
    db = ROOT / "test_forestmate.db"
    if db.exists():
        db.unlink()


@pytest.fixture(autouse=True)
def clean_db():
    from server import models  # noqa: F401
    from server.db import Base, engine

    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield


@pytest.fixture
def seed_mountain():
    def _seed(**fields):
        from server.db import SessionLocal
        from server.models import Mountain

        data = {
            "list_no": "M1", "name": "테스트산", "sido": "서울특별시",
            "addr": "서울특별시 은평구", "height": 500, "is_top100": False,
        }
        data.update(fields)
        db = SessionLocal()
        try:
            mountain = Mountain(**data)
            db.merge(mountain)
            db.commit()
            return mountain
        finally:
            db.close()
    return _seed


@pytest.fixture
def register_device(client):
    def _register(**fields):
        payload = {"name": "테스터", "fit": 2}
        payload.update(fields)
        res = client.post("/api/v1/devices", json=payload)
        assert res.status_code == 201
        body = res.json()
        return {"Authorization": f"Bearer {body['token']}"}, body
    return _register
