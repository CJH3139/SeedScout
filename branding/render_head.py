import math
import sys
from PIL import Image, ImageDraw, ImageFilter

skin = Image.open("skin.png").convert("RGBA")

SCALE = 4
OUT = 512
W = OUT * SCALE

# face regions in the 64x64 skin: (x, y) top-left of the 8x8 tile
BASE = {"top": (8, 0), "front": (8, 8), "left": (16, 8), "right": (0, 8)}
HAT = {"top": (40, 0), "front": (40, 8), "left": (48, 8), "right": (32, 8)}


def tile(region, shade):
    x, y = region
    t = skin.crop((x, y, x + 8, y + 8))
    if shade != 1.0:
        r, g, b, a = t.split()
        r = r.point(lambda v: int(v * shade))
        g = g.point(lambda v: int(v * shade))
        b = b.point(lambda v: int(v * shade))
        t = Image.merge("RGBA", (r, g, b, a))
    return t


def place(canvas, tex, p0, u, v):
    """Draw tex (8x8) so that texture (0,0)->p0, (8,0)->p0+u, (0,8)->p0+v."""
    ux, uy = u[0] / 8.0, u[1] / 8.0
    vx, vy = v[0] / 8.0, v[1] / 8.0
    det = ux * vy - vx * uy
    a, b = vy / det, -vx / det
    d, e = -uy / det, ux / det
    c = -(a * p0[0] + b * p0[1])
    f = -(d * p0[0] + e * p0[1])
    layer = tex.transform((W, W), Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
    canvas.alpha_composite(layer)


def cube(canvas, faces, edge, cx, top, shades):
    ex = 0.866 * edge
    ey = 0.5 * edge
    A = (cx, top)
    B = (cx + ex, top + ey)
    C = (cx, top + edge)
    D = (cx - ex, top + ey)
    # top: (0,0)->A (8,0)->B (0,8)->D
    place(canvas, tile(faces["top"], shades["top"]), A, (B[0] - A[0], B[1] - A[1]), (D[0] - A[0], D[1] - A[1]))
    # front (viewer's left face): (0,0)->D (8,0)->C (0,8)->down edge
    place(canvas, tile(faces["front"], shades["front"]), D, (C[0] - D[0], C[1] - D[1]), (0, edge))
    # character's left side (viewer's right face): (0,0)->C (8,0)->B
    place(canvas, tile(faces["left"], shades["left"]), C, (B[0] - C[0], B[1] - C[1]), (0, edge))


def render(edge_px, with_shadow=True):
    canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    edge = edge_px * SCALE
    cx = W / 2
    total_h = 2 * edge
    top = (W - total_h) / 2
    shades = {"top": 1.0, "front": 0.82, "left": 0.62}
    cube(canvas, BASE, edge, cx, top, shades)
    hat_edge = edge * 1.0625
    hat_top = top - (hat_edge - edge) / 2
    cube(canvas, HAT, hat_edge, cx, hat_top, shades)
    head = canvas.resize((OUT, OUT), Image.LANCZOS)

    bg = Image.new("RGBA", (OUT, OUT), (255, 255, 255, 255))
    if with_shadow:
        sh = Image.new("RGBA", (OUT, OUT), (0, 0, 0, 0))
        dr = ImageDraw.Draw(sh)
        e = edge_px
        dr.ellipse((OUT / 2 - 0.95 * e, OUT / 2 + 0.78 * e, OUT / 2 + 0.95 * e, OUT / 2 + 1.12 * e), fill=(0, 0, 0, 70))
        sh = sh.filter(ImageFilter.GaussianBlur(14))
        bg.alpha_composite(sh)
    bg.alpha_composite(head)
    return bg


def circle(img):
    mask = Image.new("L", (OUT * 4, OUT * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, OUT * 4 - 1, OUT * 4 - 1), fill=255)
    mask = mask.resize((OUT, OUT), Image.LANCZOS)
    out = Image.new("RGBA", (OUT, OUT), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


edge_px = int(sys.argv[1]) if len(sys.argv) > 1 else 150
square = render(edge_px)
square.save("seedscout-icon-square.png")
circle(square).save("seedscout-icon-circle.png")
square.resize((64, 64), Image.LANCZOS).save("seedscout-icon-64.png")
print("ok")
