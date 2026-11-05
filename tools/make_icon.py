"""Draws the Drehzahl launcher icon (dark disc, red tach arc, needle) as PNGs. Python standard library only."""
import math
import os
import struct
import sys
import zlib

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
BG = (15, 17, 21)
RING = (40, 45, 54)
RED = (227, 25, 43)
WHITE = (240, 242, 245)


def blend(dst, src, a):
    return tuple(int(d + (s - d) * a) for d, s in zip(dst, src))


def pixel(x, y, n):
    c = (n - 1) / 2.0
    dx, dy = x - c, y - c
    r = math.hypot(dx, dy) / (n / 2.0)
    if r > 1.0:
        return (0, 0, 0, 0)
    edge = min(1.0, (1.0 - r) * n / 2.0)
    col = BG
    ang = (math.degrees(math.atan2(dy, dx)) + 360) % 360
    in_sweep = ang >= 135 or ang <= 45
    if 0.62 <= r <= 0.78 and in_sweep:
        col = RED if (ang >= 135 or ang <= 15) else RING
    for k in range(9):
        t = math.radians(135 + k * 33.75)
        tx, ty = math.cos(t), math.sin(t)
        along = (dx * tx + dy * ty) / (n / 2.0)
        perp = abs(-dx * ty + dy * tx) / (n / 2.0)
        if 0.5 <= along <= 0.58 and perp < 0.025:
            col = WHITE
    nt = math.radians(-35)
    nx, ny = math.cos(nt), math.sin(nt)
    along = (dx * nx + dy * ny) / (n / 2.0)
    perp = abs(-dx * ny + dy * nx) / (n / 2.0)
    if -0.05 <= along <= 0.55 and perp < 0.035:
        col = WHITE
    if r < 0.09:
        col = RED
    return col + (int(255 * edge),)


def png(n):
    rows = b""
    for y in range(n):
        rows += b"\x00" + b"".join(bytes(pixel(x, y, n)) for x in range(n))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    head = struct.pack(">IIBBBBB", n, n, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", head) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")


if __name__ == "__main__":
    res = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"
    for name, n in SIZES.items():
        d = os.path.join(res, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "ic_launcher.png"), "wb") as f:
            f.write(png(n))
    print("icons written to", res)
