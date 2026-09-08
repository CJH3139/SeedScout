import struct
import zlib
from pathlib import Path

def write_png(path, pixels):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)

CLEAR = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)
DARK = (30, 30, 30, 255)

def generic():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            border = x in (1, 14) or y in (1, 14)
            inside = 1 < x < 14 and 1 < y < 14
            row.append(DARK if border else WHITE if inside else CLEAR)
        rows.append(row)
    return rows

def arrow():

    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            half = y // 2 + 1 if y < 10 else 3
            center = 7.5
            in_shape = abs(x - center) <= half - 0.5 and (y < 10 or (y < 15 and abs(x - center) <= 2.5))
            row.append(WHITE if in_shape else CLEAR)
        rows.append(row)

    out = [r[:] for r in rows]
    for y in range(16):
        for x in range(16):
            if rows[y][x] == CLEAR:
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < 16 and 0 <= nx < 16 and rows[ny][nx] == WHITE:
                            out[y][x] = DARK
    return out

root = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "seedscout" / "textures" / "gui"
write_png(root / "icons" / "generic.png", generic())
write_png(root / "arrow.png", arrow())
print("wrote", root)
