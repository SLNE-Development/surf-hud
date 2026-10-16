"""Generates the source textures of the core-shader HUD prototype into ../textures.

Icons are 9x9 glyph images. Strips are 3 px wide (left edge, repeatable middle, right edge) and are cut
into glyphs of any width by the GenerateHudPack task.

Usage: python tools/generate_textures.py
"""

import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "textures"
T = (0, 0, 0, 0)

PALETTE = {
    ".": T,
    "K": (20, 20, 28, 255),      # outline
    "R": (230, 40, 50, 255),     # red
    "r": (150, 20, 30, 255),     # dark red
    "W": (255, 255, 255, 255),
    "Y": (255, 210, 60, 255),    # gold
    "y": (190, 130, 20, 255),    # dark gold
    "B": (90, 170, 255, 255),    # blue
    "b": (40, 90, 170, 255),     # dark blue
    "S": (230, 190, 150, 255),   # skin
    "H": (90, 60, 40, 255),      # hair
    "G": (60, 200, 90, 255),     # green
}

ICONS = {
    "heart": [
        ".KK...KK.",
        "KRRK.KRRK",
        "KRWRKRRRK",
        "KRRRRRRrK",
        "KRRRRRRrK",
        ".KRRRRrK.",
        "..KRRrK..",
        "...KrK...",
        "....K....",
    ],
    "coin": [
        "..KKKKK..",
        ".KYYYYYK.",
        "KYYWYYyyK",
        "KYWYYYYyK",
        "KYYYYYYyK",
        "KYYYYYYyK",
        "KyYYYYyyK",
        ".KyyyyyK.",
        "..KKKKK..",
    ],
    "clock": [
        "..KKKKK..",
        ".KWWWWWK.",
        "KWWWBWWWK",
        "KWWWBWWWK",
        "KWWWBBBWK",
        "KWWWWWWWK",
        "KWWWWWWWK",
        ".KWWWWWK.",
        "..KKKKK..",
    ],
    "star": [
        "....K....",
        "...KYK...",
        "...KYK...",
        "KKKYYYKKK",
        "KYYYWYYyK",
        ".KyYYYyK.",
        "..KYyYK..",
        ".KYK.KYK.",
        ".KK...KK.",
    ],
    "player": [
        ".KKKKKKK.",
        "KHHHHHHHK",
        "KHSSSSSHK",
        "KSKSSSKSK",
        "KSSSSSSSK",
        "KSSHHHSSK",
        ".KSSSSSK.",
        "KBBBBBBBK",
        "KbBBBBBbK",
    ],
}


def strip(rows):
    """rows: list of (left, middle, right) colors from top to bottom."""
    return [list(r) for r in rows]


BORDER = (120, 200, 255, 255)
PANEL = (16, 18, 28, 200)
BAR_EDGE = (10, 10, 16, 255)
BAR_TRACK = (40, 44, 60, 255)

STRIPS = {
    # 13 px tall panel with a 1 px border and cut corners
    "panel": [(T, BORDER, T)] + [(BORDER, PANEL, BORDER)] * 11 + [(T, BORDER, T)],
    # 7 px tall bar track
    "bar_bg": [(T, BAR_EDGE, T)] + [(BAR_EDGE, BAR_TRACK, BAR_EDGE)] * 5 + [(T, BAR_EDGE, T)],
    # 5 px tall light-gray gradient, tinted per use through the text color (vote timer bar)
    "bar_tint": [
        ((255, 255, 255, 255),) * 3,
        ((235, 235, 235, 255),) * 3,
        ((215, 215, 215, 255),) * 3,
        ((190, 190, 190, 255),) * 3,
        ((165, 165, 165, 255),) * 3,
    ],
    # 5 px tall fill with a vertical gradient, drawn inside the track
    "bar_fill": [
        ((140, 255, 160, 255),) * 3,
        ((90, 230, 120, 255),) * 3,
        ((60, 200, 90, 255),) * 3,
        ((40, 170, 70, 255),) * 3,
        ((30, 140, 55, 255),) * 3,
    ],
}


def write_png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


if __name__ == "__main__":
    for name, rows in ICONS.items():
        write_png(ROOT / "icons" / f"{name}.png", [[PALETTE[c] for c in row] for row in rows])
    for name, rows in STRIPS.items():
        write_png(ROOT / "strips" / f"{name}.png", strip(rows))
    print(f"wrote textures to {ROOT}")
