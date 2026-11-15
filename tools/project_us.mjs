// Projects [lon, lat] points with the same Albers USA projection us-atlas uses for its pre-projected files.
// Usage: node tools/project_us.mjs < capitals.json > points.json   (needs: npm i d3-geo)
import { geoAlbersUsa } from "d3-geo";
const input = JSON.parse(await new Promise(r => { let s = ""; process.stdin.on("data", d => s += d).on("end", () => r(s)); }));
const p = geoAlbersUsa().scale(1300).translate([487.5, 305]);
const out = Object.fromEntries(Object.entries(input).map(([k, v]) => [k, p(v)]));
process.stdout.write(JSON.stringify(out));
