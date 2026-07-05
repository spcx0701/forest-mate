const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

const SCRIPT = path.join(__dirname, "capture_screens.js");
const ROOT = path.dirname(__dirname);

async function loadCaptureModule(env = {}) {
  const code = fs.readFileSync(SCRIPT, "utf8");
  const module = { exports: {} };
  const state = { launched: false, exitCode: null };
  const sandbox = {
    __dirname,
    __filename: SCRIPT,
    clearTimeout,
    console,
    exports: module.exports,
    module,
    process: {
      env: { ...process.env, ...env },
      exit(code) {
        state.exitCode = code;
      },
    },
    require(id) {
      if (id === "puppeteer-core") {
        return {
          launch() {
            state.launched = true;
            throw new Error("browser launch should stay inside CLI main");
          },
        };
      }
      return require(id);
    },
    setTimeout,
  };

  vm.runInNewContext(code, sandbox, { filename: SCRIPT });
  await new Promise((resolve) => setImmediate(resolve));
  return { exports: module.exports, state };
}

test("capture script does not launch Chrome when imported for tests", async () => {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), "forestmate-capture-test-"));
  const loaded = await loadCaptureModule({ OUT: out });

  assert.equal(loaded.state.launched, false);
  assert.equal(loaded.state.exitCode, null);
  assert.equal(typeof loaded.exports.resolveOutputDir, "function");
});

test("capture output defaults to a repo-local private artifact directory", async () => {
  const loaded = await loadCaptureModule({ OUT: "" });
  const resolved = loaded.exports.resolveOutputDir({ cwd: ROOT, env: {} });

  assert.equal(resolved, path.join(ROOT, "artifacts", "screenshots"));
  assert.equal(resolved.startsWith(os.tmpdir()), false);
});
