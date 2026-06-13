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
from .base import AdapterError, fetch_json, service_key

KMA_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst"
# 산불위험예보 V2 (구버전 forestPoint는 폐기됨). 파라미터: ServiceKey(대문자)·localAreas.
FIRE_URL = "https://apis.data.go.kr/1400377/forestPointV2/forestPointListSigunguSearchV2"

# 기상청 단기예보(getVilageFcst) 발표시각 — 이 8개만 유효(매 3시간, 02시 시작).
_KMA_SLOTS = [2, 5, 8, 11, 14, 17, 20, 23]


def _kma_base(now: datetime | None = None) -> tuple[str, str]:
    """가장 최근의 유효 발표시각(base_date, base_time)을 계산.

    이전 코드는 (hour//3)*3 → 00·03·06…을 써서 '자료없음'이 떴다.
    발표 후 약 45분 뒤 자료가 안정적이라 now-45분 기준으로 직전 슬롯을 고른다.
    """
    t = (now or datetime.now()) - timedelta(minutes=45)
    for s in reversed(_KMA_SLOTS):
        if t.hour >= s:
            return t.strftime("%Y%m%d"), f"{s:02d}00"
    return (t - timedelta(days=1)).strftime("%Y%m%d"), "2300"  # 02:45 이전 → 전일 23시


def _snapshot(region_id: str) -> dict:
    return REGIONS[region_id]["snapshot"]


async def get_weather(region_id: str) -> dict:
    """기상 — 기온·풍속·강수확률·점수. source: live|snapshot."""
    settings = get_settings()
    region = REGIONS[region_id]
    if not settings.live_data:
        return {**_snapshot(region_id)["weather"], "source": "snapshot"}

    base_date, base_time = _kma_base()
    params = {
        "serviceKey": service_key(),
        "dataType": "JSON", "numOfRows": 1000, "pageNo": 1,
        "base_date": base_date, "base_time": base_time,
        "nx": region["nx"], "ny": region["ny"],
    }
    try:
        data = await fetch_json(KMA_URL, params, cache_key=f"kma:{region_id}")
        items = data["response"]["body"]["items"]["item"]
        # 가장 이른 예보시각의 값을 현재값 대용으로(카테고리별 최초 1건).
        vals: dict[str, str] = {}
        for it in sorted(items, key=lambda x: (x["fcstDate"], x["fcstTime"])):
            vals.setdefault(it["category"], it["fcstValue"])
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
        "ServiceKey": service_key(),
        "_type": "json", "numOfRows": 1, "pageNo": 1,
        "localAreas": region["sgg"], "excludeForecast": 0,
    }
    try:
        data = await fetch_json(FIRE_URL, params, cache_key=f"fire:{region_id}")
        item = data["response"]["body"]["items"]["item"]
        if isinstance(item, list):
            item = item[0]
        # 당일 산불위험지수(0~100). V2 응답 필드 변형 흡수.
        risk = 40
        for k in ("meanavg", "meanAvg", "d0", "d1", "today", "dangerLevel"):
            v = item.get(k)
            if v not in (None, ""):
                risk = int(float(v))
                break
        score = max(0, 100 - risk)
        level = "낮음" if risk < 51 else "보통" if risk < 66 else "높음" if risk < 86 else "매우 높음"
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
