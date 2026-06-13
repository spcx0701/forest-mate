"""산림청 산정보 서비스(cultureInfoService) 어댑터 — 전국 3,368개 산.

활용가이드: OpenAPI활용가이드_산림청_산정보_v2.6
  - End Point: http://openapi.forest.go.kr/openapi/service/cultureInfoService
  - 오퍼레이션: mntInfoOpenAPI(산정보) / frtrlSectnOpenAPI(숲길구간) / mntInfoImgOpenAPI(이미지)
  - 요청: serviceKey, searchWrd(선택), pageNo, numOfRows
  - 응답(XML) item 필드: mntilistno·mntiname·mntisname·mntiadd(소재지)·mntihigh(높이)
    ·mntidetails·mntisummary·mntitop·mntiadmin·mntiadminnum·mntinfdt
좌표(위경도)는 본 서비스가 제공하지 않음 — 소재지(mntiadd)의 시·도/시군구로 지역을 분류한다.
"""
import xml.etree.ElementTree as ET

from .base import AdapterError, fetch_text, service_key

MNT_INFO_URL = "http://openapi.forest.go.kr/openapi/service/cultureInfoService/mntInfoOpenAPI"

_FIELDS = ("mntilistno", "mntiname", "mntisname", "mntiadd", "mntihigh",
           "mntidetails", "mntisummary", "mntitop", "mntiadmin", "mntiadminnum", "mntinfdt")


def _parse(xml_text: str) -> tuple[list[dict], int]:
    """응답 XML → (item dict 리스트, totalCount). 키 오류 시 AdapterError."""
    root = ET.fromstring(xml_text)
    # data.go.kr 표준 에러 봉투(<OpenAPI_ServiceResponse>) 또는 resultCode != 00 → 실패
    code = root.findtext(".//resultCode") or root.findtext(".//returnReasonCode")
    if code and code.strip() not in ("00", "0"):
        msg = root.findtext(".//resultMsg") or root.findtext(".//returnAuthMsg") or code
        raise AdapterError(f"forest API resultCode={code.strip()} ({msg})")

    items: list[dict] = []
    for it in root.iter("item"):
        row = {f: (it.findtext(f) or "").strip() for f in _FIELDS}
        if row["mntiname"]:
            items.append(row)
    total_txt = root.findtext(".//totalCount")
    total = int(total_txt) if total_txt and total_txt.strip().isdigit() else len(items)
    return items, total


async def fetch_mountains(page: int = 1, rows: int = 100, search: str = "") -> tuple[list[dict], int]:
    """산정보 한 페이지 조회. (items, totalCount) 반환."""
    params = {
        "serviceKey": service_key(),
        "pageNo": page, "numOfRows": rows, "searchWrd": search,
    }
    # openapi.forest.go.kr 게이트웨이는 연결이 느려 기본 3초로는 ConnectTimeout이 난다.
    xml_text = await fetch_text(MNT_INFO_URL, params,
                                cache_key=f"mnt:{search}:{page}:{rows}", timeout=12.0)
    return _parse(xml_text)
