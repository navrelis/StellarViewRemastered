#!/usr/bin/env python3
"""Stellar View Remastered - pixel-art logo generator.

Draws the logo pixel by pixel on a 64x64 grid with a fixed palette and writes

    logo/logo_64.png                                  native 64x64
    logo/logo_512.png                                 native x8, nearest neighbour
    src/main/resources/assets/stellarview/icon.png    copy of the 512 px file

Deterministic: no randomness (only fixed integer hashes), no network, no input
files. Run from anywhere:  py logo/make_logo.py
Requires Pillow only.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT_64 = ROOT / "logo" / "logo_64.png"
OUT_512 = ROOT / "logo" / "logo_512.png"
OUT_ICON = ROOT / "src" / "main" / "resources" / "assets" / "stellarview" / "icon.png"

W = H = 64
SCALE = 8
MAX_COLOURS = 24

# --------------------------------------------------------------------------
# Palette: ramps, hue-shifted (shadows towards blue/violet, lights towards warm)
# --------------------------------------------------------------------------
PALETTE = {
    # night sky ramp: near-black indigo -> deep violet -> Milky Way lavender
    "S0": "#0a0a1e",
    "S1": "#121236",
    "S2": "#1b1850",
    "S3": "#2a2170",
    "S4": "#3e2f8f",
    "M0": "#5e48b0",
    "M1": "#8f78d0",
    # moon ramp: warm highlight -> cream -> beige -> grey-violet -> slate blue
    "L0": "#fff8dc",
    "L1": "#f2e3b3",
    "L2": "#cdbd9f",
    "L3": "#9c98ad",
    "D0": "#4d5380",
    "D1": "#262b50",
    # planet ramp: peach -> coral -> rose -> plum
    "P0": "#ffcf8a",
    "P1": "#f0895a",
    "P2": "#b84a68",
    "P3": "#6a2a66",
    # ring accent: mint -> teal
    "R0": "#a8f0e0",
    "R1": "#4fa8b0",
    # cool star accent
    "B0": "#8fd3ff",
    # ground silhouette: black-blue -> navy -> moonlit teal edge
    "G0": "#05060f",
    "G1": "#0c1228",
    "G2": "#1f4a52",
}

SKY_RAMP = ["S0", "S1", "S2", "S3", "S4", "M0", "M1"]
MOON_RAMP = ["D1", "D0", "L3", "L2", "L1"]

# One light source for moon and planet: upper left, slightly towards the viewer.
LIGHT_X, LIGHT_Y = -0.76, -0.65
LIGHT_ELEV = 0.40  # cos of the angle between light and view direction (gibbous)

BAYER4 = (
    (0, 8, 2, 10),
    (12, 4, 14, 6),
    (3, 11, 1, 9),
    (15, 7, 13, 5),
)


def bayer(x: int, y: int) -> float:
    """Ordered-dither threshold in (0, 1)."""
    return (BAYER4[y & 3][x & 3] + 0.5) / 16.0


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else hi if v > hi else v


def hash01(ix: int, iy: int, seed: int) -> float:
    """Fixed integer hash -> [0, 1]. Replaces any random number generator."""
    n = (ix * 374761393 + iy * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFF) / 65535.0


def value_noise(x: float, y: float, cell: float, seed: int) -> float:
    """Smooth value noise in [0, 1] built on hash01."""
    fx, fy = x / cell, y / cell
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx = tx * tx * (3 - 2 * tx)
    ty = ty * ty * (3 - 2 * ty)
    a = hash01(ix, iy, seed)
    b = hash01(ix + 1, iy, seed)
    c = hash01(ix, iy + 1, seed)
    d = hash01(ix + 1, iy + 1, seed)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def stepped(v: float, steps) -> float:
    """Sum of soft steps: integer on the flats, fractional only inside the
    narrow transition zones (these are the only places that get dithered)."""
    acc = 0.0
    for t, w in steps:
        acc += clamp((v - t) / w + 0.5)
    return acc


def pick(ramp, level: float, x: int, y: int) -> str:
    """Quantise a fractional ramp level with ordered dithering.

    The fraction is snapped to quarters first, so only the three classic
    patterns can appear between two ramp colours: 25 % dots, 50 % checker,
    75 % dots. Nothing in between, hence no irregular speckle."""
    base = math.floor(level)
    quarter = math.floor((level - base) * 4 + 0.5) / 4.0
    if bayer(x, y) < quarter:
        base += 1
    return ramp[int(clamp(base, 0, len(ramp) - 1))]


class Canvas:
    def __init__(self) -> None:
        self.px = [["S0"] * W for _ in range(H)]

    def put(self, x: int, y: int, key: str) -> None:
        if 0 <= x < W and 0 <= y < H:
            self.px[y][x] = key

    def get(self, x: int, y: int) -> str:
        return self.px[y][x]

    def stamp(self, x0: int, y0: int, rows, legend, only=None) -> None:
        """Place a hand-drawn sprite; '.' is transparent."""
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch == ".":
                    continue
                x, y = x0 + dx, y0 + dy
                if not (0 <= x < W and 0 <= y < H):
                    continue
                if only is not None and self.px[y][x] not in only:
                    continue
                self.px[y][x] = legend[ch]


# --------------------------------------------------------------------------
# Layout
# --------------------------------------------------------------------------
MOON_CX, MOON_CY, MOON_R = 23.0, 22.0, 14.0   # pixel-edge centre, 28 px disc
BAND_A = (-2.0, 56.0)                          # Milky Way centre line
BAND_B = (66.0, 16.0)


def draw_sky(c: Canvas) -> None:
    ax, ay = BAND_A
    bx, by = BAND_B
    blen = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / blen, (by - ay) / blen
    for y in range(H):
        for x in range(W):
            px, py = x + 0.5, y + 0.5
            # the sky itself stays one flat colour: all light comes from the band
            level = 0.0
            # Milky Way: widest near the horizon (lower left), tapering up right,
            # brightest just right of the tree, calmer towards the image border
            s = (px - ax) * ux + (py - ay) * uy
            d = -(px - ax) * uy + (py - ay) * ux
            sn = clamp(s / blen)
            half_width = 10.5 - 3.5 * sn
            core = max(0.0, 1.0 - (d / half_width) ** 2) ** 1.6
            cloud = value_noise(s, d * 1.6, 6.0, 11)
            amp = (6.3 - 3.0 * abs(sn - 0.22)) * (0.7 + 0.3 * clamp(x / 5.0))
            level += core * amp * (0.6 + 0.55 * cloud)
            # dark dust rift running along the band, slightly off centre
            wobble = (value_noise(s, 0.0, 9.0, 23) - 0.5) * 4.0
            rift = math.exp(-(((d - 1.0 - wobble) / 1.3) ** 2))
            level -= rift * core * 2.4 * (0.3 + 0.9 * value_noise(s, d, 5.0, 37))
            # the lower 45 % of each step stays flat, the rest is a dither seam
            base = math.floor(level)
            frac = clamp((level - base - 0.45) / 0.55)
            c.put(x, y, pick(SKY_RAMP, base + frac * 0.999, x, y))


# Hand-placed single-pixel stars (x, y, colour)
STARS = [
    # dim field stars
    (5, 6, "M0"), (12, 3, "M1"), (33, 9, "M0"), (59, 24, "M0"), (4, 17, "M1"),
    (3, 40, "M0"), (41, 21, "M1"), (57, 44, "M0"), (36, 47, "M1"), (20, 4, "M0"),
    (47, 4, "M1"), (60, 5, "M0"), (30, 52, "M0"),
    # bright field stars
    (28, 4, "L0"), (55, 3, "L1"), (5, 12, "L1"), (46, 22, "L0"), (60, 37, "L1"),
    (41, 40, "L0"), (4, 25, "L0"),
    # coloured stars inside the Milky Way band
    (4, 49, "L0"), (19, 41, "L0"), (17, 50, "P0"), (24, 46, "B0"), (28, 39, "L0"),
    (33, 36, "B0"), (40, 33, "L0"), (37, 38, "P1"), (45, 28, "P0"), (51, 26, "L0"),
    (55, 21, "B0"), (60, 19, "L0"), (23, 42, "M1"), (48, 31, "M1"),
]

# Plus-shaped bright stars: (x, y, size)
PLUS_STARS = [(40, 6, 2), (5, 32, 1), (43, 45, 1)]


def draw_stars(c: Canvas) -> None:
    for x, y, key in STARS:
        c.put(x, y, key)
    for x, y, size in PLUS_STARS:
        c.put(x, y, "L0")
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.put(x + dx, y + dy, "L1" if size > 1 else "M1")
            if size > 1:
                c.put(x + 2 * dx, y + 2 * dy, "M0")


def draw_shooting_star(c: Canvas) -> None:
    # tail upper right, head lower left: one clean 1:1 diagonal
    trail = [
        (60, 32, "S4"), (59, 33, "S4"), (58, 34, "M0"), (57, 35, "M0"),
        (56, 36, "M1"), (55, 37, "M1"), (54, 38, "L1"), (53, 39, "L1"),
        # 2x2 head
        (52, 40, "L0"), (51, 40, "L0"), (52, 41, "L0"), (51, 41, "L0"),
    ]
    for x, y, key in trail:
        c.put(x, y, key)


def light_dot(nx: float, ny: float) -> float:
    """Lambert term for a unit sphere normal (nx, ny, nz>=0)."""
    nz = math.sqrt(max(0.0, 1.0 - nx * nx - ny * ny))
    ln = math.hypot(LIGHT_X, LIGHT_Y)
    side = math.sqrt(1.0 - LIGHT_ELEV * LIGHT_ELEV)
    return (nx * LIGHT_X / ln + ny * LIGHT_Y / ln) * side + nz * LIGHT_ELEV


MOON_STEPS = (
    (-0.36, 0.34),   # D1 -> D0   wide 25/50/75 % dither on the night side
    (-0.05, 0.16),   # D0 -> L3   narrow dither seam at the terminator
    (0.13, 0.001),   # L3 -> L2   hard band
    (0.27, 0.001),   # L2 -> L1   hard band
)
MOON_RIM = 2.4       # thickness of the highlight crescent on the lit limb

CRATER_LEGEND = {"a": "L0", "b": "L1", "c": "L2", "d": "L3"}
CRATER_BIG = [
    ".ddd..",
    "dcccc.",
    "dcccca",
    ".cccca",
    "..aaa.",
]
CRATER_MED = [
    ".dd.",
    "dcca",
    "dcca",
    ".aa.",
]
CRATER_SMALL = [
    "dd.",
    "dca",
    ".aa",
]
MARE = [
    "..cccc..",
    ".cccccc.",
    "cccccccc",
    "ccccccc.",
    ".cccccc.",
    "...ccc..",
]


def draw_moon(c: Canvas) -> None:
    ln = math.hypot(LIGHT_X, LIGHT_Y)
    ox, oy = -LIGHT_X / ln * MOON_RIM, -LIGHT_Y / ln * MOON_RIM
    for y in range(H):
        for x in range(W):
            dx, dy = x + 0.5 - MOON_CX, y + 0.5 - MOON_CY
            if dx * dx + dy * dy > MOON_R * MOON_R:
                continue
            b = light_dot(dx / MOON_R, dy / MOON_R)
            key = pick(MOON_RAMP, stepped(b, MOON_STEPS), x, y)
            if key == "L1" and (dx - ox) ** 2 + (dy - oy) ** 2 > MOON_R * MOON_R:
                key = "L0"  # rim light: disc minus the same disc pushed off the light
            c.put(x, y, key)
    lit = {"L1"}
    c.stamp(12, 19, MARE, CRATER_LEGEND, only=lit)
    c.stamp(20, 11, CRATER_BIG, CRATER_LEGEND, only=lit)
    c.stamp(21, 17, CRATER_MED, CRATER_LEGEND, only=lit)
    c.stamp(15, 14, CRATER_SMALL, CRATER_LEGEND, only=lit)
    c.stamp(13, 27, CRATER_SMALL, CRATER_LEGEND, only=lit)
    c.stamp(28, 14, CRATER_SMALL, CRATER_LEGEND, only=lit)


# Hand-pixelled ringed planet, 21x9. Digits = planet ramp (0 light .. 3 dark),
# r = lit ring, s = ring in shade (far half, and the planet's shadow on it).
PLANET_LEGEND = {"0": "P0", "1": "P1", "2": "P2", "3": "P3", "r": "R0", "s": "R1"}
PLANET = [
    "........00112.ssssss.",
    ".......0001112......r",
    "......000111122....r.",
    ".....s001111122...r..",
    "...ss.011111223.ss...",
    "..s...1111122rrs.....",
    ".s....1111rrr23......",
    "r......rrr2233.......",
    ".rrrrrr.22333........",
]
PLANET_X, PLANET_Y = 40, 8


def draw_planet(c: Canvas) -> None:
    c.stamp(PLANET_X, PLANET_Y, PLANET, PLANET_LEGEND)


# Blocky terrain on a 2 px block grid: (run width, top y), each sums to 64.
HILLS_BACK = [(6, 56), (8, 54), (6, 56), (8, 58), (6, 56), (4, 54), (4, 52),
              (4, 50), (6, 48), (4, 50), (4, 52), (4, 54)]
HILLS_FRONT = [(4, 60), (10, 58), (8, 60), (10, 62), (6, 60), (6, 58), (6, 56),
               (8, 54), (6, 56)]

# Square oak: 2 px trunk, two-tier canopy, moonlit top edges.
TREE_LEGEND = {"h": "G2", "g": "G1", "t": "G0"}
TREE = [
    "..hhhhhh..",
    "..ggggtt..",
    "hhgggggghh",
    "ggggggggtt",
    "ggggggggtt",
    "tttttttttt",
    "....tt....",
    "....tt....",
    "....tt....",
    "....tt....",
]
TREE_X, TREE_Y = 5, 44


def draw_ground(c: Canvas) -> None:
    def tops(runs):
        out = []
        for width, top in runs:
            out.extend([top] * width)
        assert len(out) == W, len(out)
        return out

    back, front = tops(HILLS_BACK), tops(HILLS_FRONT)
    for x in range(W):
        for y in range(back[x], H):
            c.put(x, y, "G2" if y == back[x] else "G1")
    c.stamp(TREE_X, TREE_Y, TREE, TREE_LEGEND)
    for x in range(W):
        for y in range(front[x], H):
            c.put(x, y, "G0")


def render() -> Canvas:
    c = Canvas()
    draw_sky(c)
    draw_stars(c)
    draw_shooting_star(c)
    draw_moon(c)
    draw_planet(c)
    draw_ground(c)
    return c


def hex_to_rgba(h: str):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def main() -> None:
    canvas = render()
    native = Image.new("RGBA", (W, H))
    native.putdata([hex_to_rgba(PALETTE[k]) for row in canvas.px for k in row])
    big = native.resize((W * SCALE, H * SCALE), Image.NEAREST)

    OUT_64.parent.mkdir(parents=True, exist_ok=True)
    OUT_ICON.parent.mkdir(parents=True, exist_ok=True)
    native.save(OUT_64, format="PNG", optimize=True)
    big.save(OUT_512, format="PNG", optimize=True)
    OUT_ICON.write_bytes(OUT_512.read_bytes())

    self_check()


def self_check() -> None:
    native = Image.open(OUT_64)
    native.load()
    assert native.size == (W, H), native.size
    assert native.mode == "RGBA", native.mode
    npx = native.load()
    pixels = [npx[x, y] for y in range(H) for x in range(W)]
    assert all(p[3] == 255 for p in pixels), "non-opaque pixel found"
    print(f"[ok] native: {native.size[0]}x{native.size[1]} {native.mode}, all alpha 255")

    colours = set(pixels)
    allowed = {hex_to_rgba(v) for v in PALETTE.values()}
    assert len(colours) <= MAX_COLOURS, len(colours)
    assert colours <= allowed, "colour outside the palette"
    print(f"[ok] distinct colours: {len(colours)} (limit {MAX_COLOURS})")
    for name, value in PALETTE.items():
        count = pixels.count(hex_to_rgba(value))
        print(f"       {name} {value}  {count:4d} px" + ("" if count else "  (unused)"))

    big = Image.open(OUT_512)
    big.load()
    assert big.size == (W * SCALE, H * SCALE), big.size
    assert big.mode == "RGBA", big.mode
    bpx = big.load()
    for y in range(H * SCALE):
        for x in range(W * SCALE):
            assert bpx[x, y] == npx[x // SCALE, y // SCALE], (x, y)
    print(f"[ok] 512: {big.size[0]}x{big.size[1]}, every {SCALE}x{SCALE} block equals its native pixel")

    assert OUT_ICON.read_bytes() == OUT_512.read_bytes(), "icon.png differs"
    print("[ok] icon.png is byte-identical to logo_512.png")

    for path in (OUT_64, OUT_512, OUT_ICON):
        print(f"       {path.relative_to(ROOT).as_posix()}: {path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
