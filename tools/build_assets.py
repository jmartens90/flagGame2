#!/usr/bin/env python3
"""Builds the app's bundled game data from openly licensed sources.

Each flag set ends up in app/src/main/assets/<set>/:
  items.json   names, groups, capitals, map points
  map.json     outlines for the "find it on the map" mode (projected, y pointing down)
  flags/       flag images (webp)
  color/       colouring templates: every pixel in one of the PALETTE colours (png)

Sources (see README for licences), extracted under <src>:
  flag-icons/package        npm flag-icons           used to spot territories that fly another flag
  world-atlas/package       npm world-atlas          Natural Earth country shapes
  world-countries/package   npm world-countries      names, capitals, regions
  region-flags/package      npm region-flags         country, territory and US state flags
  us-atlas/package          npm us-atlas             US Census state shapes (Albers USA)
  belgium/                  tools/fetch_belgium.py   Wikimedia Commons flags + Eurostat GISCO provinces
  points.json               tools/project_us.mjs     US capitals projected like us-atlas

Capital coordinates come from GeoNames via the geonamescache pip package.

Usage: python3 tools/build_assets.py <src> app/src/main/assets
"""
import io, json, math, os, shutil, sys
import cairosvg
import geonamescache
import numpy as np
from PIL import Image

sys.setrecursionlimit(100000)
SRC, OUT = sys.argv[1], sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------- colours
# Must match Palette in the app (ColorQuestion.kt).
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

def classify(lab):
    """Name a colour the way a person would (hue first), returning a PALETTE index."""
    L, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    C = np.hypot(a, b)
    h = np.degrees(np.arctan2(b, a)) % 360
    out = np.where(L > 55, 7, 8)
    red = (h >= 340) | (h < 50)
    out = np.where(red, np.where((L < 35) & ((h < 25) | (h >= 340)), 1, 0), out)
    out = np.where((h >= 50) & (h < 72), 2, out)
    out = np.where((h >= 72) & (h < 110), 3, out)
    out = np.where((h >= 110) & (h < 200), 4, out)
    out = np.where((h >= 200) & (h < 340), np.where(L > 55, 5, 6), out)
    return np.where(C < 12, np.where(L > 55, 7, 8), out)

assert classify(to_lab(np.array([p[1] for p in PALETTE]))).tolist() == list(range(len(PALETTE)))

# ---------------------------------------------------------------- flags
def expand_entities(text):
    """Inline <!ENTITY> definitions (Illustrator exports), which the SVG renderer refuses for safety."""
    import re
    ents = dict(re.findall(r'<!ENTITY\s+(\w+)\s+"([^"]*)"\s*>', text))
    text = re.sub(r"<!DOCTYPE[^\[>]*\[.*?\]>", "", text, flags=re.S)
    for k, v in ents.items():
        text = text.replace(f"&{k};", v)
    return text

def render(svg, width=None, height=None):
    text = open(svg, encoding="utf-8").read()
    if "<!ENTITY" in text:
        png = cairosvg.svg2png(bytestring=expand_entities(text).encode(), output_width=width, output_height=height)
    else:
        png = cairosvg.svg2png(url=svg, output_width=width, output_height=height)
    return Image.open(io.BytesIO(png)).convert("RGBA")

def flatten(img):
    bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
    return Image.alpha_composite(bg, img).convert("RGB")

def color_template(svg):
    """Palette-index image of the flag, or None if it has too much fine detail to colour in."""
    probe = render(svg, height=240)
    w = 480
    h = max(120, min(400, round(w * probe.height / probe.width)))
    small = np.array(flatten(render(svg, w, h)))
    px = small.reshape(-1, 3)
    uniq, inv, cnt = np.unique(px, axis=0, return_inverse=True, return_counts=True)
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
    U = uniq.astype(float); D = U[dom]
    best = np.min(np.linalg.norm(U[:, None, :] - D[None], axis=2), axis=1)
    for j in range(len(D)):
        for k in range(j + 1, len(D)):
            v = D[k] - D[j]; n = v @ v
            if n == 0: continue
            t = np.clip((U - D[j]) @ v / n, 0, 1)
            best = np.minimum(best, np.linalg.norm(D[j] + t[:, None] * v - U, axis=1))
    detail = cnt[best > 14].sum() / px.shape[0]
    dom_lab = to_lab(uniq[dom])
    dom_pal = classify(dom_lab)
    lab = to_lab(px)
    lbl = dom_pal[((lab[:, None, :] - dom_lab[None, :, :]) ** 2).sum(-1).argmin(1)]
    used = sorted(set(dom_pal.tolist()))
    if not (2 <= len(used) <= 6 and detail < 0.03):
        return None
    out = Image.fromarray(lbl.reshape(h, w).astype(np.uint8), "P")
    pal = [v for _, rgb in PALETTE for v in rgb]
    out.putpalette(pal + [0] * (768 - len(pal)))
    return out

def write_flags(set_dir, items, svg_of, max_w=600, h=360):
    os.makedirs(f"{set_dir}/flags", exist_ok=True)
    os.makedirs(f"{set_dir}/color", exist_ok=True)
    n = 0
    for it in items:
        svg = svg_of(it)
        img = render(svg, height=h)
        if img.width > max_w:
            img = render(svg, width=max_w)
        flatten(img).save(f"{set_dir}/flags/{it['code']}.webp", "WEBP", quality=88, method=6)
        tpl = color_template(svg)
        it["colorable"] = tpl is not None
        if tpl is not None:
            tpl.save(f"{set_dir}/color/{it['code']}.png", optimize=True)
            n += 1
    return n

# ---------------------------------------------------------------- geometry
def rdp(pts, eps):
    if len(pts) < 3: return pts
    a, b = np.array(pts[0]), np.array(pts[-1])
    p = np.array(pts)
    ab = b - a; n = np.hypot(*ab)
    dist = np.hypot(*(p - a).T) if n == 0 else np.abs(ab[0] * (p[:, 1] - a[1]) - ab[1] * (p[:, 0] - a[0])) / n
    i = int(dist.argmax())
    if dist[i] > eps:
        return rdp(pts[: i + 1], eps)[:-1] + rdp(pts[i:], eps)
    return [pts[0], pts[-1]]

def topo_rings(topo, obj):
    """Yields (geometry, [outer rings as point lists]) from a TopoJSON object."""
    tr = topo.get("transform")
    arcs = []
    for arc in topo["arcs"]:
        x = y = 0; pts = []
        for dx, dy in arc:
            if tr: x += dx; y += dy; pts.append((x * tr["scale"][0] + tr["translate"][0], y * tr["scale"][1] + tr["translate"][1]))
            else: pts.append((dx, dy))
        arcs.append(pts)
    def ring(idx):
        pts = []
        for i in idx:
            a = arcs[i] if i >= 0 else arcs[~i][::-1]
            pts.extend(a if not pts else a[1:])
        return pts
    for g in topo["objects"][obj]["geometries"]:
        polys = g["arcs"] if g["type"] == "MultiPolygon" else [g["arcs"]] if g["type"] == "Polygon" else []
        yield g, [ring(p[0]) for p in polys]

def encode(ring, eps, scale, project, wrap=False):
    s = rdp(ring, eps)
    if len(s) < 4: s = ring
    if len(s) < 4: return []
    copies = [s]
    if wrap:   # rings that cross the antimeridian: unwrap and draw a copy on both sides
        xs = [p[0] for p in s]
        if max(abs(xs[i + 1] - xs[i]) for i in range(len(xs) - 1)) > 180:
            s = [(p[0] + 360 if p[0] < 0 else p[0], p[1]) for p in s]
            copies = [s, [(p[0] - 360, p[1]) for p in s]]
    out = []
    for c in copies:
        out.append([round(v * scale) for p in c for v in project(*p)])
    return out

def write_map(set_dir, shapes, other, bounds, scale, cap_tol):
    json.dump({"scale": scale, "bounds": bounds, "capTol": cap_tol, "shapes": shapes, "other": other},
              open(f"{set_dir}/map.json", "w"), separators=(",", ":"))

def write_items(set_dir, items):
    json.dump(items, open(f"{set_dir}/items.json", "w"), ensure_ascii=False, separators=(",", ":"))

# ---------------------------------------------------------------- capitals
GC = geonamescache.GeonamesCache(min_city_population=500)
CITIES = list(GC.get_cities().values())

def latin(s):
    return all(ord(ch) < 0x250 for ch in s) and len(s) > 2

def find_city(cc, name, admin1=None):
    m = [c for c in CITIES if c["countrycode"] == cc and (admin1 is None or c["admin1code"] == admin1)
         and (c["name"] == name or name in c["alternatenames"])]
    return max(m, key=lambda c: c["population"]) if m else None

def capital_alts(city, name):
    if not city: return []
    alts = {city["name"], *[a for a in city["alternatenames"] if latin(a)]}
    alts.discard(name)
    return sorted(alts)[:25]

US_CAPITALS_ONLY = len(sys.argv) > 3 and sys.argv[3] == "--us-capitals"

# US states: postal code -> (name, census region, capital)
US = {  # postal: (name, census region, capital)
    "AL": ("Alabama", "South", "Montgomery"), "AK": ("Alaska", "West", "Juneau"),
    "AZ": ("Arizona", "West", "Phoenix"), "AR": ("Arkansas", "South", "Little Rock"),
    "CA": ("California", "West", "Sacramento"), "CO": ("Colorado", "West", "Denver"),
    "CT": ("Connecticut", "Northeast", "Hartford"), "DE": ("Delaware", "South", "Dover"),
    "FL": ("Florida", "South", "Tallahassee"), "GA": ("Georgia", "South", "Atlanta"),
    "HI": ("Hawaii", "West", "Honolulu"), "ID": ("Idaho", "West", "Boise"),
    "IL": ("Illinois", "Midwest", "Springfield"), "IN": ("Indiana", "Midwest", "Indianapolis"),
    "IA": ("Iowa", "Midwest", "Des Moines"), "KS": ("Kansas", "Midwest", "Topeka"),
    "KY": ("Kentucky", "South", "Frankfort"), "LA": ("Louisiana", "South", "Baton Rouge"),
    "ME": ("Maine", "Northeast", "Augusta"), "MD": ("Maryland", "South", "Annapolis"),
    "MA": ("Massachusetts", "Northeast", "Boston"), "MI": ("Michigan", "Midwest", "Lansing"),
    "MN": ("Minnesota", "Midwest", "Saint Paul"), "MS": ("Mississippi", "South", "Jackson"),
    "MO": ("Missouri", "Midwest", "Jefferson City"), "MT": ("Montana", "West", "Helena"),
    "NE": ("Nebraska", "Midwest", "Lincoln"), "NV": ("Nevada", "West", "Carson City"),
    "NH": ("New Hampshire", "Northeast", "Concord"), "NJ": ("New Jersey", "Northeast", "Trenton"),
    "NM": ("New Mexico", "West", "Santa Fe"), "NY": ("New York", "Northeast", "Albany"),
    "NC": ("North Carolina", "South", "Raleigh"), "ND": ("North Dakota", "Midwest", "Bismarck"),
    "OH": ("Ohio", "Midwest", "Columbus"), "OK": ("Oklahoma", "South", "Oklahoma City"),
    "OR": ("Oregon", "West", "Salem"), "PA": ("Pennsylvania", "Northeast", "Harrisburg"),
    "RI": ("Rhode Island", "Northeast", "Providence"), "SC": ("South Carolina", "South", "Columbia"),
    "SD": ("South Dakota", "Midwest", "Pierre"), "TN": ("Tennessee", "South", "Nashville"),
    "TX": ("Texas", "South", "Austin"), "UT": ("Utah", "West", "Salt Lake City"),
    "VT": ("Vermont", "Northeast", "Montpelier"), "VA": ("Virginia", "South", "Richmond"),
    "WA": ("Washington", "West", "Olympia"), "WV": ("West Virginia", "South", "Charleston"),
    "WI": ("Wisconsin", "Midwest", "Madison"), "WY": ("Wyoming", "West", "Cheyenne"),
}
if US_CAPITALS_ONLY:
    # Step 1 of 2: list capital coordinates so tools/project_us.mjs can project them.
    out = {}
    for code, (name, _, cap) in US.items():
        city = find_city("US", cap, code)
        out[code] = [city["longitude"], city["latitude"]]
    json.dump(out, sys.stdout); sys.exit()


# ================================================================= countries + territories
wc = json.load(open(f"{SRC}/world-countries/package/countries.json"))
FLAGS = f"{SRC}/flag-icons/package/flags/4x3"
SOVEREIGN_EXTRA = {"VA", "PS", "XK", "TW"}

def country_item(c):
    names = {c["name"]["common"], c["name"]["official"]}
    names.update(s for s in c.get("altSpellings", []) if len(s) > 3)
    for t in c.get("translations", {}).values(): names.add(t["common"])
    for n in c["name"].get("native", {}).values(): names.add(n["common"])
    names.discard(c["name"]["common"])
    it = {
        "code": c["cca2"].lower(), "name": c["name"]["common"], "alt": sorted(names),
        "group": c["region"], "x": round(c["latlng"][1], 2), "y": round(-c["latlng"][0], 2),
        "small": c.get("area", 0) < 2500,
    }
    if c.get("capital"):
        cap = c["capital"][0]
        city = find_city(c["cca2"], cap)
        it["capital"] = cap
        it["capitalAlt"] = capital_alts(city, cap)
        if city: it["cx"], it["cy"] = round(city["longitude"], 3), round(-city["latitude"], 3)
        else: it["cx"], it["cy"] = it["x"], it["y"]
    return it

sovereign = [c for c in wc if c.get("unMember") or c["cca2"] in SOVEREIGN_EXTRA]
sov_codes = {c["cca2"] for c in sovereign}
svg_bytes = {f[:-4]: open(f"{FLAGS}/{f}", "rb").read() for f in os.listdir(FLAGS)}
territories = []
for c in wc:
    code = c["cca2"]
    if code in sov_codes or code in ("AQ", "BV", "SJ", "UM", "HM") or code.lower() not in svg_bytes: continue
    # Leave out territories that simply fly another country's flag (Bouvet Island, Saint Martin, ...)
    if any(svg_bytes[code.lower()] == svg_bytes.get(s.lower()) for s in sov_codes): continue
    territories.append(c)
thumb = lambda code: np.asarray(flatten(render(f"{FLAGS}/{code.lower()}.svg", 64, 48)), dtype=float)
sov_thumbs = {s: thumb(s) for s in sov_codes if s.lower() in svg_bytes}
territories = [c for c in territories
               if min(np.abs(thumb(c["cca2"]) - t).mean() for t in sov_thumbs.values()) > 4]

topo = json.load(open(f"{SRC}/world-atlas/package/countries-50m.json"))
num2code = {c["ccn3"]: c["cca2"].lower() for c in wc if c.get("ccn3")}
world_rings = {}
for g, rings in topo_rings(topo, "countries"):
    if g.get("id") == "010": continue   # Antarctica
    name = g["properties"]["name"]
    code = num2code.get(g.get("id")) or {"Kosovo": "xk", "Somaliland": "so", "N. Cyprus": "cy"}.get(name, "?")
    enc = [r for ring in rings for r in encode(ring, 0.06, 10, lambda x, y: (x, -y), wrap=True)]
    world_rings.setdefault(code, []).extend(enc)

WORLD_BOUNDS = [-180, -84, 180, 58]
for set_name, members in (("countries", sovereign), ("territories", territories)):
    d = f"{OUT}/{set_name}"
    shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
    items = sorted((country_item(c) for c in members), key=lambda i: i["name"])
    codes = {i["code"] for i in items}
    # region-flags keeps each flag's real proportions (Monaco 4:5, Qatar 11:28); flag-icons squeezes all to 4:3.
    n = write_flags(d, items, lambda it: f"{SRC}/region-flags/package/svg/{it['code'].upper()}.svg")
    shapes = {k: v for k, v in world_rings.items() if k in codes}
    other = [r for k, v in world_rings.items() if k not in codes for r in v]
    write_map(d, shapes, other, WORLD_BOUNDS, 10, 2.5)
    write_items(d, items)
    print(f"{set_name}: {len(items)} items, {n} colourable, no shape: {sorted(codes - set(shapes))}", file=sys.stderr)

# ================================================================= US states
points = json.load(open(f"{SRC}/points.json"))   # code -> [x, y] in us-atlas Albers pixels
d = f"{OUT}/us"
shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
name2code = {v[0]: k for k, v in US.items()}
ustopo = json.load(open(f"{SRC}/us-atlas/package/states-albers-10m.json"))
us_shapes, us_other = {}, []
for g, rings in topo_rings(ustopo, "states"):
    code = name2code.get(g["properties"]["name"])
    enc = [r for ring in rings for r in encode(ring, 0.4, 1, lambda x, y: (x, y))]
    if code: us_shapes[code.lower()] = enc
    else: us_other.extend(enc)
items = []
for code, (name, region, cap) in sorted(US.items(), key=lambda kv: kv[1][0]):
    city = find_city("US", cap, code)
    rings = us_shapes[code.lower()]
    xs = [r[i] for r in rings for i in range(0, len(r), 2)]; ys = [r[i] for r in rings for i in range(1, len(r), 2)]
    items.append({
        "code": code.lower(), "name": name, "alt": [], "group": region,
        "x": round((min(xs) + max(xs)) / 2), "y": round((min(ys) + max(ys)) / 2), "small": False,
        "capital": cap, "capitalAlt": capital_alts(city, cap),
        "cx": round(points[code][0], 1), "cy": round(points[code][1], 1),
    })
n = write_flags(d, items, lambda it: f"{SRC}/region-flags/package/svg/US-{it['code'].upper()}.svg")
write_map(d, us_shapes, us_other, [-60, 10, 960, 610], 1, 12)
write_items(d, items)
print(f"us: {len(items)} items, {n} colourable", file=sys.stderr)

# ================================================================= Belgium
BE = f"{SRC}/belgium"
lst = json.load(open(f"{BE}/list.json"))
K = math.cos(math.radians(50.5))
proj = lambda lon, lat: (lon * K, -lat)
gj = json.load(open(f"{BE}/nuts2.geojson"))
be_shapes = {}
for f in gj["features"]:
    p = f["properties"]
    if p["CNTR_CODE"] != "BE": continue
    geom = f["geometry"]
    polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
    be_shapes[p["NUTS_ID"].lower()] = [r for poly in polys for r in encode([tuple(q) for q in poly[0]], 0.002, 1000, proj)]
d = f"{OUT}/belgium"
shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
items = []
lang_alt = lambda *ns: sorted(set(ns))
for code, en, nl, fr, svg, cap in lst["provinces"]:
    city = find_city("BE", cap) or find_city("BE", {"Antwerp": "Antwerpen", "Ghent": "Gent", "Bruges": "Brugge", "Brussels": "Brussels"}.get(cap, cap))
    cap_names = next(([c[0], c[1], c[2]] for c in lst["cities"] if c[0] == cap), [cap])
    rings = be_shapes[code.lower()]
    xs = [r[i] for r in rings for i in range(0, len(r), 2)]; ys = [r[i] for r in rings for i in range(1, len(r), 2)]
    cx, cy = proj(city["longitude"], city["latitude"])
    items.append({
        "code": code.lower(), "name": en, "names": {"nl": nl, "fr": fr}, "alt": [a for a in lang_alt(nl, fr, "Province of " + en, "Provincie " + nl, "Province de " + fr) if a != en],
        "group": "Provinces", "x": round((min(xs) + max(xs)) / 2000, 4), "y": round((min(ys) + max(ys)) / 2000, 4), "small": False,
        "capital": cap, "capitalNames": {"nl": cap_names[1] if len(cap_names) > 1 else cap, "fr": cap_names[2] if len(cap_names) > 2 else cap},
        "capitalAlt": [a for a in set(cap_names + capital_alts(city, cap)) if a != cap][:25],
        "cx": round(cx, 4), "cy": round(cy, 4), "svg": svg,
    })
for en, nl, fr, svg, prov in lst["cities"]:
    city = find_city("BE", en) or find_city("BE", nl) or find_city("BE", fr)
    if en == "Brussels": city = find_city("BE", "Brussels") or city
    x, y = proj(city["longitude"], city["latitude"])
    items.append({
        "code": "c-" + en.lower().replace(" ", "-").replace("è", "e"), "name": en, "names": {"nl": nl, "fr": fr},
        "alt": [a for a in lang_alt(nl, fr) if a != en], "group": "Cities", "in": prov.lower(),
        "x": round(x, 4), "y": round(y, 4), "small": True, "svg": svg,
    })
have = lambda it: os.path.exists(f := f"{BE}/svg/{it['svg'].replace(' ', '_')}") and os.path.getsize(f) > 0
missing = [it["name"] for it in items if not have(it)]
if missing:
    print("WARNING: Belgian flags not downloaded yet, left out:", missing, file=sys.stderr)
    items = [it for it in items if have(it)]
n = write_flags(d, items, lambda it: f"{BE}/svg/{it.pop('svg').replace(' ', '_')}")
xs = [r[i] / 1000 for v in be_shapes.values() for r in v for i in range(0, len(r), 2)]
ys = [r[i] / 1000 for v in be_shapes.values() for r in v for i in range(1, len(r), 2)]
# Neighbouring countries as grey land, from the world outlines (stored as degrees * 10)
neighbours = [[round(v / 10 * (K if i % 2 == 0 else 1) * 1000) for i, v in enumerate(r)]
              for c in ("nl", "de", "fr", "lu", "gb") for r in world_rings.get(c, [])]
write_map(d, be_shapes, neighbours, [min(xs) - 0.05, min(ys) - 0.05, max(xs) + 0.05, max(ys) + 0.05], 1000, 0.05)
write_items(d, items)
print(f"belgium: {len(items)} items, {n} colourable", file=sys.stderr)
