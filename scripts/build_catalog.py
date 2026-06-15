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


async def main() -> None:
    feats = shapefile_features()
    print(f"shapefile 시설 보유 산: {len(feats)}")
    # 이어받기 — 이전 결과의 정밀 좌표 재사용(VWorld 호출 절약).
    # 구버전(geo 필드 없음)은 좌표가 시도 중심점이 아니면 정밀로 간주.
    centroids = {(c[0], c[1]) for c in SIDO_CENTROID.values()}
    prev: dict[str, dict] = {}
    if OUT.exists():
        for r in json.loads(OUT.read_text(encoding="utf-8")):
            precise = r.get("geo") == "vworld" or (
                r.get("geo") is None and r.get("lat") is not None
                and (r["lat"], r["lon"]) not in centroids)
            if precise:
                r["geo"] = "vworld"
                prev[r["list_no"]] = r
    print(f"이전 정밀좌표 재사용: {len(prev)}개")
    sem = asyncio.Semaphore(3)                 # 저부하 — VWorld 과부하 방지
    done = [0]
    geocoded = [len(prev)]

    async with httpx.AsyncClient() as client:
        cat = await fetch_catalog(client)
        print(f"카탈로그 수신: {len(cat)}개")

        async def enrich(m: dict) -> dict:
            base = {
                "list_no": m["list_no"], "name": m["name"], "addr": m.get("addr", ""),
                "sido": m.get("sido", ""), "height": m.get("height", 0),
                "top100": m.get("top100", False), "summary": (m.get("summary") or "")[:600],
                "facilities": feats.get(m["name"], {}),
            }
            if m["list_no"] in prev:           # 이미 정밀 — 메타만 갱신
                p = prev[m["list_no"]]
                base.update(lat=p["lat"], lon=p["lon"], sgg=p["sgg"], geo="vworld")
                done[0] += 1
                return base
            async with sem:
                g = await geocode(client, m["addr"]) if m.get("addr") else None
                await asyncio.sleep(0.05)
            if g:
                base.update(lat=g[0], lon=g[1], sgg=g[2], geo="vworld")
                geocoded[0] += 1
            else:
                k = sido_key(m.get("sido") or m.get("addr") or "")
                c = SIDO_CENTROID[k]
                base.update(lat=c[0], lon=c[1], sgg=c[2], geo="sido")
            done[0] += 1
            if done[0] % 200 == 0:
                print(f"  진행 {done[0]}/{len(cat)} (geocoded={geocoded[0]})")
            return base

        rows = await asyncio.gather(*(enrich(m) for m in cat))
    precise = sum(1 for r in rows if r["geo"] == "vworld")
    miss = sum(1 for r in rows if r["geo"] == "sido")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
    print(f"\n저장: {OUT.relative_to(ROOT)}  ({OUT.stat().st_size // 1024} KB)")
    print(f"정밀(vworld)={precise}  시도폴백={miss}  "
          f"시설보유={sum(1 for r in rows if r['facilities'])}")


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
