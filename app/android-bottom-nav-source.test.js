const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const mainActivity = fs.readFileSync(
  path.join(__dirname, "../packaging/android/app/src/main/java/kr/forestmate/app/MainActivity.kt"),
  "utf8",
);

test("native Android tab bar does not float above system navigation", () => {
  assert.match(mainActivity, /BottomNavLayout\.rootBottomPaddingPx\(\)/);
  assert.match(mainActivity, /BottomNavLayout\.tabContentSafeBottomInsetPx\(/);
  assert.doesNotMatch(mainActivity, /setPadding\(0,\s*0,\s*0,\s*navigationBarHeight\(\)\)/);
});
