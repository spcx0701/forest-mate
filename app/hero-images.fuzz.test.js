const test = require("node:test");
const assert = require("node:assert/strict");
const fc = require("fast-check");

const { heroProxyUrl } = require("./hero-images.js");

test("hero proxy URL keeps fuzzed names and heights in a same-origin encoded request", () => {
  fc.assert(
    fc.property(
      fc.string(),
      fc.double({ min: -10000, max: 10000, noNaN: true, noDefaultInfinity: true }),
      (name, height) => {
        const url = heroProxyUrl(name, height);
        const parsed = new URL(url, "https://forestmate.local");
        const encodedName = parsed.searchParams.get("name");
        const encodedHeight = Number(parsed.searchParams.get("height"));

        assert.equal(parsed.origin, "https://forestmate.local");
        assert.equal(parsed.pathname, "/api/v1/mountain-hero");
        assert.ok(encodedName.length > 0);
        assert.doesNotMatch(encodedName, /\s/);
        assert.ok(Number.isInteger(encodedHeight));
        assert.ok(encodedHeight >= 0);
      },
    ),
  );
});
