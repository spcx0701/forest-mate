"""산림청 산정보(전국산정보표준데이터) 어댑터 — 전국 산 카탈로그.

엔드포인트(클라우드 도달 가능): apis.data.go.kr/1400000/service/cultureInfoService2/mntInfoOpenAPI2
  ※ 제공기관 자체 게이트웨이(openapi.forest.go.kr)는 해외/클라우드 IP에서 연결이
    막혀(ConnectTimeout) data.go.kr 표준 프록시(cultureInfoService2)를 사용한다.
  ※ 같은 data.go.kr 계정의 인증키로 '전국산정보표준데이터(15029183)' 활용신청 시 사용 가능.

요청: serviceKey, pageNo, numOfRows, (searchWrd). 응답: XML.
필드(가이드 v2.6): mntilistno·mntiname·mntiadd(소재지)·mntihigh(높이)·mntidetails
  ·mntisummary·mntitop·mntiadmin·mntiadminnum. 표준데이터 변형 필드명은 후보키로 흡수.
"""
import defusedxml.ElementTree as ET

from .base import AdapterError, fetch_text, service_key

MNT_INFO_URL = "http://apis.data.go.kr/1400000/service/cultureInfoService2/mntInfoOpenAPI2"

# 산정보 표준 프록시는 응답이 느리고 간헐적 ReadTimeout이 나 여유 타임아웃을 크게 둔다.
_TIMEOUT = 20.0


def _parse(xml_text: str) -> tuple[list[dict], int]:
    """응답 XML → (item 원시 dict 리스트, totalCount). 키/서비스 오류 시 AdapterError."""
    root = ET.fromstring(xml_text)
    code = root.findtext(".//resultCode") or root.findtext(".//returnReasonCode")
    if code and code.strip() not in ("00", "0"):
        msg = root.findtext(".//resultMsg") or root.findtext(".//returnAuthMsg") or code
        raise AdapterError(f"forest API resultCode={code.strip()} ({msg})")

    items: list[dict] = []
    for it in root.iter("item"):
        # 필드명 변형(mntInfoOpenAPI vs mntInfoOpenAPI2)을 모두 흡수 — 하위 태그 전체 포착.
        row = {child.tag.lower(): (child.text or "").strip() for child in it}
        if any(row.get(k) for k in ("mntiname", "mntnnm", "mntn_nm", "name")):
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
    xml_text = await fetch_text(MNT_INFO_URL, params,
                                cache_key=f"mnt:{search}:{page}:{rows}", timeout=_TIMEOUT)
    return _parse(xml_text)
