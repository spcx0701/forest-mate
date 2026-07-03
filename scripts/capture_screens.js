/* 현재 Contour 디자인 앱의 5개 폰 화면 + 관제 대시보드를 PNG로 캡처.
   사용: node scripts/capture_screens.js   (dev 서버가 localhost:PORT 에 떠 있어야 함) */
const puppeteer = require("puppeteer-core");
const path = require("node:path");
const fs = require("node:fs");

const CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const BASE = process.env.BASE || "http://localhost:8770";
const OUT = process.env.OUT || "/tmp/fm_shots";
fs.mkdirSync(OUT, { recursive: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function capturePhone(browser, name, setup, { screen = name, finalize = () => {} } = {}) {
  const page = await browser.newPage();
  await page.setViewport({ width: 430, height: 932, deviceScaleFactor: 2 });
  // ?t=<screen> → FTUE(온보딩 로그인) 모달을 건너뛰고 해당 화면으로 진입 (app.js:2396)
  await page.goto(`${BASE}/?t=${screen}`, { waitUntil: "networkidle2" });
  await sleep(1000); // 초기 렌더/시드
  await page.evaluate(() => { const o = document.getElementById("onboard"); if (o) o.classList.remove("show"); });
  await page.evaluate(setup);
  await sleep(1500); // 전환·지도·애니메이션·async 렌더 안정화
  await page.evaluate(finalize); // async 렌더 끝난 뒤 마지막 보정
  await page.evaluate(() => {
    const o = document.getElementById("onboard"); if (o) o.classList.remove("show");
    const tt = document.getElementById("toast"); if (tt) { tt.classList.remove("show"); tt.style.display = "none"; }
    window.scrollTo(0, 0);
  });
  await sleep(300);
  const file = path.join(OUT, `app_${name}.png`);
  await page.screenshot({ path: file, type: "png" });
  console.log("shot", file);
  await page.close();
}

(async () => {
  const browser = await puppeteer.launch({
    executablePath: CHROME, headless: "new",
    args: ["--no-sandbox", "--hide-scrollbars", "--force-device-scale-factor=2"],
  });

  await capturePhone(browser, "home", () => { try { show("home"); } catch (e) {} });

  await capturePhone(browser, "trail", () => {
    try {
      selectCourse("bukhansan");
      startHike();
      for (let i = 0; i < 3; i++) demoStep();
      show("trail");
    } catch (e) { console.log("trail setup err", e.message); }
  });

  // AI: init에서 seedChat이 이미 1회 실행됨 — 재호출하면 대화가 중복되므로 호출하지 않음
  await capturePhone(browser, "ai", () => { try { show("ai"); } catch (e) {} });

  await capturePhone(browser, "sos", () => { try { show("sos"); } catch (e) {} });

  // 리포트가 채워진 모습(데이터 있는 사용자 UI 시연) — 앱의 실제 렌더 함수 사용.
  // renderMy()가 async라 setup 직후 덮어도 뒤늦게 0으로 되돌아갈 수 있어 finalize(안정화 후)에서 적용.
  const seedMy = () => {
    try {
      if (typeof S !== "undefined") { S.profile.name = "산친구"; S.aiCount = 12; }
      window.lastSum = {
        badges: [{ earned: true }, { earned: true }, { earned: true }, { earned: true }],
        regions: 3, distinct_courses: 4,
      };
      const summary = {
        days: 96, level: 7, cnt: 4, km: 32.5, kcal: 8420, co2: 12.4,
        bars: [{ h: 32, label: "1월" }, { h: 54, label: "2월" }, { h: 40, label: "3월" },
               { h: 76, label: "4월" }, { h: 62, label: "5월" }, { h: 92, label: "6월" }],
      };
      if (typeof renderProfileSummary === "function") renderProfileSummary(summary);
      if (typeof renderProfileExtra === "function") renderProfileExtra();
    } catch (e) { console.log("my seed err", e.message); }
  };
  await capturePhone(browser, "my", () => { try { show("my"); } catch (e) {} }, { finalize: seedMy });

  // 관제 대시보드 (웹) — 3000x1960 비율.
  // /api/v1/dashboard/summary 를 차단해 cloudBridge(실데이터 0)를 막고 시연 시뮬레이션(3,482명 등) 유지.
  const d = await browser.newPage();
  await d.setViewport({ width: 1500, height: 980, deviceScaleFactor: 2 });
  await d.goto(BASE + "/dashboard.html", { waitUntil: "networkidle2" });
  await sleep(2600); // 히트맵 애니메이션·렌더 안정화 (cloudBridge가 실데이터 0으로 세팅)
  // 관제 시연 상태 주입: 앱의 데모 콘텐츠를 그대로 DOM에 채움 (KPI·LIVE 배지·이벤트 피드)
  await d.evaluate(() => {
    const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    set("kpiHikers", "3,482"); set("kpiAlerts", "1,294"); set("kpiSos", "1");
    const badge = document.getElementById("liveBadge"); if (badge) badge.style.display = "inline-flex";
    const feed = document.getElementById("feed");
    if (feed) {
      const evs = [
        ["sos", "🚨", "SOS 접수 — 60대 남성, 인수봉 동면", "국가지점번호 다사 5702 2788 · 구조대 ETA 9분", "16:41"],
        ["warn", "🤖", "AI 조난위험 — 이동 정지 감지", "사용자 응답 대기 · 단계 격상 임계 12분", "16:33"],
        ["warn", "⚠️", "위험구간 진입 경고 발송", "Y계곡 우회 안내 · 수락률 91%", "16:18"],
        ["ok", "✅", "조난 의심 해제", "휴식 확인(사용자 응답) — 모니터링 종료", "15:52"],
        ["ok", "📢", "일몰 안내 일괄 발송", "하산 권고 푸시 — 대상 1,800여 명", "15:30"],
      ];
      feed.innerHTML = evs.map(([c, i, t, b, tm]) =>
        `<div class="ev ${c}"><div class="ic">${i}</div><div><b>${t}</b><span>${b}</span></div><time>${tm}</time></div>`).join("");
    }
  });
  await sleep(250);
  await d.screenshot({ path: path.join(OUT, "dashboard.png"), type: "png" });
  console.log("shot dashboard");
  await d.close();

  await browser.close();
  console.log("DONE");
})().catch((e) => { console.error(e); process.exit(1); });
