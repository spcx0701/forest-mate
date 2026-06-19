const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const appDir = __dirname;
const leafletVersion = "20260618-local-leaflet";
const cssVersion = "20260618-modal-bounce";

test("app shell loads Leaflet from same-origin vendored assets", () => {
  const index = fs.readFileSync(path.join(appDir, "index.html"), "utf8");

  assert.doesNotMatch(index, /https:\/\/unpkg\.com\/leaflet/);
  assert.match(index, new RegExp(`href="vendor/leaflet/leaflet\\.css\\?v=${leafletVersion}"`));
  assert.match(index, new RegExp(`src="vendor/leaflet/leaflet\\.js\\?v=${leafletVersion}"`));
  assert.match(index, new RegExp(`app\\.css\\?v=${cssVersion}`));
  assert.match(index, new RegExp(`app\\.js\\?v=${leafletVersion}`));
});

test("service worker precaches the same local Leaflet assets", () => {
  const sw = fs.readFileSync(path.join(appDir, "sw.js"), "utf8");

  assert.match(sw, /const CACHE = "forestmate-v37";/);
  assert.match(sw, /contour\.css\?v=/);
  assert.match(sw, new RegExp(`vendor/leaflet/leaflet\\.css\\?v=${leafletVersion}`));
  assert.match(sw, new RegExp(`vendor/leaflet/leaflet\\.js\\?v=${leafletVersion}`));
  assert.match(sw, /vendor\/leaflet\/images\/marker-icon\.png/);
  assert.match(sw, /vendor\/leaflet\/images\/marker-icon-2x\.png/);
  assert.match(sw, /vendor\/leaflet\/images\/marker-shadow\.png/);
  assert.match(sw, /vendor\/leaflet\/images\/layers\.png/);
  assert.match(sw, /vendor\/leaflet\/images\/layers-2x\.png/);
  assert.match(sw, new RegExp(`app\\.css\\?v=${cssVersion}`));
  assert.match(sw, new RegExp(`app\\.js\\?v=${leafletVersion}`));
});

test("condition detail map does not ship a static fallback that can mask Leaflet failures", () => {
  const appJs = fs.readFileSync(path.join(appDir, "app.js"), "utf8");
  const appCss = fs.readFileSync(path.join(appDir, "app.css"), "utf8");

  assert.doesNotMatch(appJs, /conditionStaticMap|condition-static-map/);
  assert.doesNotMatch(appCss, /condition-static-map|static-zone|static-contour|static-route/);
  assert.match(
    appJs,
    /<div id="conditionLeafletMap" class="condition-leaflet-map" role="img" aria-label="\$\{esc\(map\.title\)\}"><\/div>/,
  );
});

test("vendored Leaflet assets exist in the app bundle", () => {
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/leaflet.css")));
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/leaflet.js")));
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/images/marker-icon.png")));
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/images/marker-icon-2x.png")));
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/images/marker-shadow.png")));
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/images/layers.png")));
  assert.ok(fs.existsSync(path.join(appDir, "vendor/leaflet/images/layers-2x.png")));
});
