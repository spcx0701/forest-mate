"""공공데이터 어댑터 공통 — TTL 캐시 + 타임아웃 + 폴백 신호."""
import time
from typing import Any

import httpx

from ..config import get_settings


class AdapterError(Exception):
    """외부 API 실패 — 서비스 레이어가 스냅샷으로 폴백한다."""


_cache: dict[str, tuple[float, Any]] = {}


async def fetch_json(url: str, params: dict, cache_key: str | None = None) -> Any:
    settings = get_settings()
    key = cache_key or f"{url}|{sorted(params.items())!r}"
    now = time.monotonic()

    hit = _cache.get(key)
    if hit and now - hit[0] < settings.adapter_cache_ttl_s:
        return hit[1]

    try:
        async with httpx.AsyncClient(timeout=settings.adapter_timeout_s) as client:
            res = await client.get(url, params=params)
            res.raise_for_status()
            data = res.json()
    except Exception as exc:  # 네트워크·파싱·4xx/5xx 모두 폴백 대상
        raise AdapterError(str(exc)) from exc

    _cache[key] = (now, data)
    return data


def clear_cache() -> None:
    _cache.clear()
