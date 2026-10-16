"""Generates the HUD panel texture used by the end_of_frame prototype.

Writes surf-hud-eof-test/assets/surf/textures/effect/hud_panel.png: a 100x34 panel with a border,
a red orientation marker in the top-left corner, the title "SURF HUD" and an empty bar track.
The shader fills the track (BarRect in end_of_frame.json) based on GameTime.

Usage: python tools/generate_panel.py
"""

import struct
import zlib
from pathlib import Path

WIDTH, HEIGHT = 100, 34
BAR_RECT = (7, 21, 86, 6)  # must match HudConfig.BarRect in end_of_frame.json

BACKGROUND = (16, 18, 28, 200)
BORDER = (120, 200, 255, 255)
TEXT = (235, 240, 255, 255)
MARKER = (255, 60, 60, 255)
TRACK = (40, 44, 60, 255)
TRACK_BORDER = (10, 10, 16, 255)

# 3x5 pixel glyphs, one string per row, "#" is set.
GLYPHS = {
    "S": ["###", "#..", "###", "..#", "###"],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"],
    "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "F": ["###", "#..", "##.", "#..", "#.."],
    "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "D": ["##.", "#.#", "#.#", "#.#", "##."],
    " ": ["...", "...", "...", "...", "..."],
}


def blank():
    return [[(0, 0, 0, 0) for _ in range(WIDTH)] for _ in range(HEIGHT)]


def fill(pixels, x, y, w, h, color):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            pixels[yy][xx] = color


def text(pixels, x, y, value, color):
    for glyph in value:
        for row, line in enumerate(GLYPHS[glyph]):
            for col, cell in enumerate(line):
                if cell == "#":
                    pixels[y + row][x + col] = color
        x += 4


def draw():
    pixels = blank()
    fill(pixels, 1, 1, WIDTH - 2, HEIGHT - 2, BACKGROUND)
    # 1px border with cut corners
    fill(pixels, 1, 0, WIDTH - 2, 1, BORDER)
    fill(pixels, 1, HEIGHT - 1, WIDTH - 2, 1, BORDER)
    fill(pixels, 0, 1, 1, HEIGHT - 2, BORDER)
    fill(pixels, WIDTH - 1, 1, 1, HEIGHT - 2, BORDER)
    fill(pixels, 3, 3, 3, 3, MARKER)
    text(pixels, 9, 4, "SURF HUD", TEXT)
    x, y, w, h = BAR_RECT
    fill(pixels, x - 1, y - 1, w + 2, h + 2, TRACK_BORDER)
    fill(pixels, x, y, w, h, TRACK)
    return pixels


def write_png(path, pixels):
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


if __name__ == "__main__":
    target = Path(__file__).resolve().parent.parent / "surf-hud-eof-test/assets/surf/textures/effect/hud_panel.png"
    write_png(target, draw())
    print(f"wrote {target}")
