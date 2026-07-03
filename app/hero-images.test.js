const test = require("node:test");
const assert = require("node:assert/strict");

test("hero image loader uses the same-origin proxy and restores the generated fallback when it fails", async () => {
  const { loadHeroImage } = require("./hero-images.js");
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

  await loadHeroImage(img, "북한산", 836);

  assert.match(assigned[0], /^data:image\/svg\+xml;charset=utf-8,/);
  assert.equal(img.src, "/api/v1/mountain-hero?name=%EB%B6%81%ED%95%9C%EC%82%B0&height=836");
  assert.equal(typeof img.onerror, "function");

  img.onerror();

  assert.match(img.src, /^data:image\/svg\+xml;charset=utf-8,/);
  assert.equal(assigned.length, 3);
});
