#!/usr/bin/env python3
"""Builds the app's bundled game data from openly licensed npm packages.

Inputs (npm pack, then extract each into <src>/<name>/package):
  flag-icons      (MIT)   - flag SVGs
  world-atlas     (ISC)   - Natural Earth (public domain) country shapes, TopoJSON
  world-countries (ODbL)  - country names, alt spellings, translations, regions

Usage: python3 tools/build_assets.py <src-dir> app/src/main/assets
"""
import io, json, math, os, sys
import cairosvg
sys.setrecursionlimit(100000)
import numpy as np
from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]
FLAGS = f"{SRC}/flag-icons/package/flags/4x3"
os.makedirs(f"{OUT}/flags", exist_ok=True)
os.makedirs(f"{OUT}/color", exist_ok=True)

# ---------- countries ----------
wc = json.load(open(f"{SRC}/world-countries/package/countries.json"))
states = [c for c in wc if c.get("unMember") or c["cca2"] in ("VA", "PS", "XK", "TW")]
countries = []
for c in states:
    names = {c["name"]["common"], c["name"]["official"]}
    names.update(s for s in c.get("altSpellings", []) if len(s) > 3)
    for t in c.get("translations", {}).values():
        names.add(t["common"])
    for n in c["name"].get("native", {}).values():
        names.add(n["common"])
    names.discard(c["name"]["common"])
    countries.append({
        "code": c["cca2"].lower(),
        "name": c["name"]["common"],
        "alt": sorted(names),
        "region": c["region"],
        "lat": round(c["latlng"][0], 2),
        "lng": round(c["latlng"][1], 2),
        "area": c.get("area", 0),
    })
countries.sort(key=lambda c: c["name"])
codes = {c["code"] for c in countries}

# ---------- flags ----------
W, H = 480, 360
def render(code, w, h):
    png = cairosvg.svg2png(url=f"{FLAGS}/{code}.svg", output_width=w, output_height=h)
    return Image.open(io.BytesIO(png)).convert("RGB")

# Coloring palette (must match Palette in the app)
PALETTE = [
    ("Red", (0xD5, 0x2B, 0x1E)), ("Maroon", (0x8D, 0x1B, 0x3D)),
    ("Orange", (0xFF, 0x8C, 0x00)), ("Yellow", (0xFC, 0xD1, 0x16)),
    ("Green", (0x00, 0x9A, 0x44)), ("Light blue", (0x75, 0xAA, 0xDB)),
    ("Blue", (0x00, 0x38, 0xA8)), ("White", (0xFF, 0xFF, 0xFF)),
    ("Black", (0x00, 0x00, 0x00)),
]
def to_lab(rgb):
    a = rgb.astype(np.float64) / 255.0
    a = np.where(a > 0.04045, ((a + 0.055) / 1.055) ** 2.4, a / 12.92)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = a @ m.T / np.array([0.9505, 1.0, 1.089])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)
PAL_LAB = to_lab(np.array([p[1] for p in PALETTE]))

def classify(lab):
    """Name a colour the way a person would (hue first), returning a PALETTE index."""
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    C = np.hypot(a, b)
    h = np.degrees(np.arctan2(b, a)) % 360
    out = np.where(L > 55, 7, 8)                                   # white / black
    red = (h >= 340) | (h < 50)
    out = np.where(red, np.where((L < 35) & ((h < 25) | (h >= 340)), 1, 0), out)
    out = np.where((h >= 50) & (h < 72), 2, out)
    out = np.where((h >= 72) & (h < 110), 3, out)
    out = np.where((h >= 110) & (h < 200), 4, out)
    out = np.where((h >= 200) & (h < 340), np.where(L > 55, 5, 6), out)
    achromatic = C < 12
    return np.where(achromatic, np.where(L > 55, 7, 8), out)
assert classify(PAL_LAB).tolist() == list(range(len(PALETTE))), classify(PAL_LAB)

CW, CH = 320, 240
colorable = []
for c in countries:
    code = c["code"]
    img = render(code, W, H)
    img.save(f"{OUT}/flags/{code}.webp", "WEBP", quality=88, method=6)
    # Coloring template: every pixel snapped to the nearest palette colour
    small = np.array(render(code, CW, CH))
    px = small.reshape(-1, 3)
    # Dominant exact colours of the flag (anti-aliased edges and emblem details are not)
    uniq, inv, cnt = np.unique(px, axis=0, return_inverse=True, return_counts=True)
    inv = inv.reshape(-1)
    dom = []
    for i in np.argsort(-cnt):
        if cnt[i] / px.shape[0] < 0.004 or len(dom) >= 8: break
        col = uniq[i].astype(float)
        blend = False   # anti-aliased edge colour between two stripes?
        for j in dom:
            for k in dom:
                if j >= k: continue
                d1, d2 = uniq[j].astype(float), uniq[k].astype(float)
                v = d2 - d1; n = v @ v
                if n == 0: continue
                t = np.clip((col - d1) @ v / n, 0, 1)
                if np.linalg.norm(d1 + t * v - col) < 8 and 0.05 < t < 0.95: blend = True
        if not blend: dom.append(i)
    dom = np.array(dom)
    # "detail" = pixels not explained by a dominant colour or a blend of two (emblems etc.)
    U = uniq.astype(float); D = U[dom]
    best = np.min(np.linalg.norm(U[:, None, :] - D[None], axis=2), axis=1)
    for j in range(len(D)):
        for k in range(j + 1, len(D)):
            v = D[k] - D[j]; n = v @ v
            t = np.clip((U - D[j]) @ v / n, 0, 1)
            best = np.minimum(best, np.linalg.norm(D[j] + t[:, None] * v - U, axis=1))
    detail = cnt[best > 14].sum() / px.shape[0]
    dom_lab = to_lab(uniq[dom])
    dom_pal = classify(dom_lab)
    lab = to_lab(px)
    nearest_dom = ((lab[:, None, :] - dom_lab[None, :, :]) ** 2).sum(-1).argmin(1)
    lbl = dom_pal[nearest_dom]
    used = sorted(set(dom_pal.tolist()))
    if os.environ.get("DBG"): print(code, round(float(detail), 3), [PALETTE[i][0] for i in used], file=sys.stderr)
    ok = 2 <= len(used) <= 6 and detail < 0.03
    if ok:
        out = Image.fromarray(lbl.reshape(CH, CW).astype(np.uint8), "P")
        pal = []
        for _, rgb in PALETTE: pal += list(rgb)
        out.putpalette(pal + [0] * (768 - len(pal)))
        out.save(f"{OUT}/color/{code}.png", optimize=True)
        colorable.append(code)
    c["colorable"] = bool(ok)
print(f"{len(countries)} countries, {len(colorable)} colorable", file=sys.stderr)

# ---------- world map ----------
topo = json.load(open(f"{SRC}/world-atlas/package/countries-50m.json"))
sx, sy = topo["transform"]["scale"]; tx, ty = topo["transform"]["translate"]
arcs = []
for arc in topo["arcs"]:
    x = y = 0; pts = []
    for dx, dy in arc:
        x += dx; y += dy
        pts.append((x * sx + tx, y * sy + ty))
    arcs.append(pts)
def arc_pts(i):
    return arcs[i] if i >= 0 else arcs[~i][::-1]
def ring(idx):
    pts = []
    for i in idx:
        a = arc_pts(i)
        pts.extend(a if not pts else a[1:])
    return pts
def rdp(pts, eps):
    if len(pts) < 3: return pts
    a, b = np.array(pts[0]), np.array(pts[-1])
    p = np.array(pts)
    ab = b - a; n = np.hypot(*ab)
    if n == 0: dist = np.hypot(*(p - a).T)
    else: dist = np.abs(ab[0] * (p[:, 1] - a[1]) - ab[1] * (p[:, 0] - a[0])) / n
    i = int(dist.argmax())
    if dist[i] > eps:
        return rdp(pts[: i + 1], eps)[:-1] + rdp(pts[i:], eps)
    return [pts[0], pts[-1]]
num2code = {c["ccn3"]: c["cca2"].lower() for c in wc if c.get("ccn3")}
shapes = {}
other = []
for g in topo["objects"]["countries"]["geometries"]:
    code = num2code.get(g.get("id")) or ("xk" if g["properties"]["name"] == "Kosovo" else None)
    if g["properties"]["name"] in ("Somaliland",): code = "so"
    if g["properties"]["name"] in ("N. Cyprus",): code = "cy"
    polys = g["arcs"] if g["type"] == "MultiPolygon" else [g["arcs"]] if g["type"] == "Polygon" else []
    rings = []
    for poly in polys:
        outer = ring(poly[0])
        s = rdp(outer, 0.06)
        if len(s) < 4:
            if len(outer) >= 4: s = outer
            else: continue
        # Rings that cross the antimeridian: unwrap, then draw a copy on each side of the map.
        xs = [p[0] for p in s]
        if max(abs(xs[i + 1] - xs[i]) for i in range(len(xs) - 1)) > 180:
            s = [(p[0] + 360 if p[0] < 0 else p[0], p[1]) for p in s]
            copies = [s, [(p[0] - 360, p[1]) for p in s]]
        else:
            copies = [s]
        for s in copies:
            rings.append([v for p in s for v in (round(p[0] * 10), round(-p[1] * 10))])
    if not rings or g.get("id") == "010": continue   # skip Antarctica
    if code in codes:
        shapes.setdefault(code, []).extend(rings)
    else:
        other.extend(rings)
missing = sorted(codes - set(shapes))
print("no map shape (marker only):", missing, file=sys.stderr)
json.dump({"scale": 10, "countries": shapes, "other": other}, open(f"{OUT}/world.json", "w"), separators=(",", ":"))
json.dump(countries, open(f"{OUT}/countries.json", "w"), ensure_ascii=False, separators=(",", ":"))
