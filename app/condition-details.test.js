const test = require("node:test");
const assert = require("node:assert/strict");

const { buildConditionDetail, buildConditionSummaryItems } = require("./condition-details.js");

const context = {
  index: 72,
  regionName: "서울 은평구",
  placeLabel: "북한산 · 서울 은평구",
  fire: { level: "보통", score: 71, src: "국립산림과학원 예보" },
  landslide: { grade: 4, label: "양호", score: 82 },
  weather: { temp: 20, wind: 6.8, rainProb: 30, label: "흐림", score: 64, station: "아차산 인근 AWS" },
  sunsetAt: "19:51",
  sunsetScore: 42,
  mapRegions: [
    {
      name: "서울 은평구",
      mountain: "북한산",
      lat: 37.6584,
      lon: 126.9778,
      fire: { level: "낮음", score: 92 },
      landslide: { grade: 5, label: "안전", score: 95 },
      weather: { temp: 18, wind: 4.2, rainProb: 10, label: "맑음", score: 88, station: "북한산 관측소" },
      sunsetAt: "19:52",
    },
    {
      name: "서울 종로구",
      mountain: "인왕산",
      lat: 37.5772,
      lon: 126.961,
      fire: { level: "낮음", score: 90 },
      landslide: { grade: 4, label: "양호", score: 84 },
      weather: { temp: 19, wind: 3.1, rainProb: 20, label: "구름 조금", score: 80, station: "인왕산 관측소" },
      sunsetAt: "19:52",
    },
    {
      name: "경기 구리시",
      mountain: "아차산",
      lat: 37.5713,
      lon: 127.103,
      fire: { level: "보통", score: 71 },
      landslide: { grade: 4, label: "양호", score: 82 },
      weather: { temp: 20, wind: 6.8, rainProb: 30, label: "흐림", score: 64, station: "아차산 AWS" },
      sunsetAt: "19:51",
    },
    {
      name: "서울 도봉구",
      mountain: "도봉산",
      lat: 37.6987,
      lon: 127.0114,
      fire: { level: "낮음", score: 92 },
      landslide: { grade: 5, label: "안전", score: 95 },
      weather: { temp: 18, wind: 4.2, rainProb: 10, label: "맑음", score: 88, station: "북한산 관측소" },
      sunsetAt: "19:52",
    },
  ],
};

test("summary items expose the four tappable safety factors", () => {
  const items = buildConditionSummaryItems(context);

  assert.deepEqual(items.map((item) => item.id), ["fire", "landslide", "weather", "sunset"]);
  for (const item of items) {
    assert.equal(typeof item.title, "string");
    assert.ok(item.title.length > 0);
    assert.equal(typeof item.body, "string");
    assert.ok(item.body.length > 0);
    assert.match(item.ariaLabel, /상세/);
  }
});

test("each condition detail has decision-grade hiking information", () => {
  for (const id of ["fire", "landslide", "weather", "sunset"]) {
    const detail = buildConditionDetail(id, context);

    assert.equal(detail.id, id);
    assert.equal(typeof detail.title, "string");
    assert.ok(detail.title.length > 0);
    assert.equal(typeof detail.heroValue, "string");
    assert.ok(detail.heroValue.length > 0);
    assert.ok(detail.metrics.length >= 3, `${id} should show at least three concrete metrics`);
    assert.equal(typeof detail.primaryAction, "string", `${id} should expose one main action`);
    assert.ok(detail.primaryAction.length > 0, `${id} should expose one main action`);
    assert.ok(detail.source.length > 0, `${id} should expose data provenance`);
  }
});

test("each condition detail exposes weather-widget style source and location layers", () => {
  for (const id of ["fire", "landslide", "weather", "sunset"]) {
    const detail = buildConditionDetail(id, context);

    assert.ok(detail.feed.length >= 3, `${id} should expose source feed chips`);
    assert.ok(detail.feed.some((item) => item.kind === "관측" || item.kind === "예보" || item.kind === "지도"));
    assert.ok(detail.map.zones.length >= 4, `${id} should expose location-specific zones`);
    assert.ok(detail.map.legend.length >= 3, `${id} should expose a map legend`);
    assert.equal(detail.map.provider, "leaflet");
    assert.equal(detail.cards.length, 6, `${id} should fill a balanced six-card grid`);
    assert.ok(detail.updatedAt.length > 0, `${id} should show data freshness`);
  }
});

test("current state signal distribution is a unique radar vector, not a fake time series", () => {
  const signatures = [];

  for (const id of ["fire", "landslide", "weather", "sunset"]) {
    const detail = buildConditionDetail(id, context);
    const labels = detail.radar.axes.map((axis) => axis.label);

    assert.equal(detail.trend, undefined, `${id} should not expose line/trend data without a real time axis`);
    assert.equal(labels.length, 6, `${id} should use a balanced six-axis radar`);
    assert.equal(new Set(labels).size, labels.length, `${id} should not duplicate radar axis labels`);
    signatures.push(labels.join("|"));
  }

  assert.equal(new Set(signatures).size, 4, "the four detail panels should not reuse the same radar shape");
});

test("sunset detail does not expose internal score naming", () => {
  const sunset = buildConditionDetail("sunset", context);
  const visibleText = JSON.stringify({
    metrics: sunset.metrics,
    cards: sunset.cards,
    radar: sunset.radar,
    primaryAction: sunset.primaryAction,
  });

  assert.ok(!visibleText.includes("일몰점수"));
  assert.ok(!visibleText.includes("일몰 점수"));
});

test("detail data avoids prose filler in the hero area", () => {
  for (const id of ["fire", "landslide", "weather", "sunset"]) {
    const detail = buildConditionDetail(id, context);
    const visibleText = JSON.stringify(detail);

    assert.equal(detail.panelNote, undefined);
    assert.ok(!visibleText.includes("한 화면에 모았습니다"));
    assert.ok(!visibleText.includes("함께 봅니다"));
  }
});

test("condition maps compare real regions and mountains, not invented trail zones", () => {
  for (const id of ["fire", "landslide", "weather", "sunset"]) {
    const detail = buildConditionDetail(id, context);
    const mapText = JSON.stringify(detail.map);

    assert.ok(mapText.includes("서울 은평구"));
    assert.ok(mapText.includes("서울 종로구"));
    assert.ok(mapText.includes("경기 구리시"));
    assert.ok(mapText.includes("서울 도봉구"));
    assert.ok(mapText.includes("북한산"));
    assert.ok(mapText.includes("인왕산"));
    assert.ok(mapText.includes("아차산"));
    assert.ok(mapText.includes("도봉산"));
    assert.doesNotMatch(mapText, /능선권|건조권|계곡권|하산권|급사면|절개지|현재 구역/);
    for (const zone of detail.map.zones) {
      assert.equal(typeof zone.lat, "number");
      assert.equal(typeof zone.lon, "number");
      assert.equal(zone.x, undefined);
      assert.equal(zone.y, undefined);
    }
  }
});

test("detail guidance is framed for pre-trip checking", () => {
  for (const id of ["fire", "landslide", "weather", "sunset"]) {
    const detail = buildConditionDetail(id, context);
    assert.equal(detail.actionTitle, "출발 전 확인");
    assert.doesNotMatch(detail.primaryAction, /지금 할 일|안내판|근처|즉시|이미 산행/);
  }
});
