"""Draws the Skyhop icon set in code (same hopper as the game). No source images."""
from PIL import Image, ImageDraw, ImageFilter
import math, os

def hopper(size, bg=True, pad=0.0):
    S = size
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if bg:
        # sky gradient background with rounded corners
        grad = Image.new("RGBA", (S, S))
        gd = ImageDraw.Draw(grad)
        for y in range(S):
            t = y / S
            r = int(30 + (79 - 30) * t); g = int(58 + (143 - 58) * t); b = int(110 + (214 - 110) * t)
            gd.line([(0, y), (S, y)], fill=(r, g, b, 255))
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
        img.paste(grad, (0, 0), mask)
        d = ImageDraw.Draw(img)
        # clouds
        for cx, cy, cs in [(0.22, 0.30, 0.10), (0.78, 0.24, 0.08), (0.70, 0.78, 0.09)]:
            for ox, oy, rr in [(0, 0, 1.0), (1.1, -0.35, 1.15), (2.3, 0, 0.9), (1.2, 0.45, 1.05)]:
                x = (cx + ox * cs * 0.5) * S; y = (cy + oy * cs * 0.5) * S; r = cs * rr * S * 0.5
                d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, 200))
    # hopper geometry, centred, scaled
    cx, cy = S * 0.5, S * 0.52
    R = S * (0.30 - pad)
    k = R / 15.0
    def P(x, y): return (cx + x * k, cy + y * k)
    # tail
    d.polygon([P(-12, -2), P(-24, -10), P(-22, 4)], fill=(31, 122, 140, 255))
    # body
    d.ellipse([P(-15, -15), P(15, 15)], fill=(46, 196, 182, 255))
    # belly
    d.ellipse([P(-7, -2), P(11, 12)], fill=(233, 255, 249, 255))
    # wing
    d.ellipse([P(-13, -1), P(7, 9)], fill=(31, 122, 140, 255))
    # goggles strap
    d.arc([P(-13, -16), P(13, 10)], start=200, end=340, fill=(59, 47, 47, 255), width=max(2, int(3 * k)))
    # lens
    d.ellipse([P(0.5, -10.5), P(13.5, 2.5)], fill=(59, 47, 47, 255))
    d.ellipse([P(2.5, -8.5), P(11.5, 0.5)], fill=(255, 209, 102, 255))
    d.ellipse([P(6.5, -6), P(10.5, -2)], fill=(59, 47, 47, 255))
    d.ellipse([P(8.6, -6.4), P(10.4, -4.6)], fill=(255, 255, 255, 255))
    # beak
    d.polygon([P(12, 1), P(24, 4), P(12, 8)], fill=(255, 140, 66, 255))
    return img

os.makedirs("icons", exist_ok=True)
big = hopper(1024)
big.resize((512, 512), Image.LANCZOS).save("icons/icon-512.png")
big.resize((192, 192), Image.LANCZOS).save("icons/icon-192.png")
big.resize((180, 180), Image.LANCZOS).save("icons/apple-touch-icon.png")
big.resize((32, 32), Image.LANCZOS).save("icons/favicon-32.png")
# maskable: full-bleed square background, hopper smaller in the safe zone
m = Image.new("RGBA", (1024, 1024))
md = ImageDraw.Draw(m)
for y in range(1024):
    t = y / 1024
    md.line([(0, y), (1024, y)], fill=(int(30 + 49 * t), int(58 + 85 * t), int(110 + 104 * t), 255))
fg = hopper(1024, bg=False, pad=0.06)
m.alpha_composite(fg)
m.resize((512, 512), Image.LANCZOS).save("icons/icon-512-maskable.png")
# Android adaptive icon layers
fg.resize((432, 432), Image.LANCZOS).save("icons/adaptive-foreground.png")
# Play Store 512 icon (no alpha)
store = Image.new("RGB", (512, 512), (30, 58, 110))
store.paste(big.resize((512, 512), Image.LANCZOS), (0, 0), big.resize((512, 512), Image.LANCZOS))
store.save("icons/play-store-512.png")
# feature graphic 1024x500
fgph = Image.new("RGB", (1024, 500))
fd = ImageDraw.Draw(fgph)
for y in range(500):
    t = y / 500
    fd.line([(0, y), (1024, y)], fill=(int(30 + 49 * t), int(58 + 85 * t), int(110 + 104 * t)))
h = hopper(420, bg=False)
fgph.paste(h, (60, 40), h)
from PIL import ImageFont
try:
    f1 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 96)
    f2 = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 40)
except Exception:
    f1 = f2 = None
fd.text((470, 150), "SKYHOP", font=f1, fill=(255, 209, 102), stroke_width=6, stroke_fill=(20, 30, 60))
fd.text((476, 280), "by Parker Data Link", font=f2, fill=(233, 242, 255))
fd.text((476, 335), "Tap to hop. Dodge towers.", font=f2, fill=(207, 224, 255))
fgph.save("icons/feature-graphic.png")
print("icons written")
