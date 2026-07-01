import re
from html.parser import HTMLParser
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
README_KO = ROOT / "README.md"
README_EN = ROOT / "README.en.md"
HOME = ROOT / "app" / "home.html"
STORE_BADGE_ALTS = (
    "Get it on Google Play",
    "Get it on F-Droid",
    "Get it on Obtainium",
    "Get it on GitHub",
)

pytestmark = pytest.mark.no_db


class _ImageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.images: list[dict[str, str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "img":
            self.images.append({key: value or "" for key, value in attrs})


def _center_blocks(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    return re.findall(r'<p align="center">\n(.*?)\n</p>', text, flags=re.S)


def _img_attrs(block: str) -> list[tuple[str, str, str]]:
    images = _images(block)
    return [(image.get("alt", ""), image.get("src", ""), image.get("height", "")) for image in images]


def _images(block: str) -> list[dict[str, str]]:
    parser = _ImageParser()
    parser.feed(block)
    return parser.images


def _store_badges(path: Path) -> list[dict[str, str]]:
    for block in _center_blocks(path):
        badges = [image for image in _images(block) if image.get("alt") in STORE_BADGE_ALTS]
        if badges:
            assert [badge.get("alt") for badge in badges] == list(STORE_BADGE_ALTS)
            return badges
    raise AssertionError(f"store badge block not found in {path}")


def _top_banner(block: str) -> tuple[str, str]:
    match = re.search(r'<img src="([^"]+)" [^>]*width="([^"]+)"', block)
    assert match is not None
    return match.groups()


def test_readme_variants_use_the_same_top_banner() -> None:
    korean_blocks = _center_blocks(README_KO)
    english_blocks = _center_blocks(README_EN)

    expected = ("assets/readme/forestmate-readme-banner.png", "100%")
    assert _top_banner(korean_blocks[0]) == expected
    assert _top_banner(english_blocks[0]) == expected


def test_readme_variants_keep_shared_badges_in_sync() -> None:
    korean_blocks = _center_blocks(README_KO)
    english_blocks = _center_blocks(README_EN)

    assert _img_attrs(korean_blocks[1]) == _img_attrs(english_blocks[1])
    assert _img_attrs(korean_blocks[3]) == _img_attrs(english_blocks[3])


def test_readme_store_badges_share_one_display_height() -> None:
    for path in (README_KO, README_EN):
        store_badges = _store_badges(path)
        assert {badge.get("height") for badge in store_badges} == {"80"}


def test_homepage_store_badges_do_not_shrink_obtainium() -> None:
    home = HOME.read_text(encoding="utf-8")

    assert ".store-badge img{height:80px;" in home
    assert "store-badge--obtainium" not in home
    assert "height:54px" not in home
