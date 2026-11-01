"""Draws the Eichhorn icon (orange bookmark ribbon with a play cut-out) without extra packages.

    python3 tools/make_icons.py      # writes desktopApp/icon.png and tools/build/icon-512.png
Then `tools/make_icons.sh` derives icon.icns and the browser-extension icons with sips/iconutil (macOS).
"""
import os, struct, sys, zlib

SIZE = 512
SS = 3  # supersampling per axis
BG = (0x1C, 0x1F, 0x24)
ACCENT = (0xE0, 0x8A, 0x3C)

def inside_poly(x, y, pts):
    c = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]; xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            c = not c
        j = i
    return c

def inside_rrect(x, y, x0, y0, x1, y1, r):
    if not (x0 <= x <= x1 and y0 <= y <= y1):
        return False
    cx = min(max(x, x0 + r), x1 - r); cy = min(max(y, y0 + r), y1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r

RIBBON = [(0.33, 0.22), (0.67, 0.22), (0.67, 0.80), (0.50, 0.67), (0.33, 0.80)]
PLAY = [(0.435, 0.33), (0.435, 0.53), (0.595, 0.43)]

def pixel(u, v, full_bleed):
    if not full_bleed and not inside_rrect(u, v, 0.08, 0.08, 0.92, 0.92, 0.19):
        return None
    if inside_poly(u, v, RIBBON) and not inside_poly(u, v, PLAY):
        return ACCENT
    return BG

def render(size, full_bleed=False):
    rows = []
    for py in range(size):
        row = bytearray([0])
        for px in range(size):
            r = g = b = a = 0
            for sy in range(SS):
                for sx in range(SS):
                    c = pixel((px + (sx + .5) / SS) / size, (py + (sy + .5) / SS) / size, full_bleed)
                    if c:
                        r += c[0]; g += c[1]; b += c[2]; a += 1
            n = SS * SS
            row += bytes([r // a, g // a, b // a, 255 * a // n] if a else [0, 0, 0, 0])
        rows.append(bytes(row))
    raw = b"".join(rows)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.makedirs(os.path.join(root, "tools/build"), exist_ok=True)
    png = render(SIZE)
    for path in ("tools/build/icon-512.png", "desktopApp/icon.png", "desktopApp/src/main/resources/icon.png"):
        with open(os.path.join(root, path), "wb") as f:
            f.write(png)
    print("ok")
