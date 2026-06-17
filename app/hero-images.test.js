const test = require("node:test");
const assert = require("node:assert/strict");

test("hero image loader restores the generated fallback when a remote thumbnail fails", async () => {
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
  const fetchImpl = async () => ({
    ok: true,
    json: async () => ({
      thumbnail: { source: "https://upload.wikimedia.org/example/bukhansan.jpg" },
    }),
  });

  await loadHeroImage(img, "북한산", 836, { fetchImpl });

  assert.match(assigned[0], /^data:image\/svg\+xml;charset=utf-8,/);
  assert.equal(img.src, "https://upload.wikimedia.org/example/bukhansan.jpg");
  assert.equal(typeof img.onerror, "function");

  img.onerror();

  assert.match(img.src, /^data:image\/svg\+xml;charset=utf-8,/);
  assert.equal(assigned.length, 3);
});
