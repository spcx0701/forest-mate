"""공공데이터 어댑터 공통 — TTL 캐시 + 타임아웃 + 폴백 신호 + 키 정규화."""
import time
from typing import Any
from urllib.parse import unquote

import httpx

from ..config import get_settings


class AdapterError(Exception):
    """외부 API 실패 — 서비스 레이어가 스냅샷으로 폴백한다."""


_cache: dict[str, tuple[float, Any]] = {}
# 진단용: cache_key -> 마지막 실패 사유(키 값은 포함하지 않음). /_diag에서 노출.
last_errors: dict[str, str] = {}


def service_key() -> str:
    """data.go.kr 인증키 정규화.

    공공데이터포털은 '인코딩 키'(%2B·%2F·%3D 포함)와 '디코딩 키'(원시) 두 형태를 준다.
    httpx가 params를 한 번 인코딩하므로, 인코딩 키를 그대로 넣으면 %가 %25로 이중
    인코딩돼 인증 실패한다. '%'가 있으면 원시 키로 되돌려 항상 1회만 인코딩되게 한다.
    """
    k = get_settings().data_go_kr_key
    return unquote(k) if "%" in k else k


async def fetch_json(url: str, params: dict, cache_key: str | None = None) -> Any:
    settings = get_settings()
    key = cache_key or f"{url}|{sorted(params.items())!r}"
    now = time.monotonic()

    hit = _cache.get(key)
    if hit and now - hit[0] < settings.adapter_cache_ttl_s:
        return hit[1]

    body = ""
    try:
        async with httpx.AsyncClient(timeout=settings.adapter_timeout_s) as client:
            res = await client.get(url, params=params)
        body = res.text
        res.raise_for_status()
        data = res.json()  # data.go.kr는 키 오류 시 200+XML을 주기도 함 → 여기서 폴백
    except Exception as exc:
        # 진단 기록(응답 본문 일부 포함 — 키는 응답에 없으므로 안전)
        last_errors[key] = f"{type(exc).__name__}: {str(exc)[:160]} | body={body[:200]!r}"
        raise AdapterError(str(exc)) from exc

    last_errors.pop(key, None)
    _cache[key] = (now, data)
    return data


async def fetch_text(url: str, params: dict, cache_key: str | None = None) -> str:
    """XML만 제공하는 서비스(산림청 산정보 등)용 — 원문 텍스트 반환."""
    settings = get_settings()
    key = cache_key or f"text|{url}|{sorted(params.items())!r}"
    now = time.monotonic()

    hit = _cache.get(key)
    if hit and now - hit[0] < settings.adapter_cache_ttl_s:
        return hit[1]

    body = ""
    try:
        async with httpx.AsyncClient(timeout=settings.adapter_timeout_s) as client:
            res = await client.get(url, params=params)
        body = res.text
        res.raise_for_status()
    except Exception as exc:
        last_errors[key] = f"{type(exc).__name__}: {str(exc)[:160]} | body={body[:200]!r}"
        raise AdapterError(str(exc)) from exc

    last_errors.pop(key, None)
    _cache[key] = (now, body)
    return body


def clear_cache() -> None:
    _cache.clear()
    last_errors.clear()
