import asyncio
from datetime import datetime
from types import SimpleNamespace

import pytest
from defusedxml.common import EntitiesForbidden

from server.adapters import base, mountains, public_data
from server.seed import REGIONS


def _run(coro):
    return asyncio.run(coro)


@pytest.mark.parametrize(("raw_key", "expected"), [
    ("abc%2Fdef%3D", "abc/def="),
    ("plain-key", "plain-key"),
])
def test_service_key_decodes_once(monkeypatch, raw_key, expected):
    monkeypatch.setattr(base, "get_settings",
                        lambda: SimpleNamespace(data_go_kr_key=raw_key))
    assert base.service_key() == expected


def test_fetch_helpers_cache_and_redact(monkeypatch):
    class Response:
        def __init__(self, text, payload=None, exc=None):
            self.text = text
            self._payload = payload
            self._exc = exc

        def raise_for_status(self):
            if self._exc:
                raise self._exc

        def json(self):
            return self._payload

    class FakeClient:
        queue = [
            Response('{"ok": true}', {"ok": True}),
            Response("plain text"),
            Response("body serviceKey=SECRET", exc=RuntimeError("serviceKey=SECRET")),
        ]
        calls = []

        def __init__(self, *_, **__):
            self.timeout = None

        async def __aenter__(self):
            return self

        async def __aexit__(self, *_):
            return None

        async def get(self, url, params):
            await asyncio.sleep(0)
            self.calls.append((url, params, self.timeout))
            return self.queue.pop(0)

    base.clear_cache()
    monkeypatch.setattr(base, "get_settings",
                        lambda: SimpleNamespace(adapter_cache_ttl_s=600, adapter_timeout_s=3.0))
    monkeypatch.setattr(base.httpx, "AsyncClient", FakeClient)

    assert _run(base.fetch_json("https://example.test/json", {}, cache_key="json")) == {"ok": True}
    assert _run(base.fetch_json("https://example.test/json", {}, cache_key="json")) == {"ok": True}
    assert _run(base.fetch_text("https://example.test/text", {}, cache_key="text")) == "plain text"
    assert len(FakeClient.calls) == 2

    with pytest.raises(base.AdapterError) as exc:
        _run(base.fetch_json("https://example.test/fail", {}, cache_key="err"))
    assert "SECRET" not in str(exc.value)
    assert "SECRET" not in base.last_errors["err"]


@pytest.mark.parametrize(("now", "expected"), [
    (datetime(2026, 6, 15, 2, 30), ("20260614", "2300")),
    (datetime(2026, 6, 15, 6, 0), ("20260615", "0500")),
    (datetime(2026, 6, 15, 23, 59), ("20260615", "2300")),
], ids=["before-first-slot", "morning-slot", "late-slot"])
def test_kma_base_uses_valid_publish_slots(now, expected):
    assert public_data._kma_base(now) == expected


def test_public_data_live_parsing_and_fallback(monkeypatch):
    region = {**REGIONS["eunpyeong"], "id": "eunpyeong"}
    kma_items = [
        {"fcstDate": "20260615", "fcstTime": "0300", "category": "TMP", "fcstValue": "20"},
        {"fcstDate": "20260615", "fcstTime": "0300", "category": "WSD", "fcstValue": "5"},
        {"fcstDate": "20260615", "fcstTime": "0300", "category": "POP", "fcstValue": "20"},
        {"fcstDate": "20260616", "fcstTime": "0300", "category": "TMP", "fcstValue": "22"},
        {"fcstDate": "20260616", "fcstTime": "0300", "category": "WSD", "fcstValue": "9"},
        {"fcstDate": "20260616", "fcstTime": "0300", "category": "POP", "fcstValue": "60"},
    ]

    async def ok_fetch(url, params, cache_key):
        await asyncio.sleep(0)
        assert params.get("serviceKey", params.get("ServiceKey")) == "KEY"
        assert cache_key.endswith(":eunpyeong")
        if url == public_data.KMA_URL:
            return {"response": {"body": {"items": {"item": kma_items}}}}
        return {"response": {"body": {"items": {"item": [{"meanAvg": "64"}]}}}}

    monkeypatch.setattr(public_data, "get_settings", lambda: SimpleNamespace(live_data=True))
    monkeypatch.setattr(public_data, "service_key", lambda: "KEY")
    monkeypatch.setattr(public_data, "fetch_json", ok_fetch)

    weather = _run(public_data.get_weather(region))
    forecast = _run(public_data.get_forecast(region, days=2))
    fire = _run(public_data.get_fire_risk(region))
    cond = _run(public_data.conditions_for_region(region))

    assert weather == {"temp": 20.0, "wind": 5.0, "rain_prob": 20, "label": "맑음",
                       "score": 75, "station": "기상청 단기예보", "source": "live"}
    assert [d["source"] for d in forecast] == ["live", "live"]
    assert forecast[1]["rain_prob"] == 60 and forecast[1]["score"] == 15
    assert fire == {"level": "보통", "score": 36,
                    "src": "국립산림과학원 산불위험예보", "source": "live"}
    assert cond["weather"]["source"] == "live" and cond["landslide"]["source"] == "etl"

    async def fail_fetch(*_args, **_kwargs):
        raise base.AdapterError("temporary")

    monkeypatch.setattr(public_data, "fetch_json", fail_fetch)
    assert _run(public_data.get_weather(region))["source"] == "snapshot"
    assert _run(public_data.get_forecast(region)) == []
    assert _run(public_data.get_fire_risk(region))["source"] == "snapshot"


def test_mountain_xml_parse_extracts_items_and_total():
    xml = """
    <response>
      <header><resultCode>00</resultCode></header>
      <body>
        <items>
          <item>
            <mntilistno>1</mntilistno>
            <mntiname>북한산</mntiname>
            <mntiadd>서울특별시</mntiadd>
          </item>
        </items>
        <totalCount>1</totalCount>
      </body>
    </response>
    """

    items, total = mountains._parse(xml)

    assert total == 1
    assert items == [{"mntilistno": "1", "mntiname": "북한산", "mntiadd": "서울특별시"}]


def test_mountain_xml_parse_raises_adapter_error_on_api_error():
    xml = """
    <response>
      <header>
        <resultCode>99</resultCode>
        <resultMsg>bad key</resultMsg>
      </header>
    </response>
    """

    with pytest.raises(base.AdapterError, match="resultCode=99"):
        mountains._parse(xml)


def test_mountain_fetch_passes_cache_key_and_timeout(monkeypatch):
    xml = """
    <response><body><items><item>
      <mntilistno>001</mntilistno><mntiname>북한산</mntiname>
      <mntiadd>서울특별시 은평구</mntiadd>
    </item></items><totalCount>7</totalCount></body></response>
    """

    async def fake_fetch_text(url, params, cache_key, request_timeout_s):
        await asyncio.sleep(0)
        assert url == mountains.MNT_INFO_URL
        assert params == {"serviceKey": "KEY", "pageNo": 2, "numOfRows": 5, "searchWrd": "북한"}
        assert cache_key == "mnt:북한:2:5"
        assert request_timeout_s == mountains._TIMEOUT
        return xml

    monkeypatch.setattr(mountains, "service_key", lambda: "KEY")
    monkeypatch.setattr(mountains, "fetch_text", fake_fetch_text)
    assert _run(mountains.fetch_mountains(page=2, rows=5, search="북한"))[1] == 7


def test_mountain_xml_parse_rejects_xml_entities():
    xml = """<?xml version="1.0"?>
    <!DOCTYPE root [
      <!ENTITY unsafe SYSTEM "file:///etc/passwd">
    ]>
    <response><body><items><item><mntiname>&unsafe;</mntiname></item></items></body></response>
    """

    with pytest.raises(EntitiesForbidden):
        mountains._parse(xml)
