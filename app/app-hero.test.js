const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");
const assert = require("node:assert/strict");

function loadHeroFunctions(fetchImpl) {
  const source = fs.readFileSync(path.join(__dirname, "app.js"), "utf8");
  const lines = source.split("\n");
  const escStart = lines.findIndex((line) => line.startsWith("const HTML_ENTITIES"));
  const escEnd = lines.findIndex((line) => line.startsWith("const cssToken"));
  const heroStart = lines.findIndex((line) => line.startsWith("function themedHero"));
  const heroEnd = lines.findIndex((line) => line.startsWith("async function openMountainDetail"));
  assert.ok(escStart >= 0 && escEnd > escStart, "escape helpers should be discoverable");
  assert.ok(heroStart >= 0 && heroEnd > heroStart, "hero image helpers should be discoverable");

  const module = { exports: {} };
  vm.runInNewContext(
    `${lines.slice(escStart, escEnd).join("\n")}
${lines.slice(heroStart, heroEnd).join("\n")}
module.exports = { themedHero, loadHero };`,
    { module, fetch: fetchImpl },
  );
  return module.exports;
}

test("loadHero uses the same-origin proxy and restores the generated fallback when it fails", async () => {
  const { loadHero } = loadHeroFunctions(async () => {
    throw new Error("loadHero should not fetch external image metadata from the browser");
  });
  const assigned = [];
  const img = {
    _src: "",
    onerror: null,
    set src(value) {
      this._src = value;
      assigned.push(value);
    },
    get src() {
      return this._src;
    },
  };

  await loadHero(img, "북한산", 836);

  assert.match(assigned[0], /^data:image\/svg\+xml;charset=utf-8,/);
  assert.equal(img.src, "/api/v1/mountain-hero?name=%EB%B6%81%ED%95%9C%EC%82%B0&height=836");
  assert.equal(typeof img.onerror, "function");

  img.onerror();

  assert.match(img.src, /^data:image\/svg\+xml;charset=utf-8,/);
});
