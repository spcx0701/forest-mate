"""전국 산 카탈로그(영속) 빌드 — 1회성 로컬 ETL.

산출물: server/data/catalog.json — 산정보(이름·주소·높이·소개) + VWorld 지오코딩
좌표(lat/lon)·시군구코드(sgg) + 등산로 주요지점 시설 요약.

소스:
  - 라이브 API https://forestmate.onrender.com/api/v1/mountains (산정보 적재분, 페이지)
  - VWorld getcoord (주소→좌표+법정동코드) — 동명이산을 주소로 구분
  - 등산로 주요지점 shapefile(FRT000901, EPSG:5179) — 산별 대피소·조망점 등 시설 집계

운영(서버)은 이 JSON을 시작 시 적재하므로 재배포에도 전량 영속.
"""
import asyncio
import collections
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "server" / "data" / "catalog.json"
SHP = "/Users/dong9733/Downloads/_fmwork/TB_FGDI_WG_MT_WAY_PT_ALL"

API = "https://forestmate.onrender.com/api/v1/mountains"
VWORLD = "https://api.vworld.kr/req/address"
KEY = "4746F25E-1B66-3478-98B2-7441EEB9E2A3"

# 시도 대표지점(지오코딩 실패 시 폴백) — server/geo.py와 동일 좌표.
SIDO_CENTROID = {
    "서울": (37.5663, 126.9779, "11140"), "부산": (35.1798, 129.0750, "26470"),
    "대구": (35.8714, 128.6014, "27110"), "인천": (37.4563, 126.7052, "28200"),
    "광주": (35.1601, 126.8514, "29155"), "대전": (36.3504, 127.3845, "30170"),
    "울산": (35.5384, 129.3114, "31140"), "세종": (36.4801, 127.2890, "36110"),
    "경기": (37.2636, 127.0286, "41110"), "강원": (37.8813, 127.7298, "51110"),
    "충북": (36.6357, 127.4914, "43110"), "충남": (36.6588, 126.6728, "44800"),
    "전북": (35.8203, 127.1088, "52110"), "전남": (34.8161, 126.4629, "46840"),
    "경북": (36.5760, 128.5056, "47170"), "경남": (35.2383, 128.6924, "48120"),
    "제주": (33.4890, 126.4983, "50110"),
}
_ALIAS = [("서울", "서울"), ("부산", "부산"), ("대구", "대구"), ("인천", "인천"),
          ("광주", "광주"), ("대전", "대전"), ("울산", "울산"), ("세종", "세종"),
          ("경기", "경기"), ("강원", "강원"), ("충청북", "충북"), ("충북", "충북"),
          ("충청남", "충남"), ("충남", "충남"), ("전라북", "전북"), ("전북", "전북"),
          ("전라남", "전남"), ("전남", "전남"), ("경상북", "경북"), ("경북", "경북"),
          ("경상남", "경남"), ("경남", "경남"), ("제주", "제주")]


@dataclass
class EnrichContext:
    feats: dict[str, dict]
    prev: dict[str, dict]
    sem: asyncio.Semaphore
    client: httpx.AsyncClient
    done: list[int]
    geocoded: list[int]
    total: int


def sido_key(text: str) -> str:
    s = (text or "").strip()
    for a, k in _ALIAS:
        if s.startswith(a) or a in s:
            return k
    return "서울"


async def _get_page(client: httpx.AsyncClient, page: int) -> dict:
    """페이지 조회 — 무료티어 콜드스타트 대비 재시도(백오프)."""
    last = None
    for n in range(5):
        try:
            r = await client.get(API, params={"page": page, "size": 100}, timeout=90)
            return r.json()
        except Exception as e:  # noqa: BLE001
            last = e
            print(f"  page {page} 재시도 {n + 1}/5 ({type(e).__name__})")
            await asyncio.sleep(3 * (n + 1))
    raise last


async def fetch_catalog(client: httpx.AsyncClient) -> list[dict]:
    print("  서버 워밍업…")
    try:
        await client.get(API.replace("/mountains", "/healthz"), timeout=90)
    except httpx.HTTPError as e:
        print(f"  워밍업 실패 무시 ({type(e).__name__})")
    out, page = [], 1
    while True:
        d = await _get_page(client, page)
        out.extend(d["items"])
        if page * 100 >= d["total"] or not d["items"]:
            break
        page += 1
    return out


async def _try_addr(client: httpx.AsyncClient, addr: str) -> tuple[float, float, str] | None:
    """단일 주소 1회 시도. OK면 좌표, NOT_FOUND면 None, 일시장애(502 등)면 예외."""
    r = await client.get(VWORLD, params={
        "service": "address", "request": "getcoord", "version": "2.0",
        "crs": "epsg:4326", "type": "parcel", "address": addr,
        "format": "json", "key": KEY}, timeout=15)
    if r.status_code >= 500:
        raise RuntimeError(f"vworld {r.status_code}")   # 일시장애 → 재시도 대상
    d = r.json()["response"]
    if d.get("status") == "OK":
        p = d["result"]["point"]
        code = (d["refined"]["structure"].get("level4LC") or "")[:5]
        return round(float(p["y"]), 6), round(float(p["x"]), 6), code
    return None                                          # NOT_FOUND → 다음 후보


async def geocode(client: httpx.AsyncClient, addr: str) -> tuple[float, float, str] | None:
    """주소 → (lat, lon, sgg5). 전체→축약 후보, 일시장애는 백오프 재시도."""
    parts = addr.split()
    cands = [addr]
    if len(parts) >= 3:
        cands.append(" ".join(parts[:3]))
    cands.append(" ".join(parts[:2]))
    for cand in cands:
        for attempt in range(4):                         # 502 등 일시장애 재시도
            try:
                res = await _try_addr(client, cand)
                if res:
                    return res
                break                                    # NOT_FOUND → 다음 후보
            except Exception:
                await asyncio.sleep(1.0 * (attempt + 1))
    return None


def shapefile_features() -> dict[str, dict]:
    """산이름 → {대피소·조망점·정상·위험지역·헬기장·화장실·약수터 카운트}."""
    try:
        import shapefile
    except ImportError:
        print("  (pyshp 없음 — 시설 보강 생략)")
        return {}
    r = shapefile.Reader(SHP, encoding="cp949")
    flds = [f[0] for f in r.fields[1:]]
    KEEP = {"대피소", "정상", "조망점", "위험지역", "헬기장", "화장실", "음수대", "약수터"}
    feat: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
    for i in range(len(r)):
        d = dict(zip(flds, r.record(i)))
        nm, sp = d.get("MNTN_NM"), d.get("SAFE_SPOT2")
        if nm and sp in KEEP:
            feat[nm][sp] += 1
    return {k: dict(v) for k, v in feat.items()}


def _is_precise(row: dict, centroids: set[tuple[float, float]]) -> bool:
    """기존 catalog 행이 VWorld 정밀좌표인지 판정."""
    if row.get("geo") == "vworld":
        return True
    return (
        row.get("geo") is None
        and row.get("lat") is not None
        and (row["lat"], row["lon"]) not in centroids
    )


def load_previous_precise() -> dict[str, dict]:
    """이전 결과에서 재사용 가능한 정밀 좌표만 로드."""
    centroids = {(c[0], c[1]) for c in SIDO_CENTROID.values()}
    prev: dict[str, dict] = {}
    if not OUT.exists():
        return prev
    for row in json.loads(OUT.read_text(encoding="utf-8")):
        if _is_precise(row, centroids):
            row["geo"] = "vworld"
            prev[row["list_no"]] = row
    return prev


def base_row(mountain: dict, feats: dict[str, dict]) -> dict:
    return {
        "list_no": mountain["list_no"],
        "name": mountain["name"],
        "addr": mountain.get("addr", ""),
        "sido": mountain.get("sido", ""),
        "height": mountain.get("height", 0),
        "top100": mountain.get("top100", False),
        "summary": (mountain.get("summary") or "")[:600],
        "facilities": feats.get(mountain["name"], {}),
    }


def apply_previous(base: dict, mountain: dict, prev: dict[str, dict]) -> bool:
    if mountain["list_no"] not in prev:
        return False
    saved = prev[mountain["list_no"]]
    base.update(lat=saved["lat"], lon=saved["lon"], sgg=saved["sgg"], geo="vworld")
    return True


def apply_geocode_or_fallback(base: dict, mountain: dict, g: tuple[float, float, str] | None) -> bool:
    if g:
        base.update(lat=g[0], lon=g[1], sgg=g[2], geo="vworld")
        return True
    key = sido_key(mountain.get("sido") or mountain.get("addr") or "")
    centroid = SIDO_CENTROID[key]
    base.update(lat=centroid[0], lon=centroid[1], sgg=centroid[2], geo="sido")
    return False


async def enrich_mountain(mountain: dict, ctx: EnrichContext) -> dict:
    base = base_row(mountain, ctx.feats)
    if apply_previous(base, mountain, ctx.prev):
        ctx.done[0] += 1
        return base
    async with ctx.sem:
        g = await geocode(ctx.client, mountain["addr"]) if mountain.get("addr") else None
        await asyncio.sleep(0.05)
    if apply_geocode_or_fallback(base, mountain, g):
        ctx.geocoded[0] += 1
    ctx.done[0] += 1
    if ctx.done[0] % 200 == 0:
        print(f"  진행 {ctx.done[0]}/{ctx.total} (geocoded={ctx.geocoded[0]})")
    return base


async def main() -> None:
    feats = shapefile_features()
    print(f"shapefile 시설 보유 산: {len(feats)}")
    # 이어받기 — 이전 결과의 정밀 좌표 재사용(VWorld 호출 절약).
    # 구버전(geo 필드 없음)은 좌표가 시도 중심점이 아니면 정밀로 간주.
    prev = load_previous_precise()
    print(f"이전 정밀좌표 재사용: {len(prev)}개")
    sem = asyncio.Semaphore(3)                 # 저부하 — VWorld 과부하 방지
    done = [0]
    geocoded = [len(prev)]

    async with httpx.AsyncClient() as client:
        cat = await fetch_catalog(client)
        print(f"카탈로그 수신: {len(cat)}개")
        ctx = EnrichContext(feats, prev, sem, client, done, geocoded, len(cat))
        rows = await asyncio.gather(*(
            enrich_mountain(m, ctx)
            for m in cat
        ))
    precise = sum(1 for r in rows if r["geo"] == "vworld")
    miss = sum(1 for r in rows if r["geo"] == "sido")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
    print(f"\n저장: {OUT.relative_to(ROOT)}  ({OUT.stat().st_size // 1024} KB)")
    print(f"정밀(vworld)={precise}  시도폴백={miss}  "
          f"시설보유={sum(1 for r in rows if r['facilities'])}")


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
