"""README 히어로 — 실제 앱 스크린샷을 안드로이드 폰 프레임에 합성.

`app/screens/*.png`(실제 캡처)를 둥근 베젤·펀치홀 카메라가 있는 폰 목업으로 감싸
연보라 배경 위에 3대 나열(Now in Android README 스타일). 산출물:
  assets/readme/forestmate-hero.png  (README 상단 배너)
스크린샷을 새로 캡처해 교체한 뒤 이 스크립트를 다시 실행하면 갱신된다.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "assets" / "readme" / "forestmate-hero.png"
SHOTS = ["home", "ai", "sos"]          # 현재도 정확한 대표 화면 3종
SCREEN_H = 980
BG = (244, 238, 251)                   # 연보라 (Now in Android 톤)
BEZEL = 16


def phone(shot_path: Path, screen_h: int) -> Image.Image:
    shot = Image.open(shot_path).convert("RGB")
    sw, sh = shot.size
    screen_w = round(screen_h * sw / sh)
    shot = shot.resize((screen_w, screen_h), Image.LANCZOS)
    rad = 48
    mask = Image.new("L", (screen_w, screen_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, screen_w - 1, screen_h - 1], radius=rad, fill=255)

    pw, ph = screen_w + BEZEL * 2, screen_h + BEZEL * 2
    img = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, pw - 1, ph - 1], radius=rad + BEZEL, fill=(20, 30, 24, 255))  # 다크 베젤
    img.paste(shot, (BEZEL, BEZEL), mask)
    # 펀치홀 카메라
    cx, cy, cr = BEZEL + screen_w // 2, BEZEL + 26, 10
    d.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=(36, 46, 40, 255))
    return img


def main() -> None:
    phones = [phone(ROOT / "app" / "screens" / f"{s}.png", SCREEN_H) for s in SHOTS]
    pw, ph = phones[0].size
    gap, mx, my = 56, 80, 96
    y_off = [28, 0, 28]                 # 가운데 폰을 살짝 위로 — 아치형 배치
    W = mx * 2 + pw * 3 + gap * 2
    H = my * 2 + ph + max(y_off)

    canvas = Image.new("RGBA", (W, H), BG + (255,))
    x = mx
    for img, yo in zip(phones, y_off):
        y = my + yo
        shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle(
            [x + 4, y + 22, x + pw + 4, y + ph + 22], radius=66, fill=(60, 38, 96, 70))
        canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(26)))
        canvas.alpha_composite(img, (x, y))
        x += pw + gap

    OUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUT, optimize=True)
    print(f"saved {OUT.relative_to(ROOT)}  {canvas.size}  {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
