"""등산로 선(line) 베이크 — FRT000801(EPSG:5179) → 산코드별 경로 JSON.

산출물: server/data/trails.json — { MNTN_CODE: {name, segs:[{nm,dffl,lt,pts:[[lat,lon]...]}]} }
좌표는 WGS84·5자리(약 1m) 반올림 + 점 솎기(세그먼트당 최대 ~25점)로 경량화.
카탈로그(list_no)와 코드가 일치하는 산만 포함. 서버가 /mountains/{code}/trails로 제공.
"""
import json
from pathlib import Path

import shapefile
from pyproj import Transformer

ROOT = Path(__file__).resolve().parents[1]
SHP = "/Users/dong9733/Downloads/_fmway/TB_FGDI_WG_MT_WAY_ALL"
OUTDIR = ROOT / "server" / "data" / "trails"   # 산코드별 개별 파일(요청 시에만 읽음)
TF = Transformer.from_crs("EPSG:5179", "EPSG:4326", always_xy=True)


def decimate(pts, cap=25):
    """폴리라인 점 솎기 — 처음/끝 보존, 균등 간격으로 최대 cap개."""
    if len(pts) <= cap:
        return pts
    step = (len(pts) - 1) / (cap - 1)
    out = [pts[round(i * step)] for i in range(cap)]
    return out


def main():
    cat = {c["list_no"] for c in json.load(open(ROOT / "server" / "data" / "catalog.json"))}
    r = shapefile.Reader(SHP, encoding="cp949")
    fld = [f[0] for f in r.fields[1:]]
    out: dict = {}
    seg_n = 0
    for i in range(len(r)):
        d = dict(zip(fld, r.record(i)))
        code = d["MNTN_CODE"]
        if code not in cat:
            continue
        pts5179 = r.shape(i).points
        if len(pts5179) < 2:
            continue
        pts = []
        for x, y in decimate(pts5179):
            lon, lat = TF.transform(x, y)
            pts.append([round(lat, 5), round(lon, 5)])
        entry = out.setdefault(code, {"name": d["MNTN_NM"], "segs": []})
        entry["segs"].append({
            "nm": d.get("PMNTN_NM", ""), "dffl": d.get("PMNTN_DFFL", ""),
            "lt": d.get("PMNTN_LT", 0), "pts": pts,
        })
        seg_n += 1

    OUTDIR.mkdir(parents=True, exist_ok=True)
    for old in OUTDIR.glob("*.json"):
        old.unlink()
    total = 0
    for code, entry in out.items():
        f = OUTDIR / f"{code}.json"
        f.write_text(json.dumps(entry, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        total += f.stat().st_size
    print(f"산(코드): {len(out)}  세그먼트: {seg_n}  파일 {len(out)}개  합계 {total / 1024 / 1024:.1f} MB → {OUTDIR.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
