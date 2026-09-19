#!/usr/bin/env python3
"""Turn Natural Earth country outlines into app/src/main/assets/worldmap.bin.

The asset IS committed, unlike the two engine binaries - it is 17 KB and it is
data, not a build product that changes with a toolchain. This script is here so
the number is reproducible and so the next person can change the simplification
without reverse-engineering the format.

Source: https://github.com/martynafford/natural-earth-geojson
        110m/cultural/ne_110m_admin_0_countries.json
Natural Earth is public domain, so there is nothing to attribute and nothing
that forbids shipping it inside the APK.

Usage:
    git clone --depth 1 https://github.com/martynafford/natural-earth-geojson /tmp/ne
    python3 scripts/build-worldmap.py /tmp/ne/110m/cultural/ne_110m_admin_0_countries.json

Format, little-endian throughout:

    magic   4 bytes  "GJWM"
    version uint16   1
    rings   uint32
    then per ring:
        points  uint16
        points x (int16 lon, int16 lat)  in hundredths of a degree

int16 hundredths give about 1.1 km at the equator. That is far finer than a
world map drawn on a phone screen can show, and it halves the file against
float32. GhajarWorldMap reads this and projects to 0..1 on load.
"""

import io
import json
import math
import os
import struct
import sys

# Degrees of Ramer-Douglas-Peucker tolerance. The 110m source is already
# simplified; this takes it to the point where the whole world is a few
# thousand points, which draws in one pass without thinning a coastline into
# something unrecognisable. Raise it for a smaller file, lower it for detail.
EPS = 0.35

# A ring with fewer points than this is a speck at world scale.
MIN_POINTS = 4


def rings_of(geometry):
    """Outer rings only. Holes are invisible at this scale and double the size."""
    kind = geometry["type"]
    if kind == "Polygon":
        return [geometry["coordinates"][0]]
    if kind == "MultiPolygon":
        return [polygon[0] for polygon in geometry["coordinates"]]
    return []


def simplify(points, eps):
    """Ramer-Douglas-Peucker, iterative.

    Iterative rather than recursive on purpose: some rings in this data run to
    thousands of points and the recursive form hits Python's limit on them.
    """
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        ax, ay = points[a]
        bx, by = points[b]
        dx, dy = bx - ax, by - ay
        den = math.hypot(dx, dy)
        worst, worst_i = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            if den == 0:
                d = math.hypot(px - ax, py - ay)
            else:
                d = abs(dy * px - dx * py + bx * ay - by * ax) / den
            if d > worst:
                worst, worst_i = d, i
        if worst > eps:
            keep[worst_i] = True
            stack.append((a, worst_i))
            stack.append((worst_i, b))
    return [p for p, k in zip(points, keep) if k]


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    source = sys.argv[1]
    dest = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "..", "app", "src", "main", "assets", "worldmap.bin"
    )

    with open(source) as handle:
        collection = json.load(handle)

    out_rings = []
    for feature in collection["features"]:
        geometry = feature.get("geometry")
        if not geometry:
            continue
        for ring in rings_of(geometry):
            points = [(float(x), float(y)) for x, y in ring]
            # The renderer closes the path itself, so the repeated last point
            # is dead weight.
            if len(points) > 1 and points[0] == points[-1]:
                points = points[:-1]
            if len(points) < MIN_POINTS:
                continue
            reduced = simplify(points, EPS)
            if len(reduced) < MIN_POINTS:
                continue
            out_rings.append(reduced)

    buffer = io.BytesIO()
    buffer.write(b"GJWM")
    buffer.write(struct.pack("<HI", 1, len(out_rings)))
    for ring in out_rings:
        buffer.write(struct.pack("<H", len(ring)))
        for lon, lat in ring:
            lon_i = max(-18000, min(18000, int(round(lon * 100))))
            lat_i = max(-9000, min(9000, int(round(lat * 100))))
            buffer.write(struct.pack("<hh", lon_i, lat_i))

    data = buffer.getvalue()
    dest = os.path.normpath(dest)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as handle:
        handle.write(data)

    total = sum(len(r) for r in out_rings)
    print(f"{len(out_rings)} rings, {total} points, {len(data)} bytes -> {dest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
