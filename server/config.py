"""숲길동무 백엔드 설정 — 환경변수(.env)로 주입한다.

운영 전환 체크리스트:
  - DATABASE_URL을 PostgreSQL(+PostGIS)로 교체
  - DATA_GO_KR_KEY 발급(공공데이터포털) 후 설정 → 어댑터가 실 API 호출
  - K_ANONYMITY=50 (위치정보 통계 표출 기준)
  - ANTHROPIC_API_KEY 설정 시 AI 챗이 LLM(RAG) 모드로 동작
"""
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "forestmate-api"
    env: str = "dev"  # dev | staging | prod
    database_url: str = "sqlite:///./forestmate.db"

    # 공공데이터 API 키 (없으면 동일 스키마의 스냅샷으로 폴백 — 데모/오프라인 모드)
    data_go_kr_key: str = ""

    # 외부 API 타임아웃/캐시
    adapter_timeout_s: float = 3.0
    adapter_cache_ttl_s: int = 600  # 산불위험예보·기상 등은 10분 캐시

    # 위치정보 익명화: 통계 표출 최소 군집 크기 (위치정보법 준수 기준 k≥50, 개발은 1)
    k_anonymity: int = 1

    # 조난 감지 규칙 (운영: 30분 / 데모: 짧게)
    distress_stall_minutes: float = 30.0
    distress_min_points: int = 3

    # AI 챗 — LLM(RAG) 모드. 키가 없으면 규칙 기반 의도 엔진으로 동작.
    anthropic_api_key: str = ""
    llm_model: str = "claude-opus-4-8"
    llm_max_tokens: int = 1024

    cors_origins: str = "*"

    # Web Push(VAPID) — 둘 다 있으면 푸시 활성. 비우면 인앱 알림만.
    vapid_public_key: str = ""
    vapid_private_key: str = ""
    vapid_subject: str = "mailto:forestmate@example.com"

    # 계정/로그인 — 자체 세션 + OAuth Authorization Code 콜백
    public_base_url: str = ""  # 비우면 요청 Host 기준으로 redirect_uri 생성
    auth_session_days: int = 90
    auth_state_ttl_minutes: int = 10
    watch_pair_claim_window_s: int = 60
    watch_pair_claim_max_attempts: int = 10
    google_client_id: str = ""
    google_client_secret: str = ""
    kakao_client_id: str = ""      # Kakao REST API key
    kakao_client_secret: str = ""
    naver_client_id: str = ""
    naver_client_secret: str = ""

    @property
    def push_enabled(self) -> bool:
        return bool(self.vapid_public_key and self.vapid_private_key)

    @property
    def llm_enabled(self) -> bool:
        return bool(self.anthropic_api_key)

    @property
    def live_data(self) -> bool:
        return bool(self.data_go_kr_key)


@lru_cache
def get_settings() -> Settings:
    return Settings()
