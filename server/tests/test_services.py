import asyncio
from types import SimpleNamespace

import pytest

from server.seed import COURSES, REGIONS
from server.config import Settings
from server.services import chat, llm, push


def _run(coro):
    return asyncio.run(coro)


def _cond():
    region = REGIONS["eunpyeong"]
    return {**region["snapshot"], "name": region["name"],
            "sunset_at": region["sunset_at"],
            "sunset_score": region["snapshot"]["sunset_score"]}


def test_settings_enables_gemini_from_gemini_api_key():
    settings = Settings(gemini_api_key="gemini-secret")
    assert settings.llm_enabled is True
    assert settings.resolved_llm_provider == "gemini"
    assert settings.llm_api_key_for_provider == "gemini-secret"
    assert settings.resolved_llm_model == "gemini-3.5-flash"


def test_settings_keeps_claude_when_only_anthropic_key_is_set():
    settings = Settings(anthropic_api_key="anthropic-secret")
    assert settings.llm_enabled is True
    assert settings.resolved_llm_provider == "claude"
    assert settings.llm_api_key_for_provider == "anthropic-secret"
    assert settings.resolved_llm_model == "claude-opus-4-8"


@pytest.mark.parametrize(("message", "lang", "intent", "fragment"), [
    ("정상까지 얼마나 남았어?", "ko", "summit", "남은 거리"),
    ("weather today?", "en", "weather", "산악기상"),
    ("낙석 위험 조심해야 해?", "ko", "hazard", "위험 구간"),
    ("휴양림 치유 예약", "ko", "healing", "잔여석"),
    ("보험 들어야 해?", "ko", "insurance", "990원"),
    ("hello", "en", "greeting", "forest guide"),
    ("뭐 할 수 있어?", "ko", "fallback", "공공데이터"),
], ids=["summit", "weather", "hazard", "healing", "insurance", "greeting", "fallback"])
def test_rule_reply_intents(message, lang, intent, fragment):
    res = chat.rule_reply(message, lang, _cond(), COURSES[0], progress=0.25)
    assert res["intent"] == intent
    assert fragment in res["reply"]


def test_answer_uses_llm_when_enabled(monkeypatch):
    seen = {}

    async def fake_ask_llm(message, lang, context):
        await asyncio.sleep(0)
        seen.update(message=message, lang=lang, context=context)
        return "LLM reply"

    monkeypatch.setattr(chat, "get_settings", lambda: SimpleNamespace(llm_enabled=True))
    monkeypatch.setattr(llm, "ask_llm", fake_ask_llm)

    res = _run(chat.answer("질문", "ko", "bukhansan", 0.5, _cond()))
    assert res["engine"] == "claude"
    assert res["intent"] == "llm"
    assert "위험구간:" in seen["context"]


def test_answer_reports_configured_llm_engine(monkeypatch):
    async def fake_ask_llm(message, lang, context):
        await asyncio.sleep(0)
        return "LLM reply"

    monkeypatch.setattr(chat, "get_settings",
                        lambda: SimpleNamespace(llm_enabled=True, llm_provider="gemini"))
    monkeypatch.setattr(llm, "ask_llm", fake_ask_llm)

    res = _run(chat.answer("질문", "ko", "bukhansan", 0.5, _cond()))
    assert res["engine"] == "gemini"
    assert res["intent"] == "llm"


def test_claude_adapter_builds_cached_client(monkeypatch):
    created = []

    class FakeMessages:
        async def create(self, **kwargs):
            await asyncio.sleep(0)
            created.append(kwargs)
            return SimpleNamespace(content=[
                SimpleNamespace(type="tool_use", text="ignored"),
                SimpleNamespace(type="text", text="숲길 답변"),
            ])

    class FakeClient:
        def __init__(self, api_key):
            self.api_key = api_key
            self.messages = FakeMessages()

    monkeypatch.setattr(llm, "_client", None)
    monkeypatch.setattr(llm.anthropic, "AsyncAnthropic", FakeClient)
    monkeypatch.setattr(llm, "get_settings",
                        lambda: SimpleNamespace(anthropic_api_key="secret",
                                                llm_model="model-x", llm_max_tokens=77))

    assert llm._get_client().api_key == "secret"
    assert llm._get_client() is llm._client
    assert _run(llm.ask_llm("질문", "ko", "context")) == "숲길 답변"
    assert created
    assert created[0]["model"] == "model-x"
    assert created[0]["max_tokens"] == 77


def test_gemini_adapter_calls_openai_compatible_endpoint(monkeypatch):
    requests = []

    class FakeResponse:
        def raise_for_status(self):
            return None

        def json(self):
            return {"choices": [{"message": {"content": "숲길 답변"}}]}

    class FakeClient:
        def __init__(self, timeout):
            self.timeout = timeout

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_args):
            return None

        async def post(self, url, headers, json):
            requests.append({"url": url, "headers": headers, "json": json, "timeout": self.timeout})
            return FakeResponse()

    monkeypatch.setattr(llm.httpx, "AsyncClient", FakeClient)
    monkeypatch.setattr(llm, "get_settings",
                        lambda: SimpleNamespace(
                            llm_api_key="gemini-secret",
                            llm_base_url="https://generativelanguage.googleapis.com/v1beta/openai/",
                            llm_model="gemini-3.5-flash",
                            llm_max_tokens=77,
                            llm_timeout_s=12.0,
                        ))

    assert _run(llm.ask_gemini("질문", "ko", "context")) == "숲길 답변"
    assert requests
    assert requests[0]["url"] == (
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    )
    assert requests[0]["headers"]["Authorization"] == "Bearer gemini-secret"
    assert requests[0]["json"]["model"] == "gemini-3.5-flash"
    assert requests[0]["json"]["max_tokens"] == 77
    assert requests[0]["json"]["messages"][0]["role"] == "system"
    assert requests[0]["json"]["messages"][1]["role"] == "user"
    assert "[실시간 컨텍스트]\ncontext" in requests[0]["json"]["messages"][1]["content"]


def test_push_send_to_modes(monkeypatch):
    sub = SimpleNamespace(endpoint="https://push.example/sub", p256dh="p256", auth="auth")
    monkeypatch.setattr(push, "get_settings", lambda: SimpleNamespace(push_enabled=False))
    assert push.send_to(sub, "제목", "본문") is False

    import pywebpush

    calls = []

    def ok_webpush(**kwargs):
        calls.append(kwargs)

    monkeypatch.setattr(push, "get_settings",
                        lambda: SimpleNamespace(push_enabled=True, vapid_private_key="line\\nkey",
                                                vapid_subject="mailto:test@example.com"))
    monkeypatch.setattr(pywebpush, "webpush", ok_webpush)
    assert push.send_to(sub, "제목", "본문", "/go") is True
    assert calls[0]["vapid_private_key"] == "line\nkey"
    assert calls[0]["vapid_claims"]["sub"] == "mailto:test@example.com"

    def bad_webpush(**_kwargs):
        raise RuntimeError("expired")

    monkeypatch.setattr(pywebpush, "webpush", bad_webpush)
    assert push.send_to(sub, "제목", "본문") is False
