"""산 위치 → 기상 격자(nx/ny)·산불 시군구코드 해석.

산정보 카탈로그에는 좌표가 없어, 산의 소재지(시·도)를 17개 시도 대표지점으로
매핑한다. 대표지점의 위경도는 KMA 단기예보 LCC 격자공식으로 nx/ny를 산출하고
(좌표를 지어내지 않음), 산불은 대표 시군구 행정코드(localAreas)를 쓴다.
시도보다 더 정밀한 지점 좌표가 없으므로 결과는 '시도 대표지점 추정'으로 표기한다.
"""
import math

# ── KMA 단기예보 격자(Lambert Conformal Conic) 변환 — 기상청 공식 파라미터 ──
_RE, _GRID = 6371.00877, 5.0          # 지구반경(km), 격자간격(km)
_SLAT1, _SLAT2 = 30.0, 60.0           # 표준위도 1·2
_OLON, _OLAT = 126.0, 38.0            # 기준점 경도·위도
_XO, _YO = 43, 136                    # 기준점 격자 X·Y


def dfs_xy_conv(lat: float, lon: float) -> tuple[int, int]:
    """위경도 → 단기예보 격자 (nx, ny). (검증: 서울 60,127 / 은평 59,127)"""
    DEGRAD = math.pi / 180.0
    re = _RE / _GRID
    slat1, slat2 = _SLAT1 * DEGRAD, _SLAT2 * DEGRAD
    olon, olat = _OLON * DEGRAD, _OLAT * DEGRAD
    sn = math.tan(math.pi * 0.25 + slat2 * 0.5) / math.tan(math.pi * 0.25 + slat1 * 0.5)
    sn = math.log(math.cos(slat1) / math.cos(slat2)) / math.log(sn)
    sf = math.tan(math.pi * 0.25 + slat1 * 0.5)
    sf = sf ** sn * math.cos(slat1) / sn
    ro = math.tan(math.pi * 0.25 + olat * 0.5)
    ro = re * sf / ro ** sn
    ra = math.tan(math.pi * 0.25 + lat * DEGRAD * 0.5)
    ra = re * sf / ra ** sn
    theta = lon * DEGRAD - olon
    if theta > math.pi:
        theta -= 2.0 * math.pi
    if theta < -math.pi:
        theta += 2.0 * math.pi
    theta *= sn
    nx = int(ra * math.sin(theta) + _XO + 0.5)
    ny = int(ro - ra * math.cos(theta) + _YO + 0.5)
    return nx, ny


# ── 17개 시도 대표지점(시·도청 소재지 좌표 + 대표 시군구 행정코드) ──
SIDO_REF: dict[str, dict] = {
    "서울": {"name": "서울", "lat": 37.5663, "lon": 126.9779, "sgg": "11140"},
    "부산": {"name": "부산", "lat": 35.1798, "lon": 129.0750, "sgg": "26470"},
    "대구": {"name": "대구", "lat": 35.8714, "lon": 128.6014, "sgg": "27110"},
    "인천": {"name": "인천", "lat": 37.4563, "lon": 126.7052, "sgg": "28200"},
    "광주": {"name": "광주", "lat": 35.1601, "lon": 126.8514, "sgg": "29155"},
    "대전": {"name": "대전", "lat": 36.3504, "lon": 127.3845, "sgg": "30170"},
    "울산": {"name": "울산", "lat": 35.5384, "lon": 129.3114, "sgg": "31140"},
    "세종": {"name": "세종", "lat": 36.4801, "lon": 127.2890, "sgg": "36110"},
    "경기": {"name": "경기", "lat": 37.2636, "lon": 127.0286, "sgg": "41110"},
    "강원": {"name": "강원", "lat": 37.8813, "lon": 127.7298, "sgg": "51110"},
    "충북": {"name": "충북", "lat": 36.6357, "lon": 127.4914, "sgg": "43110"},
    "충남": {"name": "충남", "lat": 36.6588, "lon": 126.6728, "sgg": "44800"},
    "전북": {"name": "전북", "lat": 35.8203, "lon": 127.1088, "sgg": "52110"},
    "전남": {"name": "전남", "lat": 34.8161, "lon": 126.4629, "sgg": "46840"},
    "경북": {"name": "경북", "lat": 36.5760, "lon": 128.5056, "sgg": "47170"},
    "경남": {"name": "경남", "lat": 35.2383, "lon": 128.6924, "sgg": "48120"},
    "제주": {"name": "제주", "lat": 33.4890, "lon": 126.4983, "sgg": "50110"},
}

# 소재지 문자열 변형 → 표준 시도 키 (긴 별칭부터 매칭).
_SIDO_ALIASES = [
    ("서울", "서울"), ("부산", "부산"), ("대구", "대구"), ("인천", "인천"),
    ("광주", "광주"), ("대전", "대전"), ("울산", "울산"), ("세종", "세종"),
    ("경기", "경기"), ("강원", "강원"),
    ("충청북도", "충북"), ("충북", "충북"), ("충청남도", "충남"), ("충남", "충남"),
    ("전라북도", "전북"), ("전북", "전북"), ("전라남도", "전남"), ("전남", "전남"),
    ("경상북도", "경북"), ("경북", "경북"), ("경상남도", "경남"), ("경남", "경남"),
    ("제주", "제주"),
]

_DEFAULT_SNAPSHOT = {
    "fire": {"level": "낮음", "score": 88, "src": "국립산림과학원 산불위험예보(스냅샷)"},
    "landslide": {"grade": 5, "label": "안전", "score": 92},
    "weather": {"temp": 18.0, "wind": 3.5, "rain_prob": 15, "label": "맑음",
                "score": 82, "station": "기상청 단기예보(추정)"},
    "sunset_score": 45,
}


def normalize_sido(text: str) -> str:
    """소재지/시도 문자열 → 표준 시도 키. 미상이면 '서울'(폴백)."""
    s = (text or "").strip()
    for alias, key in _SIDO_ALIASES:
        if s.startswith(alias) or alias in s:
            return key
    return "서울"


def region_for_mountain(m) -> dict:
    """Mountain → conditions 계산용 ad-hoc region dict (시도 대표지점)."""
    key = normalize_sido(m.sido or m.addr)
    ref = SIDO_REF[key]
    nx, ny = dfs_xy_conv(ref["lat"], ref["lon"])
    return {
        "id": f"sido:{key}",                       # 동일 시도는 캐시 공유
        "name": f"{ref['name']} 대표지점",
        "nx": nx, "ny": ny, "sgg": ref["sgg"],
        "sunset_at": "19:30",
        "snapshot": _DEFAULT_SNAPSHOT,
    }
