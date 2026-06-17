(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.FM_CONDITION_DETAILS = factory();
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function num(v, fallback = 0) {
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
  }

  function clamp(v, min = 0, max = 100) {
    return Math.max(min, Math.min(max, num(v, min)));
  }

  function pct(v) {
    return `${Math.round(clamp(v))}%`;
  }

  function rainProb(weather) {
    return num(weather && (weather.rainProb ?? weather.rain_prob), 0);
  }

  function scoreTone(score) {
    const s = num(score);
    return s >= 80 ? "ok" : s >= 60 ? "mid" : "bad2";
  }

  function statusWord(score) {
    const s = num(score);
    return s >= 80 ? "안정" : s >= 60 ? "주의" : "위험";
  }

  function formatWind(wind) {
    const w = num(wind);
    return `${w.toFixed(w % 1 ? 1 : 0)}m/s`;
  }

  function sunsetMinutes(sunsetAt) {
    if (!/^\d{1,2}:\d{2}$/.test(String(sunsetAt || ""))) return null;
    const [h, m] = sunsetAt.split(":").map(Number);
    const now = new Date();
    const sunset = new Date(now);
    sunset.setHours(h, m, 0, 0);
    return Math.round((sunset - now) / 60000);
  }

  function sunsetMarginText(sunsetAt) {
    const mins = sunsetMinutes(sunsetAt);
    if (mins == null) return "일몰 시각 확인 필요";
    if (mins <= 0) return "이미 일몰 이후";
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return h ? `${h}시간 ${String(m).padStart(2, "0")}분 남음` : `${m}분 남음`;
  }

  function placeText(ctx) {
    return ctx.placeLabel || ctx.regionName || "현재 선택 지역";
  }

  function freshness(ctx) {
    if (ctx.updatedAt) return `${ctx.updatedAt} 갱신`;
    return ctx.mode === "cloud" ? "실시간 API 갱신" : "오프라인 스냅샷";
  }

  function modeLabel(ctx) {
    return ctx.mode === "cloud" ? "LIVE" : "SNAPSHOT";
  }

  function mountainText(ctx) {
    if (ctx.mountainName) return ctx.mountainName;
    const place = placeText(ctx);
    const head = place.split("·")[0].replace(/\u{1f4cd}|\u{1f3d4}\ufe0f?/gu, "").trim();
    return head || "선택 산";
  }

  function regionText(ctx) {
    return ctx.regionName || placeText(ctx).split("·").slice(-1)[0].trim() || placeText(ctx);
  }

  function windRisk(weather) {
    return clamp(num(weather && weather.wind) * 12);
  }

  function tempBurden(weather) {
    const temp = num(weather && weather.temp, 18);
    return clamp(Math.abs(temp - 18) * 6);
  }

  function mix(a, b, aw = 0.5) {
    return clamp(num(a) * aw + num(b) * (1 - aw));
  }

  function sunsetPressure(sunsetAt) {
    const mins = sunsetMinutes(sunsetAt);
    if (mins == null) return 55;
    if (mins <= 0) return 100;
    return clamp(100 - (mins / 240) * 100);
  }

  function radarAxis(label, value, note) {
    return { label, value: Math.round(clamp(value)), note };
  }

  function riskLevel(value) {
    const v = clamp(value);
    return v >= 70 ? "high" : v >= 40 ? "mid" : "low";
  }

  function zone(label, value, note, risk, lat, lon, size = "m") {
    const v = Math.round(clamp(risk));
    return { label, value, note, risk: v, level: riskLevel(v), lat, lon, size };
  }

  function regionLatLon(row, index) {
    const lat = num(row && row.lat, NaN);
    const lon = num(row && row.lon, NaN);
    if (Number.isFinite(lat) && Number.isFinite(lon)) return [lat, lon];
    const name = `${(row && row.name) || ""} ${(row && row.mountain) || ""}`;
    const n = String(name || "");
    const known = [
      [/은평|북한산/, [37.6584, 126.9778]],
      [/종로|인왕산/, [37.5772, 126.961]],
      [/도봉|도봉산/, [37.6987, 127.0114]],
      [/구리|아차산|경기/, [37.5713, 127.103]],
      [/강원|설악|오대/, [38.1195, 128.4656]],
      [/충청|계룡|속리/, [36.3504, 127.3845]],
      [/전라|무등|내장/, [35.1595, 126.8526]],
      [/경상|대구|팔공/, [35.8714, 128.6014]],
      [/부산|금정/, [35.1796, 129.0756]],
      [/제주|한라/, [33.3617, 126.5292]],
    ];
    const hit = known.find(([re]) => re.test(n));
    if (hit) return hit[1];
    const fallback = [[37.5665, 126.978], [37.4138, 127.5183], [36.3504, 127.3845], [35.1595, 126.8526], [35.8714, 128.6014], [33.3617, 126.5292]];
    return fallback[index % fallback.length];
  }

  function mapRows(ctx) {
    const rows = Array.isArray(ctx.mapRegions) ? ctx.mapRegions : [];
    if (rows.length) return rows;
    return [{
      name: regionText(ctx),
      mountain: mountainText(ctx),
      fire: ctx.fire || {},
      landslide: ctx.landslide || {},
      weather: ctx.weather || {},
      sunsetAt: ctx.sunsetAt,
      selected: true,
    }];
  }

  function rowRisk(id, row) {
    const weather = row.weather || {};
    if (id === "fire") return clamp(100 - num(row.fire && row.fire.score, 70));
    if (id === "landslide") return clamp((6 - num(row.landslide && row.landslide.grade, 5)) * 20);
    if (id === "sunset") return sunsetPressure(row.sunsetAt);
    return mix(rainProb(weather), windRisk(weather), 0.55);
  }

  function rowValue(id, row) {
    const weather = row.weather || {};
    if (id === "fire") return (row.fire && row.fire.level) || "확인";
    if (id === "landslide") return `${(row.landslide && row.landslide.grade) ?? "—"}등급`;
    if (id === "sunset") return row.sunsetAt || "--:--";
    return `${weather.temp ?? "—"}° · ${rainProb(weather)}%`;
  }

  function rowNote(id, row) {
    const mountain = row.mountain || "산 정보";
    if (id === "fire") return `${mountain} · 산불예보`;
    if (id === "landslide") return `${mountain} · ${(row.landslide && row.landslide.label) || "위험지도"}`;
    if (id === "sunset") return `${mountain} · 일몰`;
    return `${mountain} · ${(row.weather && row.weather.station) || "기상"}`;
  }

  function feedItem(kind, label, value) {
    return { kind, label, value };
  }

  function sourceFeed(ctx, id) {
    const weather = ctx.weather || {};
    const fire = ctx.fire || {};
    const base = {
      fire: [
        feedItem("예보", "산불위험예보", fire.source === "live" ? "live" : fire.src || "NIFoS"),
        feedItem("관측", "산악기상", `${formatWind(weather.wind)} 바람`),
        feedItem("위치", "격자/시군구", placeText(ctx)),
      ],
      landslide: [
        feedItem("지도", "산사태위험지도", `${(ctx.landslide || {}).grade ?? "—"}등급`),
        feedItem("예보", "강수 신호", `${rainProb(weather)}%`),
        feedItem("위치", "사면/등산로", placeText(ctx)),
      ],
      weather: [
        feedItem("관측", "산악기상", weather.station || "관측망"),
        feedItem("예보", "기상청 단기예보", `${rainProb(weather)}% 강수`),
        feedItem("위치", "산 기준", placeText(ctx)),
      ],
      sunset: [
        feedItem("예보", "일몰시각", ctx.sunsetAt || "--:--"),
        feedItem("위치", "지역 기준", placeText(ctx)),
        feedItem("산행", "하산 여유", sunsetMarginText(ctx.sunsetAt)),
      ],
    };
    return base[id] || [];
  }

  function card(label, value, note, level = "neutral") {
    return { label, value, note, level };
  }

  function comparisonCard(ctx, note = "지역/산별 분포") {
    return card("비교 산", `${mapRows(ctx).length}곳`, note, "neutral");
  }

  function landslideAxes(ls, weather, rain, gradeRisk, slopeRisk) {
    return [
      radarAxis("지도위험", gradeRisk, `${ls.grade ?? "—"}등급`),
      radarAxis("강우압력", rain, "예상 강수"),
      radarAxis("사면불안", slopeRisk, ls.label || "상태 확인"),
      radarAxis("계곡주의", mix(gradeRisk, rain, 0.55), "물길 주변"),
      radarAxis("낙석주의", mix(gradeRisk, windRisk(weather), 0.7), "절개지·암릉"),
      radarAxis("우회필요", mix(slopeRisk, rain, 0.62), "대체 하산로"),
    ];
  }

  function landslideCards(ctx, ls, rain) {
    const highGrade = num(ls.grade, 5) <= 2;
    return [
      card("위험지도", `${ls.grade ?? "—"}등급`, "산사태정보시스템", highGrade ? "warn" : "safe"),
      card("상태", ls.label || "—", statusWord(ls.score), ls.score >= 80 ? "safe" : "warn"),
      card("강수 영향", `${rain}%`, "최근/예상 강수 신호", rain >= 30 ? "warn" : "neutral"),
      card("지도 기준", regionText(ctx), "시군구/격자", "neutral"),
      comparisonCard(ctx),
      card("코스 판단", highGrade ? "대체 권장" : "진행 가능", "출발 전 선택", highGrade ? "warn" : "safe"),
    ];
  }

  function weatherAxes(weather, rain, wind, temp, volatility) {
    return [
      radarAxis("강풍", wind, formatWind(weather.wind)),
      radarAxis("비구름", rain, `${rain}% 강수`),
      radarAxis("체감냉각", temp, `${weather.temp ?? "—"}°C`),
      radarAxis("시야저하", mix(rain, volatility, 0.55), weather.label || "관측"),
      radarAxis("변덕성", volatility, "예보 불확실성"),
      radarAxis("노면미끄럼", mix(rain, temp, 0.72), "암릉·데크"),
    ];
  }

  function weatherCards(ctx, weather, rain) {
    const highWind = num(weather.wind) >= 7;
    const weatherRisk = rain >= 30 || highWind;
    return [
      card("기온", `${weather.temp ?? "—"}°C`, weather.label || "관측", "neutral"),
      card("풍속", formatWind(weather.wind), highWind ? "강풍 유의" : "보통", highWind ? "warn" : "safe"),
      card("강수확률", `${rain}%`, rain >= 30 ? "우의 준비" : "낮음", rain >= 30 ? "warn" : "safe"),
      card("관측소", weather.station || "관측망", "위치 기준", "neutral"),
      comparisonCard(ctx),
      card("코스 길이", weatherRisk ? "짧게" : "보통", "출발 전 선택", weatherRisk ? "warn" : "safe"),
    ];
  }

  function layer(label, value, note) {
    return { label, value, note };
  }

  function locationLayers(ctx, id) {
    const weather = ctx.weather || {};
    const ls = ctx.landslide || {};
    const base = {
      fire: [
        layer("지도 기준", regionText(ctx), "시군구/격자 산불예보"),
        layer("산 기준", mountainText(ctx), "선택 산행지"),
        layer("관측 보정", weather.station || "인근 산악기상", "풍속·강수"),
      ],
      landslide: [
        layer("위험지도", regionText(ctx), `${ls.grade ?? "—"}등급 사면권역`),
        layer("탐방 기준", mountainText(ctx), "계곡·절개지 우선 확인"),
        layer("강우 보정", weather.station || "인근 예보구역", `${rainProb(weather)}% 강수`),
      ],
      weather: [
        layer("관측소", weather.station || "산악기상관측망", "실측/예보 결합"),
        layer("산 기준", mountainText(ctx), "능선 체감 보정"),
        layer("지역 예보", regionText(ctx), "단기예보 격자"),
      ],
      sunset: [
        layer("일몰 기준", regionText(ctx), ctx.sunsetAt || "--:--"),
        layer("산 기준", mountainText(ctx), "하산 거리와 고도차 반영"),
        layer("현재 시각", "기기 시간", sunsetMarginText(ctx.sunsetAt)),
      ],
    };
    return base[id] || [];
  }

  function mapLegend() {
    return [
      { label: "낮음", level: "low" },
      { label: "주의", level: "mid" },
      { label: "높음", level: "high" },
    ];
  }

  function conditionMap(ctx, id) {
    const titles = {
      fire: ["지역/산별 산불위험", "산불예보"],
      landslide: ["지역/산별 산사태", "위험지도"],
      weather: ["지역/산별 기상", "관측/예보"],
      sunset: ["지역/산별 일몰", "일몰시각"],
    };
    const zones = mapRows(ctx).map((row, index) => {
      const name = row.name || regionText(ctx);
      const [lat, lon] = regionLatLon(row, index);
      return zone(name, rowValue(id, row), rowNote(id, row), rowRisk(id, row), lat, lon, row.selected ? "l" : "m");
    });
    return {
      provider: "leaflet",
      title: (titles[id] || titles.weather)[0],
      caption: (titles[id] || titles.weather)[1],
      markerLabel: "선택",
      zones,
      legend: mapLegend(),
    };
  }

  function enrich(ctx, detail, opts) {
    return {
      ...detail,
      feed: sourceFeed(ctx, detail.id),
      cards: opts.cards,
      location: locationLayers(ctx, detail.id),
      map: conditionMap(ctx, detail.id),
      radar: {
        title: opts.radarTitle || "현재 위험 벡터",
        scale: opts.radarScale || "높을수록 주의",
        axes: opts.axes,
      },
      primaryAction: opts.primaryAction || (detail.guidance && detail.guidance[0]) || "",
      actionTitle: opts.actionTitle || "출발 전 확인",
      updatedAt: freshness(ctx),
      modeLabel: modeLabel(ctx),
      accent: opts.accent,
    };
  }

  function buildConditionSummaryItems(ctx) {
    const fire = ctx.fire || {};
    const landslide = ctx.landslide || {};
    const weather = ctx.weather || {};
    return [
      {
        id: "fire",
        title: `산불위험 ${fire.level || "확인"}`,
        body: fire.src || "국립산림과학원 예보",
        tone: scoreTone(fire.score),
        ariaLabel: "산불위험 상세 정보 열기",
      },
      {
        id: "landslide",
        title: `산사태 ${landslide.label || "확인"}`,
        body: `위험지도 ${landslide.grade ?? "—"}등급`,
        tone: scoreTone(landslide.score),
        ariaLabel: "산사태 상세 정보 열기",
      },
      {
        id: "weather",
        title: `산악기상 ${weather.temp ?? "—"}°C`,
        body: weather.station || "산악기상 관측값",
        tone: scoreTone(weather.score),
        ariaLabel: "산악기상 상세 정보 열기",
      },
      {
        id: "sunset",
        title: `일몰 ${ctx.sunsetAt || "--:--"}`,
        body: "16시 이후 입산 주의",
        tone: scoreTone(ctx.sunsetScore ?? 60),
        ariaLabel: "일몰 상세 정보 열기",
      },
    ];
  }

  function buildFireDetail(ctx) {
    const fire = ctx.fire || {};
    const weather = ctx.weather || {};
    const risk = clamp(100 - num(fire.score, 70));
    const dry = clamp(100 - rainProb(weather));
    const wind = windRisk(weather);
    return enrich(ctx, {
      id: "fire",
      icon: "🔥",
      title: "산불위험",
      heroValue: fire.level || "확인 필요",
      summary: `${placeText(ctx)} 기준 산불 위험 단계입니다. 마른 낙엽, 강풍, 취사·흡연 여부가 실제 체감 위험을 크게 바꿉니다.`,
      metrics: [
        { label: "위험 단계", value: fire.level || "—", note: "예보 단계" },
        { label: "확산 바람", value: formatWind(weather.wind), note: num(weather.wind) >= 7 ? "강풍 유의" : "보통" },
        { label: "건조 신호", value: `${rainProb(weather)}% 강수`, note: rainProb(weather) < 20 ? "매우 건조" : "완화 가능" },
      ],
      guidance: ["방문할 산과 주변 지역의 산불 단계가 높으면 위험이 낮은 다른 산이나 짧은 코스로 바꾸세요."],
      source: fire.src || "국립산림과학원 산불위험예보",
    }, {
      accent: "#ff9f43",
      primaryAction: "방문할 산과 주변 지역의 산불 단계가 높으면 위험이 낮은 다른 산이나 짧은 코스로 바꾸세요.",
      axes: [
        radarAxis("예보위험", risk, fire.level || "단계 확인"),
        radarAxis("건조압력", dry, `${rainProb(weather)}% 강수`),
        radarAxis("확산바람", wind, formatWind(weather.wind)),
        radarAxis("화기민감", mix(risk, dry, 0.55), "취사·흡연 주의"),
        radarAxis("신고필요", mix(risk, wind, 0.6), "연기·탄 냄새"),
        radarAxis("진입통제", mix(risk, 100 - num(ctx.index, 70), 0.65), "통제 안내"),
      ],
      cards: [
        card("위험 단계", fire.level || "—", "산불위험예보", risk >= 45 ? "warn" : "safe"),
        card("확산 바람", formatWind(weather.wind), "능선부 민감 신호", num(weather.wind) >= 7 ? "warn" : "neutral"),
        card("건조 완화", `${rainProb(weather)}%`, "강수가 낮을수록 불리", rainProb(weather) < 20 ? "warn" : "safe"),
        card("지도 기준", regionText(ctx), "시군구/격자 예보", "neutral"),
        card("비교 산", `${mapRows(ctx).length}곳`, "지역/산별 분포", "neutral"),
        card("코스 판단", risk >= 45 ? "대체 권장" : "진행 가능", "출발 전 선택", risk >= 45 ? "warn" : "safe"),
      ],
    });
  }

  function buildLandslideDetail(ctx) {
    const ls = ctx.landslide || {};
    const weather = ctx.weather || {};
    const gradeRisk = clamp((6 - num(ls.grade, 5)) * 20);
    const rain = rainProb(weather);
    const slopeRisk = clamp(100 - num(ls.score, 82));
    return enrich(ctx, {
      id: "landslide",
      icon: "⛰",
      title: "산사태",
      heroValue: `${ls.label || "확인"} · ${ls.grade ?? "—"}등급`,
      summary: `${placeText(ctx)} 주변 사면의 산사태 위험지도와 최근 강우 영향을 함께 봐야 합니다. 계곡길·절개지·낙석 구간에서는 등급이 낮아도 보수적으로 움직이세요.`,
      metrics: [
        { label: "지도 등급", value: `${ls.grade ?? "—"}등급`, note: "지역 위험지도" },
        { label: "상태", value: ls.label || "—", note: statusWord(ls.score) },
        { label: "강수 영향", value: `${rain}%`, note: rain >= 30 ? "최근/예상 강수 주의" : "낮음" },
      ],
      guidance: ["비 예보가 있거나 전날 비가 왔다면 산사태 등급이 높은 지역의 산은 후보에서 제외하세요."],
      source: "산사태정보시스템 위험지도 · 산림청 등산로 위험구간",
    }, {
      accent: "#74c69d",
      primaryAction: "비 예보가 있거나 전날 비가 왔다면 산사태 등급이 높은 지역의 산은 후보에서 제외하세요.",
      axes: landslideAxes(ls, weather, rain, gradeRisk, slopeRisk),
      cards: landslideCards(ctx, ls, rain),
    });
  }

  function buildWeatherDetail(ctx) {
    const weather = ctx.weather || {};
    const rain = rainProb(weather);
    const wind = windRisk(weather);
    const temp = tempBurden(weather);
    const volatility = clamp(100 - num(weather.score, 64));
    return enrich(ctx, {
      id: "weather",
      icon: "🌦",
      title: "산악기상",
      heroValue: `${weather.temp ?? "—"}°C · ${weather.label || "관측"}`,
      summary: `${weather.station || placeText(ctx)} 기준입니다. 산 정상과 능선은 도심보다 춥고 바람이 강해 체감온도가 빠르게 떨어질 수 있습니다.`,
      metrics: [
        { label: "기온", value: `${weather.temp ?? "—"}°C`, note: "능선부 기준" },
        { label: "풍속", value: formatWind(weather.wind), note: num(weather.wind) >= 7 ? "강풍 유의" : "보통" },
        { label: "강수확률", value: `${rain}%`, note: rain >= 30 ? "우의 준비" : "낮음" },
      ],
      guidance: ["출발 전 방문할 산의 관측소 기준 풍속과 강수확률을 보고 복장과 코스 길이를 정하세요."],
      source: weather.station || "기상청 단기예보 · 산악기상관측망",
    }, {
      accent: "#4cc9f0",
      primaryAction: "출발 전 방문할 산의 관측소 기준 풍속과 강수확률을 보고 복장과 코스 길이를 정하세요.",
      axes: weatherAxes(weather, rain, wind, temp, volatility),
      cards: weatherCards(ctx, weather, rain),
    });
  }

  function buildSunsetDetail(ctx) {
    const mins = sunsetMinutes(ctx.sunsetAt);
    const pressure = sunsetPressure(ctx.sunsetAt);
    const afterDark = mins != null && mins <= 0;
    const shortMargin = mins != null && mins < 120;
    return enrich(ctx, {
      id: "sunset",
      icon: "🌄",
      title: "일몰",
      heroValue: ctx.sunsetAt || "--:--",
      summary: `${placeText(ctx)} 기준 일몰 시각입니다. 하산은 정상 도착 시간이 아니라 마지막 갈림길·대중교통·주차장 도착 시간까지 포함해서 판단해야 합니다.`,
      metrics: [
        { label: "일몰 시각", value: ctx.sunsetAt || "—", note: "지역 기준" },
        { label: "남은 시간", value: sunsetMarginText(ctx.sunsetAt), note: "현재 기기 시간 기준" },
        { label: "전환 기준", value: "16시 전", note: "새 코스 진입 마감" },
      ],
      guidance: ["출발 전에 예상 종료 시각이 일몰 1시간 전인지 확인하고, 아니면 더 짧은 코스를 고르세요."],
      source: "지역별 일몰 시각 · 현재 위치 기준",
    }, {
      accent: "#ffd166",
      primaryAction: "출발 전에 예상 종료 시각이 일몰 1시간 전인지 확인하고, 아니면 더 짧은 코스를 고르세요.",
      axes: [
        radarAxis("시간압박", pressure, sunsetMarginText(ctx.sunsetAt)),
        radarAxis("하산여유부족", shortMargin ? 78 : pressure, "주차장·교통까지"),
        radarAxis("야간전환", afterDark ? 100 : mix(pressure, shortMargin ? 70 : 20, 0.7), "시야 저하"),
        radarAxis("장비필요", shortMargin ? 85 : 35, "헤드랜턴·보온"),
        radarAxis("갈림길주의", mix(pressure, 60, 0.55), "하산로 판단"),
        radarAxis("교통마감", mix(pressure, shortMargin ? 72 : 34, 0.52), "귀가 시간"),
      ],
      cards: [
        card("일몰", ctx.sunsetAt || "—", "지역 기준", "neutral"),
        card("남은 시간", sunsetMarginText(ctx.sunsetAt), "기기 시간 기준", shortMargin ? "warn" : "safe"),
        card("전환 기준", "16시 전", "새 코스 진입 마감", "warn"),
        card("준비물", "헤드랜턴", "보조배터리·보온층", shortMargin ? "warn" : "neutral"),
        card("비교 산", `${mapRows(ctx).length}곳`, "지역/산별 일몰", "neutral"),
        card("코스 판단", shortMargin ? "짧게" : "진행 가능", "출발 전 선택", shortMargin ? "warn" : "safe"),
      ],
    });
  }

  function buildConditionDetail(id, ctx) {
    const map = {
      fire: buildFireDetail,
      landslide: buildLandslideDetail,
      weather: buildWeatherDetail,
      sunset: buildSunsetDetail,
    };
    if (!map[id]) throw new Error(`Unknown condition detail: ${id}`);
    return map[id](ctx || {});
  }

  return { buildConditionDetail, buildConditionSummaryItems };
});
