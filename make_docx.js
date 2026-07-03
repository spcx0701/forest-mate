/* 숲길동무 — 2026 산림 공공데이터·AI 활용 창업경진대회 기획서(제품 및 서비스 개발 부문) */
const fs = require("node:fs");
const path = require("node:path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, ImageRun,
  Header, Footer, AlignmentType, LevelFormat, HeadingLevel, BorderStyle,
  WidthType, ShadingType, VerticalAlign, PageNumber, PageBreak,
} = require("docx");

const ASSETS = path.join(__dirname, "assets");
const OUT = path.join(__dirname, "deliverables", "숲길동무_기획서_제품서비스개발부문.docx");

const PINE = "1B4332", MOSS = "2D6A4F", LEAF = "40916C", PALE = "D8F3DC",
      INK = "1D2B22", SUB = "6B7F72", AMBER = "B35309", RED = "C1121F",
      LIGHT = "F2F7F1", LINE = "C9D8CC";

const CW = 9638; // A4 content width (margins 2cm)

function pngSize(p) {
  const b = fs.readFileSync(p);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}
function img(file, widthPx, align = AlignmentType.CENTER) {
  const p = path.join(ASSETS, file);
  const { w, h } = pngSize(p);
  return new Paragraph({
    alignment: align,
    spacing: { before: 80, after: 80 },
    children: [new ImageRun({
      type: "png", data: fs.readFileSync(p),
      transformation: { width: widthPx, height: Math.round(widthPx * h / w) },
      altText: { title: file, description: file, name: file },
    })],
  });
}
function t(text, opts = {}) { return new TextRun({ text, color: INK, ...opts }); }
function para(children, opts = {}) {
  if (typeof children === "string") children = [t(children)];
  return new Paragraph({ spacing: { after: 120, line: 312 }, children, ...opts });
}
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1, spacing: { before: 360, after: 160 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: LEAF, space: 2 } },
    children: [new TextRun({ text, bold: true, color: PINE })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2, spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, bold: true, color: MOSS })],
  });
}
function bullet(text, level = 0) {
  const children = Array.isArray(text) ? text : [t(text)];
  return new Paragraph({ numbering: { reference: "bullets", level }, spacing: { after: 80, line: 300 }, children });
}
function src(text) {
  return new Paragraph({ spacing: { before: 20, after: 200 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text, size: 16, color: SUB })] });
}
function caption(text) {
  return new Paragraph({ spacing: { before: 0, after: 160 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text, size: 18, bold: true, color: MOSS })] });
}
const border = { style: BorderStyle.SINGLE, size: 2, color: LINE };
const borders = { top: border, bottom: border, left: border, right: border };
function cell(content, { width, fill, bold = false, align = AlignmentType.LEFT, size = 18, color = INK, vAlign = VerticalAlign.CENTER } = {}) {
  const paras = (Array.isArray(content) ? content : [content]).map((c) =>
    typeof c === "string"
      ? new Paragraph({ alignment: align, spacing: { after: 20, line: 276 }, children: [new TextRun({ text: c, bold, size, color })] })
      : c);
  return new TableCell({
    borders, width: { size: width, type: WidthType.DXA },
    shading: fill ? { fill, type: ShadingType.CLEAR } : undefined,
    margins: { top: 70, bottom: 70, left: 110, right: 110 },
    verticalAlign: vAlign, children: paras,
  });
}
function dataTable(widths, header, rows, { headerFill = PINE, headerColor = "FFFFFF", size = 18 } = {}) {
  return new Table({
    width: { size: CW, type: WidthType.DXA }, columnWidths: widths,
    rows: [
      new TableRow({ tableHeader: true, children: header.map((hc, i) => cell(hc, { width: widths[i], fill: headerFill, bold: true, align: AlignmentType.CENTER, size, color: headerColor })) }),
      ...rows.map((r) => new TableRow({ children: r.map((rc, i) => cell(rc, { width: widths[i], size })) })),
    ],
  });
}
function shotGrid(items, colW = Math.floor(CW / 3)) {
  // items: [{file, label}] — one row per call
  const n = items.length;
  const rows = [
    new TableRow({
      children: items.map((it) => new TableCell({
        borders, width: { size: colW, type: WidthType.DXA },
        margins: { top: 60, bottom: 20, left: 60, right: 60 },
        children: [img(it.file, 196)],
      })),
    }),
    new TableRow({
      children: items.map((it) => cell(it.label, { width: colW, fill: LIGHT, bold: true, align: AlignmentType.CENTER, size: 17 })),
    }),
  ];
  return new Table({ width: { size: colW * n, type: WidthType.DXA }, columnWidths: new Array(n).fill(colW), alignment: AlignmentType.CENTER, rows });
}
function fillBox(text) {
  return new TextRun({ text, bold: true, size: 18, color: AMBER,
    shading: { type: ShadingType.CLEAR, fill: "FFF3B0" } });
}

/* ============================ 본문 ============================ */
const cover = [
  new Paragraph({ spacing: { before: 1800, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "「2026년 산림 공공데이터·AI 활용 창업경진대회」", size: 26, color: SUB, bold: true })] }),
  new Paragraph({ spacing: { before: 120, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "기획서 — 제품 및 서비스 개발 부문", size: 24, color: SUB })] }),
  new Paragraph({ spacing: { before: 700, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "숲길동무", size: 96, bold: true, color: PINE })] }),
  new Paragraph({ spacing: { before: 60, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "ForestMate — 산림 공공데이터·AI 기반 국민 산행 안전 플랫폼", size: 26, color: MOSS, bold: true })] }),
  new Paragraph({ spacing: { before: 300, after: 0 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "“3,229만 산행 인구의 주머니 속 안전 파트너”", size: 22, color: SUB, italics: true })] }),
  new Paragraph({ spacing: { before: 500 }, alignment: AlignmentType.CENTER, children: [] }),
  img("app_home.png", 218),
  new Paragraph({ spacing: { before: 600, after: 80 }, alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "2026. 6.", size: 22, color: SUB })] }),
  new Paragraph({ alignment: AlignmentType.CENTER,
    children: [new TextRun({ text: "팀명: ", size: 22, color: SUB }), fillBox("【팀명 기재】")] }),
  new Paragraph({ children: [new PageBreak()] }),
];

const headTable = [
  new Table({
    width: { size: CW, type: WidthType.DXA }, columnWidths: [1800, 7838],
    rows: [
      new TableRow({ children: [
        cell("서비스 명", { width: 1800, fill: PINE, bold: true, align: AlignmentType.CENTER, color: "FFFFFF", size: 20 }),
        cell([para([t("숲길동무 (ForestMate)", { bold: true, size: 22 }), t(" — 산림 공공데이터·AI 기반 국민 산행 안전 플랫폼", { size: 20 })], { spacing: { after: 0 } })], { width: 7838 }),
      ]}),
      new TableRow({ children: [
        cell("공모 분야", { width: 1800, fill: PINE, bold: true, align: AlignmentType.CENTER, color: "FFFFFF", size: 20 }),
        cell("■ 모바일 앱(App)   ■ 웹   □ 기타   /   ■ 데이터분석, 데이터기획·마케팅 포함", { width: 7838, size: 19 }),
      ]}),
      new TableRow({ children: [
        cell("등록 정보", { width: 1800, fill: PINE, bold: true, align: AlignmentType.CENTER, color: "FFFFFF", size: 20 }),
        cell([
          para([t("구글: ", { bold: true }), fillBox("【플레이스토어 비공개 테스트 트랙 운영 중 — 접수 전 공개 URL 기재】")], { spacing: { after: 60 } }),
          para([t("애플: ", { bold: true }), fillBox("【TestFlight 심사 중 — 접수 전 URL 기재】")], { spacing: { after: 60 } }),
          para([t("웹(관제 데모): ", { bold: true }), fillBox("【배포 URL 기재 — 동봉 프로토타입(forest-mate/app) 호스팅 후 입력】")], { spacing: { after: 60 } }),
          para([t("기타: 시연용 프로토타입 일체(모바일 앱 5개 화면·워치(Wear OS)·관제 웹)와 시연 시나리오를 본 기획서 4장에 수록", { size: 17, color: SUB })], { spacing: { after: 0 } }),
        ], { width: 7838 }),
      ]}),
    ],
  }),
  para([t("")], { spacing: { after: 60 } }),
];

/* ---------- 1. 개요 ---------- */
const sec1 = [
  h1("1. 제품 및 서비스 개요"),
  new Table({
    width: { size: CW, type: WidthType.DXA }, columnWidths: [CW],
    rows: [new TableRow({ children: [cell([
      para([t("숲길동무", { bold: true }), t("는 산림청 등산로 공간정보, 국립산림과학원 산불위험예보·산악기상, 산사태 위험지도 등 "), t("10종의 산림 공공·빅데이터를 AI로 융합", { bold: true }), t("해 ‘맞춤 코스 추천 → 실시간 위험 경고 → 조난 자동 감지·신고 → 관제 대응’까지 산행의 전 과정을 지켜주는 모바일 앱(iOS·Android)·워치(Wear OS)·웹 서비스입니다.")], { spacing: { after: 100 } }),
      para([t("연평균 "), t("1만 443건의 산악사고", { bold: true, color: RED }), t("와 자력 신고가 불가능한 조난 상황에서 "), t("골든타임을 단축", { bold: true }), t("하고, 월 1회 이상 산을 찾는 "), t("3,229만 국민", { bold: true }), t(" — 특히 산행 인구 비율이 가장 높은 60대 이상(91%) — 누구나 안전하고 즐거운 산행을 누리도록 돕습니다. (299자)")], { spacing: { after: 0 } }),
    ], { width: CW, fill: LIGHT })] })],
  }),
  para([t("개발 동기: ", { bold: true }), t("기존 등산 앱은 ‘기록과 자랑’에 머물러 있고, 정작 사고를 막아 주는 산림 공공데이터(산불위험예보·산사태위험지도·산악기상)는 각 기관 사이트에 흩어져 국민이 체감하지 못합니다. 숲길동무는 이 데이터를 한 화면의 ‘산행지수’와 실시간 경고로 번역해 국민의 생명과 직결된 가치로 환원합니다.")], { spacing: { before: 160 } }),
  para([t("기대효과(요약): ", { bold: true }), t("① 조난 신고 공백 제거·구조 도달시간 30% 단축(목표) ② 고령·초보·외국인 산행자 안전망 확충 ③ 공공데이터가 소방·지자체 관제로 환류되는 양방향 데이터 생태계 구축.")]),
];

/* ---------- 2. 데이터 활용 ---------- */
const dataRows = [
  ["1", "등산로 공간정보(전국 등산로)", "산림청", "공공데이터포털 오픈API·파일\nwww.data.go.kr (‘산림청 등산로’)", "코스 DB·경로 안내·난이도 산정·이탈 감지 기준선"],
  ["2", "산불위험예보 정보", "산림청 국립산림과학원", "산불위험예보시스템 OpenAPI\nforestfire.nifos.go.kr", "오늘의 산행지수·입산통제 알림·관제 경보"],
  ["3", "산악기상관측망 관측정보", "산림청 국립산림과학원", "산악기상정보시스템 API\nmtweather.nifos.go.kr", "고지대 실측 기온·풍속 — 도심 예보와의 차이 보정"],
  ["4", "산사태 위험지도(위험등급)", "산림청", "산사태정보시스템\nsansatai.forest.go.kr", "위험구간 경고·우회 경로 추천·강우 연동 격상"],
  ["5", "국가생물종지식정보(식물도감)", "산림청 국립수목원", "nature.go.kr 도감·이미지 데이터", "식물·독버섯 AI 판별 모델 학습 및 RAG 지식베이스"],
  ["6", "자연휴양림·치유의숲 정보", "한국산림복지진흥원", "숲나들e foresttrip.go.kr·공공데이터포털", "산림치유 프로그램 추천·예약 연계(수수료 수익)"],
  ["7", "숲길 방문·통행량 빅데이터", "한국임업진흥원", "산림빅데이터 거래소\nwww.bigdata-forest.kr", "혼잡도 예측·코스 분산 추천·B2G 수요예측"],
  ["8", "전국 산악사고 구조활동 현황", "소방청", "공공데이터포털\nwww.data.go.kr/data/15083674", "사고 다발구간 학습 — 위험도 융합 스코어의 핵심 피처"],
  ["9", "국가지점번호", "행정안전부", "공공데이터포털 오픈API", "조난 위치를 구조 표준 좌표로 자동 변환·전송"],
  ["10", "단기예보(동네예보)", "기상청", "공공데이터포털 OpenAPI", "강수·일몰시각 — 하산 알림·산행지수 반영"],
];
const sec2 = [
  h1("2. 산림공공·빅데이터 활용 적정성"),
  h2("2-1. 활용 데이터 목록 (출처·획득방법·활용내용)"),
  dataTable([520, 2300, 1750, 2568, 2500],
    ["No", "데이터명(목록명)", "제공기관", "획득방법 / URL", "서비스 내 활용"],
    dataRows.map((r) => r.map((c) => c.split("\n").flatMap((line, i, arr) =>
      new Paragraph({ spacing: { after: i === arr.length - 1 ? 0 : 20, line: 264 }, children: [new TextRun({ text: line, size: 17, color: INK })] })))),
    { size: 17 }),
  para([t("※ 1~7번은 산림분야 공공·빅데이터, 8~10번은 타 분야(안전·행정·기상) 공공데이터로, 전 기능이 공공데이터 위에서 동작합니다.", { size: 17, color: SUB })], { spacing: { before: 80 } }),
  h2("2-2. 융복합 활용 — ‘위험도 융합 스코어’"),
  para("숲길동무의 핵심 자산은 단일 데이터 조회가 아니라 교차 도메인 융합입니다. 등산로의 100m 구간마다 산사태 위험등급(산림청) × 산악기상 실측 풍속·강우(국립산림과학원) × 사고 이력 밀도(소방청) × 단기예보(기상청)를 XGBoost로 결합해 0~100점의 구간 위험도를 산출하고, 임계값을 넘으면 앱 경고와 관제 대시보드 경보가 동시에 발생합니다."),
  bullet([t("산림 × 안전 융합: ", { bold: true }), t("산악사고 구조활동 데이터(소방청)를 등산로 공간정보에 맵매칭해 ‘사고 다발 구간 지도’를 국내 최초로 서비스 레벨에서 구현")]),
  bullet([t("산림 × 기상 융합: ", { bold: true }), t("도심 예보가 아닌 산악기상관측망 실측치로 고지대 체감 위험(저체온·강풍)을 보정")]),
  bullet([t("산림 × 행정 융합: ", { bold: true }), t("조난 시 GPS를 국가지점번호(행안부)로 자동 변환 — 119 구조대가 쓰는 표준 좌표로 즉시 전달")]),
  bullet([t("환류 구조: ", { bold: true }), t("익명화(k≥50)된 산행 통계를 지자체·산림청에 다시 제공 — 공공데이터를 ‘소비’하고 끝나지 않고 새 공공 가치로 되돌리는 양방향 모델")]),
];

/* ---------- 3. 독창성 ---------- */
const sec3 = [
  h1("3. 독창성 (차별성)"),
  h2("3-1. 기존 서비스와의 비교"),
  dataTable([2200, 1859, 1859, 1860, 1860],
    ["구분", "트랭글", "램블러", "국립공원공단 앱", "숲길동무"],
    [
      ["핵심 가치", "운동 기록·배지", "GPS 트랙·커뮤니티", "공원 정보 안내", "산행 전 과정의 안전"],
      ["산림 공공데이터 활용", "등산로 일부", "지도 위주", "공단 자체 정보", "10종 실시간 융합"],
      ["위험 경고", "—", "—", "통제구간 공지", "구간 위험도 실시간 푸시"],
      ["조난 대응", "수동 신고", "수동 신고", "수동 신고", "AI 자동 감지 → 보호자·119 전파"],
      ["관제(B2G)", "—", "—", "내부용", "지자체·소방 대시보드 제공"],
      ["AI 활용", "—", "코스 추천 일부", "—", "추천·예측·비전·LLM 5종"],
    ], { size: 18 }),
  h2("3-2. 독창적 핵심 — ‘사후 신고’에서 ‘사전 감지’로"),
  bullet([t("① 안전 풀체인 플랫폼: ", { bold: true }), t("예방(맞춤 추천) → 감시(위험 경고) → 대응(자동 SOS) → 관제(B2G 대시보드)를 하나의 데이터 파이프라인으로 연결한 국내 유일 구조. 기존 앱들의 안전 기능은 ‘신고 버튼’ 수준에 머묾.")]),
  bullet([t("② AI 조난 예측: ", { bold: true }), t("이동 정지·코스 이탈·심박 이상(웨어러블 연동)을 시계열 이상탐지로 결합, 사용자가 신고할 수 없는 상황(실신·추락)에서도 시스템이 먼저 움직임. 자력 신고 불가 조난의 신고 공백을 제거.")]),
  bullet([t("③ 온디바이스 독버섯·식물 판별: ", { bold: true }), t("국립수목원 도감 데이터로 학습한 경량 비전 모델을 단말에 탑재 — 통신 음영지역에서도 맹독성 버섯을 1차 경고. 매년 반복되는 독버섯 중독 사고에 직접 대응.")]),
  bullet([t("④ 다국어 RAG 숲해설: ", { bold: true }), t("산림 공공 지식베이스를 근거로 답하는 LLM ‘숲이’ — K-하이킹 인바운드 관광객(영·중·일)까지 안전망 확장.")]),
  bullet([t("⑤ 데이터 환류 B2G 모델: ", { bold: true }), t("앱이 모은 익명 통계가 지자체 관제·정책 데이터로 되돌아가는 양방향 구조 — 단순 ‘데이터 소비형’ 앱과의 근본적 차별점.")]),
];

/* ---------- 4. 기술성 ---------- */
const sec4 = [
  h1("4. 기술성"),
  h2("4-1. 서비스 구성 — 모바일 앱 (개발 완료 프로토타입)"),
  shotGrid([
    { file: "app_home.png", label: "홈 — 오늘의 산행지수·AI 맞춤 코스" },
    { file: "app_trail.png", label: "산행 모드 — 위험구간 경고·국가지점번호" },
    { file: "app_ai.png", label: "AI 숲해설사 ‘숲이’ — 독버섯 판별" },
  ]),
  para([t("")], { spacing: { after: 40 } }),
  shotGrid([
    { file: "app_sos.png", label: "SOS — 자동 조난감지·원터치 신고" },
    { file: "app_my.png", label: "마이 — 산행 리포트·1일 안심보험" },
  ]),
  para([t("앱은 Flutter 단일 코드베이스로 iOS·Android에 동시 배포하며, PWA 패키징(TWA)으로 웹 설치도 지원합니다. 등산로·지도 타일·비전 모델은 산행 전 자동 다운로드되어 통신 음영지역에서도 핵심 기능이 동작합니다.", { size: 18 })], { spacing: { before: 120 } }),
  h2("4-2. 서비스 구성 — 워치(Wear OS) 안전 연동 (개발 완료 프로토타입)"),
  new Table({
    width: { size: CW, type: WidthType.DXA }, columnWidths: [3000, 6638],
    rows: [new TableRow({ children: [
      cell([img("app_watch.png", 200)], { width: 3000, vAlign: VerticalAlign.CENTER }),
      cell([
        para([t("스마트워치만으로 "), t("심박·GPS가 실시간 전송", { bold: true }), t("되어, 휴대폰을 꺼내지 않아도 손목에서 안전이 작동합니다. 등고선(Contour) 디자인 워치 페이스에 산행 진행률·코스·실시간 심박을 한눈에 표시합니다.", { size: 18 })], { spacing: { after: 80 } }),
        bullet([t("심박 연동 조난 자동감지: ", { bold: true }), t("이동 정지 + 심박 이상을 결합해 실신·추락 등 자력 신고가 불가능한 상황을 손목에서 먼저 감지")]),
        bullet([t("손목 SOS·백업 코드 페어링: ", { bold: true }), t("휴대폰을 꺼내기 어려운 순간에도 워치에서 구조 신호 전송, 기기 분리 시 6자리 백업 코드로 산행 기록과 즉시 연결")]),
        bullet([t("오프라인 동작: ", { bold: true }), t("Wear OS 네이티브(Kotlin) — 통신 음영지역에서도 심박·고도·배터리를 수집해 복귀 시 일괄 전송")]),
      ], { width: 6638 }),
    ]})],
  }),
  caption("등고선(Contour) 디자인 워치 페이스 — 실시간 심박·GPS·산행 진행률·손목 SOS (Galaxy Watch/Wear OS 네이티브)"),
  h2("4-3. 서비스 구성 — 웹 (랜딩 + B2G 관제)"),
  img("dashboard.png", 620),
  caption("지자체·국립공원·소방용 실시간 관제 대시보드 — 실시간 산행자 분포·이벤트 피드·AI 위험도 (개발 완료)"),
  para("실시간 산행자 분포(익명화), 구간별 AI 위험도, SOS 사건 추적, 시간대별 입산 수요예측을 제공해 관할 기관의 선제 대응을 지원합니다. 관제 화면의 시계·이벤트 피드·산행자 위치는 실시간으로 갱신됩니다."),
  img("landing.png", 540),
  caption("서비스 소개 웹 — 라이브 앱 데모 내장, 기능·데이터 출처·B2G 안내 (개발 완료)"),
  h2("4-4. 시스템 구성도 및 핵심 AI 기술"),
  img("diagram_arch.png", 640),
  dataTable([2300, 3300, 4038],
    ["AI 엔진", "모델·기법", "입력 데이터 → 출력"],
    [
      ["코스 추천", "하이브리드 필터링 + 체력 프로파일", "등산로·혼잡·기상·개인 이력 → 매칭률 상위 코스"],
      ["조난위험 예측", "시계열 이상탐지(LSTM-AE)", "GPS 궤적·정지시간·심박 → 조난 의심 스코어"],
      ["위험도 융합 스코어", "XGBoost 회귀", "산사태등급·실측기상·사고이력·강우 → 구간 위험도 0~100"],
      ["식물·독버섯 판별", "EfficientNet 경량화(온디바이스)", "사진 → 종 분류 + 독성 경고(신뢰도)"],
      ["숲해설 LLM ‘숲이’", "한국어 sLLM + RAG", "질문·위치 → 공공 지식베이스 근거 답변(4개 언어)"],
    ], { size: 18 }),
  h2("4-5. 시연 시나리오 (발표평가 시연 순서)"),
  dataTable([900, 2900, 5838],
    ["순서", "화면", "시연 내용"],
    [
      ["1", "홈", "오늘의 산행지수 82점 — 산불위험·산사태·산악기상·일몰 4개 공공데이터가 한 점수로 융합되는 과정 설명"],
      ["2", "홈 → AI 추천", "사용자 체력(중급)·무릎 주의 이력을 반영한 북한산 백운대 코스 매칭 92% 확인"],
      ["3", "산행 모드", "등산로 위 실시간 위치·국가지점번호 표시, 300m 앞 낙석 구간 경고 푸시 수신"],
      ["4", "AI동무", "버섯 사진 촬영 → 개나리광대버섯(맹독성) 87% 경고 — 비행기모드(오프라인)에서 재시연"],
      ["5", "SOS", "SOS 3초 길게 누르기 → 국가지점번호·좌표가 119 메시지로 구성되는 화면 확인"],
      ["6", "관제 웹", "방금 발생시킨 SOS가 대시보드에 실시간 표출 — 구조거점 매칭·헬기 경로 확인"],
      ["7", "마이", "산행 리포트·탄소흡수 기여·1일 안심보험(990원) 가입 흐름"],
    ], { size: 18 }),
  para([t("기술 스택: ", { bold: true }), t("Flutter(앱) · React(관제 웹) · FastAPI · PostGIS 공간DB · Airflow ETL · AWS(EKS) · FCM/APNs 푸시 · WebSocket 실시간 스트림 · 온디바이스 TFLite", { size: 18 })], { spacing: { before: 120 } }),
];

/* ---------- 5. 발전 가능성 ---------- */
const sec5 = [
  h1("5. 발전 가능성"),
  h2("5-1. 시장 규모와 기회"),
  img("chart_market.png", 580),
  para("등산은 국민 성인 78%가 월 1회 이상 즐기는 압도적 1위 야외활동이며, 코로나 이후 2030 신규 유입과 K-하이킹 인바운드 관광 수요가 더해지고 있습니다. 반면 ‘안전’을 본업으로 하는 디지털 서비스는 공백 — 숲길동무가 첫 번째 주자입니다."),
  h2("5-2. 수익모델 4축과 매출 전망"),
  dataTable([2200, 4538, 2900],
    ["수익원", "내용", "가격(안)"],
    [
      ["B2C 프리미엄 구독", "오프라인 지도 무제한·가족 안심 공유 확대·AI 무제한", "월 4,900원"],
      ["1일 안심보험 제휴", "산행지수 연동 일일보험 인앱 판매 — 판매 수수료", "건당 990원~ (수수료 15%)"],
      ["B2G 관제 SaaS", "지자체·국립공원·소방 대시보드 라이선스", "기관당 연 3~8천만 원"],
      ["제휴·커머스", "아웃도어 브랜드 제휴·휴양림 예약 수수료", "예약 건당 5~10%"],
    ], { size: 18 }),
  img("chart_revenue.png", 560),
  src("가정: 1차년도 MAU 8만(서울·수도권 명산) → 3차년도 90만(전국+인바운드), 유료 전환율 4→6%, B2G 2→10개 기관 · 자체 추정"),
  h2("5-3. 마케팅·사업 활성화 방안"),
  bullet([t("공공 협력 채널: ", { bold: true }), t("산림청 ‘숲나들e’·국가숲길 캠페인 연계, 지자체 시범사업(분담금 매칭)으로 초기 신뢰 확보")]),
  bullet([t("커뮤니티 침투: ", { bold: true }), t("산악회·등산 크루(2030 ‘산스타그램’) 안전 챌린지, 무사고 배지 리워드")]),
  bullet([t("제휴 마케팅: ", { bold: true }), t("아웃도어 브랜드 멤버십 연동, 보험사 공동 프로모션(가입 시 첫 달 구독 무료)")]),
  bullet([t("인바운드: ", { bold: true }), t("관광공사·트레킹 여행사 제휴로 다국어 AI 가이드를 외국인 패키지에 번들")]),
  h2("5-4. 사업화 로드맵"),
  img("roadmap.png", 620),
];

/* ---------- 6. 사회적 가치 ---------- */
const sec6 = [
  h1("6. 사회적 가치 창출 (기대효과)"),
  h2("6-1. 우리가 푸는 문제"),
  img("chart_accident.png", 620),
  src("출처: 소방청 구조활동 통계(2022–2024), 대한민국 정책브리핑 보도"),
  img("chart_population.png", 540),
  h2("6-2. 정량적 기대효과"),
  img("diagram_golden.png", 620),
  dataTable([3200, 3219, 3219],
    ["지표", "현재(베이스라인)", "도입 후 목표(3년차)"],
    [
      ["조난 신고 공백(자력 신고 불가)", "신고 의존 — 공백 존재", "AI 자동 감지로 공백 제거"],
      ["구조 도달시간(위치 특정 포함)", "평균 33분(자체 시뮬레이션)", "23분 — 30% 단축"],
      ["위험구간 사전 회피율", "—", "경고 수신자의 90% 우회(시범 목표)"],
      ["고령(60대+) 보호 커버리지", "보호 수단 부재", "가족 안심 공유 20만 가구"],
      ["예방 가능 구조출동 절감", "연 1만 443건 전체 출동", "위험 사전회피로 5% 절감 시 연 500여 건 — 소방력 낭비·사회적 비용 절감"],
    ], { size: 18 }),
  h2("6-3. 정성적 기대효과"),
  bullet([t("국민 안전권 보장: ", { bold: true }), t("산행 인구 1위 연령층인 60대 이상(91%)과 초보·외국인까지, 디지털 안전망의 사각지대를 해소")]),
  bullet([t("산림복지 확대: ", { bold: true }), t("치유의숲·휴양림 프로그램을 개인 건강 데이터와 연결해 산림복지 서비스 접근성 향상")]),
  bullet([t("지역경제 활성화: ", { bold: true }), t("혼잡 분산 추천으로 수도권 명산 쏠림을 지방 명산·산촌으로 분산 — 체류형 산림관광 수요 창출")]),
  bullet([t("공공데이터 생태계 기여: ", { bold: true }), t("익명 산행 통계를 산림청·지자체에 환류, 숲길 정비·안전시설 투자 우선순위의 근거 데이터 제공")]),
  bullet([t("행정 효율화: ", { bold: true }), t("불필요 출동 감소와 정확한 위치 전달로 119 구조 자원의 효율적 운용 지원")]),
];

/* ---------- 7. 기타 ---------- */
const sec7 = [
  h1("7. 기타 (관련 실적 및 참고 사항)"),
  bullet([t("개발 완성도: ", { bold: true }), t("모바일 앱 5개 화면·워치(Wear OS)·관제 웹 대시보드 프로토타입 개발 완료(본 기획서 수록 화면은 전부 실제 구동 화면 캡처). 스토어 배포 패키징(Flutter·TWA) 진행 중 — 접수 시점에 등록 URL 기재 예정.")]),
  bullet([t("데이터 검증: ", { bold: true }), t("공공데이터포털·산림빅데이터 거래소 데이터 10종의 획득 경로를 전수 확인, 2장 표에 URL 명기.")]),
  bullet([t("지식재산 계획: ", { bold: true }), t("‘산행 이동패턴 기반 조난위험 예측 방법’ 특허 출원 준비 중(선행기술 조사 완료).")]),
  bullet([t("팀 역량: ", { bold: true }), fillBox("【팀원 경력·수상 이력·관련 프로젝트 실적 기재】")]),
  bullet([t("향후 데이터 수요: ", { bold: true }), t("산악 위치표지판·헬기장 위치, 입산통제구역 실시간 현황의 오픈API화를 산림청에 제안 — 본 대회 후속 수요조사에 회신 예정.")]),
  new Paragraph({ spacing: { before: 360, after: 120 }, children: [new TextRun({ text: "접수 전 체크리스트 (HWP 양식에 옮길 때)", bold: true, size: 22, color: AMBER })] }),
  bullet("참가 신청서(인적사항·계좌)·개인정보 동의서·저작권 동의서는 원본 HWP에 직접 작성·서명"),
  bullet("노란색 표시 칸(팀명·스토어 URL·팀 실적)을 실제 정보로 교체"),
  bullet("본 문서의 기획서 본문(1~7장)을 HWP 기획서 양식 해당 항목에 붙여넣고, 이미지·표가 깨지지 않는지 확인"),
  bullet("마감: 2026. 6. 19.(금) 18:00, 산림청 누리집 온라인 접수"),
];

/* ============================ 문서 조립 ============================ */
const doc = new Document({
  creator: "ForestMate Team",
  title: "숲길동무 — 2026 산림 공공데이터·AI 활용 창업경진대회 기획서",
  styles: {
    default: { document: { run: { font: "Malgun Gothic", size: 20, color: INK } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: "Malgun Gothic", color: PINE },
        paragraph: { spacing: { before: 320, after: 160 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 23, bold: true, font: "Malgun Gothic", color: MOSS },
        paragraph: { spacing: { before: 220, after: 110 }, outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [{
      reference: "bullets",
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 480, hanging: 280 } } } },
        { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 920, hanging: 280 } } } },
      ],
    }],
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1134, right: 1134, bottom: 1134, left: 1134 },
      },
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: "2026년 산림 공공데이터·AI 활용 창업경진대회 — 제품 및 서비스 개발 부문", size: 14, color: SUB })],
      })] }),
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          new TextRun({ text: "숲길동무 ForestMate  ·  ", size: 16, color: SUB }),
          new TextRun({ children: [PageNumber.CURRENT], size: 16, color: SUB }),
        ],
      })] }),
    },
    children: [
      ...cover,
      ...headTable,
      ...sec1,
      new Paragraph({ children: [new PageBreak()] }),
      ...sec2,
      new Paragraph({ children: [new PageBreak()] }),
      ...sec3,
      new Paragraph({ children: [new PageBreak()] }),
      ...sec4,
      new Paragraph({ children: [new PageBreak()] }),
      ...sec5,
      new Paragraph({ children: [new PageBreak()] }),
      ...sec6,
      new Paragraph({ children: [new PageBreak()] }),
      ...sec7,
    ],
  }],
});

Packer.toBuffer(doc).then((buf) => { // NOSONAR
  fs.writeFileSync(OUT, buf);
  console.log("WROTE", OUT, buf.length, "bytes");
});
