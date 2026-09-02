#!/usr/bin/env python3
"""
Build every store asset qnote needs, from sources kept in this repo.

    python3 tools/make_store_assets.py

Produces:

    store/play/icon-512.png          Play hi-res icon (512x512, no alpha)
    store/play/feature-graphic.png   Play feature graphic (1024x500)
    store/play/screenshot-*.png      collected from the Roborazzi renders
    store/pebble/icon_144x144.png    Pebble app store large icon
    store/pebble/icon_80x80.png      Pebble app store small icon
    store/pebble/screenshot_emery.png / preview_emery.gif  copied from the watch build

Icons all come from tools/icon.svg so there is one piece of artwork, not five
that drift apart. Screenshots are never synthesised here: the phone ones are
real Roborazzi renders of the shipping composables, and the watch ones are real
QEMU captures. This script only collects and resizes them.
"""

import shutil
import subprocess
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Pillow is required: pip install Pillow")

ROOT = Path(__file__).resolve().parent.parent
ICON_SVG = ROOT / "tools" / "icon.svg"
STORE = ROOT / "store"
PLAY = STORE / "play"
PEBBLE = STORE / "pebble"

ROBORAZZI = ROOT / "qnote-android" / "app" / "build" / "outputs" / "roborazzi"
WATCH_SHOTS = ROOT / "qnote-watch" / "store"

ACCENT = (0x00, 0xAA, 0xFF)
BACKGROUND = (0x10, 0x14, 0x18)
FOREGROUND = (0xE4, 0xE9, 0xEE)
MUTED = (0x9F, 0xAD, 0xBA)


def render_icon(size: int, out: Path, background: tuple | None = None) -> None:
    """Rasterise icon.svg at `size` px. `background` flattens away the alpha."""
    subprocess.run(
        ["rsvg-convert", "-w", str(size), "-h", str(size), "-o", str(out), str(ICON_SVG)],
        check=True,
    )
    if background is not None:
        # Play rejects a hi-res icon with an alpha channel.
        img = Image.open(out).convert("RGBA")
        flat = Image.new("RGB", img.size, background)
        flat.paste(img, mask=img.split()[3])
        flat.save(out)
    print(f"  {out.relative_to(ROOT)}  ({size}x{size})")


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def make_feature_graphic(out: Path) -> None:
    """The 1024x500 banner at the top of a Play listing."""
    width, height = 1024, 500
    img = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(img)

    # A soft accent wash on the right, so the flat dark panel has some depth
    # without needing a photo.
    for x in range(width // 2, width):
        t = (x - width // 2) / (width / 2)
        shade = tuple(
            int(BACKGROUND[i] + (ACCENT[i] - BACKGROUND[i]) * (t**3) * 0.18) for i in range(3)
        )
        draw.line([(x, 0), (x, height)], fill=shade)

    mark_size = 190
    mark_path = out.parent / "_mark_tmp.png"
    render_icon(mark_size, mark_path)
    mark = Image.open(mark_path).convert("RGBA")
    # Round the corners so the mark reads as an app icon rather than a sticker.
    radius = mark_size // 5
    mask = Image.new("L", (mark_size, mark_size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, mark_size - 1, mark_size - 1], radius, fill=255)
    img.paste(mark, (78, (height - mark_size) // 2), mask)
    mark_path.unlink()

    text_x = 78 + mark_size + 56
    draw.text((text_x, 178), "qnote", font=load_font(96, bold=True), fill=FOREGROUND)
    draw.text((text_x, 288), "Speak a note into your Pebble.", font=load_font(34), fill=ACCENT)
    draw.text((text_x, 334), "Find it on your phone.", font=load_font(34), fill=MUTED)

    img.save(out)
    print(f"  {out.relative_to(ROOT)}  ({width}x{height})")


def make_preview_gif(shots: list[Path], out: Path) -> None:
    """
    Cycle the watch screenshots into the Pebble store's rollover preview.

    qnote is not an animated watchface, so there is nothing to capture live the
    way the skill's create_preview_gif.py does. Cycling the real screens shows
    the actual flow — open, list, read — and every frame is a genuine capture.
    """
    frames = [Image.open(p).convert("P", palette=Image.ADAPTIVE) for p in shots]
    if not frames:
        print("  ! no watch screenshots to build a preview from")
        return
    frames[0].save(
        out,
        save_all=True,
        append_images=frames[1:],
        duration=1400,
        loop=0,
        optimize=True,
    )
    print(f"  {out.relative_to(ROOT)}  ({frames[0].width}x{frames[0].height}, {len(frames)} frames)")


def collect(src_dir: Path, pattern: str, dest_dir: Path, label: str) -> int:
    found = sorted(src_dir.glob(pattern)) if src_dir.exists() else []
    for path in found:
        target = dest_dir / path.name
        shutil.copy2(path, target)
        with Image.open(target) as img:
            print(f"  {target.relative_to(ROOT)}  ({img.width}x{img.height})")
    if not found:
        print(f"  ! no {label} found in {src_dir.relative_to(ROOT)}")
    return len(found)


def main() -> int:
    PLAY.mkdir(parents=True, exist_ok=True)
    PEBBLE.mkdir(parents=True, exist_ok=True)

    print("Play icons")
    render_icon(512, PLAY / "icon-512.png", background=ACCENT)

    print("Play feature graphic")
    make_feature_graphic(PLAY / "feature-graphic.png")

    print("Play screenshots (Roborazzi renders of the real screens)")
    shots = collect(ROBORAZZI, "*.png", PLAY, "screenshots")
    if shots < 2:
        print("  ! Play requires at least 2 phone screenshots.")
        print("    Run: cd qnote-android && ./gradlew testDebugUnitTest")

    print("Pebble icons")
    render_icon(144, PEBBLE / "icon_144x144.png")
    render_icon(80, PEBBLE / "icon_80x80.png")

    print("Pebble screenshots (real QEMU captures)")
    collect(WATCH_SHOTS, "screenshot_*.png", PEBBLE, "watch screenshots")

    print("Pebble preview GIF")
    # Ordered as the app is used: opens listening, then the list, then a note.
    order = [
        WATCH_SHOTS / "screenshot_emery_dictating.png",
        WATCH_SHOTS / "screenshot_emery.png",
        WATCH_SHOTS / "screenshot_emery_detail.png",
    ]
    make_preview_gif([p for p in order if p.exists()], PEBBLE / "preview_emery.gif")

    print("\nDone. Listing copy lives in store/play/listing.md and store/pebble/STORE.md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
