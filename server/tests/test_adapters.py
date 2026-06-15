import pytest
from defusedxml.common import EntitiesForbidden

from server.adapters.base import AdapterError
from server.adapters.mountains import _parse


def test_mountain_xml_parse_extracts_items_and_total():
    xml = """
    <response>
      <header><resultCode>00</resultCode></header>
      <body>
        <items>
          <item>
            <mntilistno>1</mntilistno>
            <mntiname>북한산</mntiname>
            <mntiadd>서울특별시</mntiadd>
          </item>
        </items>
        <totalCount>1</totalCount>
      </body>
    </response>
    """

    items, total = _parse(xml)

    assert total == 1
    assert items == [{"mntilistno": "1", "mntiname": "북한산", "mntiadd": "서울특별시"}]


def test_mountain_xml_parse_raises_adapter_error_on_api_error():
    xml = """
    <response>
      <header>
        <resultCode>99</resultCode>
        <resultMsg>bad key</resultMsg>
      </header>
    </response>
    """

    with pytest.raises(AdapterError, match="resultCode=99"):
        _parse(xml)


def test_mountain_xml_parse_rejects_xml_entities():
    xml = """<?xml version="1.0"?>
    <!DOCTYPE root [
      <!ENTITY unsafe SYSTEM "file:///etc/passwd">
    ]>
    <response><body><items><item><mntiname>&unsafe;</mntiname></item></items></body></response>
    """

    with pytest.raises(EntitiesForbidden):
        _parse(xml)
