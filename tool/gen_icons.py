#!/usr/bin/env python3
"""零终端 — 程序化生成应用图标（终末地风格：白底 + 黑色直角框 + 等高线波点 + 故障方块）。

仓库不直接存放二进制图标，clone 后先运行本脚本：

    python tool/gen_icons.py

生成内容：
  composeApp/src/androidMain/res/mipmap-*/ic_launcher.png           (48/72/96/144/192)
  composeApp/src/androidMain/res/mipmap-*/ic_launcher_foreground.png (108/162/216/324/432)
  composeApp/src/commonMain/composeResources/drawable/icon.png       (512)
  composeApp/src/desktopMain/resources/icon.png                      (512)
  composeApp/src/desktopMain/resources/icon.ico                      (16..256)
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
BLACK = (10, 10, 12, 255)
WHITE = (255, 255, 255, 255)
GRAY = (200, 200, 204, 255)
YELLOW = (255, 214, 0, 255)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyhbd.ttc",      # 微软雅黑 Bold
    r"C:\Windows\Fonts\simhei.ttf",      # 黑体
    "/System/Library/Fonts/PingFang.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for p in FONT_CANDIDATES:
        if Path(p).exists():
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                continue
    # 最后兜底：matplotlib  bundled 字体目录里随便抓一个 CJK
    try:
        from matplotlib import font_manager
        for f in font_manager.fontManager.ttflist:
            if any(k in f.name for k in ("CJK", "Hei", "YaHei", "WenQuanYi", "Noto Sans SC")):
                return ImageFont.truetype(f.fname, size)
    except Exception:
        pass
    return ImageFont.load_default()


def draw_corner_brackets(d: ImageDraw.ImageDraw, s: int, inset: int, thick: int, arm: int):
    """四个黑色直角框。"""
    for cx, cy, dx, dy in [
        (inset, inset, 1, 1),
        (s - inset, inset, -1, 1),
        (inset, s - inset, 1, -1),
        (s - inset, s - inset, -1, -1),
    ]:
        d.rectangle([cx if dx > 0 else cx - thick, cy, cx + thick if dx > 0 else cx,
                     cy + arm * dy if dy > 0 else cy - arm],
                    fill=BLACK) if False else None
        # 竖臂
        x0 = cx if dx > 0 else cx - thick
        y0 = cy if dy > 0 else cy - arm
        d.rectangle([x0, y0, x0 + thick, y0 + arm], fill=BLACK)
        # 横臂
        x1 = cx if dx > 0 else cx - arm
        y1 = cy if dy > 0 else cy - thick
        d.rectangle([x1, y1, x1 + arm, y1 + thick], fill=BLACK)


def draw_waves(d: ImageDraw.ImageDraw, s: int):
    """等高线风格波点带：多条正弦曲线，用圆点描出。"""
    lines = 7
    for li in range(lines):
        base_y = s * 0.34 + li * s * 0.075
        amp = s * (0.020 + 0.008 * math.sin(li * 1.7))
        freq = 2.0 + li * 0.35
        step = max(2, s // 170)
        r = max(1, s // 340)
        for x in range(0, s + step, step):
            y = base_y + amp * math.sin(x / s * math.pi * freq + li * 0.9)
            d.ellipse([x - r, y - r, x + r, y + r], fill=GRAY)


def draw_glyph(base: Image.Image, ch: str, cx: float, cy: float, size: int,
               angle: float = 0.0, color=BLACK):
    """在 (cx, cy) 处画一个居中的粗体汉字，可旋转。"""
    f = load_font(size)
    tile = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
    td = ImageDraw.Draw(tile)
    td.text((size, size), ch, font=f, fill=color, anchor="mm", stroke_width=max(1, size // 40))
    if angle:
        tile = tile.rotate(angle, resample=Image.BICUBIC, center=(size, size))
    base.alpha_composite(tile, (int(cx - size), int(cy - size)))


def draw_glitch_block(base: Image.Image, x: int, y: int, w: int, h: int):
    """右下黑色故障方块 + //ZERO TERMINAL。"""
    d = ImageDraw.Draw(base)
    d.rectangle([x, y, x + w, y + h], fill=BLACK)
    # 像素毛边：四周随机小方块
    import random
    rnd = random.Random(7)
    u = max(3, w // 28)
    for _ in range(26):
        side = rnd.choice("ltrb")
        gx = rnd.randint(x - u, x + w)
        gy = rnd.randint(y - u, y + h)
        if side == "l":
            gx = x - u * rnd.randint(1, 2)
        elif side == "r":
            gx = x + w + u * rnd.randint(0, 1)
        elif side == "t":
            gy = y - u * rnd.randint(1, 2)
        else:
            gy = y + h + u * rnd.randint(0, 1)
        if rnd.random() < 0.75:
            d.rectangle([gx, gy, gx + u, gy + u], fill=BLACK)
    # 文字
    f1 = load_font(int(h * 0.20))
    f2 = load_font(int(h * 0.16))
    tx, ty = x + int(w * 0.12), y + int(h * 0.18)
    d.text((tx, ty), "//", font=f1, fill=YELLOW)
    slash_w = d.textlength("//", font=f1)
    d.text((tx + slash_w, ty), "ZERO", font=f1, fill=WHITE)
    d.text((tx, ty + int(h * 0.30)), "TERMINAL", font=f2, fill=WHITE)


def render(size: int) -> Image.Image:
    s = size
    img = Image.new("RGBA", (s, s), WHITE)
    d = ImageDraw.Draw(img)
    draw_waves(d, s)
    glyph = int(s * 0.34)
    draw_glyph(img, "零", s * 0.30, s * 0.26, glyph)
    draw_glyph(img, "终", s * 0.74, s * 0.24, glyph, angle=-4)
    draw_glyph(img, "端", s * 0.28, s * 0.68, glyph, angle=2)
    bw, bh = int(s * 0.40), int(s * 0.36)
    draw_glitch_block(img, s - bw - int(s * 0.08), s - bh - int(s * 0.10), bw, bh)
    d = ImageDraw.Draw(img)
    draw_corner_brackets(d, s, int(s * 0.035), int(s * 0.045), int(s * 0.17))
    return img


def render_foreground(size: int) -> Image.Image:
    """自适应图标前景：透明底，设计缩到中心 66% 安全区。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = render(int(size * 0.66))
    off = (size - inner.width) // 2
    img.alpha_composite(inner, (off, off))
    return img


def main():
    import sys
    force = "--force" in sys.argv
    targets = []
    master = render(512)
    out_common = ROOT / "composeApp/src/commonMain/composeResources/drawable/icon.png"
    out_desktop_png = ROOT / "composeApp/src/desktopMain/resources/icon.png"
    out_ico = ROOT / "composeApp/src/desktopMain/resources/icon.ico"
    targets += [(out_common, master), (out_desktop_png, master)]

    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, scale in densities.items():
        targets.append((ROOT / f"composeApp/src/androidMain/res/mipmap-{name}/ic_launcher.png",
                        render(int(48 * scale))))
        targets.append((ROOT / f"composeApp/src/androidMain/res/mipmap-{name}/ic_launcher_foreground.png",
                        render_foreground(int(108 * scale))))

    written = skipped = 0
    for path, img in targets:
        if path.exists() and not force:
            skipped += 1
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        written += 1
    if force or not out_ico.exists():
        master.save(out_ico, sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
        written += 1
    else:
        skipped += 1
    print(f"图标生成完成：写入 {written} 个，跳过已存在 {skipped} 个（--force 可强制覆盖）")


if __name__ == "__main__":
    main()
