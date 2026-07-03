const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const mainActivity = fs.readFileSync(
  path.join(__dirname, "../packaging/android/app/src/main/java/kr/forestmate/app/MainActivity.kt"),
  "utf8",
);
const bottomNavLayout = fs.readFileSync(
  path.join(__dirname, "../packaging/android/app/src/main/java/kr/forestmate/app/ui/BottomNavLayout.kt"),
  "utf8",
);

test("native Android tab bar does not float above system navigation", () => {
  assert.match(mainActivity, /BottomNavLayout\.rootBottomPaddingPx\(\)/);
  assert.match(
    mainActivity,
    /BottomNavLayout\.tabContentSafeBottomInsetPx\(\s*navigationBarHeight\(\),\s*Contour\.dp\(this,\s*BottomNavLayout\.maxVisualSafeBottomInsetDp\),\s*\)/s,
  );
  assert.doesNotMatch(mainActivity, /setPadding\(0,\s*0,\s*0,\s*navigationBarHeight\(\)\)/);
});

test("native Android bottom nav caps large visual safe area insets", () => {
  assert.match(bottomNavLayout, /maxVisualSafeBottomInsetDp\s*=\s*40f/);
  assert.match(bottomNavLayout, /coerceAtMost\(maxInsetPx\.coerceAtLeast\(0\)\)/);
});
