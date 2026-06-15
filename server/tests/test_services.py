import asyncio
from types import SimpleNamespace

import pytest

from server.seed import COURSES, REGIONS
from server.services import chat, llm, push


def _run(coro):
    return asyncio.run(coro)


def _cond():
    region = REGIONS["eunpyeong"]
    return {**region["snapshot"], "name": region["name"],
            "sunset_at": region["sunset_at"],
            "sunset_score": region["snapshot"]["sunset_score"]}


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
        seen.update(message=message, lang=lang, context=context)
        return "LLM reply"

    monkeypatch.setattr(chat, "get_settings", lambda: SimpleNamespace(llm_enabled=True))
    monkeypatch.setattr(llm, "ask_llm", fake_ask_llm)

    res = _run(chat.answer("질문", "ko", "eunpyeong", "bukhansan", 0.5, _cond()))
    assert res["engine"] == "claude"
    assert res["intent"] == "llm"
    assert "위험구간:" in seen["context"]


def test_llm_adapter_builds_cached_client(monkeypatch):
    created = []

    class FakeMessages:
        async def create(self, **kwargs):
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
    assert created[0]["model"] == "model-x"
    assert created[0]["max_tokens"] == 77


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
