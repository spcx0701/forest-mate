const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const appDir = __dirname;
const cssVersion = "20260703-bottom-nav-safe-area";

function cssRule(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = css.match(new RegExp(`${escaped}\\{(?<body>[^}]*)\\}`, "s"));
  assert.ok(match, `${selector} rule should exist`);
  return match.groups.body;
}

test("bottom navigation caps Android safe-area inset instead of floating above content", () => {
  const css = fs.readFileSync(path.join(appDir, "app.css"), "utf8");
  const root = cssRule(css, ":root");
  const phone = cssRule(css, "#phone");
  const screen = cssRule(css, ".screen");
  const nav = cssRule(css, "nav");

  assert.match(root, /--bottom-nav-safe-inset:min\(env\(safe-area-inset-bottom\),18px\)/);
  assert.match(root, /--bottom-nav-height:calc\(66px \+ var\(--bottom-nav-safe-inset\)\)/);
  assert.match(phone, /height:100vh; height:100dvh/);
  assert.match(phone, /max-height:none/);
  assert.doesNotMatch(phone, /max-height:932px/);
  assert.match(screen, /padding:18px 18px calc\(var\(--bottom-nav-height\) \+ 18px\)/);
  assert.match(nav, /padding:8px 6px calc\(10px \+ var\(--bottom-nav-safe-inset\)\)/);
  assert.doesNotMatch(nav, /env\(safe-area-inset-bottom\)/);
});

test("bottom navigation css changes are cache-busted in the app shell and service worker", () => {
  const index = fs.readFileSync(path.join(appDir, "index.html"), "utf8");
  const sw = fs.readFileSync(path.join(appDir, "sw.js"), "utf8");

  assert.match(index, new RegExp(`app\\.css\\?v=${cssVersion}`));
  assert.match(sw, new RegExp(`app\\.css\\?v=${cssVersion}`));
});
