"""공공데이터 어댑터 — 키가 있으면 실 API, 없거나 실패하면 스냅샷 폴백.

실 엔드포인트(공공데이터포털 활용신청 후 키 발급):
  - 기상청 단기예보:  apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst
  - 산불위험예보:     apis.data.go.kr/1400377/forestPoint/forestPointListSigunguSearch
  - 산악기상관측망:   apis.data.go.kr/1400377/mtweather/mountListSearch
계약(필드명)은 발급 후 응답 샘플로 1회 검증할 것 — 변경 시 이 모듈만 수정하면 된다.
모든 함수는 동일 스키마를 반환하므로 서비스 레이어는 출처를 신경 쓰지 않는다.
"""
from datetime import datetime, timedelta

from ..config import get_settings
from ..seed import REGIONS
from .base import AdapterError, fetch_json

KMA_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
FIRE_URL = "https://apis.data.go.kr/1400377/forestPoint/forestPointListSigunguSearch"


def _snapshot(region_id: str) -> dict:
    return REGIONS[region_id]["snapshot"]


async def get_weather(region_id: str) -> dict:
    """기상 — 기온·풍속·강수확률·점수. source: live|snapshot."""
    settings = get_settings()
    region = REGIONS[region_id]
    if not settings.live_data:
        return {**_snapshot(region_id)["weather"], "source": "snapshot"}

    base = datetime.now() - timedelta(hours=1)
    params = {
        "serviceKey": settings.data_go_kr_key,
        "dataType": "JSON", "numOfRows": 200, "pageNo": 1,
        "base_date": base.strftime("%Y%m%d"),
        "base_time": f"{(base.hour // 3) * 3:02d}00" or "0200",
        "nx": region["nx"], "ny": region["ny"],
    }
    try:
        data = await fetch_json(KMA_URL, params, cache_key=f"kma:{region_id}")
        items = data["response"]["body"]["items"]["item"]
        vals = {it["category"]: it["fcstValue"] for it in items}
        temp = float(vals.get("TMP", 18))
        wind = float(vals.get("WSD", 3))
        rain = int(vals.get("POP", 10))
        score = max(0, min(100, 100 - rain - max(0, (wind - 4)) * 5))
        label = "맑음" if rain < 30 else "비 예보"
        return {"temp": temp, "wind": wind, "rain_prob": rain, "label": label,
                "score": int(score), "station": "기상청 단기예보", "source": "live"}
    except (AdapterError, KeyError, ValueError):
        return {**_snapshot(region_id)["weather"], "source": "snapshot"}


async def get_fire_risk(region_id: str) -> dict:
    """산불위험지수 — 국립산림과학원 예보. source: live|snapshot."""
    settings = get_settings()
    region = REGIONS[region_id]
    if not settings.live_data:
        return {**_snapshot(region_id)["fire"], "source": "snapshot"}

    params = {
        "serviceKey": settings.data_go_kr_key,
        "_type": "json", "numOfRows": 1, "pageNo": 1,
        "localAreaCode": region["sgg"], "excludeForecast": 0,
    }
    try:
        data = await fetch_json(FIRE_URL, params, cache_key=f"fire:{region_id}")
        item = data["response"]["body"]["items"]["item"]
        if isinstance(item, list):
            item = item[0]
        d1 = int(float(item.get("d1", 40)))  # 오늘 위험지수(0~100, 높을수록 위험)
        score = max(0, 100 - d1)
        level = "낮음" if d1 < 51 else "보통" if d1 < 66 else "높음" if d1 < 86 else "매우 높음"
        return {"level": level, "score": score,
                "src": "국립산림과학원 산불위험예보", "source": "live"}
    except (AdapterError, KeyError, TypeError, ValueError):
        return {**_snapshot(region_id)["fire"], "source": "snapshot"}


async def get_landslide(region_id: str) -> dict:
    """산사태 위험등급 — 산사태정보시스템은 공간 레이어(WMS/SHP) 제공이라
    운영에서는 ETL로 구간별 등급을 사전 적재한다. 여기서는 적재 결과 스냅샷."""
    return {**_snapshot(region_id)["landslide"], "source": "etl"}


async def get_region_conditions(region_id: str) -> dict:
    if region_id not in REGIONS:
        raise KeyError(region_id)
    region = REGIONS[region_id]
    return {
        "region_id": region_id,
        "name": region["name"],
        "fire": await get_fire_risk(region_id),
        "landslide": await get_landslide(region_id),
        "weather": await get_weather(region_id),
        "sunset_at": region["sunset_at"],
        "sunset_score": region["snapshot"]["sunset_score"],
    }
