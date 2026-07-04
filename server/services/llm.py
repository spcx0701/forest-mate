"""AI 숲해설사 '숲이' — LLM(RAG) 어댑터.

GEMINI_API_KEY/LLM_API_KEY 또는 ANTHROPIC_API_KEY가 설정되면 provider API로
답하고, 없으면 호출부가 규칙 기반 의도 엔진(chat.py)으로 폴백한다. 시스템 프롬프트는
고정 본문(지식베이스 포함)을 앞에, 요청별 컨텍스트는 messages에 넣는다.
"""
import anthropic
import httpx

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


class LLMProviderError(RuntimeError):
    """Provider call failed or returned an unexpected response."""


def _get_client() -> anthropic.AsyncAnthropic:
    global _client
    if _client is None:
        _client = anthropic.AsyncAnthropic(api_key=get_settings().anthropic_api_key)
    return _client


def _setting(settings, name: str, default=None):
    return getattr(settings, name, default)


def _resolved_model(settings, default: str) -> str:
    return _setting(settings, "resolved_llm_model", None) or _setting(settings, "llm_model", default)


def _gemini_api_key(settings) -> str:
    return (_setting(settings, "llm_api_key_for_provider", None)
            or _setting(settings, "gemini_api_key", None)
            or _setting(settings, "llm_api_key", ""))


async def ask_llm(message: str, lang: str, context: str) -> str:
    """RAG 컨텍스트(실시간 기상·코스 상태)를 붙여 Claude에 질의한다."""
    settings = get_settings()
    provider = _setting(settings, "resolved_llm_provider", None) or _setting(settings, "llm_provider", "claude")
    if provider == "gemini":
        return await ask_gemini(message, lang, context)
    return await ask_claude(message, lang, context)


async def ask_claude(message: str, lang: str, context: str) -> str:
    """RAG 컨텍스트(실시간 기상·코스 상태)를 붙여 Claude에 질의한다."""
    settings = get_settings()
    response = await _get_client().messages.create(
        model=_resolved_model(settings, "claude-opus-4-8"),
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


async def ask_gemini(message: str, lang: str, context: str) -> str:
    """Google Gemini OpenAI-compatible chat completions로 질의한다."""
    settings = get_settings()
    api_key = _gemini_api_key(settings)
    if not api_key:
        raise LLMProviderError("Gemini API key is not configured")

    base_url = _setting(
        settings,
        "llm_base_url",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
    ).rstrip("/")
    payload = {
        "model": _resolved_model(settings, "gemini-3.5-flash"),
        "max_tokens": settings.llm_max_tokens,
        "messages": [
            {"role": "system", "content": _SYSTEM},
            {
                "role": "user",
                "content": (
                    f"[응답 언어: {lang}]\n[실시간 컨텍스트]\n{context}\n\n[질문]\n{message}"
                ),
            },
        ],
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=_setting(settings, "llm_timeout_s", 12.0)) as client:
            response = await client.post(f"{base_url}/chat/completions", headers=headers, json=payload)
            response.raise_for_status()
            data = response.json()
    except (httpx.HTTPError, ValueError) as exc:
        raise LLMProviderError("Gemini chat completion failed") from exc

    try:
        content = data["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise LLMProviderError("Gemini chat completion returned no text") from exc
    if not isinstance(content, str) or not content.strip():
        raise LLMProviderError("Gemini chat completion returned empty text")
    return content
