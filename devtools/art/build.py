"""The Huey, as code: its model (a life-size Bell UH-1H, one block a metre), its two profiles, its
recipe, and its rotor loop.

Run from the repository root:

    uv run --no-project python devtools/art/build.py
    uv run --no-project --with numpy python devtools/art/build.py sounds    # needs ffmpeg

Everything it writes is committed; this script is the source of truth for those files. The shape is
measured from public-domain references (devtools/art/reference/SOURCES.md); the model is original
work. Copyright 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT.parent / "tools/bbgen"))
import bbgen  # noqa: E402  (the shared Blockbench writer: minecraft mods/tools/bbgen)

ASSETS = ROOT / "src/main/resources/assets/huey"
DATA = ROOT / "src/main/resources/data/huey"

# ------------------------------------------------------------------ units

# Built in metres, written in Blockbench pixels (16 a metre, a profile of scale 0.0625). A station
# `s` is metres aft of the nose; the model's origin is on the ground under the mast, so the
# helicopter stands on its skids and turns about its rotor. +Z is the nose, +X the left.
PX = 16.0
MAST = 3.57                 # the mast's station
TAIL = 12.62                # the tail's end


def z(s: float) -> float:
    return (MAST - s) * PX


def p(v: float) -> float:
    return v * PX


# ---------------------------------------------------------------- the atlas

def make_atlas() -> bbgen.TexelAtlas:
    a = bbgen.TexelAtlas(size=256, density=1.0, seed=0x0E1)
    a.material("paint", (232, 232, 226), w=128, h=64, grain=9)
    a.material("glass", (172, 206, 212), w=64, h=32, grain=6, alpha=110)
    a.material("blade", (46, 48, 52), w=128, h=16, grain=6)
    a.material("paint_trim", (204, 204, 198), w=32, h=32, grain=8)
    a.material("glass_green", (118, 192, 150), w=32, h=32, grain=6, alpha=130)
    a.material("chin", (44, 58, 66), w=32, h=32, grain=6)
    a.material("metal", (140, 144, 150), w=32, h=32, grain=10)
    a.material("dark", (38, 40, 44), w=32, h=32, grain=8)
    a.material("exhaust", (74, 64, 58), w=32, h=32, grain=16)
    a.material("skid", (66, 68, 62), w=32, h=32, grain=8)
    a.material("floor", (60, 62, 60), w=32, h=32, grain=10,
               pattern=lambda x, y, c: bbgen.shade(c, -10) if (x + 2 * y) % 6 == 0 else c)
    a.material("interior", (150, 154, 142), w=32, h=32, grain=8)

    def weave(x, y, c):
        return bbgen.shade(c, -26) if x % 3 == 0 or y % 3 == 0 else c

    a.material("webbing", (176, 48, 38), w=32, h=32, grain=10, pattern=weave)
    a.material("seat", (72, 76, 72), w=32, h=32, grain=8)
    a.material("panel", (30, 32, 34), w=32, h=32, grain=6)
    a.material("gauge", (14, 14, 16), w=16, h=16, grain=4)
    a.material("needle", (244, 244, 232), w=16, h=16, grain=2)
    a.material("lamp", (250, 246, 220), w=16, h=16, grain=4)
    a.material("beacon", (210, 30, 26), w=16, h=16, grain=6)
    a.material("tip", (218, 192, 64), w=16, h=16, grain=6)
    a.material("band_white", (236, 236, 232), w=16, h=16, grain=4)
    a.material("band_red", (196, 34, 30), w=16, h=16, grain=6)
    a.material("boom", (198, 176, 64), w=64, h=16, grain=8)
    a.material("tank", (210, 210, 204), w=32, h=32, grain=6)
    a.material("grille", (52, 54, 56), w=32, h=32, grain=4,
               pattern=lambda x, y, c: bbgen.shade(c, 26) if y % 2 == 0 else c)
    return a


# ------------------------------------------------------------- the helpers

class Huey:
    def __init__(self) -> None:
        self.m = bbgen.Model("huey", make_atlas(), seed="huey/huey")

    # A box in metres: x across (left +), y up, s along (aft +).
    def box(self, folder, name, x0, x1, y0, y1, s0, s1, mat, faces=None):
        self.m.cube(folder, name, [p(x0), p(y0), z(s1)], [p(x1), p(y1), z(s0)], mat, faces=faces)

    def pair(self, folder, name, x0, x1, y0, y1, s0, s1, mat, faces=None):
        """The box on the left (x0..x1 > 0) and its mirror on the right."""
        self.m.mirror_x(folder, name, [p(x0), p(y0), z(s1)], [p(x1), p(y1), z(s0)], mat, faces=faces)

    def across(self, folder, name, a, b, s0, s1, t, mat, mirror=True, grow=None):
        """A plate on the cross-section's segment from a = (x, y) to b (metres), from station s0 to
        s1, t thick, centred on the segment and lengthened by `grow` at each end to close the joints."""
        grow = t / 2 if grow is None else grow
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dy) + 2 * grow
        cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        angle = math.degrees(math.atan2(dy, dx))
        frm = [p(cx - length / 2), p(cy - t / 2), z(s1)]
        to = [p(cx + length / 2), p(cy + t / 2), z(s0)]
        origin = [p(cx), p(cy), (z(s0) + z(s1)) / 2]
        if mirror:
            self.m.mirror_x(folder, name, frm, to, mat, rotation=(0, 0, angle), origin=origin)
        else:
            self.m.cube(folder, name, frm, to, mat, rotation=(0, 0, angle), origin=origin)

    def along(self, folder, name, a, b, x0, x1, t, mat, mirror=False, grow=None):
        """A plate on the side view's segment from a = (s, y) to b (metres), across x0..x1, t thick."""
        grow = t / 2 if grow is None else grow
        ds, dy = b[0] - a[0], b[1] - a[1]
        length = math.hypot(ds, dy) + 2 * grow
        cs, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        # Lying along -z (aft) before turning; turned about x so its far end rises by dy over ds.
        angle = math.degrees(math.atan2(dy, ds))
        frm = [p(x0), p(cy - t / 2), z(cs + length / 2)]
        to = [p(x1), p(cy + t / 2), z(cs - length / 2)]
        origin = [p((x0 + x1) / 2), p(cy), z(cs)]
        if mirror:
            self.m.mirror_x(folder, name, frm, to, mat, rotation=(angle, 0, 0), origin=origin)
        else:
            self.m.cube(folder, name, frm, to, mat, rotation=(angle, 0, 0), origin=origin)

    def octagon(self, folder, name, centre, apothem, s0, s1, mat):
        """A regular octagonal prism along the body from station s0 to s1, about centre = (x, y): four
        bars across it, at 0, 45, 90 and 135 degrees, each as long as the octagon is across and as
        thick as one side is long -- their union is the octagon."""
        cx, cy = centre
        half = apothem * math.tan(math.radians(22.5))
        for k, turn in enumerate((0, 45, 90, 135)):
            self.m.cube(folder, f"{name}{k}", [p(cx - apothem), p(cy - half), z(s1)], [p(cx + apothem), p(cy + half), z(s0)],
                        mat, rotation=(0, 0, turn), origin=[p(cx), p(cy), (z(s0) + z(s1)) / 2])

    def rod(self, folder, name, a, b, d, mat, mirror=False):
        """A square rod d thick from a = (x, y, s) to b (metres)."""
        ax, ay, az = p(a[0]), p(a[1]), z(a[2])
        bx, by, bz = p(b[0]), p(b[1]), z(b[2])
        dx, dy, dz = bx - ax, by - ay, bz - az
        length = math.sqrt(dx * dx + dy * dy + dz * dz)
        mid = [(ax + bx) / 2, (ay + by) / 2, (az + bz) / 2]
        ux, uy, uz = dx / length, dy / length, dz / length
        # A cube standing along +y, turned about x then y (X then Y then Z) to point along u.
        pitch = math.degrees(math.atan2(math.hypot(ux, uz), uy))
        yaw = math.degrees(math.atan2(ux, uz))
        h = p(d) / 2
        frm = [mid[0] - h, mid[1] - length / 2, mid[2] - h]
        to = [mid[0] + h, mid[1] + length / 2, mid[2] + h]
        if mirror:
            self.m.mirror_x(folder, name, frm, to, mat, rotation=(pitch, yaw, 0), origin=mid)
        else:
            self.m.cube(folder, name, frm, to, mat, rotation=(pitch, yaw, 0), origin=mid)


# ------------------------------------------------------------- the airframe

# The cabin's cross-section, left half (x, y), from the belly's middle round to the roof's.
BELLY_Y, FLOOR_Y = 0.44, 0.61
WAIST = 1.20
SECTION = [(0.0, BELLY_Y), (0.86, BELLY_Y), (WAIST, 0.70), (WAIST, 1.92), (0.98, 2.22), (0.0, 2.28)]
ROOF_Y = SECTION[-1][1]
SKIN = 0.07                 # a metal sheet, a pixel or so
COCKPIT, BULKHEAD = 0.92, 4.46   # where the cockpit starts; the bulkhead behind the troop seat
PILOT_DOOR = (1.25, 1.95)  # the cockpit doors, shut
PANEL = (1.95, 2.40)       # the hinged panel between a cockpit door and the cargo opening
DOOR = (2.40, 4.28)        # the cargo opening, 1.88 long; its door slid back over the aft fuselage
DOOR_TOP = 1.84
SLID = (4.30, 6.18)        # where the slid-back cargo door lies
WINDSHIELD = ((0.52, 1.52), (1.06, ROOF_Y))     # its foot and its head, (station, height)

# The nose, in layers a pixel thick up from the belly. Its side view is where each layer's front is
# (FRONT, a station by height); its section narrows toward the belly (NARROW). In plan each layer
# rounds from its front by a quarter circle, inside an envelope: a bulb 1.72 across, the cockpit's
# shoulders flaring out to the cabin's width behind it. Boxes of a layer that round to the same width
# are one box. The lower front is the chin windows, drawn dark, as they look outside.
FRONT = [(0.44, 0.50), (0.50, 0.36), (0.56, 0.25), (0.64, 0.15), (0.74, 0.08), (0.86, 0.03), (0.98, 0.005),
         (1.08, 0.0), (1.18, 0.02), (1.28, 0.07), (1.38, 0.15), (1.46, 0.28), (1.52, 0.46)]
NARROW = [(0.44, 0.76), (0.52, 0.86), (0.62, 0.93), (0.74, 0.98), (0.86, 1.0), (1.52, 1.0)]
NOSE_BACK = COCKPIT + 0.02
TIP = 0.40                  # a layer's half-width at its very front
BULB = (0.86, 0.32, 0.66)   # the bulb's half-width, and the stations where the shoulders start and end
REACH = 0.45                # how far behind its front a layer's quarter circle runs
CHIN = (0.52, 1.04, 0.80)   # the chin windows: from, to (heights), and back to (station)


def interp(table, y):
    for (ya, va), (yb, vb) in zip(table, table[1:]):
        if ya <= y <= yb:
            return va + (y - ya) / (yb - ya) * (vb - va)
    return table[0][1] if y < table[0][0] else table[-1][1]


def envelope(s: float) -> float:
    """The nose's half-width allowed at station s: the bulb, then the shoulders out to the waist."""
    f = min(max((s - BULB[1]) / (BULB[2] - BULB[1]), 0.0), 1.0)
    return BULB[0] + (WAIST - BULB[0]) * f * f * (3 - 2 * f)


def plan(s: float, front: float) -> float:
    """The half-width at station s of a layer whose front is at `front`."""
    f = min(max(s - front, 0.0) / REACH, 1.0)
    return min(TIP + (1.4 - TIP) * math.sqrt(1.0 - (1.0 - f) ** 2), envelope(s))


def nose(h: Huey) -> None:
    layer = 1.0 / PX
    y, i = BELLY_Y, 0
    while y < WINDSHIELD[0][1] - 1e-6:
        y1 = min(y + layer, WINDSHIELD[0][1])
        mid = (y + y1) / 2
        front, narrow = interp(FRONT, mid), interp(NARROW, mid)
        chin = CHIN[0] <= mid < CHIN[1]
        # Walk back from the front a pixel at a time; a box ends where the width (in pixels) changes
        # or the chin window does.
        s0, j = front, 0
        while s0 < NOSE_BACK - 1e-6:
            hw_px = round(plan(s0 + 0.5 / PX, front) * narrow * PX)
            glass = chin and s0 < CHIN[2]
            s1 = s0 + 1.0 / PX
            while s1 < NOSE_BACK - 1e-6:
                nxt = round(plan(s1 + 0.5 / PX, front) * narrow * PX)
                if nxt != hw_px or (chin and (s1 < CHIN[2]) != glass):
                    break
                s1 += 1.0 / PX
            s1 = min(s1, NOSE_BACK)
            hw = hw_px / PX
            h.box("chin" if glass else "paint/nose", f"l{i:02d}b{j:02d}", -hw, hw, y, y1, s0, s1, "chin" if glass else "paint")
            s0, j = s1, j + 1
        y, i = y1, i + 1
    # The rib down the nose between the chin windows.
    h.box("paint/nose", "rib", -0.08, 0.08, CHIN[0], CHIN[1], -0.012, 0.36, "paint")


def shell(h: Huey) -> None:
    """The cockpit and the cabin: belly, sides, roof, round corners, as sheets; the cockpit doors
    shut, the hinged panels, the cargo openings open, their doors slid back."""
    s0, s1 = COCKPIT, BULKHEAD
    (x0, y0), (x1, y1), (x2, y2), (x3, y3), (x4, y4), (x5, y5) = SECTION
    head = WINDSHIELD[1][0]
    h.across("paint/shell", "belly", (-x1, y0 + SKIN / 2), (x1, y1 + SKIN / 2), s0, s1, SKIN, "paint", mirror=False)
    h.across("paint/shell", "lower", (x1, y1), (x2, y2), s0, s1, SKIN, "paint")
    h.across("paint/shell", "upper", (x3, y3), (x4, y4), head - 0.02, s1, SKIN, "paint")
    h.across("paint/shell", "roof", (-x4, y5 - SKIN / 2), (x4, y5 - SKIN / 2), head - 0.04, s1, SKIN, "paint", mirror=False)
    wall = (WAIST - SKIN, WAIST)
    h.pair("paint/shell", "side_front", *wall, y2, 1.52, s0, PILOT_DOOR[0], "paint")
    h.pair("paint/shell", "door_header", *wall, DOOR_TOP, y3, DOOR[0], DOOR[1], "paint")
    h.pair("paint/shell", "side_rear", *wall, y2, y3, DOOR[1], s1, "paint")
    # The cockpit doors, shut: a window over a lower window, framed.
    door = (WAIST - SKIN, WAIST + 0.02)
    a, b = PILOT_DOOR
    h.pair("paint/doors", "door_low", *door, y2, 0.76, a, b, "paint")
    h.pair("paint/doors", "door_mid", *door, 1.12, 1.24, a, b, "paint")
    h.pair("paint/doors", "door_top", *door, 2.00, y3, a, b, "paint")
    h.pair("paint/doors", "door_fore", *door, y2, y3, a, a + 0.06, "paint")
    h.pair("paint/doors", "door_aft", *door, y2, y3, b - 0.06, b, "paint")
    h.pair("glass/doors", "door_window", WAIST - 0.04, WAIST - 0.02, 1.24, 2.00, a + 0.06, b - 0.06, "glass")
    h.pair("glass/doors", "door_window_low", WAIST - 0.04, WAIST - 0.02, 0.76, 1.12, a + 0.06, b - 0.06, "glass")
    # The hinged panels behind them, each with its small window.
    a, b = PANEL
    h.pair("paint/doors", "panel_low", *wall, y2, 1.30, a, b, "paint")
    h.pair("paint/doors", "panel_top", *wall, 1.90, y3, a, b, "paint")
    h.pair("paint/doors", "panel_fore", *wall, 1.30, 1.90, a, a + 0.07, "paint")
    h.pair("paint/doors", "panel_aft", *wall, 1.30, 1.90, b - 0.07, b, "paint")
    h.pair("glass/cabin", "panel_window", WAIST - 0.04, WAIST - 0.02, 1.30, 1.90, a + 0.07, b - 0.07, "glass")
    # The cargo doors, slid back on their rails over the aft fuselage: two windows each, dark with
    # the fuselage behind them.
    a, b = SLID
    out = (WAIST + 0.01, WAIST + 0.06)
    h.pair("paint/doors", "cargo_low", *out, 0.66, 1.24, a, b, "paint")
    h.pair("paint/doors", "cargo_top", *out, 1.76, 1.88, a, b, "paint")
    h.pair("paint/doors", "cargo_fore", *out, 1.24, 1.76, a, a + 0.12, "paint")
    h.pair("paint/doors", "cargo_mid", *out, 1.24, 1.76, (a + b) / 2 - 0.06, (a + b) / 2 + 0.06, "paint")
    h.pair("paint/doors", "cargo_aft", *out, 1.24, 1.76, b - 0.12, b, "paint")
    h.pair("chin", "cargo_window_fore", WAIST + 0.02, WAIST + 0.05, 1.24, 1.76, a + 0.12, (a + b) / 2 - 0.06, "chin")
    h.pair("chin", "cargo_window_aft", WAIST + 0.02, WAIST + 0.05, 1.24, 1.76, (a + b) / 2 + 0.06, b - 0.12, "chin")
    h.pair("cargo_rails", "rail_top", WAIST, WAIST + 0.04, 1.88, 1.93, DOOR[0], b, "dark")
    h.pair("cargo_rails", "rail_low", WAIST, WAIST + 0.04, 0.62, 0.66, DOOR[0], b, "dark")
    # The floor inside, and the bulkhead the rear seat leans on.
    # The floor inside, inside the belly's round corners, and the bulkhead the rear seat leans on,
    # stepped in under the roof's.
    h.box("floor", "floor", -1.02, 1.02, FLOOR_Y - 0.06, FLOOR_Y, s0, s1, "floor")
    h.box("interior", "bulkhead", -(WAIST - SKIN), WAIST - SKIN, FLOOR_Y, y3, s1 - 0.06, s1, "interior")
    h.box("interior", "bulkhead_top", -(x4 - 0.04), x4 - 0.04, y3, ROOF_Y - SKIN, s1 - 0.06, s1, "interior")


def windshield(h: Huey) -> None:
    """Two great panes over the instrument panel, a post between them, the corner windows down to
    the doors, the brow, and the green windows in the roof."""
    low, high = WINDSHIELD

    def at(y):
        return (low[0] + (y - low[1]) / (high[1] - low[1]) * (high[0] - low[0]), y)

    # In three bands, each as wide as the cabin is at its height, so no corner pokes out of the roof.
    bands = ((low[1], 1.70, 1.10), (1.70, 1.92, WAIST - 0.03), (1.92, 2.08, 1.07), (2.08, high[1], 0.96))
    for i, (ya, yb, hw) in enumerate(bands):
        h.along("glass/windshield", f"pane{i}_l", at(ya), at(yb), 0.05, hw, 0.04, "glass", grow=0.0)
        h.along("glass/windshield", f"pane{i}_r", at(ya), at(yb), -hw, -0.05, 0.04, "glass", grow=0.0)
    h.along("paint/cockpit/frame", "post", low, high, -0.05, 0.05, 0.08, "paint_trim")
    h.along("paint/windshield", "sill", (low[0] - 0.03, low[1] - 0.01), (low[0] + 0.03, low[1] + 0.03), -1.10, 1.10, 0.05, "paint")
    # The corner windows, upright in the sides' plane, from the slant back to the doors.
    for i, (ya, yb) in enumerate(((low[1], 1.66), (1.66, 1.80), (1.80, 1.92))):
        h.pair("glass/windshield", f"corner{i}", WAIST - 0.03, WAIST - 0.01, ya, yb, at(yb)[0] - 0.02, PILOT_DOOR[0], "glass")
    # The green windows in the roof over the pilots.
    h.pair("glass/roof", "eyebrow", 0.08, 0.86, ROOF_Y - 0.05, ROOF_Y + 0.01, high[0] + 0.04, 1.74, "glass_green")
    h.box("paint/roof", "eyebrow_post", -0.08, 0.08, ROOF_Y - 0.06, ROOF_Y + 0.02, high[0] - 0.02, 1.74, "paint")


def aft(h: Huey) -> None:
    """Behind the bulkhead: full height and width under the slid doors, the belly rising, then
    quickly into the boom."""
    #        s0    s1    hw    bottom  top
    steps = [(4.46, 4.90, WAIST, 0.46, ROOF_Y),
             (4.90, 5.34, WAIST, 0.51, ROOF_Y),
             (5.34, 5.78, WAIST, 0.57, ROOF_Y),
             (5.78, 6.18, WAIST, 0.64, ROOF_Y)]
    # Then round into the boom: eight steps from the cabin's section to the boom's.
    n = 8
    for k in range(n):
        f = (k + 1) / n
        e = 1 - (1 - f) ** 2
        steps.append((6.18 + 0.58 * k / n, 6.18 + 0.58 * (k + 1) / n,
                      WAIST + (0.50 - WAIST) * e, 0.64 + (0.82 - 0.64) * f, ROOF_Y + (1.92 - ROOF_Y) * e))
    for i, (s0, s1, hw, bot, top) in enumerate(steps):
        r = min(0.24, hw * 0.25)
        h.box("paint/aft", f"core{i}", -hw, hw, bot + r, top - r, s0, s1, "paint")
        h.box("paint/aft", f"belly{i}", -(hw - r), hw - r, bot, bot + r, s0, s1, "paint")
        h.box("paint/aft", f"back{i}", -(hw - r), hw - r, top - r, top, s0, s1, "paint")


COWL_HW, COWL_TOP = 0.60, 2.62


def cowling(h: Huey) -> None:
    """The engine and transmission housing on the roof, its grille and inspection windows, the
    mast's fairing, the beacon, and the exhaust outlet at its tail, facing aft."""
    hw, top = COWL_HW, COWL_TOP
    h.along("paint/cowling", "nose", (2.40, ROOF_Y), (2.78, top), -hw + 0.06, hw - 0.06, 0.10, "paint")
    h.box("paint/cowling", "body", -hw, hw, ROOF_Y - 0.02, top - 0.10, 2.70, 5.92, "paint")
    h.box("paint/cowling", "crown", -(hw - 0.10), hw - 0.10, top - 0.10, top, 2.75, 5.86, "paint")
    h.pair("cowling/grille", "grille", hw, hw + 0.02, 2.34, 2.50, 2.95, 3.25, "grille")
    h.pair("chin", "inspection_fore", hw, hw + 0.02, 2.36, 2.48, 3.55, 4.10, "chin")
    h.pair("chin", "inspection_aft", hw, hw + 0.02, 2.36, 2.48, 4.25, 4.85, "chin")
    # The mast's fairing, the mast, the swashplate.
    h.box("paint/cowling", "fairing", -0.28, 0.28, top, top + 0.12, MAST - 0.28, MAST + 0.28, "paint")
    h.box("metal", "mast", -0.07, 0.07, top + 0.12, ROTOR_Y - 0.06, MAST - 0.07, MAST + 0.07, "metal")
    h.box("metal", "swashplate", -0.25, 0.25, 2.86, 2.93, MAST - 0.25, MAST + 0.25, "metal")
    h.box("dark", "collective", -0.11, 0.11, 2.93, 3.10, MAST - 0.11, MAST + 0.11, "dark")
    # The anti-collision beacon.
    h.box("beacon", "beacon", -0.07, 0.07, top, top + 0.12, 5.50, 5.64, "beacon")
    # The exhaust: a round outlet on the cowling's tail, its mouth dark.
    h.octagon("exhaust", "outlet", (0.0, 2.42), 0.22, 5.80, 6.28, "exhaust")
    h.octagon("exhaust", "mouth", (0.0, 2.42), 0.16, 6.27, 6.285, "dark")


BOOM = (6.76, 11.30)


def boom(h: Huey) -> None:
    """The tail boom in fine steps, the elevator through it, the fin and the gearbox on top."""
    n = 16
    for i in range(n):
        s0 = BOOM[0] + (BOOM[1] - BOOM[0]) * i / n
        s1 = BOOM[0] + (BOOM[1] - BOOM[0]) * (i + 1) / n
        f = (i + 0.5) / n
        hw = 0.50 - 0.26 * f
        bot = 0.82 + 0.56 * f
        top = 1.90 + 0.05 * f
        r = 0.08
        h.box("paint/boom", f"core{i:02d}", -hw, hw, bot + r, top - r, s0, s1 + 0.005, "paint")
        h.box("paint/boom", f"belly{i:02d}", -(hw - r), hw - r, bot, bot + r, s0, s1 + 0.005, "paint")
        h.box("paint/boom", f"back{i:02d}", -(hw - r), hw - r, top - r, top, s0, s1 + 0.005, "paint")
    # The synchronized elevator, through the boom.
    h.box("paint/elevator", "elevator", -1.42, 1.42, 1.44, 1.53, 9.20, 9.95, "paint")
    # The fin: swept back, its leading edge from the boom's top to the gearbox, its trailing edge
    # from under the boom's end; in thin layers, so its edges step a pixel at a time.
    lead = ((10.95, 1.94), (12.20, 3.00))
    trail = ((11.75, 1.42), (12.62, 2.96))

    def edge(line, y):
        (sa, ya), (sb, yb) = line
        return sa + (y - ya) / (yb - ya) * (sb - sa)

    y, k = 1.42, 0
    while y < 2.99:
        y1 = min(y + 0.125, 3.00)
        mid = (y + y1) / 2
        front = max(10.98, edge(lead, mid)) if mid > 1.94 else 10.98
        back = edge(trail, mid)
        half = 0.13 - 0.04 * (mid - 1.42) / 1.58
        h.box("paint/fin", f"layer{k:02d}", -half, half, y, y1, front, back, "paint")
        y, k = y1, k + 1
    h.box("paint/fin", "gearbox", -0.14, 0.26, 2.96, 3.22, 12.18, 12.60, "paint")
    # The tail skid, under the fin.
    h.rod("skid", "tail_skid", (0.0, 1.46, 11.62), (0.0, 1.12, 12.36), 0.07, "skid")


def tail_rotor(h: Huey) -> None:
    """Two blades on the fin's left, turning about x through the hub."""
    hub = (0.36, 3.11, 12.42)
    r = 1.295
    h.box("rotor_tail", "hub", 0.26, 0.40, hub[1] - 0.12, hub[1] + 0.12, hub[2] - 0.12, hub[2] + 0.12, "metal")
    h.box("rotor_tail", "shaft", 0.20, 0.30, hub[1] - 0.05, hub[1] + 0.05, hub[2] - 0.05, hub[2] + 0.05, "metal")
    chord = 0.21
    for i, sign in enumerate((1, -1)):
        y0, y1 = sorted((hub[1] + sign * 0.10, hub[1] + sign * (r - 0.30)))
        h.box("rotor_tail", f"blade{i}", 0.30, 0.35, y0, y1, hub[2] - chord / 2, hub[2] + chord / 2, "blade")
        ya, yb = sorted((hub[1] + sign * (r - 0.30), hub[1] + sign * (r - 0.15)))
        h.box("rotor_tail", f"band{i}a", 0.30, 0.35, ya, yb, hub[2] - chord / 2, hub[2] + chord / 2, "band_red")
        ya, yb = sorted((hub[1] + sign * (r - 0.15), hub[1] + sign * r))
        h.box("rotor_tail", f"band{i}b", 0.30, 0.35, ya, yb, hub[2] - chord / 2, hub[2] + chord / 2, "band_white")


ROTOR_Y = 3.90              # the blades' plane at the hub
ROTOR_R = 7.355             # half of 14.71
TAIL_HUB = (0.33, 3.11, 12.42)


def main_rotor(h: Huey) -> None:
    """The hub, two blades fore and aft at rest, and the stabilizer bar across over them with its weights."""
    chord, thick = 0.533, 0.08
    h.box("rotor_main", "hub", -0.16, 0.16, ROTOR_Y - 0.08, ROTOR_Y + 0.08, MAST - 0.36, MAST + 0.36, "dark")
    h.box("rotor_main", "trunnion", -0.10, 0.10, ROTOR_Y - 0.12, ROTOR_Y + 0.14, MAST - 0.10, MAST + 0.10, "metal")
    h.box("rotor_main", "nut", -0.06, 0.06, ROTOR_Y + 0.14, ROTOR_Y + 0.30, MAST - 0.06, MAST + 0.06, "metal")
    for i, sign in enumerate((1, -1)):
        tip = MAST - sign * ROTOR_R
        root = MAST - sign * 0.36
        sa, sb = sorted((root, tip + sign * 0.30))
        h.box("rotor_main", f"blade{i}", -chord / 2, chord / 2, ROTOR_Y - thick / 2, ROTOR_Y + thick / 2, sa, sb, "blade")
        sa, sb = sorted((tip + sign * 0.30, tip))
        h.box("rotor_main", f"tip{i}", -chord / 2, chord / 2, ROTOR_Y - thick / 2, ROTOR_Y + thick / 2, sa, sb, "tip")
    bar_y = ROTOR_Y + 0.20
    h.box("rotor_main", "bar", -1.38, 1.38, bar_y - 0.025, bar_y + 0.025, MAST - 0.025, MAST + 0.025, "metal")
    for i, sign in enumerate((1, -1)):
        x0, x1 = sorted((sign * 1.00, sign * 1.40))
        h.box("rotor_main", f"weight{i}", x0, x1, bar_y - 0.06, bar_y + 0.06, MAST - 0.06, MAST + 0.06, "dark")


SKID_X = 1.30               # half the tread of 2.61
SKID_S = (1.20, 4.95)       # where the tubes run on the ground
CROSS = (1.85, 4.10)        # the cross tubes' stations


def skids(h: Huey) -> None:
    """Two tubes on the ground, their toes turned up, on two arched cross tubes."""
    d = 0.10
    h.pair("skids", "tube", SKID_X - d / 2, SKID_X + d / 2, 0.0, d, SKID_S[0], SKID_S[1], "skid")
    h.rod("skids", "toe", (SKID_X, d / 2, SKID_S[0] + 0.02), (SKID_X, 0.26, 0.90), d, "skid", mirror=True)
    h.rod("skids", "heel", (SKID_X, d / 2, SKID_S[1] - 0.02), (SKID_X, 0.12, 5.10), d, "skid", mirror=True)
    for i, s in enumerate(CROSS):
        h.rod("skids", f"leg{i}", (SKID_X, d, s), (0.88, BELLY_Y - 0.02, s), 0.11, "skid", mirror=True)
        h.box("skids", f"beam{i}", -0.90, 0.90, BELLY_Y - 0.10, BELLY_Y - 0.01, s - 0.055, s + 0.055, "skid")


SEAT_Y = 0.94               # the cushions, where a seated rider's hips go; the eye is 1.02 over them


def interior(h: Huey) -> None:
    """The instrument panel and its two gauges, the pedestal, the pilots' seats, and the troop
    seats: a bench across the bulkhead and a row facing forward behind the pilots, red webbing on
    tubes."""
    h.box("panel", "panel", -0.98, 0.98, 1.10, 1.54, COCKPIT, COCKPIT + 0.10, "panel")
    h.box("panel", "shield", -1.00, 1.00, 1.54, 1.60, COCKPIT - 0.04, COCKPIT + 0.26, "panel")
    h.box("panel", "pedestal", -0.16, 0.16, FLOOR_Y, 1.12, COCKPIT + 0.05, 1.70, "panel")
    for kind, x in (("speed", -0.38), ("fuel", -0.68)):
        h.box("panel", f"dial_{kind}", x - 0.11, x + 0.11, 1.22, 1.44, COCKPIT + 0.10, COCKPIT + 0.11, "gauge")
        h.box(f"needle_{kind}", "needle", x - 0.01, x + 0.01, 1.33, 1.42, COCKPIT + 0.11, COCKPIT + 0.12, "needle")
    for side in (1, -1):
        x, tag = side * 0.55, "l" if side > 0 else "r"
        h.box("seats", f"pilot_pan_{tag}", x - 0.26, x + 0.26, FLOOR_Y + 0.28, SEAT_Y - 0.04, 1.52, 2.02, "seat")
        h.box("seats", f"pilot_back_{tag}", x - 0.26, x + 0.26, SEAT_Y - 0.04, 1.86, 2.02, 2.12, "seat")
        h.box("seats", f"pilot_leg_{tag}", x - 0.20, x + 0.20, FLOOR_Y, FLOOR_Y + 0.28, 1.60, 1.96, "dark")
    for row, (s_pan, s_back) in enumerate(((3.00, 3.42), (3.96, 4.40))):
        h.box("seats", f"bench{row}_pan", -1.08, 1.08, SEAT_Y - 0.06, SEAT_Y - 0.02, s_pan, s_back, "webbing")
        h.box("seats", f"bench{row}_back", -1.08, 1.08, SEAT_Y - 0.02, SEAT_Y + 0.62, s_back - 0.04, s_back, "webbing")
        h.box("seats", f"bench{row}_rail", -1.10, 1.10, SEAT_Y - 0.08, SEAT_Y - 0.04, s_pan - 0.02, s_pan + 0.02, "metal")
        for i, x in enumerate((-1.0, 0.0, 1.0)):
            h.rod("seats", f"bench{row}_leg{i}", (x, FLOOR_Y, s_pan + 0.08), (x, SEAT_Y - 0.06, s_pan + 0.02), 0.04, "metal")


HOOK = (0.0, BELLY_Y - 0.22, MAST)


def hook(h: Huey) -> None:
    """The cargo hook, hung under the belly at the mast: where a sling load's rope is made fast."""
    x, y, s0 = HOOK
    h.box("hook", "mount", -0.14, 0.14, BELLY_Y - 0.06, BELLY_Y, s0 - 0.20, s0 + 0.20, "dark")
    h.box("hook", "hanger", -0.03, 0.03, y + 0.04, BELLY_Y - 0.06, s0 - 0.03, s0 + 0.03, "metal")
    h.box("hook", "hook", -0.04, 0.04, y, y + 0.05, s0 - 0.08, s0 + 0.06, "metal")


def lamps(h: Huey) -> None:
    """The landing light and the searchlight under the nose."""
    for name, x, s in (("landing", 0.30, 0.80), ("search", -0.30, 1.10)):
        h.box("lenses", name, x - 0.13, x + 0.13, BELLY_Y - 0.08, BELLY_Y, s - 0.13, s + 0.13, "lamp")
        h.box("paint/lamps", name + "_rim", x - 0.16, x + 0.16, BELLY_Y - 0.03, BELLY_Y, s - 0.16, s + 0.16, "paint_trim")


SPRAY_S, SPRAY_Y, SPRAY_HALF = 4.40, 0.26, 5.5


def spray_boom(h: Huey) -> None:
    """The crop sprayer's rig, drawn only when one is fitted: a boom 11 across behind the rear cross
    tube, nozzles along it, braces to the skids, and a tank on each skid."""
    d = 0.08
    h.box("spray_boom", "boom", -SPRAY_HALF, SPRAY_HALF, SPRAY_Y - d / 2, SPRAY_Y + d / 2, SPRAY_S - d / 2, SPRAY_S + d / 2, "boom")
    n = 23
    for i in range(n):
        x = -SPRAY_HALF + 0.2 + (2 * SPRAY_HALF - 0.4) * i / (n - 1)
        h.box("spray_boom", f"nozzle{i:02d}", x - 0.025, x + 0.025, SPRAY_Y - 0.13, SPRAY_Y - d / 2, SPRAY_S - 0.025, SPRAY_S + 0.025, "dark")
    h.rod("spray_boom", "brace", (SKID_X, 0.10, CROSS[1] + 0.05), (3.2, SPRAY_Y, SPRAY_S), 0.05, "boom", mirror=True)
    h.rod("spray_boom", "stay", (0.9, BELLY_Y - 0.05, CROSS[1]), (3.6, SPRAY_Y + 0.02, SPRAY_S), 0.035, "metal", mirror=True)
    h.pair("spray_boom", "tank", SKID_X + 0.06, SKID_X + 0.40, 0.10, 0.42, 2.30, 3.40, "tank")
    h.pair("spray_boom", "tank_cap", SKID_X + 0.17, SKID_X + 0.29, 0.42, 0.47, 2.80, 2.92, "dark")
    h.pair("spray_boom", "hose", SKID_X + 0.20, SKID_X + 0.26, 0.10, 0.16, 3.40, SPRAY_S, "dark")


def build_model() -> bbgen.Model:
    h = Huey()
    nose(h)
    shell(h)
    windshield(h)
    aft(h)
    cowling(h)
    boom(h)
    tail_rotor(h)
    main_rotor(h)
    skids(h)
    interior(h)
    hook(h)
    lamps(h)
    spray_boom(h)
    return h.m


# ------------------------------------------------------------- the profiles


def v(x: float, y: float, s0: float) -> list:
    """A point in metres as the profiles write it: mesh pixels, (x, y, z)."""
    return [round(p(x), 3), round(p(y), 3), round(z(s0), 3)]


SEATS = [(-0.55, 1.82, True), (0.55, 1.82, False)] \
    + [(x, 3.20, False) for x in (-0.75, 0.0, 0.75)] \
    + [(x, 4.17, False) for x in (-0.75, 0.0, 0.75)]


def vehicle_profile() -> dict:
    """The Huey to Vanilla Wheels: what it looks like, who sits where, what it stands on, its tank,
    its gauges and lamps, its paint, glass, cockpit and loop."""
    skid_front, skid_back = SKID_S[0] + 0.10, SKID_S[1] - 0.10
    return {
        "mesh": "huey:huey",
        "scale": 0.0625,
        "handedness": "right",
        "body": {
            # The cabin's width and height; the length is the hull's, nose to tail.
            "width": 2.6, "length": 12.6, "height": 2.65,
            # The four boxes that take clicks and hits beyond the body's own (which stands under the mast).
            "parts": [{"at": v(0, BELLY_Y, 1.15), "width": 2.3, "height": 1.85},
                      {"at": v(0, 0.50, 5.70), "width": 1.9, "height": 1.80},
                      {"at": v(0, 0.95, 8.60), "width": 1.0, "height": 1.00},
                      {"at": v(0, 1.30, 11.90), "width": 1.3, "height": 1.70}],
        },
        "seats": [{"at": v(x, SEAT_Y, s0), "driver": True} if driver else {"at": v(x, SEAT_Y, s0)}
                  for x, s0, driver in SEATS],
        # The skids' ends, where it touches the ground; drawn with the body.
        "wheels": {"radius": 1, "drawn": False,
                   "positions": [{"forward": round(z(s0), 3), "right": round(p(side * SKID_X), 3)}
                                 for s0 in (skid_front, skid_back) for side in (-1, 1)]},
        # Flight along the heading, blocks a tick: Rotorcraft reads these as its horizontal speeds.
        "engine": {"max_speed": 1.4, "acceleration": 0.03, "reverse_speed": 0.4, "brake": 0.04},
        "climb": 0.5,
        "mass": 2.2,
        "fuel": {"capacity": 36000},
        "gauges": [
            {"kind": "speed", "part": {"group": "needle_speed"}, "pivot": v(-0.38, 1.33, COCKPIT + 0.11),
             "axis": [0, 0, 1], "zero": -2.094, "sweep": 4.189},
            {"kind": "fuel", "part": {"group": "needle_fuel"}, "pivot": v(-0.68, 1.33, COCKPIT + 0.11),
             "axis": [0, 0, 1], "zero": -2.094, "sweep": 4.189},
        ],
        "headlights": {"at": [v(0.30, BELLY_Y - 0.08, 0.80), v(-0.30, BELLY_Y - 0.08, 1.10)],
                       "part": {"group": "lenses"}, "range": 16},
        "paint": {"part": {"group": "paint"}, "default": "green", "factory": "#4f5536"},
        "glass": {"group": "glass"},
        "cockpit": {"group": "cockpit"},
        "sounds": {"engine": "huey:rotor", "pitch": [0.62, 1.04], "volume": [0.35, 1.0]},
        "repair": {"ingredient": {"tag": "c:ingots/steel"}, "full_cost": 24},
    }


def aircraft_profile() -> dict:
    """The Huey to Rotorcraft: how it climbs and turns, its rotors, its hook, its sprayer mount."""
    return {
        "climb_rate": 0.4, "descent_rate": 0.5, "vertical_acceleration": 0.03,
        "yaw_rate": 3.0, "spool_ticks": 60, "tilt": 12,
        "rotors": [
            # Counter-clockwise seen from above, as American rotors turn; the tail rotor five times as fast.
            {"part": {"group": "rotor_main"}, "pivot": v(0, ROTOR_Y, MAST), "axis": [0, 1, 0], "speed": 1.0,
             "radius": round(p(ROTOR_R), 2)},
            {"part": {"group": "rotor_tail"}, "pivot": v(*TAIL_HUB), "axis": [1, 0, 0], "speed": 5.0,
             "radius": round(p(1.295), 2)},
        ],
        "hook": v(*HOOK),
        "sprayer": {"part": {"group": "spray_boom"}, "at": v(0, SPRAY_Y, SPRAY_S), "width": 11},
        # What meets the world in the air; the blades and the spray boom are drawn only.
        "hull": [hull_box(*b) for b in HULL],
    }


#       x0      x1     y0       y1      s0     s1
HULL = [(-WAIST, WAIST, BELLY_Y, ROOF_Y, 0.0, 6.20),             # the cabin and the aft fuselage
        (-COWL_HW, COWL_HW, ROOF_Y, COWL_TOP, 2.40, 6.30),       # the cowling and the exhaust
        (-1.36, 1.36, 0.0, BELLY_Y, 0.85, 5.12),                 # the skids
        (-0.50, 0.50, 0.80, 1.95, 6.20, BOOM[1]),                # the boom
        (-1.42, 1.42, 1.44, 1.53, 9.20, 9.95),                   # the elevator
        (-0.16, 0.26, 1.42, 3.22, 10.95, TAIL)]                  # the fin and its gearbox


def hull_box(x0, x1, y0, y1, s0, s1) -> dict:
    return {"from": v(x0, y0, s1), "to": v(x1, y1, s0)}


CHASSIS_RECIPE = {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": ["GGG", "BBB", "BBB"],
    "key": {"G": {"tag": "c:glass_panes"}, "B": {"tag": "c:storage_blocks/steel"}},
    "result": {"id": "vanillawheels:chassis", "count": 1, "components": {"vanillawheels:vehicle": "huey:huey"}},
}


def unlock(recipe: str, tag: str) -> dict:
    """The recipe-book unlock: on holding what starts it, so a pooled crafting fill finds it."""
    return {"parent": "minecraft:recipes/root",
            "criteria": {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe}},
                         "has_it": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": tag}]}}},
            "requirements": [["has_the_recipe", "has_it"]],
            "rewards": {"recipes": [recipe]}}


SOUNDS_JSON = {
    # Heard a long way off; the loop's own level is set by the profile's sounds.volume.
    "rotor": {"sounds": [{"name": "huey:rotor", "attenuation_distance": 64}]},
}

LANG = {
    "vehicle.huey.huey": "Huey",
}


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def template(path: Path, size) -> None:
    """An empty template of air, size [x, y, z]: the tests lay their own ground."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("{DataVersion: 3955, size: [%d, %d, %d], data: [], entities: [], palette: [{Name: \"minecraft:air\"}]}\n" % tuple(size), encoding="utf-8")


def main() -> None:
    build_model().write(ASSETS / "vanillawheels/mesh/huey.bbmodel")
    write_json(DATA / "vanillawheels/vehicle/huey.json", vehicle_profile())
    write_json(DATA / "rotorcraft/aircraft/huey.json", aircraft_profile())
    write_json(DATA / "recipe/huey_chassis.json", CHASSIS_RECIPE)
    write_json(DATA / "advancement/recipes/huey_chassis.json", unlock("huey:huey_chassis", "#c:storage_blocks/steel"))
    write_json(ASSETS / "sounds.json", SOUNDS_JSON)
    write_json(ASSETS / "lang/en_us.json", LANG)
    # The pad the gametests fly over: room for the whole hull, nine metres of tail behind the mast.
    template(ROOT / "devtools/gameteststructures/pad.snbt", (48, 40, 48))


SOUND_SRC = ROOT / "devtools/art/sounds/src"


def sounds() -> None:
    """The rotor's loop: see devtools/art/sounds/SOURCES.md. Needs ffmpeg and numpy (`--with numpy`)."""
    sys.path.insert(0, str(ROOT.parent / "tools/sound"))
    import cutlib  # noqa: E402  (the shared cutter: minecraft mods/tools/sound)
    start, length, fade = 72.812, 2.808, 0.20
    cutlib.write_ogg(ASSETS / "sounds/rotor.ogg",
                     cutlib.loop(SOUND_SRC / "156906-uh-1-helicopter.ogg", start, start + length + fade, fade, 0.8))


if __name__ == "__main__":
    if sys.argv[1:] == ["sounds"]:
        sounds()
    else:
        main()
