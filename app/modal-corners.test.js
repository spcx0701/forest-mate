const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const appDir = __dirname;

test("condition detail sheets clip the full-bleed panel instead of exposing white sheet corners", () => {
  const css = fs.readFileSync(path.join(appDir, "app.css"), "utf8");

  assert.match(
    css,
    /#extSheet:has\(>\.condition-panel\)\{[^}]*padding:0[^}]*background:transparent/s,
  );
  assert.match(css, /\.condition-panel\{[^}]*margin:0;/s);
});

test("condition detail sheets keep the sheet as the scroll owner for drag bounce", () => {
  const css = fs.readFileSync(path.join(appDir, "app.css"), "utf8");

  assert.match(
    css,
    /#extSheet:has\(>\.condition-panel\)\{[^}]*overflow-y:auto[^}]*-webkit-overflow-scrolling:touch/s,
  );
  assert.doesNotMatch(css, /#extSheet:has\(>\.condition-panel\)\{[^}]*overflow:hidden/s);
  assert.doesNotMatch(css, /\.condition-panel\{[^}]*overflow-y:auto/s);
  assert.match(css, /\.condition-panel\{[^}]*overflow:hidden/s);
});

test("modal corner css changes are cache-busted in the app shell and service worker", () => {
  const index = fs.readFileSync(path.join(appDir, "index.html"), "utf8");
  const sw = fs.readFileSync(path.join(appDir, "sw.js"), "utf8");

  assert.match(index, /app\.css\?v=20260618-modal-bounce/);
  assert.match(index, /app\.js\?v=20260618-local-leaflet/);
  assert.match(sw, /app\.css\?v=20260618-modal-bounce/);
  assert.match(sw, /app\.js\?v=20260618-local-leaflet/);
});
