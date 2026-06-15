import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
README_KO = ROOT / "README.md"
README_EN = ROOT / "README.en.md"


def _center_blocks(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    return re.findall(r'<p align="center">\n(.*?)\n</p>', text, flags=re.S)


def _img_attrs(block: str) -> list[tuple[str, str, str]]:
    return re.findall(
        r'<img alt="([^"]+)" src="([^"]+)"(?: height="([^"]+)")?',
        block,
    )


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
