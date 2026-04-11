"""
Rebuild the Vozcribe macOS app icon so it matches Apple's visual language:
squircle tile, inset artwork, subtle gradient, drop shadow.

Reuses the existing waveform glyph from the current 1024 PNG.
Outputs all sizes the AppIcon.appiconset Contents.json references.
"""

import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_DIR = os.path.join(
    ROOT, "VoxType", "Assets.xcassets", "AppIcon.appiconset"
)
SRC = os.path.join(ASSET_DIR, "icon_1024x1024.png")

CANVAS = 1024
TILE = 824
INSET = (CANVAS - TILE) // 2
RADIUS = 185  # ~22.4% of TILE — matches Apple's macOS squircle curvature

TOP = (214, 92, 64)
BOTTOM = (158, 52, 32)

GLYPH_SCALE = 0.60


def tint(alpha, color):
    """Build an RGBA image of solid `color` masked by `alpha`."""
    size = alpha.size
    r, g, b = color
    return Image.merge("RGBA", (
        Image.new("L", size, r),
        Image.new("L", size, g),
        Image.new("L", size, b),
        alpha,
    ))


def make_gradient(size, top, bottom):
    img = Image.new("RGB", (size, size), top)
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        r = round(top[0] + (bottom[0] - top[0]) * t)
        g = round(top[1] + (bottom[1] - top[1]) * t)
        b = round(top[2] + (bottom[2] - top[2]) * t)
        d.line([(0, y), (size, y)], fill=(r, g, b))
    return img


def make_squircle_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=radius, fill=255
    )
    return mask


def extract_glyph(src_path, out_size):
    gray = Image.open(src_path).convert("L").resize(
        (out_size, out_size), Image.LANCZOS
    )
    # White glyph ~255, orange background ~120-140; threshold then boost.
    alpha = gray.point(
        lambda p: 0 if p < 150 else min(255, int((p - 150) * 2.8))
    )
    return tint(alpha, (255, 255, 255))


def top_highlight(size, mask):
    hl = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(hl)
    band = size // 2
    for y in range(band):
        a = int(55 * (1 - y / band) ** 2)
        d.line([(0, y), (size, y)], fill=a)
    clipped = Image.new("L", (size, size), 0)
    clipped.paste(hl, (0, 0), mask)
    return tint(clipped, (255, 255, 255))


def build_tile():
    mask = make_squircle_mask(TILE, RADIUS)
    bg = make_gradient(TILE, TOP, BOTTOM)

    tile = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    tile.paste(bg, (0, 0))
    tile.putalpha(mask)

    tile = Image.alpha_composite(tile, top_highlight(TILE, mask))

    glyph_size = int(TILE * GLYPH_SCALE)
    glyph = extract_glyph(SRC, glyph_size)
    gx = gy = (TILE - glyph_size) // 2

    shadow_alpha = Image.new("L", (TILE, TILE), 0)
    shadow_alpha.paste(
        glyph.split()[3].point(lambda p: int(p * 0.35)),
        (gx, gy + 6),
    )
    shadow_alpha = shadow_alpha.filter(ImageFilter.GaussianBlur(radius=10))
    shadow_clipped = Image.new("L", (TILE, TILE), 0)
    shadow_clipped.paste(shadow_alpha, (0, 0), mask)

    tile = Image.alpha_composite(tile, tint(shadow_clipped, (0, 0, 0)))
    tile.paste(glyph, (gx, gy), glyph)
    return tile


def build_canvas():
    outer_alpha = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(outer_alpha).rounded_rectangle(
        [INSET, INSET, INSET + TILE - 1, INSET + TILE - 1],
        radius=RADIUS, fill=110,
    )
    outer_alpha = outer_alpha.filter(ImageFilter.GaussianBlur(radius=22))
    shifted = Image.new("L", (CANVAS, CANVAS), 0)
    shifted.paste(outer_alpha, (0, 14))

    canvas = tint(shifted, (0, 0, 0))
    tile = build_tile()
    canvas.paste(tile, (INSET, INSET), tile)
    return canvas


def main():
    master = build_canvas()
    sizes = [1024, 512, 256, 128, 64, 32, 16]
    for s in sizes:
        img = master.resize((s, s), Image.LANCZOS)
        out = os.path.join(ASSET_DIR, f"icon_{s}x{s}.png")
        img.save(out, "PNG")
        print(f"wrote {out}")


if __name__ == "__main__":
    main()
