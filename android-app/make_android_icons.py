"""Writes launcher icons and splash screens into android/app/src/main/res from the Skyhop icon set."""
from PIL import Image, ImageDraw
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "android", "app", "src", "main", "res")
ICONS = os.path.join(HERE, "..", "icons")

full = Image.open(os.path.join(ICONS, "icon-512.png")).convert("RGBA")
fg = Image.open(os.path.join(ICONS, "adaptive-foreground.png")).convert("RGBA")

# legacy + round launcher icons
for d, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    folder = os.path.join(RES, "mipmap-" + d)
    os.makedirs(folder, exist_ok=True)
    full.resize((size, size), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
    # round: mask to circle
    r = full.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(r, (0, 0), mask)
    out.save(os.path.join(folder, "ic_launcher_round.png"))
    # adaptive foreground (108dp)
    fsize = int(size * 108 / 48)
    canvas = Image.new("RGBA", (fsize, fsize), (0, 0, 0, 0))
    inner = fg.resize((int(fsize * 0.62), int(fsize * 0.62)), Image.LANCZOS)
    canvas.paste(inner, ((fsize - inner.width) // 2, (fsize - inner.height) // 2), inner)
    canvas.save(os.path.join(folder, "ic_launcher_foreground.png"))

# adaptive background colour
values = os.path.join(RES, "values")
with open(os.path.join(values, "ic_launcher_background.xml"), "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#1E3A6E</color>\n</resources>\n')

# splash screens: sky gradient with the hopper in the middle
def splash(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=(int(30 + 49 * t), int(58 + 85 * t), int(110 + 104 * t)))
    s = int(min(w, h) * 0.32)
    h2 = fg.resize((s, s), Image.LANCZOS)
    img.paste(h2, ((w - s) // 2, (h - s) // 2), h2)
    return img

for d, (pw, ph) in [("mdpi", (320, 480)), ("hdpi", (480, 800)), ("xhdpi", (720, 1280)), ("xxhdpi", (960, 1600)), ("xxxhdpi", (1280, 1920))]:
    for orient, (w, h) in [("port", (pw, ph)), ("land", (ph, pw))]:
        folder = os.path.join(RES, "drawable-%s-%s" % (orient, d))
        os.makedirs(folder, exist_ok=True)
        splash(w, h).save(os.path.join(folder, "splash.png"))
splash(480, 800).save(os.path.join(RES, "drawable", "splash.png"))
print("android icons + splash written")
