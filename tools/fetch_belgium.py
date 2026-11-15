#!/usr/bin/env python3
"""Downloads the Belgian data that is not on npm into <dir> (used as <src>/belgium by build_assets.py).

  list.json        copy of tools/belgium_list.json (provinces, cities, names, flag files)
  svg/             flag SVGs from Wikimedia Commons (see tools/belgium_flags.json for licences)
  nuts2.geojson    Eurostat GISCO NUTS level 2 regions (= Belgian provinces), © EuroGeographics

Wikimedia rate-limits downloads, so this waits as long as it is asked to and can be re-run;
files already present are skipped.

Usage: python3 tools/fetch_belgium.py <dir>
"""
import json, os, shutil, sys, time, urllib.error, urllib.request

UA = {"User-Agent": "FlagGameAssetBuilder/1.0 (https://github.com/jmartens90/flaggame2; educational flag quiz)"}
HERE = os.path.dirname(os.path.abspath(__file__))
out = sys.argv[1]
os.makedirs(f"{out}/svg", exist_ok=True)
shutil.copy(f"{HERE}/belgium_list.json", f"{out}/list.json")

def get(url):
    for attempt in range(30):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120).read()
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            wait = int(e.headers.get("Retry-After") or 0) or 30 * (attempt + 1)
            print(f"rate limited, waiting {wait}s", file=sys.stderr)
            time.sleep(wait + 5)
    raise RuntimeError(f"gave up on {url}")

nuts = f"{out}/nuts2.geojson"
if not os.path.exists(nuts):
    open(nuts, "wb").write(get("https://gisco-services.ec.europa.eu/distribution/v2/nuts/geojson/NUTS_RG_01M_2021_4326_LEVL_2.geojson"))

for name, meta in json.load(open(f"{HERE}/belgium_flags.json")).items():
    path = f"{out}/svg/{name.replace(' ', '_')}"
    if os.path.exists(path) and os.path.getsize(path) > 0: continue
    data = get(meta["url"])
    open(path, "wb").write(data)
    print("downloaded", name, file=sys.stderr)
    time.sleep(30)
