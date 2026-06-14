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

    db = ROOT / "test_forestmate.db"
    if db.exists():
        db.unlink()
