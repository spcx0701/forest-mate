/* 숲길동무 — 2026 산림 공공데이터·AI 활용 창업경진대회 2차 발표자료 (15장) */
const pptxgen = require("pptxgenjs");
const fs = require("node:fs");
const path = require("node:path");
const React = require("react");
const RDS = require("react-dom/server");
const sharp = require("sharp");
const FA = require("react-icons/fa");

const ASSETS = "/Users/dong9733/Documents/LYT Kit 2/forest-mate/assets";
const OUT = "/Users/dong9733/Documents/LYT Kit 2/forest-mate/deliverables/숲길동무_발표자료.pptx";

const PINE = "1B4332", MOSS = "2D6A4F", LEAF = "40916C", MINT = "74C69D",
      PALE = "D8F3DC", LIGHT = "F2F7F1", INK = "1D2B22", SUB = "6B7F72",
      AMBER = "F4A261", AMBERD = "B35309", RED = "E63946", SKY = "457B9D", WHITE = "FFFFFF";
const F = "Malgun Gothic";
const W = 13.33, H = 7.5;

const px = (p) => path.join(ASSETS, p);
function pngDim(p) { const b = fs.readFileSync(px(p)); return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) }; }
function imgH(p, wIn) { const d = pngDim(p); return wIn * d.h / d.w; }
function imgW(p, hIn) { const d = pngDim(p); return hIn * d.w / d.h; }
const mkShadow = () => ({ type: "outer", color: "1B4332", blur: 10, offset: 3, angle: 135, opacity: 0.18 });

async function icon(Comp, color, size = 256) {
  const svg = RDS.renderToStaticMarkup(React.createElement(Comp, { color: "#" + color, size: String(size) }));
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  return "image/png;base64," + buf.toString("base64");
}

(async () => {
  const I = {
    shield: await icon(FA.FaShieldAlt, WHITE),
    shieldG: await icon(FA.FaShieldAlt, MOSS),
    route: await icon(FA.FaRoute, MOSS),
    robot: await icon(FA.FaRobot, MOSS),
    radar: await icon(FA.FaSatelliteDish, MOSS),
    sos: await icon(FA.FaPhoneAlt, WHITE),
    sosR: await icon(FA.FaPhoneAlt, RED),
    city: await icon(FA.FaCity, MOSS),
    leaf: await icon(FA.FaLeaf, MINT),
    leafD: await icon(FA.FaLeaf, MOSS),
    chart: await icon(FA.FaChartLine, MOSS),
    users: await icon(FA.FaUsers, AMBERD),
    warn: await icon(FA.FaExclamationTriangle, RED),
    warnW: await icon(FA.FaExclamationTriangle, WHITE),
    mountain: await icon(FA.FaMountain, MOSS),
    db: await icon(FA.FaDatabase, MOSS),
    bell: await icon(FA.FaBell, MOSS),
    heart: await icon(FA.FaHeartbeat, RED),
    globe: await icon(FA.FaGlobeAsia, MOSS),
    cloud: await icon(FA.FaCloudSunRain, MOSS),
    map: await icon(FA.FaMapMarkedAlt, MOSS),
    hands: await icon(FA.FaHandshake, MOSS),
    seed: await icon(FA.FaSeedling, MOSS),
    won: await icon(FA.FaCoins, MOSS),
    eye: await icon(FA.FaEye, MOSS),
    clock: await icon(FA.FaClock, WHITE),
    clockA: await icon(FA.FaClock, AMBERD),
    check: await icon(FA.FaCheckCircle, MOSS),
    star: await icon(FA.FaStar, AMBERD),
  };

  const pres = new pptxgen();
  pres.layout = "LAYOUT_WIDE";
  pres.author = "ForestMate Team";
  pres.title = "숲길동무 — 2026 산림 공공데이터·AI 활용 창업경진대회";

  let pageNo = 0;
  function newSlide(dark = false) {
    const s = pres.addSlide();
    s.background = { color: dark ? PINE : WHITE };
    pageNo++;
    if (pageNo > 1) {
      s.addText(`숲길동무 ForestMate — 2026 산림 공공데이터·AI 활용 창업경진대회`, {
        x: 0.6, y: H - 0.42, w: 8, h: 0.3, fontSize: 9, fontFace: F,
        color: dark ? MINT : SUB, align: "left" });
      s.addText(String(pageNo), { x: W - 1, y: H - 0.42, w: 0.4, h: 0.3, fontSize: 10, fontFace: F, color: dark ? MINT : SUB, align: "right" });
    }
    return s;
  }
  function kickTitle(s, kicker, title, dark = false, y = 0.42) {
    s.addText(kicker, { x: 0.62, y, w: 11, h: 0.32, fontSize: 13, bold: true, fontFace: F, color: dark ? MINT : LEAF, charSpacing: 2, margin: 0 });
    s.addText(title, { x: 0.6, y: y + 0.32, w: 12.1, h: 0.66, fontSize: 27, bold: true, fontFace: F, color: dark ? WHITE : INK, margin: 0 });
  }
  function card(s, x, y, w, h, fill = WHITE) {
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, fill: { color: fill }, rectRadius: 0.09, shadow: mkShadow(), line: { color: "E3EAE4", width: 0.75 } });
  }
  function chip(s, x, y, w, text, fill = PALE, color = PINE, fontSize = 10.5) {
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h: 0.34, fill: { color: fill }, rectRadius: 0.17, line: { type: "none" } });
    s.addText(text, { x, y: y - 0.012, w, h: 0.36, fontSize, bold: true, fontFace: F, color, align: "center", valign: "middle", margin: 0 });
  }
  function iconCircle(s, data, x, y, d = 0.62, fill = PALE) {
    s.addShape(pres.shapes.OVAL, { x, y, w: d, h: d, fill: { color: fill }, line: { type: "none" } });
    const pad = d * 0.27;
    s.addImage({ data, x: x + pad, y: y + pad, w: d - 2 * pad, h: d - 2 * pad });
  }
  function phone(s, file, x, y, h) {
    const w = imgW(file, h);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: x - 0.07, y: y - 0.07, w: w + 0.14, h: h + 0.14, fill: { color: "0E1F15" }, rectRadius: 0.18, shadow: mkShadow() });
    s.addImage({ path: px(file), x, y, w, h, rounding: false });
    return w;
  }
  const srcText = (s, text, x, y, w, dark = false) =>
    s.addText(text, { x, y, w, h: 0.28, fontSize: 9, fontFace: F, color: dark ? MINT : SUB, italic: true, margin: 0 });

  /* ---------------- S1 표지 ---------------- */
  {
    const s = newSlide(true);
    // right phones
    const ph = 5.9;
    const w2 = imgW("app_trail.png", 5);
    s.addImage({ path: px("app_trail.png"), x: 10.55, y: 1.35, w: w2, h: 5, transparency: 12 });
    phone(s, "app_home.png", 8.45, 0.95, ph);
    s.addText("「2026년 산림 공공데이터·AI 활용 창업경진대회」  제품 및 서비스 개발 부문", {
      x: 0.75, y: 0.95, w: 7.4, h: 0.4, fontSize: 14, fontFace: F, color: MINT, bold: true, margin: 0 });
    s.addText("숲길동무", { x: 0.68, y: 1.75, w: 7.4, h: 1.5, fontSize: 80, bold: true, fontFace: F, color: WHITE, margin: 0 });
    s.addText("ForestMate — 산림 공공데이터·AI 기반 국민 산행 안전 플랫폼", {
      x: 0.75, y: 3.35, w: 7.4, h: 0.5, fontSize: 18, fontFace: F, color: PALE, bold: true, margin: 0 });
    s.addText([
      { text: "맞춤 추천 → 위험 경고 → 조난 자동 감지 → 관제 대응,\n", options: { color: "CFE7D6" } },
      { text: "산행의 전 과정을 하나의 데이터 파이프라인으로 지킵니다.", options: { color: "CFE7D6" } },
    ], { x: 0.75, y: 4.05, w: 7.2, h: 0.85, fontSize: 14.5, fontFace: F, margin: 0, lineSpacingMultiple: 1.25 });
    chip(s, 0.75, 5.3, 2.45, "산림 공공데이터 10종 융합", "FFFFFF", PINE, 11);
    chip(s, 3.35, 5.3, 1.9, "AI 엔진 5종 탑재", "FFFFFF", PINE, 11);
    chip(s, 5.4, 5.3, 2.5, "iOS·Android·웹 동시 지원", "FFFFFF", PINE, 11);
    s.addText([
      { text: "팀명  ", options: { color: MINT, bold: true } },
      { text: "【팀명 기재】", options: { color: WHITE } },
      { text: "      2026. 6.", options: { color: "9DBFA9" } },
    ], { x: 0.75, y: 6.35, w: 7, h: 0.4, fontSize: 13, fontFace: F, margin: 0 });
  }

  /* ---------------- S2 문제 1 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "PROBLEM 01", "해마다 1만 건 — 산에서 골든타임이 사라지고 있습니다");
    // left stat card
    card(s, 0.6, 1.78, 4.1, 4.7, LIGHT);
    iconCircle(s, I.warn, 0.95, 2.12, 0.66, "FDECEC");
    s.addText("연평균 산악사고 구조활동", { x: 0.95, y: 2.95, w: 3.5, h: 0.35, fontSize: 13.5, bold: true, fontFace: F, color: SUB, margin: 0 });
    s.addText([{ text: "10,443", options: { fontSize: 54, bold: true, color: RED } }, { text: " 건", options: { fontSize: 20, bold: true, color: INK } }],
      { x: 0.92, y: 3.3, w: 3.6, h: 0.95, fontFace: F, margin: 0 });
    s.addText("최근 3년 누계 31,330건\n사망 325명 · 부상 6,348명", { x: 0.95, y: 4.42, w: 3.5, h: 0.75, fontSize: 13, fontFace: F, color: INK, lineSpacingMultiple: 1.3, margin: 0 });
    chip(s, 0.95, 5.55, 2.9, "인명피해 11~15시 낮 시간 집중", "FDECEC", "A4161A");
    // middle doughnut (PNG)
    card(s, 4.95, 1.78, 3.75, 4.7);
    s.addText("인명피해의 주말 집중", { x: 5.2, y: 2, w: 3.2, h: 0.35, fontSize: 14, bold: true, fontFace: F, color: INK, margin: 0 });
    s.addImage({ path: px("donut_weekend.png"), x: 5.44, y: 2.55, w: 2.78, h: imgH("donut_weekend.png", 2.78) });
    s.addText("등산객이 몰리는 주말, 구조 수요도 함께 폭증", { x: 5.2, y: 5.65, w: 3.3, h: 0.6, fontSize: 11.5, fontFace: F, color: SUB, margin: 0 });
    // right bar (PNG)
    card(s, 9.05, 1.78, 3.7, 4.7);
    s.addText("최근 3년 인명피해 합계", { x: 9.3, y: 2, w: 3.2, h: 0.35, fontSize: 14, bold: true, fontFace: F, color: INK, margin: 0 });
    s.addImage({ path: px("bar_casualty.png"), x: 9.32, y: 2.65, w: 3.18, h: imgH("bar_casualty.png", 3.18) });
    s.addText("‘신고조차 못 하는 조난’이 가장 치명적 — 실신·추락 시 골든타임은 신고 없이 흘러갑니다.", {
      x: 9.3, y: 5.55, w: 3.25, h: 0.8, fontSize: 11.5, fontFace: F, color: "A4161A", bold: true, margin: 0 });
    srcText(s, "출처: 소방청 구조활동 통계(2022–2024), 정책브리핑", 0.62, 6.62, 7);
  }

  /* ---------------- S3 문제 2 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "PROBLEM 02", "가장 많이 산을 찾는 세대가, 가장 위험합니다");
    card(s, 0.6, 1.78, 7.2, 4.75);
    s.addImage({ path: px("chart_population.png"), x: 0.78, y: 2.18, w: 6.84, h: imgH("chart_population.png", 6.84) });
    // right cards
    const items = [
      [I.users, "성인 78%, 3,229만 명이 월 1회 이상 산행", "국민 야외활동 압도적 1위 — 그러나 안전 서비스는 공백"],
      [I.warn, "기존 등산 앱은 ‘기록·자랑’ 중심", "사고를 막는 기능은 수동 신고 버튼 수준"],
      [I.db, "생명 데이터는 기관 사이트에 분산", "산불위험예보·산사태지도·산악기상 — 국민이 체감 못 함"],
    ];
    items.forEach(([ic, t1, t2], i) => {
      const y = 1.78 + i * 1.63;
      card(s, 8.15, y, 4.58, 1.45, i === 0 ? LIGHT : WHITE);
      iconCircle(s, ic, 8.4, y + 0.42, 0.6, i === 1 ? "FDECEC" : PALE);
      s.addText(t1, { x: 9.2, y: y + 0.18, w: 3.45, h: 0.6, fontSize: 12.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: 9.2, y: y + 0.78, w: 3.45, h: 0.55, fontSize: 10.5, fontFace: F, color: SUB, margin: 0 });
    });
  }

  /* ---------------- S4 솔루션 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "SOLUTION", "숲길동무 — 산행의 전 과정을 지키는 AI 안전 플랫폼");
    const steps = [
      [I.route, "예방", "AI 맞춤 코스 추천", "체력·위험지수·혼잡 반영, 오늘 갈 만한 안전한 산을 한 화면에", PALE],
      [I.radar, "감시", "실시간 위험 경고", "산사태·낙석·기상 급변 구간 300m 전 푸시 경고", PALE],
      [I.sosR, "대응", "조난 자동 감지·SOS", "이동·심박 이상 감지 → 보호자·119 자동 전파", "FDECEC"],
      [I.city, "관제", "B2G 대시보드", "지자체·소방 실시간 관제로 구조 골든타임 단축", "E0EAF2"],
    ];
    steps.forEach(([ic, step, t1, t2, circ], i) => {
      const x = 0.6 + i * 3.235, w = 2.95;
      card(s, x, 1.95, w, 3.5);
      iconCircle(s, ic, x + 0.28, 2.3, 0.78, circ);
      s.addText(`STEP ${i + 1} — ${step}`, { x: x + 0.28, y: 3.3, w: w - 0.5, h: 0.3, fontSize: 11, bold: true, fontFace: F, color: LEAF, charSpacing: 1.5, margin: 0 });
      s.addText(t1, { x: x + 0.28, y: 3.62, w: w - 0.5, h: 0.42, fontSize: 16.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: x + 0.28, y: 4.14, w: w - 0.52, h: 1.1, fontSize: 11.5, fontFace: F, color: SUB, lineSpacingMultiple: 1.25, margin: 0 });
      if (i < 3) s.addText("→", { x: x + w + 0, y: 3.35, w: 0.32, h: 0.5, fontSize: 20, bold: true, color: LEAF, align: "center", fontFace: F, margin: 0 });
    });
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.6, y: 5.85, w: 12.13, h: 0.92, fill: { color: PINE }, rectRadius: 0.09 });
    s.addText([
      { text: "산림 공공데이터 10종", options: { bold: true, color: MINT } },
      { text: "  ×  ", options: { color: WHITE } },
      { text: "AI 엔진 5종", options: { bold: true, color: MINT } },
      { text: "  —  ‘사후 신고’에서 ‘사전 감지’로 패러다임을 바꿉니다", options: { color: WHITE } },
    ], { x: 0.9, y: 5.85, w: 11.6, h: 0.92, fontSize: 15.5, fontFace: F, valign: "middle", margin: 0 });
  }

  /* ---------------- S5 공공데이터 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "PUBLIC DATA", "모든 기능이 산림 공공데이터 위에서 동작합니다");
    const data = [
      ["등산로 공간정보", "산림청 · 공공데이터포털", "코스 DB·이탈 감지"],
      ["산불위험예보 API", "국립산림과학원", "산행지수·입산통제"],
      ["산악기상관측망", "국립산림과학원", "고지대 실측 기상"],
      ["산사태 위험지도", "산림청", "위험구간 경고"],
      ["국가생물종지식정보", "국립수목원", "독버섯·식물 AI 학습"],
      ["휴양림·치유의숲", "한국산림복지진흥원", "치유 프로그램 연계"],
      ["숲길 방문 빅데이터", "산림빅데이터 거래소", "혼잡도·수요예측"],
      ["산악사고 구조현황", "소방청", "사고 다발구간 학습"],
      ["국가지점번호", "행정안전부", "구조 위치 표준 전달"],
      ["단기예보 API", "기상청", "강수·일몰 알림"],
    ];
    data.forEach(([t1, org, use], i) => {
      const col = i % 5, row = Math.floor(i / 5);
      const x = 0.6 + col * 2.48, y = 1.95 + row * 1.78, w = 2.28, h = 1.62;
      const forest = i < 7;
      card(s, x, y, w, h, forest ? WHITE : "F4F7FA");
      s.addShape(pres.shapes.OVAL, { x: x + 0.22, y: y + 0.18, w: 0.13, h: 0.13, fill: { color: forest ? LEAF : SKY }, line: { type: "none" } });
      s.addText(forest ? "산림 데이터" : "융복합(타 분야)", { x: x + 0.43, y: y + 0.1, w: w - 0.5, h: 0.28, fontSize: 8.5, bold: true, fontFace: F, color: forest ? LEAF : SKY, margin: 0 });
      s.addText(t1, { x: x + 0.2, y: y + 0.4, w: w - 0.4, h: 0.52, fontSize: 12, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(org, { x: x + 0.2, y: y + 0.92, w: w - 0.4, h: 0.3, fontSize: 9, fontFace: F, color: SUB, margin: 0 });
      s.addText(use, { x: x + 0.2, y: y + 1.2, w: w - 0.4, h: 0.3, fontSize: 9.5, bold: true, fontFace: F, color: MOSS, margin: 0 });
    });
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.6, y: 5.78, w: 12.13, h: 0.95, fill: { color: LIGHT }, rectRadius: 0.09, line: { color: "D7E3D9", width: 0.75 } });
    s.addText([
      { text: "위험도 융합 스코어  ", options: { bold: true, color: PINE, fontSize: 14 } },
      { text: "산사태등급 × 산악기상 실측 × 사고이력 × 강우예보를 100m 구간마다 결합 — 단순 조회가 아닌 교차 도메인 융합이 핵심 자산입니다. 익명 산행 통계는 기관에 환류(양방향).", options: { color: INK, fontSize: 12 } },
    ], { x: 0.9, y: 5.78, w: 11.6, h: 0.95, fontFace: F, valign: "middle", margin: 0, lineSpacingMultiple: 1.15 });
  }

  /* ---------------- S6~S8 핵심 기능 (폰 목업) ---------------- */
  function featureSlide(no, kicker, title, file, phoneLeft, blocks, chips, srcLine) {
    const s = newSlide();
    kickTitle(s, kicker, title);
    const ph = 5, pw = imgW(file, ph);
    const phX = phoneLeft ? 0.85 : W - 0.85 - pw;
    phone(s, file, phX, 1.85, ph);
    const tx = phoneLeft ? 0.85 + pw + 0.55 : 0.65, tw = W - tx - (phoneLeft ? 0.6 : pw + 1.6);
    let y = 2;
    blocks.forEach(([ic, t1, t2]) => {
      iconCircle(s, ic, tx, y + 0.04, 0.56);
      s.addText(t1, { x: tx + 0.78, y, w: tw - 0.78, h: 0.38, fontSize: 14.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: tx + 0.78, y: y + 0.4, w: tw - 0.78, h: 0.62, fontSize: 11.5, fontFace: F, color: SUB, lineSpacingMultiple: 1.2, margin: 0 });
      y += 1.18;
    });
    let cx = tx;
    chips.forEach(([label, wch, fill, color]) => { chip(s, cx, y + 0.12, wch, label, fill, color); cx += wch + 0.18; });
    if (srcLine) srcText(s, srcLine, tx, y + 0.62, tw);
    return s;
  }

  featureSlide(1, "FEATURE 01 — 예방", "오늘의 산행지수, 네 가지 공공데이터가 한 점수로",
    "app_home.png", false,
    [
      [I.cloud, "산행지수 0~100점", "산불위험예보 + 산사태등급 + 산악기상 실측 + 일몰시각을 융합한 오늘의 ‘갈까 말까’ 단일 지표"],
      [I.route, "AI 맞춤 코스 추천", "체력 프로파일·무릎 부상 이력·혼잡 예측까지 반영한 개인화 추천 (매칭률 표시)"],
      [I.bell, "안전 브리핑", "소방청 사고 통계 학습 — ‘주말 11~15시 집중’ 같은 행동 가이드를 선제 제공"],
    ],
    [["산불위험예보", 1.55, PALE, PINE], ["산악기상", 1.2, PALE, PINE], ["산사태지도", 1.35, PALE, PINE], ["기상청 예보", 1.4, PALE, PINE]],
    null);

  featureSlide(2, "FEATURE 02 — 감시", "위험구간 300m 앞, 앱이 먼저 알아챕니다",
    "app_trail.png", true,
    [
      [I.map, "등산로 위 실시간 내비", "산림청 등산로 공간정보 기반 — 코스 이탈 시 즉시 알림, 오프라인 지도 기본 탑재"],
      [I.warn, "구간 위험도 실시간 경고", "산사태 1등급 + 최근 강우 + 사고 이력 구간 접근 시 푸시·우회로 안내 (수락률 91% 목표)"],
      [I.shieldG, "국가지점번호 상시 표시", "행정안전부 위치 표준을 화면에 상시 노출 — 신고 시 그대로 불러주면 끝"],
    ],
    [["일몰 카운트다운", 1.7, PALE, PINE], ["가족 위치 공유", 1.55, PALE, PINE], ["통신 음영지역 대응", 1.85, PALE, PINE]],
    null);

  featureSlide(3, "FEATURE 03 — AI 동무", "독버섯을 먹기 전에, AI가 먼저 말립니다",
    "app_ai.png", false,
    [
      [I.robot, "멀티모달 AI ‘숲이’", "사진 한 장으로 식물·버섯 판별 — 개나리광대버섯(맹독성) 87% 같은 즉각 경고"],
      [I.leafD, "온디바이스 1차 판별", "국립수목원 도감 데이터로 학습한 경량 모델을 단말 탑재 — 통신이 끊겨도 동작"],
      [I.globe, "4개 언어 RAG 해설", "산림 공공 지식베이스 근거 답변(한·영·중·일) — K-하이킹 인바운드 관광 안전망"],
    ],
    [["국가생물종지식정보 학습", 2.3, PALE, PINE], ["환각 억제 RAG", 1.6, PALE, PINE], ["음성 대화", 1.15, PALE, PINE]],
    null);

  /* ---------------- S9 SOS ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "FEATURE 04 — 대응", "신고할 수 없는 순간, 시스템이 먼저 움직입니다");
    const ph = 4.95, pw = phone(s, "app_sos.png", 0.85, 1.82, ph);
    const tx = 0.85 + pw + 0.5;
    const gw = imgW("diagram_golden.png", 2.1) > 9 ? 8.9 : imgW("diagram_golden.png", 2.1);
    s.addImage({ path: px("diagram_golden.png"), x: tx, y: 1.95, w: 8.9, h: imgH("diagram_golden.png", 8.9) });
    const yb = 1.95 + imgH("diagram_golden.png", 8.9) + 0.25;
    const cells = [
      ["자동 조난 감지", "이동 멈춤 30분 + 심박 이상 → AI가 조난 의심 스코어 산출, 사용자 무응답 시 자동 전파"],
      ["원터치 SOS", "3초 길게 누르면 국가지점번호·GPS·최근접 구조거점이 119 메시지로 자동 구성"],
      ["LTE 음영 대응", "통신 불가 시 SMS 폴백 + 마지막 위치 보호자 공유 — 수색 범위 최소화"],
    ];
    cells.forEach(([t1, t2], i) => {
      const x = tx + i * 3.02, w = 2.84;
      card(s, x, yb, w, 1.95, i === 0 ? "FDECEC" : WHITE);
      s.addText(t1, { x: x + 0.22, y: yb + 0.18, w: w - 0.44, h: 0.36, fontSize: 13.5, bold: true, fontFace: F, color: i === 0 ? "A4161A" : INK, margin: 0 });
      s.addText(t2, { x: x + 0.22, y: yb + 0.58, w: w - 0.44, h: 1.25, fontSize: 10.5, fontFace: F, color: SUB, lineSpacingMultiple: 1.22, margin: 0 });
    });
  }

  /* ---------------- S10 관제 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "FEATURE 05 — 관제 (B2G)", "지자체·소방이 같은 화면을 봅니다");
    const dw = 7.75, dh = imgH("dashboard.png", dw);
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 0.55, y: 1.83, w: dw + 0.1, h: dh + 0.1, fill: { color: "0E1A13" }, rectRadius: 0.08, shadow: mkShadow() });
    s.addImage({ path: px("dashboard.png"), x: 0.6, y: 1.88, w: dw, h: dh });
    const tx = 8.75, tw = 3.95;
    const rows = [
      [I.eye, "실시간 산행자 분포", "익명화(k≥50) 히트맵 — 혼잡·위험 구역 즉시 식별"],
      [I.warnW, "SOS·조난의심 사건 추적", "구조거점 매칭, 헬기·구조대 동선 한 화면 관제"],
      [I.chart, "AI 수요예측 리포트", "시간대별 입산 예측 — 안전요원·시설 배치 근거"],
    ];
    let y = 2;
    rows.forEach(([ic, t1, t2], i) => {
      iconCircle(s, ic, tx, y, 0.56, i === 1 ? RED : PALE);
      s.addText(t1, { x: tx + 0.74, y: y - 0.03, w: tw - 0.74, h: 0.4, fontSize: 13.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: tx + 0.74, y: y + 0.36, w: tw - 0.74, h: 0.75, fontSize: 10.5, fontFace: F, color: SUB, lineSpacingMultiple: 1.2, margin: 0 });
      y += 1.32;
    });
    s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: tx, y: y + 0.05, w: 3.95, h: 1.05, fill: { color: PINE }, rectRadius: 0.09 });
    s.addText([
      { text: "구조 도달시간 33분 → 23분", options: { bold: true, color: WHITE, fontSize: 13.5, breakLine: true } },
      { text: "위치 특정 자동화로 30% 단축 (시뮬레이션 목표)", options: { color: MINT, fontSize: 9.5 } },
    ], { x: tx + 0.22, y: y + 0.05, w: 3.55, h: 1.05, fontFace: F, valign: "middle", margin: 0 });
  }

  /* ---------------- S11 기술 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "TECHNOLOGY", "검증된 스택 위의 AI 5종 엔진");
    const aw = 8, ah = imgH("diagram_arch.png", aw);
    s.addImage({ path: px("diagram_arch.png"), x: 0.6, y: 2, w: aw, h: ah });
    const tx = 8.95, tw = 3.8;
    const ai = [
      ["코스 추천", "하이브리드 필터링 + 체력 프로파일"],
      ["조난위험 예측", "시계열 이상탐지 (LSTM-AE)"],
      ["위험도 융합 스코어", "XGBoost — 구간별 0~100점"],
      ["식물·독버섯 판별", "EfficientNet 경량화·온디바이스"],
      ["숲해설 LLM ‘숲이’", "한국어 sLLM + RAG·4개 언어"],
    ];
    ai.forEach(([t1, t2], i) => {
      const y = 1.95 + i * 0.92;
      card(s, tx, y, tw, 0.8);
      s.addText(String(i + 1), { x: tx + 0.16, y: y + 0.17, w: 0.46, h: 0.46, fontSize: 15, bold: true, color: WHITE, align: "center", valign: "middle", fontFace: F, margin: 0,
        fill: { color: MOSS }, shape: pres.shapes.OVAL });
      s.addText(t1, { x: tx + 0.78, y: y + 0.1, w: tw - 0.9, h: 0.32, fontSize: 12.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: tx + 0.78, y: y + 0.42, w: tw - 0.9, h: 0.3, fontSize: 9.5, fontFace: F, color: SUB, margin: 0 });
    });
    s.addText("Flutter(iOS·Android 단일 코드)  ·  React 관제 웹  ·  FastAPI  ·  PostGIS  ·  Airflow ETL  ·  AWS EKS  ·  TFLite 온디바이스", {
      x: 0.6, y: 6.55, w: 12.1, h: 0.35, fontSize: 11, fontFace: F, color: SUB, margin: 0 });
  }

  /* ---------------- S12 차별성 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "DIFFERENTIATION", "‘운동 기록 앱’과는 출발점이 다릅니다");
    const header = [
      { text: "구분", options: { fill: { color: PINE }, color: WHITE, bold: true, align: "center", valign: "middle" } },
      { text: "트랭글", options: { fill: { color: PINE }, color: WHITE, bold: true, align: "center", valign: "middle" } },
      { text: "램블러", options: { fill: { color: PINE }, color: WHITE, bold: true, align: "center", valign: "middle" } },
      { text: "국립공원공단 앱", options: { fill: { color: PINE }, color: WHITE, bold: true, align: "center", valign: "middle" } },
      { text: "숲길동무", options: { fill: { color: AMBER }, color: WHITE, bold: true, align: "center", valign: "middle" } },
    ];
    const mk = (txt, hot = false, dim = false) => {
      let color = INK;
      if (hot) color = PINE;
      else if (dim) color = "9AA8A0";
      return { text: txt, options: { align: "center", valign: "middle", color, bold: hot, fill: hot ? { color: PALE } : undefined } };
    };
    const rows = [
      [mk("핵심 가치"), mk("운동 기록·배지", false, true), mk("GPS 트랙·커뮤니티", false, true), mk("공원 정보 안내", false, true), mk("산행 전 과정 안전", true)],
      [mk("공공데이터 활용"), mk("등산로 일부", false, true), mk("지도 위주", false, true), mk("자체 정보", false, true), mk("10종 실시간 융합", true)],
      [mk("위험 경고"), mk("—", false, true), mk("—", false, true), mk("통제구간 공지", false, true), mk("구간 위험도 푸시", true)],
      [mk("조난 대응"), mk("수동 신고", false, true), mk("수동 신고", false, true), mk("수동 신고", false, true), mk("AI 자동 감지·전파", true)],
      [mk("관제(B2G)"), mk("—", false, true), mk("—", false, true), mk("내부용", false, true), mk("지자체·소방 제공", true)],
      [mk("AI"), mk("—", false, true), mk("일부 추천", false, true), mk("—", false, true), mk("5종 엔진", true)],
    ];
    s.addTable([header, ...rows], {
      x: 0.6, y: 1.95, w: 12.13, colW: [2.2, 2.35, 2.35, 2.6, 2.63],
      rowH: [0.5, 0.62, 0.62, 0.62, 0.62, 0.62, 0.62],
      fontFace: F, fontSize: 11.5, border: { pt: 0.75, color: "DCE6DE" }, valign: "middle",
    });
    s.addText([
      { text: "독창성 한 줄 — ", options: { bold: true, color: AMBERD } },
      { text: "예방→감시→대응→관제를 하나의 공공데이터 파이프라인으로 잇는 국내 유일 ‘안전 풀체인’. 조난을 ‘신고받는’ 것이 아니라 ‘감지하는’ 첫 서비스.", options: { color: INK } },
    ], { x: 0.62, y: 6.35, w: 12, h: 0.55, fontSize: 13, fontFace: F, margin: 0 });
  }

  /* ---------------- S13 비즈니스 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "BUSINESS", "안전은 공공의 가치이자, 지불 의사가 있는 시장입니다");
    const model = [
      [I.star, "B2C 프리미엄 구독", "월 4,900원 — 오프라인 지도·가족 안심 확대", PALE],
      [I.shieldG, "1일 안심보험 제휴", "건당 990원~ 인앱 판매, 수수료 15%", PALE],
      [I.city, "B2G 관제 SaaS", "기관당 연 3~8천만 원 라이선스", "E0EAF2"],
      [I.hands, "제휴·커머스", "휴양림 예약 5~10% · 브랜드 제휴", PALE],
    ];
    model.forEach(([ic, t1, t2, circ], i) => {
      const col = i % 2, row = Math.floor(i / 2);
      const x = 0.6 + col * 3.1, y = 2 + row * 2.2, w = 2.92, h = 2;
      card(s, x, y, w, h);
      iconCircle(s, ic, x + 0.24, y + 0.24, 0.62, circ);
      s.addText(t1, { x: x + 0.24, y: y + 1, w: w - 0.48, h: 0.38, fontSize: 13.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: x + 0.24, y: y + 1.4, w: w - 0.48, h: 0.55, fontSize: 10.5, fontFace: F, color: SUB, lineSpacingMultiple: 1.18, margin: 0 });
    });
    // revenue chart (PNG)
    card(s, 7, 1.95, 5.73, 4.55);
    s.addImage({ path: px("chart_revenue.png"), x: 7.25, y: 2.2, w: 5.25, h: imgH("chart_revenue.png", 5.25) });
    const tags = [["합계 4억 → 18.5억 → 50억", 2.6, PINE, WHITE], ["MAU 8만 → 35만 → 90만", 2.4, AMBER, WHITE]];
    let cx = 7.3;
    tags.forEach(([t1, w1, f1, c1]) => { chip(s, cx, 5.05, w1, t1, f1, c1, 11); cx += w1 + 0.25; });
    srcText(s, "가정: 유료 전환율 4→6%, B2G 2→10개 기관 · 자체 추정", 7.3, 5.62, 5.2);
  }

  /* ---------------- S14 사회적 가치·로드맵 ---------------- */
  {
    const s = newSlide();
    kickTitle(s, "SOCIAL IMPACT & ROADMAP", "숫자로 약속하는 사회적 가치");
    const stats = [
      ["골든타임 30% 단축", "구조 도달 33분 → 23분", I.clockA, "FFF3E3"],
      ["위험구간 사전 회피 90%", "경고 수신자 우회 (시범 목표)", I.check, PALE],
      ["가족 안심 20만 가구", "60대+ 산행 1위 세대 보호", I.users, "FFF3E3"],
    ];
    stats.forEach(([t1, t2, ic, fill], i) => {
      const x = 0.6 + i * 4.12, w = 3.9;
      card(s, x, 1.92, w, 1.35, WHITE);
      iconCircle(s, ic, x + 0.22, 2.22, 0.7, fill);
      s.addText(t1, { x: x + 1.1, y: 2.12, w: w - 1.25, h: 0.4, fontSize: 14.5, bold: true, fontFace: F, color: INK, margin: 0 });
      s.addText(t2, { x: x + 1.1, y: 2.54, w: w - 1.25, h: 0.35, fontSize: 10.5, fontFace: F, color: SUB, margin: 0 });
    });
    const rw = 8;
    s.addImage({ path: px("roadmap.png"), x: 0.6, y: 3.6, w: rw, h: imgH("roadmap.png", rw) });
    const tx = 8.95, tw = 3.8;
    const quals = [
      "산림복지 — 치유의숲·휴양림 연계로 접근성 확대",
      "지역경제 — 혼잡 분산 추천이 지방 명산·산촌 관광 수요 창출",
      "데이터 환류 — 익명 통계를 숲길 정비·안전시설 투자 근거로 제공",
      "행정 효율 — 불필요 출동 감소, 119 자원 최적 운용",
    ];
    s.addText(quals.map((q, i) => ({ text: q, options: { bullet: { code: "2022", indent: 12 }, color: INK, breakLine: true, paraSpaceAfter: 10 } })), {
      x: tx, y: 3.75, w: tw, h: 2.8, fontSize: 11.5, fontFace: F, lineSpacingMultiple: 1.18, margin: 0 });
  }

  /* ---------------- S15 클로징 ---------------- */
  {
    const s = newSlide(true);
    s.addText("기록을 넘어,", { x: 0.9, y: 1.7, w: 11.5, h: 0.8, fontSize: 40, bold: true, color: MINT, fontFace: F, margin: 0 });
    s.addText("생명을 지키는 산행 데이터로.", { x: 0.9, y: 2.5, w: 11.5, h: 0.9, fontSize: 40, bold: true, color: WHITE, fontFace: F, margin: 0 });
    s.addText("숲길동무는 산림 공공데이터가 국민의 생명과 만나는 가장 짧은 길입니다.\n오늘 보신 모든 화면은 실제 구동 중인 프로토타입입니다 — 현장 시연으로 증명하겠습니다.", {
      x: 0.9, y: 3.62, w: 10.5, h: 0.85, fontSize: 15, color: "CFE7D6", fontFace: F, lineSpacingMultiple: 1.4, margin: 0 });
    const ph = 2.2;
    ["app_home.png", "app_trail.png", "app_ai.png", "app_sos.png", "app_my.png"].forEach((f, i) => {
      const w = imgW(f, ph);
      s.addImage({ path: px(f), x: 0.95 + i * (w + 0.32), y: 4.62, w, h: ph });
    });
    s.addText([
      { text: "감사합니다  ", options: { bold: true, color: WHITE, fontSize: 20 } },
      { text: "  숲길동무 ForestMate — 팀 【팀명 기재】", options: { color: MINT, fontSize: 13 } },
    ], { x: 7.6, y: 6.55, w: 5.3, h: 0.5, fontFace: F, align: "right", margin: 0 });
  }

  await pres.writeFile({ fileName: OUT });
  console.log("WROTE", OUT);
})().catch((e) => { console.error(e); process.exit(1); });
