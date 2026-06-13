"""AI 숲해설사 '숲이' — LLM(RAG) 어댑터.

ANTHROPIC_API_KEY가 설정되면 Claude Messages API로 답하고, 없으면 호출부가
규칙 기반 의도 엔진(chat.py)으로 폴백한다. 시스템 프롬프트는 캐시 최적화를 위해
고정 본문(지식베이스 포함)을 앞에, 요청별 컨텍스트는 messages에 넣는다.
"""
import anthropic

from ..config import get_settings
from ..seed import KNOWLEDGE_NOTES

_SYSTEM = (
    "당신은 등산 앱 '숲길동무'의 AI 숲해설사 '숲이'입니다. 산림 공공데이터를 근거로 "
    "한국어(또는 요청 언어)로 따뜻하고 간결하게 답하세요. 규칙:\n"
    "1) 아래 지식베이스와 사용자 컨텍스트에 있는 사실만 사용하고, 모르면 모른다고 답한다.\n"
    "2) 안전 관련 질문은 보수적으로 — 위험하면 하산·우회를 권한다.\n"
    "3) 야생 동식물 섭취는 항상 금지로 안내한다.\n"
    "4) 답은 3문장 이내, 수치는 컨텍스트 값을 그대로 인용한다.\n\n"
    f"### 지식베이스(공공데이터 발췌)\n{KNOWLEDGE_NOTES}"
)

_client: anthropic.AsyncAnthropic | None = None


def _get_client() -> anthropic.AsyncAnthropic:
    global _client
    if _client is None:
        _client = anthropic.AsyncAnthropic(api_key=get_settings().anthropic_api_key)
    return _client


async def ask_llm(message: str, lang: str, context: str) -> str:
    """RAG 컨텍스트(실시간 기상·코스 상태)를 붙여 Claude에 질의한다."""
    settings = get_settings()
    response = await _get_client().messages.create(
        model=settings.llm_model,
        max_tokens=settings.llm_max_tokens,
        system=[{"type": "text", "text": _SYSTEM, "cache_control": {"type": "ephemeral"}}],
        messages=[{
            "role": "user",
            "content": (
                f"[응답 언어: {lang}]\n[실시간 컨텍스트]\n{context}\n\n[질문]\n{message}"
            ),
        }],
    )
    return next((b.text for b in response.content if b.type == "text"), "")
