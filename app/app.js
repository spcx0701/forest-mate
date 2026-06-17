/* 숲길동무 앱 로직 — 데모 빌드(시뮬레이션 GPS) */
"use strict";
const $ = (id) => document.getElementById(id);
const qs = (sel, root = document) => root.querySelector(sel);
const qsa = (sel, root = document) => [...root.querySelectorAll(sel)];
const nowHM = () => { const d = new Date(); return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };
const clampNumber = (value, min, max, fallback) => {
  const n = Number(value);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
};
const safeText = (value, fallback = "") => String(value ?? fallback).replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 240);
const safeToken = (value) => /^[A-Za-z0-9._~+/=-]{12,4096}$/.test(String(value || "")) ? String(value) : null;
function normalizeApiBase(raw) {
  const fallback = "/api/v1";
  const value = String(raw || fallback).trim();
  if (value.startsWith("/") && !value.startsWith("//")) return value.replace(/\/+$/, "");
  try {
    const url = new URL(value, location.origin);
    const sameOrigin = url.origin === location.origin;
    const localHttp = url.protocol === "http:" && /^(localhost|127\.0\.0\.1|::1)$/.test(url.hostname);
    if (url.protocol === "https:" || sameOrigin || localHttp) return url.href.replace(/\/+$/, "");
  } catch {}
  return fallback;
}
function safeApiPath(path) {
  const value = String(path || "");
  const cleanPath = value.startsWith("/") ? value : `/${value}`;
  if (cleanPath.startsWith("//") || !/^\/[A-Za-z0-9._~!$&'()*+,;=:@/%?-]*$/.test(cleanPath)) {
    throw new Error("Invalid API path");
  }
  return cleanPath;
}
function sanitizeProfile(profile) {
  const value = profile && typeof profile === "object" ? profile : {};
  return {
    set: Boolean(value.set),
    name: safeText(value.name),
    fit: clampNumber(value.fit, 1, 5, DEFAULTS.profile.fit),
    knee: Boolean(value.knee),
    heart: Boolean(value.heart),
  };
}
function sanitizeAccount(user) {
  if (!user || typeof user !== "object") return null;
  return {
    id: safeText(user.id),
    email: safeText(user.email),
    profile: sanitizeProfile(user.profile || {}),
  };
}
function sanitizeState(state) {
  const value = state && typeof state === "object" ? state : {};
  return {
    profile: sanitizeProfile(value.profile),
    account: sanitizeAccount(value.account),
    settings: {
      offRoute: Boolean(value.settings?.offRoute),
      family: Boolean(value.settings?.family),
    },
    lang: ["ko", "en", "zh", "ja"].includes(value.lang) ? value.lang : DEFAULTS.lang,
    region: safeText(value.region, DEFAULTS.region),
    aiCount: clampNumber(value.aiCount, 0, 100000, 0),
    hikesDone: clampNumber(value.hikesDone, 0, 100000, 0),
    june: {
      cnt: clampNumber(value.june?.cnt, 0, 100000, 0),
      km: clampNumber(value.june?.km, 0, 1000000, 0),
      kcal: clampNumber(value.june?.kcal, 0, 100000000, 0),
      co2: clampNumber(value.june?.co2, 0, 1000000, 0),
    },
    installAt: clampNumber(value.installAt, 0, Date.now(), null),
    insurance: value.insurance ? safeText(value.insurance) : null,
  };
}

/* ---------------- 상태 저장 ---------------- */
const DEFAULTS = {
  profile: { set: false, name: "", fit: 2, knee: true, heart: false },
  account: null,
  settings: { offRoute: true, family: true },
  lang: "ko",
  region: "eunpyeong",
  aiCount: 0,
  hikesDone: 0,
  // 오프라인(로컬) 폴백용 누적치 — 신규 사용자는 0에서 시작(가짜 시드 제거).
  // cloud 모드에서는 서버 /hikes/summary 실집계로 대체된다.
  june: { cnt: 0, km: 0, kcal: 0, co2: 0 },
  installAt: null,
  insurance: null,
};
let S;
try { S = { ...DEFAULTS, ...sanitizeState(JSON.parse(localStorage.getItem("fm_state") || "{}")) }; }
catch { S = { ...DEFAULTS }; }
S.profile = { ...DEFAULTS.profile, ...S.profile };
S.settings = { ...DEFAULTS.settings, ...S.settings };
S.june = { ...DEFAULTS.june, ...S.june };
const save = () => {
  try {
    const serialized = JSON.stringify(sanitizeState(S));
    localStorage.setItem("fm_state", serialized); // NOSONAR: sanitizeState normalizes persisted browser state.
  } catch {}
};
if (!S.installAt) { S.installAt = Date.now(); save(); }   // 최초 사용일(로컬 경과일 계산용)

/* URL 파라미터 (?t=tab&demo=57 — 화면 캡처/시연용) */
const Q = new URLSearchParams(location.search);
const demoParam = Q.get("demo");
const DEMO = demoParam === null ? null : Math.min(100, Math.max(0, +demoParam || 57));
function timeoutSignal(ms) {
  if (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") return AbortSignal.timeout(ms);
  if (typeof AbortController === "undefined") return undefined;
  const controller = new AbortController();
  setTimeout(() => controller.abort(), ms);
  return controller.signal;
}

/* ---------------- 클라우드 API 클라이언트 ----------------
 * 백엔드(/api/v1)가 살아 있으면 cloud 모드: 산행지수·추천·챗·산행기록·SOS가
 * 실서버를 경유한다. 어떤 호출이든 실패하면 조용히 로컬 엔진으로 폴백 —
 * 통신 음영지역에서도 앱은 끝까지 동작해야 한다. */
const API = {
  // 기본은 동일 오리진(/api/v1). Capacitor(iOS) 번들 빌드처럼 오리진이 다른 경우
  // index.html에서 window.FM_API_BASE = "https://<배포도메인>/api/v1" 로 주입한다.
  base: normalizeApiBase(globalThis.FM_API_BASE || ""),
  mode: "local",
  lastError: "",
  token: null,
  authToken: null,
  hikeId: null,
  hikeStartPromise: null,
  async init() {
    if (location.protocol === "file:") return false;
    this.consumeAuthRedirect();
    try {
      const r = await fetch(this.url("/healthz"), { signal: timeoutSignal(1500) });
      if (!r.ok) throw new Error("health check failed");
      this.mode = "cloud";
      this.lastError = "";
      if (this.authToken) await this.loadMe();
      return true;
    } catch (err) {
      this.mode = "local";
      this.lastError = err?.message ? err.message : "server unavailable";
      return false;
    }
  },
  consumeAuthRedirect() {
    const hash = new URLSearchParams((location.hash || "").replace(/^#/, ""));
    const token = hash.get("auth_token");
    const err = hash.get("auth_error");
    if (token) {
      this.authToken = safeToken(token);
      history.replaceState(null, "", location.pathname + location.search);
      if (this.authToken) toast("로그인 완료", "워치와 웹에서도 기록을 볼 수 있어요", "🔐");
      else toast("로그인 실패", "인증 토큰 형식이 올바르지 않아요", "🔐", true);
    } else if (err) {
      history.replaceState(null, "", location.pathname + location.search);
      toast("로그인 실패", "소셜 계정 연결을 완료하지 못했어요", "🔐", true);
    }
  },
  async register() {
    const p = S.profile;
    const reg = await this.post("/devices", { name: p.name, fit: p.fit, knee: p.knee, heart: p.heart }, false);
    this.token = safeToken(reg.token);
    return reg;
  },
  url(path) {
    return this.base + safeApiPath(path);
  },
  async ensureToken() {
    if (!this.authToken && !this.token) await this.register();
  },
  headers(auth = true) {
    const h = { "Content-Type": "application/json" };
    const bearer = this.authToken || this.token;
    if (auth && bearer) h.Authorization = "Bearer " + bearer;
    return h;
  },
  async get(path, auth = true, retry = true) {
    if (auth) await this.ensureToken();
    const r = await fetch(this.url(path), { headers: this.headers(auth), signal: timeoutSignal(3000) }); // NOSONAR: safeApiPath allow-lists API paths.
    if (r.status === 401 && auth && retry) {   // 토큰 무효 → 게스트 기기 재등록 후 1회 재시도
      this.clearAccount();
      this.token = null;
      await this.register();
      return this.get(path, auth, false);
    }
    if (!r.ok) throw new Error(path + " " + r.status);
    return r.json();
  },
  async post(path, body, auth = true, retry = true) {
    if (auth) await this.ensureToken();
    const r = await fetch(this.url(path), { // NOSONAR: safeApiPath allow-lists API paths.
      method: "POST", headers: this.headers(auth), body: JSON.stringify(body || {}),
      signal: timeoutSignal(4000),
    });
    // 토큰이 무효(서버 DB 교체·만료·revoke)면 익명 기기 재등록 후 1회 재시도.
    // 이게 없으면 산행·SOS 같은 인증 호출이 조용히 로컬 폴백돼 관제에 안 잡힌다.
    if (r.status === 401 && auth && retry) {
      this.clearAccount();
      this.token = null;
      await this.register();
      return this.post(path, body, auth, false);
    }
    if (!r.ok) throw new Error(path + " " + r.status);
    return r.json();
  },
  async patch(path, body, auth = true) {
    if (auth) await this.ensureToken();
    const r = await fetch(this.url(path), { // NOSONAR: safeApiPath allow-lists API paths.
      method: "PATCH", headers: this.headers(auth), body: JSON.stringify(body || {}),
      signal: timeoutSignal(4000),
    });
    if (!r.ok) throw new Error(path + " " + r.status);
    return r.json();
  },
  setAccount(body) {
    this.authToken = safeToken(body.access_token);
    if (body.device_token) {
      this.token = safeToken(body.device_token);
    }
    S.account = sanitizeAccount(body.user);
    if (body.user?.profile) {
      S.profile = { ...S.profile, ...sanitizeProfile(body.user.profile), set: true };
    }
    save();
  },
  clearAccount(clearServerToken = true) {
    if (clearServerToken) this.authToken = null;
    localStorage.removeItem("fm_auth_token");
    localStorage.removeItem("fm_token");
    S.account = null;
    save();
  },
  async loadMe() {
    try {
      const me = await this.get("/auth/me", true, false);
      this.setAccount({ access_token: this.authToken, device_token: me.device_token, user: me.user });
      return me;
    } catch {
      this.clearAccount();
      return null;
    }
  },
  async accountRegister(email, password) {
    await this.ensureToken();
    const p = S.profile;
    const body = await this.post("/auth/register", {
      email, password, name: p.name || "산친구", fit: p.fit, knee: p.knee, heart: p.heart,
      device_token: this.token,
    }, false);
    this.setAccount(body);
    return body;
  },
  async accountLogin(email, password) {
    const body = await this.post("/auth/login", { email, password, device_token: this.token }, false);
    this.setAccount(body);
    return body;
  },
  async saveProfile() {
    if (this.mode !== "cloud" || !this.authToken) return;
    const p = S.profile;
    const me = await this.patch("/auth/me/profile", {
      name: p.name || "산친구", fit: p.fit, knee: p.knee, heart: p.heart,
    });
    S.account = me.user; save();
  },
  async logout() {
    if (this.authToken) {
      try { await this.post("/auth/logout", {}, true, false); } catch {}
    }
    this.clearAccount();
  },
  async oauth(provider) {
    await this.ensureToken();
    const p = S.profile;
    const params = new URLSearchParams({
      device_token: this.token || "",
      name: p.name || "산친구",
      fit: String(p.fit || 2),
      knee: String(!!p.knee),
      heart: String(!!p.heart),
    });
    location.href = `${this.base}/auth/oauth/${provider}/start?${params.toString()}`;
  },
};

/* ---------------- 토스트 ---------------- */
let toastTimer;
function toast(title, body, ico = "🔔", alert = false, ms = 3400) {
  $("toastTitle").textContent = title;
  $("toastBody").textContent = body;
  $("toastIco").textContent = ico;
  $("toast").classList.toggle("alert", alert);
  $("toast").classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $("toast").classList.remove("show"), ms);
}

/* ---------------- 탭 라우팅 ---------------- */
const tabs = qsa("nav a");
function safeScreenId(id) {
  switch (String(id || "")) {
    case "trail":
    case "sos":
    case "ai":
    case "my":
      return String(id);
    default:
      return "home";
  }
}
function show(id) {
  const activeId = safeScreenId(id);
  qsa(".screen").forEach((s) => {
    const active = s.id === activeId;
    s.classList.toggle("active", active);
    if (active) s.scrollTop = 0;
  });
  tabs.forEach((a) => a.classList.toggle("on", a.dataset.t === activeId));
}
tabs.forEach((a) => a.addEventListener("click", (e) => {
  e.preventDefault();
  const activeId = safeScreenId(a.dataset.t);
  history.replaceState(null, "", "#" + activeId);
  show(activeId);
}));

/* ---------------- 산행지수 ---------------- */
function calcIndex(r) {
  return Math.floor(r.fire.score * 0.3 + r.landslide.score * 0.25 + r.weather.score * 0.25 + r.sunsetScore * 0.2);
}
function idxLabel(v) {
  if (v >= 80) return "좋음 — 산행하기 좋은 날 🌤";
  if (v >= 60) return "보통 — 기상 변화에 유의하세요 ⛅";
  return "주의 — 무리한 산행은 피하세요 ⚠️";
}
function idxColor(v) {
  if (v >= 80) return "#B7E4C7";
  if (v >= 60) return "#FFD8A8";
  return "#FFB3B8";
}

let currentConditionContext = null;
function normalizeWeather(weather) {
  return { ...weather, rainProb: weather.rainProb ?? weather.rain_prob ?? 0 };
}
function conditionMapRegions() {
  const rows = [];
  for (const course of FM_DATA.courses || []) {
    const region = FM_DATA.regions[course.region];
    if (!region) continue;
    const [lat, lon] = courseLatLon(course);
    rows.push({
      name: region.name,
      mountain: (course.name || course.peak || "").split(" ")[0] || course.name,
      lat,
      lon,
      fire: region.fire,
      landslide: region.landslide,
      weather: normalizeWeather(region.weather),
      sunsetAt: region.sunsetAt,
      selected: S.selectedMountain?.name === course.name,
    });
  }
  if (!rows.length) {
    for (const region of Object.values(FM_DATA.regions || {})) {
      rows.push({
        name: region.name,
        mountain: region.name,
        fire: region.fire,
        landslide: region.landslide,
        weather: normalizeWeather(region.weather),
        sunsetAt: region.sunsetAt,
      });
    }
  }
  return rows;
}
function paintIndexCard({ v, fire, landslide, weather, sunsetAt, placeLabel, regionName, sunsetScore }) {
  const C = 276.5;
  currentConditionContext = {
    index: v,
    regionName,
    placeLabel,
    fire,
    landslide,
    weather: normalizeWeather(weather),
    sunsetAt,
    sunsetScore,
    mapRegions: conditionMapRegions(),
    mode: API.mode,
    updatedAt: nowHM(),
  };
  $("idxVal").textContent = v;
  $("idxArc").style.strokeDashoffset = (C * (1 - v / 100)).toFixed(1);
  $("idxArc").style.stroke = idxColor(v);
  const label = $("idxLabel");
  label.textContent = idxLabel(v);
  if (placeLabel) {
    const place = document.createElement("span");
    place.className = "idx-place";
    place.append(document.createTextNode(`${placeLabel} `));
    const resetLink = document.createElement("a");
    resetLink.id = "mntReset";
    resetLink.textContent = "✕ 내 지역";
    place.appendChild(resetLink);
    label.appendChild(place);
  }
  const items = FM_CONDITION_DETAILS.buildConditionSummaryItems(currentConditionContext);
  $("idxGrid").innerHTML = items.map((item) => `
    <button type="button" class="idx-item condition-trigger" data-condition="${esc(item.id)}" aria-label="${esc(item.ariaLabel)}">
      <b class="${cssToken(item.tone, "neutral")}">${esc(item.title)}</b>${esc(item.body)}
      <span class="idx-more">자세히</span>
    </button>`).join("");
  qsa("#idxGrid .condition-trigger").forEach((b) => b.addEventListener("click", () => openConditionDetail(b.dataset.condition)));
  const reset = $("mntReset");
  if (reset) reset.addEventListener("click", () => { S.selectedMountain = null; save(); renderHome(); });
}

async function fetchConditions(path) {
  const d = await API.get(path, false);
  return {
    v: d.score, sunsetAt: d.conditions.sunset_at, sunsetScore: d.conditions.sunset_score, place: d.place, regionName: d.conditions.name,
    fire: { level: d.conditions.fire.level, score: d.conditions.fire.score, src: d.conditions.fire.src },
    landslide: d.conditions.landslide, weather: normalizeWeather(d.conditions.weather),
  };
}

function openConditionDetail(id) {
  if (!currentConditionContext) return;
  const d = FM_CONDITION_DETAILS.buildConditionDetail(id, currentConditionContext);
  $("extSheet").innerHTML = `
    <div class="condition-panel" style="--condition-accent:${cssColor(d.accent)}">
      <button class="condition-close" data-close="extModal" aria-label="닫기">×</button>
      <div class="condition-topline">
        <span>${esc(d.modeLabel || "DATA")}</span>
        <span>${esc(d.updatedAt)}</span>
      </div>
      <div class="condition-hero">
        <div>
          <div class="condition-kicker">${d.icon} ${esc(d.title)}</div>
          <div class="condition-value">${esc(d.heroValue)}</div>
        </div>
        <div class="condition-gauge">
          <span>${esc(String(currentConditionContext.index || "--"))}</span>
          <small>산행지수</small>
        </div>
      </div>
      <div class="condition-feed">
        ${d.feed.map((f) => `<div class="feed-chip"><span>${esc(f.kind)}</span><b>${esc(f.label)}</b><small>${esc(f.value)}</small></div>`).join("")}
      </div>
      <div class="condition-chart">
        <div class="chart-head"><b>${esc(d.radar.title)}</b><span>${esc(d.radar.scale)}</span></div>
        ${conditionRadarSvg(d.radar.axes, d.accent)}
      </div>
      <div class="condition-map">
        <div class="chart-head"><b>${esc(d.map.title)}</b><span>${esc(d.map.caption)}</span></div>
        ${conditionMapMarkup(d.map)}
      </div>
      <div class="condition-card-grid">
        ${d.cards.map((c) => `<div class="signal-card ${cssToken(c.level)}"><span>${esc(c.label)}</span><b>${esc(c.value)}</b><small>${esc(c.note)}</small></div>`).join("")}
      </div>
      <div class="condition-guide">
        <b>${esc(d.actionTitle || "출발 전 확인")}</b>
        <div class="condition-step">${esc(d.primaryAction || d.guidance?.[0] || "")}</div>
      </div>
      <div class="condition-source">${esc(d.source)}</div>
    </div>`;
  $("extModal").classList.add("show");
  requestAnimationFrame(() => initConditionMap(d.map));
}

function conditionRadarSvg(axes, accent) {
  const W = 360, H = 196, cx = 180, cy = 96, radius = 58, labelRadius = 84;
  const safe = axes?.length ? axes : [{ label: "", value: 0, note: "" }];
  const safeAccent = cssColor(accent);
  const point = (axis, i, r = radius) => {
    const angle = -Math.PI / 2 + (Math.PI * 2 * i) / safe.length;
    const value = Math.max(0, Math.min(100, Number(axis.value) || 0));
    const rr = r * (value / 100);
    return [cx + Math.cos(angle) * rr, cy + Math.sin(angle) * rr, angle];
  };
  const maxPoint = (i, r = radius) => {
    const angle = -Math.PI / 2 + (Math.PI * 2 * i) / safe.length;
    return [cx + Math.cos(angle) * r, cy + Math.sin(angle) * r, angle];
  };
  const rings = [0.25, 0.5, 0.75, 1].map((scale) => {
    const pts = safe.map((_, i) => {
      const [x, y] = maxPoint(i, radius * scale);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(" ");
    return `<polygon class="radar-grid" points="${pts}"/>`;
  }).join("");
  const spokes = safe.map((_, i) => {
    const [x, y] = maxPoint(i);
    return `<line class="radar-spoke" x1="${cx}" y1="${cy}" x2="${x.toFixed(1)}" y2="${y.toFixed(1)}"/>`;
  }).join("");
  const shape = safe.map((axis, i) => {
    const [x, y] = point(axis, i);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(" ");
  const dots = safe.map((axis, i) => {
    const [x, y] = point(axis, i);
    return `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="3.2"/>`;
  }).join("");
  const labels = safe.map((axis, i) => {
    const [x, y] = maxPoint(i, labelRadius);
    let anchor = "middle";
    if (x < cx - 8) anchor = "end";
    else if (x > cx + 8) anchor = "start";
    return `<text x="${x.toFixed(1)}" y="${Math.max(14, Math.min(H - 12, y)).toFixed(1)}" text-anchor="${anchor}">${esc(axis.label)}</text>`;
  }).join("");
  const legend = safe.map((axis) => `<div class="radar-axis"><span>${esc(axis.label)}</span><b>${esc(String(axis.value))}</b><small>${esc(axis.note || "")}</small></div>`).join("");
  return `<div class="radar-wrap">
    <svg class="radar-graph" viewBox="0 0 ${W} ${H}" role="img" aria-label="현재 위험 벡터 레이더 그래프">
      <defs><linearGradient id="radarFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${safeAccent}" stop-opacity=".54"/><stop offset="1" stop-color="${safeAccent}" stop-opacity=".12"/></linearGradient></defs>
      <g>${rings}${spokes}</g>
      <polygon class="radar-fill" points="${shape}"/>
      <polyline class="radar-line" points="${shape} ${shape.split(" ")[0]}" fill="none"/>
      <g class="radar-dots">${dots}</g>
      <g class="radar-labels">${labels}</g>
    </svg>
    <div class="radar-axis-list">${legend}</div>
  </div>`;
}

function conditionMapMarkup(map) {
  const safe = map?.zones?.length ? map.zones : [];
  const legend = (map?.legend ? map.legend : []).map((l) => `<span class="map-legend-item ${cssToken(l.level)}"><i></i>${esc(l.label)}</span>`).join("");
  const rows = safe.map((z) => `<div class="zone-row ${cssToken(z.level)}"><span>${esc(z.label)}</span><b>${esc(z.value)}</b><small>${esc(z.note)}</small></div>`).join("");
  return `<div class="zone-map-wrap">
    <div class="map-legend">${legend}</div>
    <div id="conditionLeafletMap" class="condition-leaflet-map" role="img" aria-label="${esc(map.title)}"></div>
    <div class="zone-list">${rows}</div>
  </div>`;
}

let conditionLeafletMap = null;
function mapTileConfig() {
  if (globalThis.FM_MAP_TILE_URL) {
    return {
      url: globalThis.FM_MAP_TILE_URL,
      options: { maxZoom: 18, attribution: globalThis.FM_MAP_ATTRIBUTION || "" },
    };
  }
  if (globalThis.FM_VWORLD_KEY) {
    return {
      url: `https://api.vworld.kr/req/wmts/1.0.0/${globalThis.FM_VWORLD_KEY}/Base/{z}/{y}/{x}.png`,
      options: { maxZoom: 19, attribution: globalThis.FM_MAP_ATTRIBUTION || "VWorld" },
    };
  }
  return {
    url: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
    options: { maxZoom: 18, attribution: "© OpenStreetMap contributors" },
  };
}
function initConditionMap(mapData) {
  const el = $("conditionLeafletMap");
  const leaflet = globalThis.L;
  if (!el || !leaflet || !mapData || !Array.isArray(mapData.zones)) return;
  if (conditionLeafletMap) {
    conditionLeafletMap.remove();
    conditionLeafletMap = null;
  }
  const points = mapData.zones
    .map((z) => ({ ...z, lat: Number(z.lat), lon: Number(z.lon) }))
    .filter((z) => Number.isFinite(z.lat) && Number.isFinite(z.lon));
  if (!points.length) return;
  const map = leaflet.map(el, { attributionControl: false, zoomControl: false, dragging: true, scrollWheelZoom: false, tap: false });
  const tile = mapTileConfig();
  leaflet.tileLayer(tile.url, tile.options).addTo(map);
  const bounds = [];
  const color = { low: "#58d68d", mid: "#ffd166", high: "#ff6b6b" };
  points.forEach((z) => {
    const marker = leaflet.circleMarker([z.lat, z.lon], {
      radius: z.size === "l" ? 10 : 8,
      color: "#fff",
      weight: 3,
      fillColor: color[z.level] || "#58d68d",
      fillOpacity: 0.95,
    }).addTo(map);
    marker.bindTooltip(`${z.note.split("·")[0].trim()} · ${z.value}`, {
      permanent: true,
      direction: "right",
      offset: [9, 0],
      className: "condition-map-label",
    });
    marker.bindPopup(`<b>${esc(z.label)}</b><br>${esc(z.value)}<br>${esc(z.note)}`);
    bounds.push([z.lat, z.lon]);
  });
  map.fitBounds(bounds, { padding: [22, 22], maxZoom: 11 });
  conditionLeafletMap = map;
  setTimeout(() => map.invalidateSize(), 120);
}

async function renderHome() {
  let v, fire, landslide, weather, sunsetAt, sunsetScore, regionName, placeLabel = null;
  const sel = API.mode === "cloud" ? S.selectedMountain : null;
  if (sel) {
    try {
      const c = await fetchConditions(`/mountains/${encodeURIComponent(sel.listNo)}/index`);
      ({ v, fire, landslide, weather, sunsetAt, sunsetScore, regionName } = c);
      placeLabel = `🏔 ${sel.name} · ${c.place}`;
    } catch { S.selectedMountain = null; }
  }
  if (placeLabel === null && API.mode === "cloud" && S.activeLoc) {
    try {
      const c = await fetchConditions(`/index/gps?lat=${S.activeLoc.lat}&lon=${S.activeLoc.lon}`);
      ({ v, fire, landslide, weather, sunsetAt, sunsetScore, regionName } = c);
      placeLabel = `📍 ${S.activeLoc.label}`;
    } catch { S.activeLoc = null; }
  }
  if (placeLabel === null) {
    const r = FM_DATA.regions[S.region];
    v = calcIndex(r); fire = r.fire; landslide = r.landslide; weather = r.weather; sunsetAt = r.sunsetAt; sunsetScore = r.sunsetScore; regionName = r.name;
    if (API.mode === "cloud") {
      try { ({ v, fire, landslide, weather, sunsetAt, sunsetScore, regionName } = await fetchConditions(`/index?region=${S.region}`)); }
      catch { /* 로컬 계산 유지 */ }
    }
  }
  paintIndexCard({ v, fire, landslide, weather, sunsetAt, placeLabel, regionName, sunsetScore });
  updateLocLabel();
  renderReco();
  $("briefing").innerHTML = `<b>${FM_DATA.briefings[new Date().getDay() % FM_DATA.briefings.length].split(".")[0]}.</b><br>${FM_DATA.briefings[new Date().getDay() % FM_DATA.briefings.length].split(".").slice(1).join(".").trim()}`;
  $("newsLine").textContent = FM_DATA.news.slice(0, 3)
    .map((n) => typeof n === "object" ? (n.title || "") : String(n))
    .join(" · ");
}

async function selectMountainIndex(listNo, name) {
  $("mntModal").classList.remove("show");
  S.selectedMountain = { listNo, name }; save();
  show("home");
  await renderHome();
  toast("산행지수", `${name} 기준으로 산행지수 ${$("idxVal").textContent}점을 계산했어요`, "🏔");
}

/* ---------------- AI 추천 ---------------- */
function matchScore(c) {
  const p = S.profile, r = FM_DATA.regions[c.region];
  const crowdPen = { 높음: 8, 보통: 3, 낮음: 1 }[c.crowd] || 3;
  let s = 100 - Math.abs(c.levelN - p.fit) * 9 - (c.steep && p.knee ? 4 : 0) - crowdPen - (100 - r.weather.score) / 12;
  if (p.heart && c.levelN >= 3) s -= 6;
  return Math.round(s);
}
async function renderReco() {
  const p = S.profile;
  const notes = [];
  notes.push(`체력 ${["", "초급", "중급", "상급"][p.fit]}`);
  if (p.knee) notes.push("무릎 주의 이력");
  if (p.heart) notes.push("심혈관 주의");
  // 활성 위치(시도/GPS)가 있으면 그 주변 산을 추천 — 지역 변경이 추천에 반영됨
  const loc = API.mode === "cloud" ? S.activeLoc : null;
  if (loc) {
    try {
      const d = await API.get(`/mountains/nearby?lat=${loc.lat}&lon=${loc.lon}&radius=60&limit=6`, false);
      if (d.items?.length) {
        $("recoNote").textContent = `${loc.label} 주변 · ${notes.join(" · ")} 반영`;
        $("recoList").innerHTML = d.items.map((m) => `
          <button class="r" data-mtn-id="${esc(m.list_no)}" data-mtn-name="${esc(m.name)}">
            <div class="thumb t1"><img class="thumb-img" data-mtn="${esc((m.name || "").split(" ")[0])}" data-h="${clampNumber(m.height, 0, 10000, 0)}" alt="">
              <span class="match">${esc(m.dist_km)}km</span></div>
            <div class="body"><b>${esc(m.name)}${m.top100 ? " 🏅" : ""}</b>
              <div class="meta"><span>📍 ${esc(m.sido || "")}</span><span>⛰ ${m.height ? esc(m.height) + "m" : "—"}</span><span>${esc(loc.label)} 인근</span></div>
            </div></button>`).join("");
        qsa("#recoList .r").forEach((b) => b.addEventListener("click", () => openMountainDetail(b.dataset.mtnId, b.dataset.mtnName)));
        qsa("#recoList .thumb-img").forEach((img) => loadHero(img, img.dataset.mtn, +img.dataset.h));
        return;
      }
    } catch { /* 아래 큐레이션 코스로 폴백 */ }
  }
  $("recoNote").textContent = notes.join(" · ") + " 반영";
  let list = FM_DATA.courses.map((c) => ({ c, s: matchScore(c) })).sort((a, b) => b.s - a.s);
  if (API.mode === "cloud") {
    try {
      const cloud = await API.get(`/recommend?fit=${p.fit}&knee=${p.knee}&heart=${p.heart}`, false);
      list = cloud
        .map((r) => ({ c: FM_DATA.courses.find((c) => c.id === r.course_id), s: r.score }))
        .filter((x) => x.c);
    } catch { /* 로컬 순위 유지 */ }
  }
  $("recoList").innerHTML = list.map(({ c, s }) => `
    <button class="r" data-course="${esc(c.id)}">
      <div class="thumb ${c.theme}"><img class="thumb-img" data-mtn="${c.name.split(" ")[0]}" data-h="${Number.parseInt((c.peak || "").replace(/\D/g, ""), 10) || 0}" alt=""><span class="match">매칭 ${s}%</span></div>
      <div class="body"><b>${c.name}</b>
        <div class="meta"><span>⛰ ${c.km}km</span><span>⏱ ${fmtMin(c.minutes)}</span><span>난이도 ${c.level}</span><span>${courseHighlight(c)}</span></div>
      </div>
    </button>`).join("");
  qsa("#recoList .r").forEach((b) => b.addEventListener("click", () => openCourse(b.dataset.course)));
  qsa("#recoList .thumb-img").forEach((img) => loadHero(img, img.dataset.mtn, +img.dataset.h));
}
function courseHighlight(course) {
  if (course.view >= 5) return "전망 ★★★";
  return `혼잡 ${course.crowd}`;
}
function fmtMin(m) {
  if (m < 60) return `${m}분`;
  const minutes = m % 60;
  const hourText = `${Math.floor(m / 60)}시간`;
  const minuteText = minutes ? `${minutes}분` : "";
  return `${hourText} ${minuteText}`.trim();
}

/* ---------------- 코스 상세 모달 ---------------- */
function sparkline(elev) {
  const w = 360, h = 70, max = Math.max(...elev), min = Math.min(...elev);
  const pts = elev.map((e, i) => `${(i / (elev.length - 1)) * w},${h - 8 - ((e - min) / (max - min)) * (h - 18)}`).join(" ");
  return `<svg viewBox="0 0 ${w} ${h}" width="100%" class="elev">
    <polyline points="${pts}" fill="none" stroke="#40916C" stroke-width="3" stroke-linecap="round"/>
    <polygon points="0,${h} ${pts} ${w},${h}" fill="rgba(116,198,157,.22)" stroke="none"/>
    <text x="2" y="12" font-size="11" fill="#6b7f72">▲ 최고 ${max}m</text></svg>`;
}
function openCourse(id) {
  const c = FM_DATA.courses.find((x) => x.id === id);
  const s = matchScore(c);
  $("courseSheet").innerHTML = `
    <div class="grab"></div>
    <img id="courseHero" class="mtn-hero" alt="${c.name} 전경">
    <h3>${c.name} <span style="color:#40916C;font-size:13px">매칭 ${s}%</span></h3>
    <p class="sub">${c.route} · ${FM_DATA.regions[c.region].name}</p>
    ${sparkline(c.elev)}
    <div class="meta-row">
      <div class="kv"><b>${c.km}km</b><span>거리</span></div>
      <div class="kv"><b>${fmtMin(c.minutes)}</b><span>AI 예상시간</span></div>
      <div class="kv"><b>${c.level}</b><span>난이도</span></div>
      <div class="kv"><b>${c.crowd}</b><span>혼잡(빅데이터)</span></div>
    </div>
    ${c.hazards.map((hz) => `<div class="haz">⚠️ <div><b>${hz.type} — ${hz.grade}</b>${hz.note}</div></div>`).join("")}
    <b style="font-size:12px">🗺 들머리 위치 · 가는 길</b>
    <div id="courseMap" class="detail-map"></div>
    ${dirButtons(courseLatLon(c)[0], courseLatLon(c)[1], c.name.split(" ")[0] + " " + c.startLabel)}
    <div class="btnrow">
      <button class="btn ghost" data-close="courseModal">닫기</button>
      <button class="btn primary" id="btnStartCourse">🧭 이 코스 선택 · 가는 길 보기</button>
    </div>`;
  { const [la, lo] = courseLatLon(c); setTimeout(() => miniMap("courseMap", la, lo, c.startLabel + " · 들머리"), 60); }
  $("courseModal").classList.add("show");
  loadHero($("courseHero"), c.name.split(" ")[0], Number.parseInt((c.peak || "").replace(/\D/g, ""), 10) || 0);
  $("btnStartCourse").addEventListener("click", () => {
    $("courseModal").classList.remove("show");
    selectCourse(c.id);          // 산행 탭으로 — 들머리까지 '가는 길' 표시(아직 시작 아님)
    history.replaceState(null, "", "#trail");
    show("trail");
    toast("코스 선택됨", `${Hike.course.name} · 들머리까지 가는 길을 확인하고, 도착하면 '산행 시작'을 누르세요`, "🧭");
  });
}

/* ---------------- 산행: 지도 + 시뮬레이션 ---------------- */
const Hike = { course: null, prog: 0, active: false, ended: false, timer: null, hr: 92, alerted: {}, sunsetLeft: null, watch: null, watchPair: null, watchPoll: null };
const WATCH_FRESH_MS = 65000;

function selectCourse(id) {
  Hike.course = FM_DATA.courses.find((x) => x.id === id);
  Hike.prog = 0; Hike.active = false; Hike.ended = false; Hike.alerted = {};
  $("trailName").textContent = Hike.course.name;
  $("trailRoute").textContent = Hike.course.route;
  $("gridNo").textContent = Hike.course.gridNo;
  buildMap();
  renderHikeUI();
  renderLocCard();
}
function courseLatLon(c) {
  const m = (c.gps || "").match(/([\d.]+)[^\d]*N[^\d]*([\d.]+)/i) || (c.gps || "").match(/([\d.]+)[^\d]+([\d.]+)/);
  return m ? [Number.parseFloat(m[1]), Number.parseFloat(m[2])] : [37.6, 127];
}
function haversineKm(a, b, c, d) {
  const R = 6371, r = Math.PI / 180;
  const dp = (c - a) * r, dl = (d - b) * r;
  const x = Math.sin(dp / 2) ** 2 + Math.cos(a * r) * Math.cos(c * r) * Math.sin(dl / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}
function buildMap() {
  const c = Hike.course;
  const [lat, lon] = courseLatLon(c);
  Hike.origin = [lat, lon]; Hike.gpsTrack = []; Hike.gpsKm = 0;
  $("mapHost").innerHTML = `<div id="hikeMap"></div>`;
  setTimeout(() => {
    const leaflet = globalThis.L;
    if (!leaflet) { $("mapHost").innerHTML = `<div class="map-fallback">🗺 지도를 불러오려면 네트워크 연결이 필요해요</div>`; return; }
    const map = leaflet.map("hikeMap", { attributionControl: false }).setView([lat, lon], 14);
    leaflet.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 18 }).addTo(map);
    leaflet.marker([lat, lon]).addTo(map).bindPopup(`${c.startLabel} · 들머리`);
    Hike.map = map;
    Hike.trackLine = leaflet.polyline([], { color: "#2D6A4F", weight: 6, opacity: 0.85 }).addTo(map);
    Hike.posMarker = leaflet.circleMarker([lat, lon], { radius: 9, color: "#fff", weight: 3, fillColor: "#1B4332", fillOpacity: 1 }).addTo(map).bindPopup("내 위치");
    setTimeout(() => map.invalidateSize(), 160);
    // 코스 산의 실제 등산로 선 표시(이름→카탈로그 코드 해석)
    if (API.mode === "cloud") {
      API.get(`/mountains?q=${encodeURIComponent(c.name.split(" ")[0])}&size=1`, false)
        .then((r) => { if (r.items?.[0]) drawTrails(map, r.items[0].list_no); })
        .catch(() => {});
    }
  }, 60);
  drawProgress();
}
function recordGpsTrack(lat, lon) {
  if (Hike.gpsTrack) {
    const last = Hike.gpsTrack[Hike.gpsTrack.length - 1];
    if (last) {
      const step = haversineKm(last[0], last[1], lat, lon);
      if (step < 5) Hike.gpsKm = (Hike.gpsKm || 0) + step;   // 이상치(>5km 점프) 무시
    }
    Hike.gpsTrack.push([lat, lon]);
    if (Hike.trackLine) Hike.trackLine.setLatLngs(Hike.gpsTrack);
    if (Hike.posMarker) Hike.posMarker.setLatLng([lat, lon]);
    if (Hike.map) Hike.map.panTo([lat, lon], { animate: true });
    if (Hike.course) Hike.prog = Math.min(1, (Hike.gpsKm || 0) / Hike.course.km);
  }
}
function onGps(lat, lon, acc) {
  Hike.gps = { lat: +lat.toFixed(6), lon: +lon.toFixed(6), acc: Math.round(acc || 0) };
  recordGpsTrack(lat, lon);
  checkHazards();
  renderHikeUI();
  if (Hike.prog >= 1 && Hike.active) endHike(false);
}
function demoStep() {
  // 테스트용 — 실제 걷지 않고 약 90m씩 북동진(GPS 이동 시뮬레이션)
  const [lat, lon] = Hike.origin || [37.6, 127];
  const n = Hike.gpsTrack ? Hike.gpsTrack.length : 0;
  onGps(lat + n * 0.0006, lon + n * 0.0007, 6);
}
function checkHazards() {
  const c = Hike.course; if (!c) return;
  c.hazards.forEach((hz, i) => {
    if (!Hike.alerted[i] && Hike.prog > hz.at - 0.08 && Hike.prog < hz.at + 0.02) {
      Hike.alerted[i] = true;
      $("trailAlert").style.display = "flex";
      $("alertTitle").textContent = `${hz.type} 구간 접근`;
      $("alertBody").textContent = `${hz.grade} — ${hz.note}`;
      toast(`⚠️ ${hz.type} 구간 접근`, hz.note, "⚠️", true, 4200);
      logEvent(`${hz.type} 구간 접근 알림 → 안내 수락`);
    }
    if (Hike.alerted[i] && Hike.prog > hz.at + 0.06) $("trailAlert").style.display = "none";
  });
}
function drawProgress() { /* 실지도(Leaflet)에서는 onGps가 마커·트랙을 갱신 */ }
function interp(arr, t) {
  const f = t * (arr.length - 1), i = Math.floor(f), r = f - i;
  return Math.round(arr[Math.min(i, arr.length - 1)] * (1 - r) + arr[Math.min(i + 1, arr.length - 1)] * r);
}
function setTrailLiveState() {
  const live = $("trailLive"), tx = $("trailLiveTx");
  if (Hike.active) { live.className = "live"; tx.textContent = "안전 산행 중"; }
  else if (Hike.ended) { live.className = "live"; tx.textContent = "산행 완료 🎉"; }
  else { live.className = "live idle"; tx.textContent = Hike.prog > 0 ? "일시정지" : "들머리로 이동"; }
}
function renderWayCard(c) {
  // 시작 전: 들머리까지 '가는 길' 안내 카드(도착 후 산행 시작). 산행 중이면 숨김.
  const way = $("wayCard");
  if (!way) return;
  if (Hike.active || Hike.ended || Hike.prog !== 0) { way.style.display = "none"; return; }
  const [la, lo] = courseLatLon(c);
  way.style.display = "block";
  way.innerHTML = `<b>🧭 들머리까지 가는 길</b>
    <p>${c.startLabel} · ${c.route.split("→")[0].trim()}</p>
    ${dirButtons(la, lo, c.name.split(" ")[0] + " " + c.startLabel)}
    <div class="way-tip">들머리에 도착하면 아래 <b>산행 시작</b>을 눌러 GPS 추적을 켜세요.</div>`;
}
function hikeButtonLabel() {
  if (Hike.active) return "⏸ 일시정지";
  if (Hike.prog > 0 && !Hike.ended) return "▶ 이어가기";
  return "▶ 산행 시작";
}
function renderHikeControls() {
  $("btnHike").textContent = hikeButtonLabel();
  $("btnHike").disabled = Hike.ended;
  $("btnEnd").disabled = !(Hike.active || Hike.prog > 0) || Hike.ended;
}
function renderHikeStats(c) {
  const dist = (c.km * Hike.prog);
  $("stDist").innerHTML = `${dist.toFixed(1)}<small>km</small>`;
  $("stDistCap").textContent = `이동 / ${c.km}km`;
  $("stAlt").innerHTML = `${interp(c.elev, Hike.prog)}<small>m</small>`;
  $("stHr").innerHTML = Hike.active || Hike.prog > 0 ? `${Hike.hr}<small>bpm</small>` : "—";
}
function watchIsFresh() {
  return !!(Hike.watch?.connected && Hike.watch.seenAtMs && Date.now() - Hike.watch.seenAtMs < WATCH_FRESH_MS);
}
function renderWatchStatus() {
  const card = $("watchCard"), title = $("watchTitle"), text = $("watchText"), btn = $("btnWatchPair"), code = $("watchCode");
  if (!card || !title || !text || !btn || !code) return;
  const fresh = watchIsFresh();
  card.classList.toggle("on", fresh);
  btn.disabled = API.mode !== "cloud";
  btn.textContent = fresh ? "재연결" : "연결";
  code.style.display = Hike.watchPair ? "block" : "none";
  code.textContent = Hike.watchPair ? Hike.watchPair.code : "";
  if (fresh) {
    title.textContent = "⌚ Galaxy Watch 연결됨";
    const battery = Hike.watch.battery == null ? "배터리 —" : `배터리 ${Hike.watch.battery}%`;
    text.textContent = `${Hike.watch.hr || "—"}bpm · ${battery} · ${Hike.watch.age_sec || 0}초 전`;
  } else if (Hike.watchPair) {
    title.textContent = "⌚ 워치 연결 준비";
    text.textContent = "워치 앱 대기 · 필요 시 백업 코드 사용";
  } else if (API.mode !== "cloud") {
    title.textContent = "⌚ Galaxy Watch";
    text.textContent = "서버 연결 필요";
  } else if (!Hike.active) {
    title.textContent = "⌚ Galaxy Watch";
    text.textContent = "워치 착용 대기 · 시작하면 기록 연결";
  } else if (API.hikeId) {
    title.textContent = "⌚ Galaxy Watch";
    text.textContent = "워치앱 연결 대기";
  } else {
    title.textContent = "⌚ Galaxy Watch";
    text.textContent = "서버 산행 준비 중";
  }
}
async function ensureCloudHike() {
  if (API.mode !== "cloud" || !Hike.course) return false;
  if (API.hikeId) return true;
  if (API.hikeStartPromise) {
    try { await API.hikeStartPromise; } catch {}
    return !!API.hikeId;
  }
  API.hikeStartPromise = API.post("/hikes", { course_id: Hike.course.id })
    .then((r) => { API.hikeId = r.hike_id; return r; })
    .finally(() => { API.hikeStartPromise = null; });
  try { await API.hikeStartPromise; } catch {}
  return !!API.hikeId;
}
async function startWatchPairing() {
  if (API.mode !== "cloud") return toast("서버 연결 필요", "백엔드 연결 시 워치앱을 연결할 수 있어요", "⌚");
  if (Hike.active && !(await ensureCloudHike())) return toast("연결 실패", "서버 산행을 준비하지 못했어요", "⌚", true);
  try {
    const payload = API.hikeId ? { hike_id: API.hikeId } : {};
    const r = await API.post("/watch/pair/start", payload);
    Hike.watchPair = { code: r.code, expiresAt: Date.now() + r.expires_in * 1000 };
    renderWatchStatus();
    toast("워치 연결 준비", `필요 시 백업 코드 ${r.code}`, "⌚", false, 5200);
    startWatchPolling();
  } catch {
    toast("워치 연결 실패", "잠시 후 다시 시도해주세요", "⌚", true);
  }
}
async function fetchWatchLatest() {
  if (API.mode !== "cloud") return;
  try {
    const path = API.hikeId ? `/watch/latest?hike_id=${API.hikeId}` : "/watch/latest";
    const w = await API.get(path);
    Hike.watch = { ...w, seenAtMs: Date.now() };
    if (w.connected) {
      Hike.watchPair = null;
      if (w.hr) Hike.hr = w.hr;
      if (w.lat != null && w.lon != null) onGps(w.lat, w.lon, w.acc || 0);
      else renderHikeUI();
    } else {
      renderWatchStatus();
    }
  } catch { /* 오프라인 폴백 유지 */ }
}
function startWatchPolling() {
  clearInterval(Hike.watchPoll);
  Hike.watchPoll = setInterval(fetchWatchLatest, 5000);
  fetchWatchLatest();
}
function stopWatchPolling(reset = false) {
  clearInterval(Hike.watchPoll);
  Hike.watchPoll = null;
  if (reset) {
    Hike.watch = null;
    Hike.watchPair = null;
  }
}
function renderGpsStatus() {
  const gt = $("gpsTag");
  if (!gt) return;
  if (!Hike.active) { gt.style.display = "none"; return; }
  gt.style.display = "block";
  gt.className = Hike.gps ? "gps-tag on" : "gps-tag";
  gt.innerHTML = Hike.gps
    ? `📍 실시간 GPS 동기화 중 · 위도 ${Hike.gps.lat}, 경도 ${Hike.gps.lon} (정확도 ±${Hike.gps.acc}m)`
    : `📍 GPS 위치 확인 중… (권한 허용 시 실시간 동기화)`;
}
function renderGuardStatus() {
  // SOS 가드 상태
  $("guardMon").innerHTML = Hike.active
    ? `<span class="dot-ok"></span>자동 조난감지 작동 중`
    : `<span class="dot-off"></span>자동 조난감지 대기`;
  $("guardMonTx").textContent = Hike.active
    ? "이동 멈춤 30분 + 심박 이상 시 보호자·119에 자동 전파"
    : "산행을 시작하면 이동·심박 이상을 감시해요";
}
function renderHikeUI() {
  const c = Hike.course;
  if (!c) return;
  setTrailLiveState();
  renderWayCard(c);
  renderHikeControls();
  renderHikeStats(c);
  renderGpsStatus();
  renderGuardStatus();
  renderWatchStatus();
}
function startHike() {
  if (!Hike.course) { toast("코스를 먼저 선택하세요", "홈의 AI 추천에서 코스를 골라주세요", "🧭"); return; }
  Hike.active = true; Hike.ended = false;
  clearInterval(Hike.timer);
  Hike.timer = setInterval(tick, 1000);
  if (API.mode === "cloud" && !API.hikeId) ensureCloudHike().then(renderWatchStatus);
  startWatchPolling();
  logEvent(`입산 체크인 — ${Hike.course.name} (예상 하산 ${fmtMin(Hike.course.minutes)} 후)`);
  if (S.settings.family) logEvent("가족 안심 공유 시작 (어머니, 동생)");
  // 실제 GPS 위치 동기화 — watchPosition으로 실 위치대로 이동·기록(자동 진행 아님)
  if (navigator.geolocation) {
    Hike.geoId = navigator.geolocation.watchPosition(
      (pos) => onGps(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy),
      () => { Hike.gps = null; renderHikeUI(); },
      { enableHighAccuracy: true, maximumAge: 3000, timeout: 15000 });
  }
  toast("산행을 시작합니다", "실제 GPS 위치대로 경로가 기록돼요. 안전 감시 ON", "🥾");
  renderHikeUI();
}
function stopGeo() { if (Hike.geoId != null && navigator.geolocation) { navigator.geolocation.clearWatch(Hike.geoId); Hike.geoId = null; } }
function pauseHike() { Hike.active = false; clearInterval(Hike.timer); stopGeo(); stopWatchPolling(true); renderHikeUI(); }
function endHike(byUser = true) {
  clearInterval(Hike.timer);
  const c = Hike.course;
  const doneKm = +(c.km * Hike.prog).toFixed(1);
  Hike.active = false; Hike.ended = true; stopGeo(); stopWatchPolling(true);
  S.june.cnt += 1; S.june.km = +(S.june.km + doneKm).toFixed(1);
  S.june.kcal += Math.round(doneKm * 260);
  S.june.co2 = +(S.june.co2 + doneKm * 0.38).toFixed(1);
  S.hikesDone += 1; save();
  if (API.mode === "cloud" && API.hikeId) {
    API.post(`/hikes/${API.hikeId}/end`).then(() => renderMy()).catch(() => {});
    API.hikeId = null;
  }
  logEvent(`산행 종료 — ${doneKm}km 기록 저장 (마이 리포트 반영)`);
  toast(byUser ? "산행 종료" : "등정 완료! 🎉", `${doneKm}km · ${Math.round(doneKm * 260)}kcal — 기록이 저장됐어요`, "🏅");
  renderMy(); renderHikeUI();
}
let tickCount = 0;
function tick() {
  // 진행도는 GPS(onGps)가 갱신 — tick은 심박·서버 트랙 전송만(자동 전진 없음).
  if (!watchIsFresh()) Hike.hr = Math.round(92 + 18 * Math.sin(Hike.prog * 6) + Math.random() * 6);
  const c = Hike.course;
  if (API.mode === "cloud" && API.hikeId && ++tickCount % 5 === 0) {
    const t = { progress: +Hike.prog.toFixed(4), alt: interp(c.elev, Hike.prog), hr: Hike.hr };
    if (watchIsFresh() && Hike.watch.lat != null && Hike.watch.lon != null) {
      t.lat = Hike.watch.lat; t.lon = Hike.watch.lon;
    } else if (Hike.gps) {
      t.lat = Hike.gps.lat; t.lon = Hike.gps.lon;
    }
    API.post(`/hikes/${API.hikeId}/track`, t).catch(() => {});
  }
  renderHikeUI();
}
/* 일몰 카운트다운 — 지역 일몰시각 기준 실시간 */
function sunsetTick() {
  let secs;
  if (DEMO === null) {
    const r = FM_DATA.regions[S.region];
    const [h, m] = r.sunsetAt.split(":").map(Number);
    const t = new Date(); const sun = new Date(); sun.setHours(h, m, 0, 0);
    secs = Math.floor((sun - t) / 1000);
  } else {
    if (Hike.sunsetLeft == null) Hike.sunsetLeft = 3 * 3600 + 12 * 60;
    secs = Hike.sunsetLeft = Math.max(0, Hike.sunsetLeft - 1);
  }
  $("sunset").textContent = secs > 0 ? `${Math.floor(secs / 3600)}:${String(Math.floor(secs % 3600 / 60)).padStart(2, "0")}` : "일몰";
}
setInterval(sunsetTick, 1000);

$("btnHike").addEventListener("click", () => (Hike.active ? pauseHike() : startHike()));
$("btnEnd").addEventListener("click", () => endHike(true));
if ($("btnWatchPair")) $("btnWatchPair").addEventListener("click", startWatchPairing);
$("btnDemo").addEventListener("click", () => {
  if (!Hike.active) return toast("먼저 산행을 시작하세요", "데모 이동은 산행 중에만 동작해요", "🧪");
  demoStep();
});

/* 토글 스위치 */
qsa(".sw").forEach((sw) => {
  const key = sw.dataset.set;
  sw.classList.toggle("off", !S.settings[key]);
  sw.addEventListener("click", () => {
    S.settings[key] = !S.settings[key]; save();
    sw.classList.toggle("off", !S.settings[key]);
    renderGuards();
    toast(key === "family" ? "가족 위치 공유" : "코스 이탈 알림", S.settings[key] ? "켜짐 — 변경사항이 저장됐어요" : "꺼짐", "⚙️");
  });
});
function renderGuards() {
  $("guardFam").innerHTML = S.settings.family
    ? `<span class="dot-ok"></span>가족 안심 공유 ON` : `<span class="dot-off"></span>가족 안심 공유 OFF`;
  $("guardFamTx").textContent = S.settings.family ? "어머니 · 동생에게 실시간 위치 전송 중" : "마이 > 보호자 설정에서 다시 켤 수 있어요";
}

/* ---------------- 안전 이벤트 로그 ---------------- */
const seedEvents = [
  ["14:02", "낙석 주의 구간 접근 알림 → 우회로 안내 수락"],
  ["13:30", "가족 안심 공유 시작 (어머니, 동생)"],
  ["12:47", "입산 체크인 — 예상 하산 17:30 등록"],
];
let events = [...seedEvents];
function logEvent(text) { events.unshift([nowHM(), text]); events = events.slice(0, 6); renderEvents(); }
function renderEvents() {
  $("eventLog").innerHTML = events.map(([t, x]) => `<div class="tl"><time>${t}</time><div>${x}</div></div>`).join("");
}
function renderLocCard() {
  const c = Hike.course;
  const rows = [
    ["국가지점번호", c ? c.gridNo : "다사 5683 2741"],
    ["GPS 좌표", (c ? c.gps : "37.6584°N, 126.9778°E") + " (±4m)"],
    ["가까운 구조 거점", c ? c.rescuePoint : "백운산장 헬기장 620m"],
    ["관할 구조대", c ? c.fireStation : "서울 종로소방서 산악구조대"],
  ];
  $("locCard").innerHTML = rows.map(([k, v]) => `<div class="row"><span>${k}</span><b>${v}</b></div>`).join("");
}

/* ---------------- SOS ---------------- */
let sosHold = null, sosT0 = 0;
const RING = 559;
function sosDown(e) {
  e.preventDefault();
  $("sosBtn").style.transform = "scale(.94)";
  sosT0 = Date.now();
  sosHold = setInterval(() => {
    const f = Math.min(1, (Date.now() - sosT0) / 3000);
    $("sosRing").style.strokeDashoffset = RING * (1 - f);
    if (f >= 1) { sosUp(); openSosModal(); }
  }, 40);
}
function sosUp() {
  $("sosBtn").style.transform = "";
  clearInterval(sosHold); sosHold = null;
  $("sosRing").style.strokeDashoffset = RING;
}
["mousedown", "touchstart"].forEach((ev) => $("sosBtn").addEventListener(ev, sosDown, { passive: false }));
["mouseup", "mouseleave", "touchend"].forEach((ev) => $("sosBtn").addEventListener(ev, sosUp));

function openSosModal() {
  const c = Hike.course;
  $("sosSheet").innerHTML = `
    <div class="grab"></div>
    <h3>🚨 119에 구조를 요청할까요?</h3>
    <p class="sub">아래 정보가 119 상황실과 보호자에게 즉시 전송됩니다. 실수로 눌렀다면 취소하세요. (데모 — 실제 신고는 전송되지 않습니다)</p>
    <div class="sos-pay">
      <div class="row"><span>국가지점번호</span><b>${c ? c.gridNo : "다사 5683 2741"}</b></div>
      <div class="row"><span>GPS</span><b>${c ? c.gps : "37.6584°N, 126.9778°E"}</b></div>
      <div class="row"><span>신고자</span><b>${esc(S.profile.name)}님 · 심박 ${Hike.hr || 96}bpm</b></div>
      <div class="row"><span>가까운 구조 거점</span><b>${c ? c.rescuePoint : "백운산장 헬기장 620m"}</b></div>
    </div>
    <div class="btnrow">
      <button class="btn ghost" data-close="sosModal">취소</button>
      <button class="btn danger" id="sosSend">🚨 신고 전송</button>
    </div>`;
  $("sosModal").classList.add("show");
  $("sosSend").addEventListener("click", sosDispatch);
}
async function sosDispatch() {
  logEvent("SOS 신고 전송 — 119 상황실 접수 (국가지점번호 포함)");
  if (S.settings.family) logEvent("보호자 알림 발송 (어머니, 동생)");
  let station = Hike.course ? Hike.course.fireStation : "종로소방서 산악구조대";
  let eta = 9;
  if (API.mode === "cloud") {
    try {
      const r = await API.post("/sos", { hike_id: API.hikeId, note: "앱 SOS 버튼" });
      station = r.station || station;
      eta = r.eta_min || eta;
      logEvent("관제센터 실시간 공유 — 사건번호 " + r.sos_id.slice(0, 8));
    } catch { /* 오프라인: SMS 폴백 시나리오 */ }
  }
  const steps = [
    ["119 상황실 접수", "위치·심박·코스 정보 자동 전달 완료"],
    ["구조대 배정", station + " 출동"],
    ["구조대 이동 중", `ETA ${eta}분 — 관제센터가 실시간 추적 중`],
  ];
  const sheet = $("sosSheet");
  const grab = document.createElement("div");
  grab.className = "grab";
  const title = document.createElement("h3");
  title.textContent = "🚨 신고가 접수됐어요";
  const summary = document.createElement("p");
  summary.className = "sub";
  summary.textContent = "제자리에서 체온을 유지하세요. 휘슬·불빛으로 위치를 알리면 도움이 됩니다.";
  const stepList = document.createElement("div");
  stepList.className = "steps";
  steps.forEach(([b, s], i) => {
    const step = document.createElement("div");
    step.className = "stp";
    step.id = `stp${i}`;
    const icon = document.createElement("div");
    icon.className = "si";
    icon.textContent = String(i + 1);
    const copy = document.createElement("div");
    const strong = document.createElement("b");
    strong.textContent = b;
    const span = document.createElement("span");
    span.textContent = s;
    copy.append(strong, span);
    step.append(icon, copy);
    stepList.appendChild(step);
  });
  const row = document.createElement("div");
  row.className = "btnrow";
  const done = document.createElement("button");
  done.className = "btn ghost";
  done.id = "sosDone";
  done.textContent = "상황 종료(데모)";
  row.appendChild(done);
  sheet.replaceChildren(grab, title, summary, stepList, row);
  steps.forEach((_, i) => setTimeout(() => { const el = $("stp" + i); if (el) el.classList.add("done"); }, 400 + i * 1600));
  $("sosDone").addEventListener("click", () => {
    $("sosModal").classList.remove("show");
    logEvent("조난 상황 해제 — 모니터링 종료");
    toast("상황 종료", "안전 이벤트에 기록됐어요", "✅");
  });
}

/* ---------------- AI 챗 ---------------- */
const chatLog = () => $("chatLog");
function bubble(html, who = "bot") {
  const div = document.createElement("div");
  div.className = "msg " + who;
  if (who === "bot") {
    const label = document.createElement("div");
    label.className = "who";
    label.textContent = "🌲 숲이";
    div.appendChild(label);
    appendRichText(div, html);
  } else {
    div.textContent = String(html);
  }
  chatLog().appendChild(div);
  $("ai").scrollTop = $("ai").scrollHeight;
  return div;
}
const CHAT_HTML_TAGS = new Set(["b", "br", "div", "i", "p"]);
const CHAT_HTML_CLASSES = new Set(["danger-flag", "safe2", "conf"]);
function appendRichText(target, html) {
  const source = String(html ?? "");
  const tagPattern = /<(\/?)(b|br|div|i|p)\b([^>]*)>/gi;
  const stack = [{ tag: "", el: target }];
  let offset = 0;
  const current = () => stack[stack.length - 1].el;
  const appendText = (text) => {
    if (text) current().appendChild(document.createTextNode(text));
  };

  for (const match of source.matchAll(tagPattern)) {
    appendText(source.slice(offset, match.index));
    offset = match.index + match[0].length;
    const tag = match[2].toLowerCase();
    if (!CHAT_HTML_TAGS.has(tag)) {
      appendText(match[0]);
      continue;
    }
    if (match[1]) {
      for (let i = stack.length - 1; i > 0; i--) {
        if (stack[i].tag === tag) {
          stack.length = i;
          break;
        }
      }
      continue;
    }
    if (tag === "br") {
      current().appendChild(document.createElement("br"));
      continue;
    }

    const el = document.createElement(tag);
    const attrs = match[3] || "";
    if (tag === "div") {
      const classMatch = /\bclass\s*=\s*["']([^"']*)["']/i.exec(attrs);
      const classes = (classMatch ? classMatch[1].split(/\s+/) : [])
        .filter((name) => CHAT_HTML_CLASSES.has(name));
      if (classes.length) el.className = classes.join(" ");
    }
    if (tag === "i") {
      const widthMatch = /\bwidth\s*:\s*(\d{1,3})%/i.exec(attrs);
      if (widthMatch) el.style.width = `${clampNumber(widthMatch[1], 0, 100, 0)}%`;
    }
    current().appendChild(el);
    stack.push({ tag, el });
  }
  appendText(source.slice(offset));
}
function photoBubble(sp) {
  const div = document.createElement("div");
  div.className = "photo";
  const image = document.createElement("div");
  image.className = "ph-img";
  image.style.background = sp.grad;
  const caption = document.createElement("div");
  caption.className = "ph-cap";
  caption.textContent = "📷 방금 촬영한 사진";
  div.append(image, caption);
  chatLog().appendChild(div);
}
function speciesReply(sp) {
  const cls = sp.toxic ? "" : "safe2";
  const head = sp.toxic ? "절대 채취하거나 드시면 안 돼요!" : "좋은 발견이에요! 👀";
  bubble(`${head}
    <div class="danger-flag ${cls}">
      <b>${sp.toxic ? "🚫" : "🌿"} ${sp.name} (${sp.toxic ? "유독성" : "안전"})</b>
      <p>${sp.desc}</p>
      <div class="conf"><i style="width:${sp.conf}%"></i></div>
      <p style="margin-top:5px">AI 판별 신뢰도 ${sp.conf}% · 국가생물종지식정보(국립수목원) 대조</p>
      <p style="margin-top:4px"><b style="font-size:11px">${sp.toxic ? "⚠️" : "✅"} ${sp.action}</b></p>
    </div>`);
}
let typingEl = null;
function typing(on) {
  if (on && !typingEl) {
    typingEl = document.createElement("div");
    typingEl.className = "typing";
    typingEl.innerHTML = "<i></i><i></i><i></i>";
    chatLog().appendChild(typingEl);
    $("ai").scrollTop = $("ai").scrollHeight;
  } else if (!on && typingEl) { typingEl.remove(); typingEl = null; }
}
function aiReply(text) {
  typing(true);
  setTimeout(() => { typing(false); bubble(text); S.aiCount++; save(); renderBadges(); }, 750 + Math.random() * 500);
}
function summitAnswer() {
  const c = Hike.course;
  if (!c) return "아직 산행 중인 코스가 없어요. 홈에서 추천 코스를 고르면 남은 거리·시간을 실시간으로 알려드릴게요! 🥾";
  const leftKm = (c.km * (1 - Hike.prog)).toFixed(1);
  const leftMin = Math.round(c.minutes * (1 - Hike.prog) * 0.9);
  const r = FM_DATA.regions[c.region];
  if (S.lang !== "ko" && FM_DATA.i18n[S.lang]) return FM_DATA.i18n[S.lang].summit(leftKm, leftMin);
  return `남은 거리 <b>${leftKm}km</b>, ${S.profile.name}님 페이스라면 약 <b>${leftMin}분</b> 뒤 도착해요. 일몰(${r.sunsetAt})까지 여유가 충분하지만, 정상 부근 바람이 초속 ${(r.weather.wind * 2.1).toFixed(0)}m로 강하니 겉옷을 준비하세요. 🧥`;
}
function intent(text) {
  const t = text.toLowerCase();
  if (/(남았|얼마나|거리|도착|언제|시간)/.test(t)) return summitAnswer();
  if (/(날씨|기상|바람|비|온도|기온)/.test(t)) {
    const r = FM_DATA.regions[S.region];
    return `${r.name} 산악기상 실측(${r.weather.station}): <b>${r.weather.temp}°C, ${r.weather.label}</b>, 풍속 ${r.weather.wind}m/s, 강수확률 ${r.weather.rainProb}%. 고지대는 도심보다 5~8℃ 낮으니 보온을 챙기세요!`;
  }
  if (/(위험|낙석|산사태|사고|조심)/.test(t)) {
    const c = Hike.course || FM_DATA.courses[0];
    return `${c.name} 위험 구간 ${c.hazards.length}곳: ` + c.hazards.map((h) => `<b>${h.type}</b>(${h.grade})`).join(", ") + ". 산사태위험지도·소방청 사고이력 기준이에요. 접근 300m 전에 미리 알려드릴게요. ⚠️";
  }
  if (/(휴양림|치유|예약|숲세권)/.test(t)) return "이번 주 토 10시, 축령산 치유의숲 ‘숲 명상’ 프로그램에 잔여석이 있어요. 숲나들e에서 바로 예약을 도와드릴까요? (마이 탭 → 안심 서비스) 🏕";
  if (/(보험)/.test(t)) return "내일 산행 1일 안심보험은 산행지수 연동으로 990원부터예요. 상해·구조비용이 보장돼요. 마이 탭에서 바로 가입할 수 있어요! 🎫";
  if (/(고마|감사|땡큐|thank)/.test(t)) return "별말씀을요! 안전한 산행이 제 보람이에요. 🌲";
  if (/(안녕|hello|hi|你好|こんにちは)/.test(t)) return S.lang !== "ko" && FM_DATA.i18n[S.lang] ? FM_DATA.i18n[S.lang].hello : `안녕하세요 ${S.profile.name}님! 코스·날씨·위험 정보, 식물 사진까지 무엇이든 물어보세요. 🌿`;
  return "산림 공공데이터를 근거로 답하는 RAG 데모예요. <b>코스 안내·산악 날씨·위험 구간·식물 사진 식별</b>을 물어보시면 정확히 도와드릴 수 있어요!";
}
function sendChat(text) {
  if (!text.trim()) return;
  bubble(text, "user");
  $("chatInput").value = "";
  if (API.mode === "cloud") {
    typing(true);
    API.post("/chat", {
      message: text, lang: S.lang, region_id: S.region,
      course_id: Hike.course ? Hike.course.id : null, progress: +(Hike.prog || 0).toFixed(3),
    }, false)
      .then((r) => { typing(false); bubble(r.reply); S.aiCount++; save(); renderBadges(); })
      .catch(() => { typing(false); bubble(intent(text)); S.aiCount++; save(); renderBadges(); });
    return;
  }
  aiReply(intent(text));
}
$("btnSend").addEventListener("click", () => {
  const v = $("chatInput").value;
  if (v.trim()) sendChat(v);
  else toast("음성 인식(데모)", "실제 앱에서는 STT로 손 안 대고 질문해요", "🎤");
});
$("chatInput").addEventListener("keydown", (e) => { if (e.key === "Enter") sendChat($("chatInput").value); });
qsa(".qc[data-q]").forEach((b) => b.addEventListener("click", () => {
  if (b.dataset.q === "photo") return openPhoto();
  sendChat(b.dataset.q);
}));
qsa(".qc.lang").forEach((b) => b.addEventListener("click", () => {
  const on = b.classList.contains("on");
  qsa(".qc.lang").forEach((x) => x.classList.remove("on"));
  S.lang = on ? "ko" : b.dataset.lang;
  if (!on) b.classList.add("on");
  save();
  aiReply(S.lang === "ko" ? "다시 한국어로 안내할게요!" : FM_DATA.i18n[S.lang].hello);
}));
function openPhoto() {
  $("photoGrid").innerHTML = FM_DATA.species.map((sp) => `
    <button class="pg" data-sp="${sp.id}"><div class="pgi" style="background:${sp.grad}"></div><span>${sp.emoji} ${sp.label}</span></button>`).join("");
  $("photoModal").classList.add("show");
  qsa("#photoGrid .pg").forEach((b) => b.addEventListener("click", async () => {
    $("photoModal").classList.remove("show");
    const sp = FM_DATA.species.find((x) => x.id === b.dataset.sp);
    photoBubble(sp);
    bubble(sp.id === "mushroom" ? "길에서 봤는데, 이 버섯 먹어도 돼?" : "이거 뭐야?", "user");
    typing(true);
    let res = null;
    if (API.mode === "cloud") {
      try {
        const d = await API.post("/species/identify", { sample_id: sp.id }, false);
        res = { ...sp, name: d.name, toxic: d.toxic, conf: d.confidence, desc: d.desc, action: d.action };
      } catch { /* 오프라인 → 온디바이스 모델 */ }
    }
    if (!res) res = await ForestAPI.identifySpecies(sp.id);
    typing(false);
    speciesReply(res);
    S.aiCount++; save(); renderBadges();
  }));
}
$("btnPhoto").addEventListener("click", openPhoto);

/* 초기 대화 시드 (시연 흐름) */
function seedChat() {
  const sp = FM_DATA.species[0];
  photoBubble(sp);
  bubble("길에서 봤는데, 이 버섯 먹어도 돼?", "user");
  speciesReply(sp);
  bubble("백운대 정상까지 얼마나 남았어?", "user");
  bubble(`남은 거리 <b>1.8km</b>, ${S.profile.name || "산친구"}님 페이스라면 약 <b>55분</b> 뒤 도착해요. 일몰(19:52)까지 여유가 충분하지만, 정상 부근 바람이 초속 9m로 강하니 겉옷을 준비하세요. 🧥`);
}

/* ---------------- 마이 ---------------- */
function last6Months() {
  const out = [], d = new Date();
  for (let i = 5; i >= 0; i--) {
    const m = new Date(d.getFullYear(), d.getMonth() - i, 1);
    out.push({ key: `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, "0")}`, label: `${m.getMonth() + 1}월` });
  }
  return out;
}
function installedDays() {
  return Math.floor((Date.now() - (S.installAt || Date.now())) / 86400000) + 1;
}
function monthBars(months, monthly) {
  const byKey = Object.fromEntries(monthly.map((m) => [m.month, m.km]));
  const maxKm = Math.max(1, ...monthly.map((m) => m.km));
  return months.map((m) => ({ h: Math.round(((byKey[m.key] || 0) / maxKm) * 100), label: m.label }));
}
async function loadCloudSummary(months) {
  if (API.mode !== "cloud") return null;
  if (!API.authToken && !API.token && !S.profile.set) return null;
  try {
    const s = await API.get("/hikes/summary");
    lastSum = s;   // 배지·지역 다양성 캐시
    return {
      days: s.active_days, cnt: s.total_hikes, km: s.total_km,
      kcal: s.total_kcal, co2: s.co2_kg, level: s.level,
      bars: monthBars(months, s.monthly),
    };
  } catch { return null; }
}
function emptyCloudSummary(months) {
  return {
    days: installedDays(), cnt: 0, km: 0, kcal: 0, co2: 0, level: 1,
    bars: months.map((m) => ({ h: 0, label: m.label })),
  };
}
function localMySummary(months) {
  const curH = Math.min(100, (S.june.km || 0) * 6);
  return {
    days: installedDays(), cnt: S.june.cnt, km: S.june.km,
    kcal: S.june.kcal, co2: S.june.co2,
    level: 1 + Math.floor((S.hikesDone || 0) / 3),
    bars: months.map((m, i) => ({ h: i === months.length - 1 ? curH : 0, label: m.label })),
  };
}
function fallbackMySummary(months) {
  // 클라우드인데 일시적 실패 — 서버와 일관되게 0으로(로컬 S.june로 가짜 산행 표시 안 함)
  return API.mode === "cloud" ? emptyCloudSummary(months) : localMySummary(months);
}
function renderProfileSummary(summary) {
  $("profDays").textContent = `숲길과 함께한 지 ${summary.days}일째`;
  $("profLv").textContent = `Lv.${summary.level} 숲지기`;
  $("repCnt").textContent = `${summary.cnt}회`;
  $("repKm").textContent = `${summary.km}km`;
  $("repKcal").textContent = (summary.kcal || 0).toLocaleString();
  $("repCo2").textContent = `${summary.co2}kg`;
  $("repBars").innerHTML = summary.bars.map((b, i) => `
    <div class="b ${i === summary.bars.length - 1 ? "cur" : ""}"><i style="height:${b.h}%"></i><span>${b.label}</span></div>`).join("");
}
function renderProfileExtra() {
  const box = $("repExtra");
  if (!box) return;
  const hasBadges = lastSum?.badges;
  const aiEarned = S.aiCount >= 10 ? 1 : 0;
  const earned = hasBadges ? lastSum.badges.filter((b) => b.earned).length + aiEarned : aiEarned;
  const total = hasBadges ? lastSum.badges.length + 1 : 4;
  const regions = lastSum ? lastSum.regions : 0;
  const courses = lastSum ? lastSum.distinct_courses : 0;
  box.innerHTML =
    `<span>🧭 방문 지역 <b>${regions}</b>곳</span><span>⛰ 완등 코스 <b>${courses}</b></span><span>🏅 배지 <b>${earned}/${total}</b></span>`;
}
function renderAccountStatus() {
  const acct = S.account;
  if (!$("acctTitle")) return;
  if (acct) {
    $("acctTitle").textContent = `${acct.email || "소셜 계정"} 연결됨`;
    $("acctSub").textContent = `${(acct.providers || []).join(" · ") || "account"} · 기록 동기화 ON`;
    $("btnAccount").textContent = "관리";
  } else {
    $("acctTitle").textContent = "계정 없이 사용 중";
    $("acctSub").textContent = API.mode === "cloud" ? "가입하면 워치·웹·산행 기록이 연동돼요" : "서버 연결 시 계정을 만들 수 있어요";
    $("btnAccount").textContent = "가입/로그인";
  }
}
async function loadHikeLogItems() {
  if (API.mode !== "cloud") return [];
  if (!API.authToken && !API.token && !S.profile.set) return [];
  try { return (await API.get("/hikes")).items || []; }
  catch { return []; }
}
// cloud 모드면 서버 실집계(/hikes/summary), 아니면 로컬 누적치로 폴백.
async function renderMy() {
  $("profName").textContent = S.profile.name || "산친구";
  const months = last6Months();
  const summary = await loadCloudSummary(months) || fallbackMySummary(months);
  renderProfileSummary(summary);
  renderProfileExtra(); renderAccountStatus();
  const hikes = await loadHikeLogItems();
  renderHikeLog(hikes); renderCalendar(hikes);
  renderBadges(); renderFavs(); renderIns();
}
function renderHikeLog(items) {
  const box = $("hikeLog"); if (!box) return;
  if (!items.length) {
    box.innerHTML = `<div class="card" style="font-size:12px;color:var(--sub);text-align:center;padding:16px">아직 완료한 산행이 없어요. 산행을 완주하면 산별 거리·칼로리가 여기 쌓여요.</div>`;
    return;
  }
  box.innerHTML = items.map((h) => `
    <div class="log-row"><div><b>${esc(h.course)}</b><span>${h.date}</span></div>
      <div class="log-stat">${h.km}km · ${(h.kcal || 0).toLocaleString()}kcal</div></div>`).join("");
}
function renderCalendar(hikes) {
  const box = $("hikeCal"); if (!box) return;
  const now = new Date(), y = now.getFullYear(), mo = now.getMonth();
  const first = new Date(y, mo, 1).getDay(), dim = new Date(y, mo + 1, 0).getDate();
  const p2 = (n) => String(n).padStart(2, "0");
  const hk = new Set((hikes || []).map((h) => h.date));            // YYYY-MM-DD
  const pl = new Set((S.plans || []).map((p) => p.date));          // YYYYMMDD
  let cells = ["일", "월", "화", "수", "목", "금", "토"].map((w) => `<div class="cal-h">${w}</div>`).join("");
  for (let i = 0; i < first; i++) cells += "<div></div>";
  for (let d = 1; d <= dim; d++) {
    const isHk = hk.has(`${y}-${p2(mo + 1)}-${p2(d)}`), isPl = pl.has(`${y}${p2(mo + 1)}${p2(d)}`);
    const today = d === now.getDate();
    cells += `<div class="cal-d ${today ? "today" : ""}">${d}<div class="cal-dots">${isHk ? '<i class="dot-hk"></i>' : ""}${isPl ? '<i class="dot-pl"></i>' : ""}</div></div>`;
  }
  box.innerHTML = `<div class="cal-head">${y}년 ${mo + 1}월</div><div class="cal-grid">${cells}</div>
    <div class="cal-legend"><span><i class="dot-hk"></i> 다녀온 산</span><span><i class="dot-pl"></i> 예정 일정</span></div>`;
}
let lastSum = null;
const BADGE_DESC = {
  first: "산행을 1회 완주하면 획득해요.", five: "산행을 5회 완주하면 획득해요.",
  ten: "산행을 10회 완주하면 획득해요.", km50: "누적 이동거리 50km를 넘기면 획득해요.",
  km100: "누적 이동거리 100km를 넘기면 획득해요.", kcal: "누적 소모 5,000kcal를 넘기면 획득해요.",
  days30: "가입 후 30일 동안 활동하면 획득해요.", regions: "서로 다른 지역(시·도) 3곳을 산행하면 획득해요.",
  master: "추천 코스를 모두 완등하면 획득해요.", ai: "AI 숲이와 10회 대화하면 획득해요.",
};
let lastBadges = [];
function badgeUnit(id) {
  if (id.startsWith("km")) return "km";
  if (id === "days30") return "일";
  return "";
}
function renderBadges() {
  // 서버 실집계 배지(진척·달성) + 클라이언트 AI 대화 배지. 하드코딩 아님.
  let cards;
  if (lastSum?.badges?.length) {
    cards = lastSum.badges.map((b) => {
      const unit = badgeUnit(b.id);
      const prog = b.earned ? "달성!" : `${b.progress}/${b.goal}${unit}`;
      return { id: b.id, ic: b.icon, label: b.label, prog, ok: b.earned, goal: b.goal, cur: b.progress, unit };
    });
  } else {  // 오프라인 폴백 — 로컬 기록 기준
    const cnt = S.hikesDone || 0, km = S.june.km || 0;
    cards = [
      { id: "first", ic: "🥾", label: "첫 산행", prog: `${Math.min(cnt, 1)}/1`, ok: cnt >= 1, goal: 1, cur: cnt, unit: "" },
      { id: "five", ic: "🏔", label: "5회 등반", prog: `${Math.min(cnt, 5)}/5`, ok: cnt >= 5, goal: 5, cur: cnt, unit: "" },
      { id: "km50", ic: "📏", label: "누적 50km", prog: `${km.toFixed(0)}/50km`, ok: km >= 50, goal: 50, cur: km, unit: "km" },
    ];
  }
  cards.push({ id: "ai", ic: "💬", label: "숲이와 대화", prog: `${Math.min(S.aiCount, 10)}/10회`, ok: S.aiCount >= 10, goal: 10, cur: S.aiCount, unit: "회" });
  lastBadges = cards;
  $("badgeRow").innerHTML = cards.map((c) => `
    <div class="bd ${c.ok ? "" : "lock"}" data-bid="${c.id}"><div class="ic">${c.ic}</div><span>${c.label}<br>${c.prog}</span></div>`).join("");
  $("badgeRow").querySelectorAll(".bd").forEach((el) => el.addEventListener("click", () => openBadgeDetail(el.dataset.bid)));
}
function openBadgeDetail(bid) {
  const b = lastBadges.find((x) => x.id === bid); if (!b) return;
  const pct = Math.min(100, Math.round((b.cur / b.goal) * 100));
  openExt(`
    <div style="text-align:center;font-size:46px;margin:6px 0">${b.ic}</div>
    <h3 style="text-align:center">${b.label} ${b.ok ? "✅" : "🔒"}</h3>
    <p class="sub" style="text-align:center">${BADGE_DESC[bid] || "활동으로 획득하는 배지예요."}</p>
    <div class="bd-bar"><i style="width:${pct}%"></i></div>
    <p style="text-align:center;font-size:13px;font-weight:700;margin-top:8px">${b.ok ? "달성 완료! 🎉" : `진행 ${b.cur}/${b.goal}${b.unit} (${pct}%)`}</p>
    <div class="btnrow"><button class="btn ghost" data-close="extModal">닫기</button></div>`);
}
function insuranceFee(idx) {
  if (idx >= 80) return 990;
  if (idx >= 60) return 1290;
  return 1590;
}
function renderIns() {
  const idx = calcIndex(FM_DATA.regions[S.region]);
  const fee = insuranceFee(idx);
  if (S.insurance) {
    $("insTitle").textContent = "1일 안심보험 가입 완료 ✅";
    $("insSub").textContent = `${S.insurance.date} 산행 보장 — 상해·구조비용·휴대품`;
    $("insBtn").textContent = "보장 내역";
    $("insBtn").classList.add("joined");
  } else {
    $("insTitle").textContent = "내일 산행, 1일 안심보험";
    $("insSub").textContent = "상해·구조비용 보장 — 산행지수 연동 보험료";
    $("insBtn").textContent = `${fee.toLocaleString()}원 가입`;
    $("insBtn").classList.remove("joined");
  }
}
$("insBtn").addEventListener("click", () => {
  const idx = calcIndex(FM_DATA.regions[S.region]);
  const fee = insuranceFee(idx);
  const d = new Date(Date.now() + 86400000);
  const dateStr = `${d.getMonth() + 1}/${d.getDate()}`;
  $("insSheet").innerHTML = `
    <div class="grab"></div>
    <h3>🎫 1일 안심보험${S.insurance ? " — 보장 내역" : ""}</h3>
    <p class="sub">오늘 산행지수 ${idx}점 기준 보험료가 산정됐어요. 위험할수록 보험료가 오르는 ‘행동 연동형’ 마이크로 보험입니다. (제휴 보험사 — 데모)</p>
    <div class="sos-pay">
      <div class="row"><span>보장일</span><b>${S.insurance ? S.insurance.date : dateStr} (1일)</b></div>
      <div class="row"><span>상해 사망·후유장해</span><b>최대 5,000만 원</b></div>
      <div class="row"><span>구조·수색 비용</span><b>최대 300만 원</b></div>
      <div class="row"><span>휴대품 손해</span><b>최대 20만 원</b></div>
      <div class="row"><span>보험료</span><b>${fee.toLocaleString()}원 (산행지수 ${idx}점 연동)</b></div>
    </div>
    <div class="btnrow">
      <button class="btn ghost" data-close="insModal">닫기</button>
      ${S.insurance ? "" : `<button class="btn primary" id="insJoin">${fee.toLocaleString()}원 가입하기</button>`}
    </div>`;
  $("insModal").classList.add("show");
  const j = $("insJoin");
  if (j) j.addEventListener("click", () => {
    S.insurance = { date: dateStr, fee }; save();
    $("insModal").classList.remove("show");
    renderIns();
    toast("보험 가입 완료", `${dateStr} 산행이 보장돼요. 안전한 산행 되세요!`, "🎫");
  });
});

/* ---------------- 온보딩 ---------------- */
let ftueTimer = null;
function profileCheckActive(value) {
  if (value === "knee") return S.profile.knee;
  if (value === "heart") return S.profile.heart;
  return !S.profile.knee && !S.profile.heart;
}
function openOnboard() {
  if ($("obName")) $("obName").value = S.profile.set ? S.profile.name : "";
  qsa("#obFit button").forEach((b) => b.classList.toggle("on", +b.dataset.v === S.profile.fit));
  qsa("#obChecks .ckc").forEach((b) => {
    const v = b.dataset.v;
    b.classList.toggle("on", profileCheckActive(v));
  });
  const onboard = $("onboard");
  const splash = qs("#onboard .ftue-splash");
  const content = qs("#onboard .ftue-content");
  onboard.classList.remove("ready");
  if (splash) {
    splash.style.visibility = "visible";
    splash.style.opacity = "1";
    splash.style.removeProperty("transform");
    splash.style.removeProperty("top");
  }
  if (content) {
    content.style.opacity = "0";
    content.style.transform = "translateY(34px)";
  }
  onboard.classList.add("show");
  clearTimeout(ftueTimer);
  const reduced = Boolean(globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches);
  ftueTimer = setTimeout(() => {
    onboard.classList.add("ready");
    if (content) {
      content.style.opacity = "1";
      content.style.transform = "translateY(0)";
    }
    if (splash) {
      splash.style.opacity = "1";
      splash.style.visibility = "visible";
      splash.style.removeProperty("transform");
      splash.style.removeProperty("top");
    }
  }, reduced ? 0 : 1150);
}
function captureOnboardProfile() {
  S.profile.name = $("obName") ? ($("obName").value.trim() || "산친구") : (S.profile.name || "산친구");
  S.profile.fit = qs("#obFit button.on") ? +qs("#obFit button.on").dataset.v : (S.profile.fit || 2);
  S.profile.knee = qs('#obChecks .ckc[data-v="knee"]') ? qs('#obChecks .ckc[data-v="knee"]').classList.contains("on") : !!S.profile.knee;
  S.profile.heart = qs('#obChecks .ckc[data-v="heart"]') ? qs('#obChecks .ckc[data-v="heart"]').classList.contains("on") : !!S.profile.heart;
  S.profile.set = true; save();
}
function authMessage(msg, ok = false) {
  const el = $("authMsg");
  if (!el) return;
  el.textContent = msg || "";
  el.classList.toggle("ok", ok);
  el.classList.toggle("err", !!msg && !ok);
}
function authMessageFor(prefix) {
  if (prefix === "auth") return authMessage;
  return (text, ok) => {
    const el = $(`${prefix}Msg`);
    if (el) {
      el.textContent = text || "";
      el.className = `auth-msg ${ok ? "ok" : "err"}`;
    }
  };
}
function emailAuthCopy(mode) {
  return mode === "register"
    ? ["가입이 완료됐어요", "가입 완료", "이미 가입된 이메일이거나 입력값을 확인해주세요"]
    : ["로그인됐어요", "로그인 완료", "이메일 또는 비밀번호를 확인해주세요"];
}
async function emailAuth(mode, prefix = "auth") {
  if (API.mode !== "cloud") return toast("서버 연결 필요", "백엔드 연결 시 계정을 만들 수 있어요", "🔐");
  if (prefix === "auth") captureOnboardProfile();
  const email = $(`${prefix}Email`).value.trim();
  const password = $(`${prefix}Password`).value;
  const msg = authMessageFor(prefix);
  if (!email || !password) { msg("이메일과 비밀번호를 입력해주세요", false); return; }
  const [successMessage, toastTitle, failureMessage] = emailAuthCopy(mode);
  try {
    if (mode === "register") await API.accountRegister(email, password);
    else await API.accountLogin(email, password);
    msg(successMessage, true);
    $("onboard").classList.remove("show");
    $("extModal").classList.remove("show");
    renderMy(); renderHome();
    toast(toastTitle, "워치와 웹에서도 기록을 볼 수 있어요", "🔐");
  } catch {
    msg(failureMessage, false);
  }
}
async function socialAuth(provider) {
  if (API.mode !== "cloud") return toast("서버 연결 필요", "백엔드 연결 시 소셜 계정을 연결할 수 있어요", "🔐");
  if ($("onboard").classList.contains("show")) captureOnboardProfile();
  await API.oauth(provider);
}
qsa("#obFit button").forEach((b) => b.addEventListener("click", () => {
  qsa("#obFit button").forEach((x) => x.classList.remove("on")); b.classList.add("on");
}));
qsa("#obChecks .ckc").forEach((b) => b.addEventListener("click", () => {
  if (b.dataset.v === "none") { qsa("#obChecks .ckc").forEach((x) => x.classList.remove("on")); b.classList.add("on"); }
  else { qs('#obChecks .ckc[data-v="none"]').classList.remove("on"); b.classList.toggle("on"); }
}));
$("obSave").addEventListener("click", () => {
  captureOnboardProfile();
  API.saveProfile().catch(() => {});
  $("onboard").classList.remove("show");
  renderHome(); renderMy();
  toast("나중에 할게요", "마이 탭에서 언제든 계정을 만들 수 있어요", "🌲");
});
$("btnEditProf").addEventListener("click", openOnboard);
$("authCreate").addEventListener("click", () => emailAuth("register"));
$("authLogin").addEventListener("click", () => emailAuth("login"));
qsa(".social-row button[data-provider]").forEach((b) => b.addEventListener("click", () => socialAuth(b.dataset.provider)));

/* ---------------- 공용 닫기 / 지역 / 벨 ---------------- */
qsa("[data-close]").forEach((b) => b.addEventListener("click", () => $(b.dataset.close).classList.remove("show")));
document.addEventListener("click", (e) => {
  if (e.target?.classList?.contains("overlay")) e.target.classList.remove("show");
  const dc = e.target.closest?.("[data-close]");
  if (dc) $(dc.dataset.close).classList.remove("show");
  const routeBtn = e.target.closest?.("[data-route-provider]");
  if (routeBtn) {
    e.preventDefault();
    openRouteFromCurrent(routeBtn);
    return;
  }
  if (e.target?.id === "setHomeBtn") {           // 길찾기 '집 등록'(전역)
    if (!navigator.geolocation) return toast("위치 미지원", "이 기기는 GPS를 지원하지 않아요", "🏠");
    navigator.geolocation.getCurrentPosition(
      (pos) => { S.home = { lat: +pos.coords.latitude.toFixed(5), lon: +pos.coords.longitude.toFixed(5) }; save(); toast("집 등록 완료", "현재 위치를 집으로 저장했어요. 길찾기를 다시 열면 '집에서'가 보여요", "🏠"); },
      () => toast("위치 권한 필요", "집 등록에 위치 권한이 필요해요", "🏠"));
  }
});
/* ---------------- 위치 선택 (현재위치/시도/검색) ---------------- */
const SIDO_LOCS = [
  ["서울", 37.5663, 126.9779], ["부산", 35.1798, 129.075], ["대구", 35.8714, 128.6014],
  ["인천", 37.4563, 126.7052], ["광주", 35.1601, 126.8514], ["대전", 36.3504, 127.3845],
  ["울산", 35.5384, 129.3114], ["세종", 36.4801, 127.289], ["경기", 37.2636, 127.0286],
  ["강원", 37.8813, 127.7298], ["충북", 36.6357, 127.4914], ["충남", 36.6588, 126.6728],
  ["전북", 35.8203, 127.1088], ["전남", 34.8161, 126.4629], ["경북", 36.576, 128.5056],
  ["경남", 35.2383, 128.6924], ["제주", 33.489, 126.4983],
];
function updateLocLabel() {
  const el = $("locLabel"); if (!el) return;
  if (S.selectedMountain) el.textContent = S.selectedMountain.name;
  else if (S.activeLoc) el.textContent = S.activeLoc.label;
  else el.textContent = FM_DATA.regions[S.region] ? FM_DATA.regions[S.region].name : "서울 은평구";
}
function setLoc(loc) {            // loc: {lat,lon,label} | null(시도 기본)
  S.selectedMountain = null; S.activeLoc = loc; save();
  $("locModal").classList.remove("show");
  updateLocLabel(); renderHome(); renderIns();
  if (loc) toast("위치 변경", `${loc.label} 기준으로 산행지수를 계산했어요`, "📍");
}
function openLocPicker() {
  $("sidoGrid").innerHTML = SIDO_LOCS.map(([nm, la, lo]) =>
    `<button class="sido-chip" data-la="${la}" data-lo="${lo}" data-nm="${nm}">${nm}</button>`).join("");
  $("sidoGrid").querySelectorAll(".sido-chip").forEach((b) => b.addEventListener("click", () =>
    setLoc({ lat: +b.dataset.la, lon: +b.dataset.lo, label: b.dataset.nm })));
  $("locModal").classList.add("show");
}
$("locBtn").addEventListener("click", openLocPicker);
$("locGps").addEventListener("click", () => {
  if (!navigator.geolocation) return toast("위치 미지원", "이 기기는 GPS를 지원하지 않아요", "📍");
  toast("현재 위치 확인 중", "잠시만요…", "📍", false, 1500);
  navigator.geolocation.getCurrentPosition(
    (pos) => setLoc({ lat: +pos.coords.latitude.toFixed(5), lon: +pos.coords.longitude.toFixed(5), label: "현재 위치" }),
    () => toast("위치 권한 필요", "권한을 허용하거나 시·도를 선택해 주세요", "📍"),
    { enableHighAccuracy: true, timeout: 10000 });
});
$("locSearch").addEventListener("click", () => { $("locModal").classList.remove("show"); openMntSearch(); });

/* ---------------- 알림 — 지역·즐겨찾기·일정 맞춤 (req6) ---------------- */
function daysUntil(yyyymmdd) {
  if (yyyymmdd?.length !== 8) return null;
  const t = new Date(+yyyymmdd.slice(0, 4), +yyyymmdd.slice(4, 6) - 1, +yyyymmdd.slice(6, 8));
  const now = new Date(); now.setHours(0, 0, 0, 0);
  return Math.round((t - now) / 86400000);
}
function notifDayLabel(date) {
  const dd = daysUntil(date);
  if (dd === null) return "예정";
  if (dd === 0) return "오늘";
  return dd > 0 ? `D-${dd}` : "지난 일정";
}
function planNotifications() {
  return (S.plans || []).slice(-4).reverse().map((p) => ({
    ic: "📅",
    t: `${esc(p.name)} 산행 ${notifDayLabel(p.date)}`,
    b: `등록한 일정 (${esc(p.label || "")}) · 그날 날씨·산불을 확인하세요`,
  }));
}
function favoriteNotifications() {
  return (S.favs || []).slice(0, 3).map((f) => ({
    ic: "⭐",
    t: `${esc(f.name)} 산행 조건`,
    b: `즐겨찾기 · ${esc(f.sido || "")} 오늘 산행지수를 눌러 확인하세요`,
    fav: f,
  }));
}
function notificationLocationLabel() {
  if (S.activeLoc) return S.activeLoc.label;
  return FM_DATA.regions[S.region] ? FM_DATA.regions[S.region].name : "내 지역";
}
async function gpsNotification(locLabel) {
  if (!S.activeLoc || API.mode !== "cloud") return null;
  try {
    const g = await API.get(`/index/gps?lat=${S.activeLoc.lat}&lon=${S.activeLoc.lon}`, false);
    return {
      ic: "📍",
      t: `${esc(locLabel)} 오늘 산행지수 ${g.score}`,
      b: `🌡${g.conditions.weather.temp}° · ☔${g.conditions.weather.rain_prob}% · 🔥산불 ${g.conditions.fire.level}`,
    };
  } catch { return null; }
}
function defaultAreaNotification(locLabel) {
  return { ic: "🌬", t: `${esc(locLabel)} 기상 특보`, b: "능선부 강풍 주의 — 노출 구간은 우회로 권장 (산악기상관측망)" };
}
function onboardingNotification() {
  return { ic: "💡", t: "맞춤 알림을 받아보세요", b: "산을 즐겨찾기하거나 산행 일정을 등록하면 그 산·날짜에 맞춘 알림을 여기서 드려요" };
}
async function buildNotifications(locLabel) {
  const items = [...planNotifications(), ...favoriteNotifications()];
  const gps = await gpsNotification(locLabel);
  if (gps) items.push(gps);
  items.push(defaultAreaNotification(locLabel));
  if (!S.plans?.length && !S.favs?.length) items.push(onboardingNotification());
  return items;
}
function bindNotificationClicks(items) {
  $("notifList").querySelectorAll(".notif-item").forEach((el, i) => {
    if (items[i].fav) el.addEventListener("click", () => openMountainDetail(items[i].fav.list_no, items[i].fav.name));
  });
}
function hideBellDot() {
  const dot = $("bellBtn").querySelector("i");
  if (dot) dot.style.display = "none";
}
function renderNotificationSheet(items, locLabel) {
  $("extSheet").innerHTML = `
    <h3>🔔 알림 <small style="font-size:11px;font-weight:600;color:var(--sub)">${esc(locLabel)}·즐겨찾기·일정 기준</small></h3>
    <button class="btn primary" id="pushBtn" style="width:100%;margin-bottom:10px">🔔 푸시 알림 받기 (앱 닫아도 알림)</button>
    <div id="notifList">${items.map((n, i) => `<div class="notif-item" data-i="${i}"><div class="ni-ic">${esc(n.ic)}</div><div><b>${esc(n.t)}</b><span>${esc(n.b)}</span></div></div>`).join("")}</div>
    <div class="btnrow"><button class="btn ghost" data-close="extModal">닫기</button></div>`;
  $("pushBtn").addEventListener("click", enablePush);
  bindNotificationClicks(items);
  hideBellDot();
}
async function openNotifs() {
  $("extModal").classList.add("show");
  $("extSheet").innerHTML = `<h3>🔔 알림</h3><p class="sub">불러오는 중…</p>`;
  const locLabel = notificationLocationLabel();
  renderNotificationSheet(await buildNotifications(locLabel), locLabel);
}
function urlB64ToUint8(b64) {
  const pad = "=".repeat((4 - (b64.length % 4)) % 4);
  const raw = atob((b64 + pad).replaceAll("-", "+").replaceAll("_", "/"));
  return Uint8Array.from([...raw].map((c) => c.codePointAt(0)));
}
async function enablePush() {
  if (API.mode !== "cloud") return toast("서버 연결 필요", "백엔드 연결 시 가능해요", "🔔");
  try {
    const v = await API.get("/push/vapid", false);
    if (!v.enabled || !v.publicKey) return toast("푸시 준비 중", "관리자가 VAPID 키를 설정하면 켜져요(지금은 인앱 알림으로 동작)", "🔔", false, 4500);
    if (!("serviceWorker" in navigator) || !("PushManager" in globalThis)) return toast("미지원", "이 기기는 푸시를 지원하지 않아요", "🔔");
    if ((await Notification.requestPermission()) !== "granted") return toast("알림 권한 필요", "브라우저 알림 권한을 허용해주세요", "🔔");
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlB64ToUint8(v.publicKey) });
    await API.post("/push/subscribe", sub.toJSON());
    const t = await API.post("/push/test", {});
    toast("푸시 알림 켜짐 🔔", t.sent ? "테스트 알림을 보냈어요. 일정·즐겨찾기 산 소식을 받아요" : "구독 완료(테스트 발송은 곧 반영)", "🔔");
  } catch { toast("푸시 설정 실패", "잠시 후 다시 시도해주세요", "🔔"); }
}
$("bellBtn").addEventListener("click", openNotifs);
function refreshBellDot() {
  const dot = $("bellBtn")?.querySelector("i");
  if (dot) dot.style.display = ((S.plans || []).length || (S.favs || []).length) ? "block" : "none";
}

/* ---------------- 전국 산 검색 (산림청 산정보) ---------------- */
let mntTimer;
const HTML_ENTITIES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => HTML_ENTITIES[c]);
const cssToken = (value, fallback = "neutral") => /^[a-z0-9_-]+$/i.test(String(value || "")) ? String(value) : fallback;
const cssColor = (value, fallback = "#74C69D") => /^#[0-9a-f]{3}(?:[0-9a-f]{3})?$/i.test(String(value || "")) ? String(value) : fallback;
function openMntSearch() {
  $("mntModal").classList.add("show");
  $("mntQ").value = "";
  $("mntResults").innerHTML = API.mode === "cloud"
    ? `<div class="mnt-empty">산 이름을 입력하면 전국에서 검색해요.</div>`
    : `<div class="mnt-empty">전국 산 검색은 서버 연결 상태에서 동작해요.</div>`;
  setTimeout(() => $("mntQ").focus(), 120);
}
async function runMntSearch(q) {
  q = q.trim();
  const box = $("mntResults");
  if (!q) { box.innerHTML = `<div class="mnt-empty">산 이름을 입력하면 전국에서 검색해요.</div>`; return; }
  if (API.mode !== "cloud") { box.innerHTML = `<div class="mnt-empty">전국 산 검색은 서버 연결 상태에서 동작해요.</div>`; return; }
  box.innerHTML = `<div class="mnt-empty">검색 중…</div>`;
  try {
    const d = await API.get(`/mountains?q=${encodeURIComponent(q)}&size=30`, false);
    if (!d.items.length) { box.innerHTML = `<div class="mnt-empty">'${esc(q)}' 검색 결과가 없어요.</div>`; return; }
    box.innerHTML =
      `<div class="mnt-empty" style="text-align:left;padding:2px 2px 8px">전국 ${d.total.toLocaleString()}개 중 ${d.items.length}개 · <b>탭하면 산행지수</b></div>` +
      d.items.map(mntRow).join("");
  } catch {
    box.innerHTML = `<div class="mnt-empty">검색 중 오류가 났어요. 잠시 후 다시 시도해주세요.</div>`;
  }
}
function mntRow(m) {
  const dist = m.dist_km === null || m.dist_km === undefined ? "" : `<div class="loc">🧭 ${esc(m.dist_km)}km</div>`;
  return `
    <div class="mnt-row" data-id="${esc(m.list_no)}" data-name="${esc(m.name)}">
      <div><b>${esc(m.name)}${m.top100 ? '<span class="top">100대명산</span>' : ""}</b>
        <div class="loc">📍 ${esc(m.addr || m.sido || "")}</div>${dist}</div>
      <div class="h">${m.height ? esc(m.height) + "m" : "—"} ›</div>
    </div>`;
}
async function findNearby() {
  const box = $("mntResults");
  if (API.mode !== "cloud") { box.innerHTML = `<div class="mnt-empty">주변 산 찾기는 서버 연결 상태에서 동작해요.</div>`; return; }
  if (!navigator.geolocation) { box.innerHTML = `<div class="mnt-empty">이 기기는 위치 기능을 지원하지 않아요.</div>`; return; }
  box.innerHTML = `<div class="mnt-empty">📍 현재 위치를 확인하는 중…</div>`;
  navigator.geolocation.getCurrentPosition(async (pos) => {
    const { latitude: lat, longitude: lon } = pos.coords;
    try {
      const d = await API.get(`/mountains/nearby?lat=${lat}&lon=${lon}&radius=40&limit=25`, false);
      if (!d.items.length) { box.innerHTML = `<div class="mnt-empty">반경 40km 내 등록된 산이 없어요. 검색을 이용해 주세요.</div>`; return; }
      box.innerHTML =
        `<div class="mnt-empty" style="text-align:left;padding:2px 2px 8px">📍 내 주변 ${d.count}곳 · 가까운 순 · <b>탭하면 산행지수</b></div>` +
        d.items.map(mntRow).join("");
    } catch {
      box.innerHTML = `<div class="mnt-empty">주변 산을 불러오지 못했어요.</div>`;
    }
  }, () => {
    box.innerHTML = `<div class="mnt-empty">위치 권한이 거부됐어요. 이름으로 검색해 주세요.</div>`;
  }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
}
$("mntSearchBtn").addEventListener("click", openMntSearch);
$("mntGpsBtn").addEventListener("click", findNearby);
$("mntQ").addEventListener("input", (e) => {
  clearTimeout(mntTimer);
  const v = e.target.value;
  mntTimer = setTimeout(() => runMntSearch(v), 320);
});
$("mntResults").addEventListener("click", (e) => {
  const row = e.target.closest(".mnt-row");
  if (row?.dataset.id) openMountainDetail(row.dataset.id, row.dataset.name);
});

/* ---------------- 산 상세 (사진·시설·산행지수) ---------------- */
const FAC_ICON = { 정상: "🏔", 대피소: "🏠", 조망점: "🔭", 위험지역: "⚠️", 헬기장: "🚁", 화장실: "🚻", 음수대: "💧", 약수터: "💧" };
function themedHero(name, height = 0) {
  if (globalThis.ForestMateHeroImages?.themedHero) {
    return globalThis.ForestMateHeroImages.themedHero(name, height);
  }
  // 사진 폴백 — 높이/이름 기반 테마 SVG(외부 의존 없음)
  const h = height;
  const title = esc(name);
  let top = "#52B788";
  if (h >= 1200) top = "#2D6A4F";
  else if (h >= 600) top = "#40916C";
  const sky = h >= 1200 ? "#A8C7B5" : "#CDE7D4";
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='600' height='300'>
    <defs><linearGradient id='g' x1='0' y1='0' x2='0' y2='1'><stop offset='0' stop-color='${sky}'/><stop offset='1' stop-color='#EAF4EC'/></linearGradient></defs>
    <rect width='600' height='300' fill='url(#g)'/>
    <polygon points='0,300 150,150 260,220 380,90 500,210 600,150 600,300' fill='${top}' opacity='0.9'/>
    <polygon points='320,300 460,120 600,260 600,300' fill='${top}'/>
    <text x='24' y='280' font-family='sans-serif' font-size='22' font-weight='800' fill='#1B4332'>${title}${h ? " · " + esc(h) + "m" : ""}</text></svg>`;
  return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
}
function heroProxyUrl(name, height = 0) {
  const pageName = String(name || "").trim().split(/\s+/)[0] || "산";
  const h = Math.max(0, Math.round(Number(height) || 0));
  return `/api/v1/mountain-hero?name=${encodeURIComponent(pageName)}&height=${encodeURIComponent(h)}`;
}
async function loadHero(img, name, height) {
  if (globalThis.ForestMateHeroImages?.loadHeroImage) {
    return globalThis.ForestMateHeroImages.loadHeroImage(img, name, height);
  }
  const fallback = themedHero(name, height);
  const restoreFallback = () => {
    img.onerror = null;
    img.src = fallback;
  };
  img.onerror = restoreFallback;
  img.src = fallback;                                // 즉시 폴백
  const proxied = heroProxyUrl(name, height);
  img.onerror = restoreFallback;
  img.src = proxied;
  return img.src;
}
async function openMountainDetail(listNo, name) {
  $("extModal").classList.add("show");
  $("extSheet").innerHTML = `<h3>${esc(name)}</h3><p class="sub">정보를 불러오는 중…</p>`;
  if (API.mode !== "cloud") { return selectMountainIndex(listNo, name); }
  try {
    const d = await API.get(`/mountains/${encodeURIComponent(listNo)}/index`, false);
    const m = d.mountain, fac = m.facilities || {};
    const facHtml = Object.keys(fac).length
      ? Object.entries(fac).map(([k, v]) => `<span class="fac">${FAC_ICON[k] || "•"} ${esc(k)} ${esc(v)}</span>`).join("")
      : `<span class="sub" style="font-size:11.5px">등록된 등산로 시설 정보 없음</span>`;
    $("extSheet").innerHTML = `
      <img id="mtnHero" class="mtn-hero" alt="${esc(m.name)} 전경">
      <h3>${esc(m.name)}${m.top100 ? ' <span class="top">100대명산</span>' : ""}</h3>
      <p class="sub">📍 ${esc(m.addr || m.sido || "")} · ⛰ ${m.height ? esc(m.height) + "m" : "높이 미상"}</p>
      <div class="mtn-score">오늘의 산행지수 <b>${esc(d.score)}</b><br>🌡 ${esc(d.conditions.weather.temp)}°C · 🔥 산불 ${esc(d.conditions.fire.level)} · ${esc(d.place)}</div>
      ${m.lat ? `<b style="font-size:12px">🗺 위치 · 길찾기</b><div id="mtnMap" class="detail-map"></div>${dirButtons(m.lat, m.lon, m.name)}` : ""}
      <b style="font-size:12px">🥾 등산로 시설 (산림청 주요지점)</b>
      <div class="facs">${facHtml}</div>
      <div class="btnrow">
        <button class="btn primary" id="mtnSetHome">🏠 홈 산행지수로 설정</button>
        <button class="btn ghost" id="mtnFav">${isFav(listNo) ? "⭐ 즐겨찾기됨" : "☆ 즐겨찾기"}</button>
      </div>
      <div class="btnrow" style="margin-top:9px"><button class="btn ghost" id="mtnPlan">📅 산행 일정 잡기</button><button class="btn ghost" data-close="extModal">닫기</button></div>`;
    loadHero($("mtnHero"), m.name, m.height);
    if (m.lat) setTimeout(() => miniMap("mtnMap", m.lat, m.lon, m.name, listNo), 60);
    $("mtnSetHome").addEventListener("click", () => { $("extModal").classList.remove("show"); selectMountainIndex(listNo, m.name); });
    $("mtnFav").addEventListener("click", () => { toggleFav({ list_no: listNo, name: m.name, sido: m.sido, lat: m.lat, lon: m.lon }); $("mtnFav").textContent = isFav(listNo) ? "⭐ 즐겨찾기됨" : "☆ 즐겨찾기"; });
    $("mtnPlan").addEventListener("click", () => openPlan({ list_no: listNo, name: m.name, lat: m.lat, lon: m.lon }));
  } catch {
    $("extSheet").innerHTML = `<h3>${esc(name)}</h3><p class="sub">정보를 불러오지 못했어요.</p><div class="btnrow"><button class="btn ghost" data-close="extModal">닫기</button></div>`;
  }
}

/* ---------------- 지도 · 길찾기 (req5) ---------------- */
const _maps = {};
function miniMap(elId, lat, lon, name, listNo) {
  const el = document.getElementById(elId);
  const leaflet = globalThis.L;
  if (!el || !leaflet) return null;
  if (_maps[elId]) { _maps[elId].remove(); delete _maps[elId]; }
  const map = leaflet.map(el, { attributionControl: false }).setView([lat, lon], 13);
  leaflet.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 18 }).addTo(map);
  leaflet.marker([lat, lon]).addTo(map).bindPopup(name).openPopup();
  _maps[elId] = map;
  setTimeout(() => map.invalidateSize(), 120);
  if (listNo) drawTrails(map, listNo);   // 실제 등산로 선
  return map;
}
const TRAIL_COLOR = { 쉬움: "#2D6A4F", 보통: "#E08A1E", 어려움: "#C9304E" };
async function drawTrails(map, listNo) {
  if (!map || !listNo || API.mode !== "cloud") return 0;
  const leaflet = globalThis.L;
  if (!leaflet) return 0;
  try {
    const d = await API.get(`/mountains/${encodeURIComponent(listNo)}/trails`, false);
    if (!d.segs?.length) return 0;
    const bounds = [];
    d.segs.forEach((s) => {
      if (!s.pts?.length || s.pts.length < 2) return;
      leaflet.polyline(s.pts, { color: TRAIL_COLOR[s.dffl] || "#40916C", weight: 4, opacity: 0.85 })
        .addTo(map).bindPopup(`${esc(s.nm || "등산로")} · ${esc(s.dffl || "")} ${s.lt ? esc(s.lt) + "km" : ""}`);
      bounds.push(...s.pts);
    });
    if (bounds.length) map.fitBounds(bounds, { padding: [22, 22], maxZoom: 15 });
    return d.segs.length;
  } catch { return 0; }
}
function homeLoc() { return S.home || null; }
function routeCoord(v) {
  const n = Number(v);
  return Number.isFinite(n) ? n.toFixed(6).replace(/0+$/, "").replace(/\.$/, "") : "";
}
function routePoint(lat, lon, name) {
  return { lat: routeCoord(lat), lon: routeCoord(lon), name: String(name || "도착지").trim() || "도착지" };
}
function kakaoPointPath(p) {
  return `${encodeURIComponent(p.name)},${p.lat},${p.lon}`;
}
function kakaoRouteUrl(origin, dest) {
  return origin
    ? `https://map.kakao.com/link/from/${kakaoPointPath(origin)}/to/${kakaoPointPath(dest)}`
    : `https://map.kakao.com/link/to/${kakaoPointPath(dest)}`;
}
function googleRouteUrl(origin, dest) {
  const params = new URLSearchParams({ api: "1", destination: `${dest.lat},${dest.lon}`, travelmode: "walking" });
  if (origin) params.set("origin", `${origin.lat},${origin.lon}`);
  return `https://www.google.com/maps/dir/?${params.toString()}`;
}
function routeUrl(provider, origin, dest) {
  return provider === "google" ? googleRouteUrl(origin, dest) : kakaoRouteUrl(origin, dest);
}
function openRouteUrl(url, pendingWin) {
  if (pendingWin && !pendingWin.closed) {
    pendingWin.location.href = url;
    return;
  }
  const opened = globalThis.open?.(url, "_blank", "noopener");
  if (!opened) location.href = url;
}
function setRouteButtonBusy(btn, busy) {
  if (!btn) return;
  if (busy) {
    btn.dataset.routeText = btn.textContent;
    btn.textContent = "위치 확인 중...";
    btn.disabled = true;
  } else {
    btn.textContent = btn.dataset.routeText || btn.textContent;
    btn.disabled = false;
    delete btn.dataset.routeText;
  }
}
function currentRouteOrigin() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) { reject(new Error("geolocation unsupported")); return; }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve(routePoint(pos.coords.latitude, pos.coords.longitude, "현재 위치")),
      reject,
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 },
    );
  });
}
async function openRouteFromCurrent(btn) {
  const provider = btn.dataset.routeProvider || "kakao";
  const dest = routePoint(btn.dataset.routeLat, btn.dataset.routeLon, decodeURIComponent(btn.dataset.routeName || ""));
  if (!dest.lat || !dest.lon) return toast("길찾기 오류", "도착지 좌표가 없어 길찾기를 열 수 없어요", "🧭", true);
  const pendingWin = globalThis.open?.("about:blank", "_blank");
  if (pendingWin) pendingWin.opener = null;
  setRouteButtonBusy(btn, true);
  try {
    const origin = await currentRouteOrigin();
    openRouteUrl(routeUrl(provider, origin, dest), pendingWin);
    toast("길찾기 열기", "현재 위치와 도착지 좌표를 함께 넘겼어요", "🧭");
  } catch {
    openRouteUrl(routeUrl(provider, null, dest), pendingWin);
    toast("위치 권한 필요", "도착지는 좌표로 열었어요. 지도앱에서 출발지를 선택해주세요", "🧭", true);
  } finally {
    setRouteButtonBusy(btn, false);
  }
}
function dirButtons(lat, lon, name) {
  const dest = routePoint(lat, lon, name), h = homeLoc();
  const routeLat = clampNumber(dest.lat, -90, 90, 0);
  const routeLon = clampNumber(dest.lon, -180, 180, 0);
  const routeName = esc(encodeURIComponent(dest.name));
  const routeAttrs = `data-route-lat="${routeLat}" data-route-lon="${routeLon}" data-route-name="${routeName}"`;
  return `<div class="dir-row">
    <button type="button" class="dir-btn kakao" data-route-provider="kakao" ${routeAttrs}>📍 현재위치→ 카카오맵</button>
    <button type="button" class="dir-btn" data-route-provider="google" ${routeAttrs}>구글맵</button>
    ${h ? `<a class="dir-btn home" href="${kakaoRouteUrl(routePoint(h.lat, h.lon, "우리집"), dest)}" target="_blank" rel="noopener">🏠 집에서</a>`
        : `<button class="dir-btn" id="setHomeBtn">🏠 집 등록</button>`}
  </div>`;
}

/* ---------------- 즐겨찾기 (req6) ---------------- */
function favs() { return S.favs || (S.favs = []); }
function isFav(id) { return favs().some((f) => f.list_no === id); }
function toggleFav(m) {
  const i = favs().findIndex((f) => f.list_no === m.list_no);
  if (i >= 0) { favs().splice(i, 1); toast("즐겨찾기 해제", m.name, "☆"); }
  else { favs().push(m); toast("즐겨찾기 추가", `${m.name} — 마이 탭에서 모아봐요`, "⭐"); }
  save(); renderFavs();
}
function renderFavs() {
  const box = $("favList"); if (!box) return;
  if (!favs().length) { box.innerHTML = `<div class="card" style="font-size:12px;color:var(--sub);text-align:center;padding:16px">산 상세에서 ☆를 누르면 즐겨찾기에 담겨요.</div>`; return; }
  box.innerHTML = favs().map((f) => `
    <div class="log-row fav-row" data-id="${esc(f.list_no)}" data-name="${esc(f.name)}">
      <div><b>⭐ ${esc(f.name)}</b><span>${esc(f.sido || "")}</span></div>
      <div class="log-stat">산행지수 ›</div></div>`).join("");
  box.querySelectorAll(".fav-row").forEach((el) => el.addEventListener("click", () => openMountainDetail(el.dataset.id, el.dataset.name)));
}

/* ---------------- 산행 일정 계획 (req6) ---------------- */
async function openPlan(m) {
  $("extModal").classList.add("show");
  $("extSheet").innerHTML = `<h3>📅 ${esc(m.name)} 산행 일정</h3><p class="sub">예보를 불러오는 중…</p>`;
  let days = [];
  if (API.mode === "cloud" && m.lat) {
    try { days = (await API.get(`/forecast?lat=${m.lat}&lon=${m.lon}`, false)).days; } catch { /* */ }
  }
  const planTone = (score) => {
    if (score >= 70) return "good";
    if (score >= 50) return "mid";
    return "bad";
  };
  const planTip = (score) => {
    if (score >= 70) return "산행하기 좋아요";
    if (score >= 50) return "기상 변화 유의";
    return "산행 비권장";
  };
  const rows = days.length ? days.map((d) => {
    const ok = planTone(d.score);
    const tip = planTip(d.score);
    return `<div class="plan-row ${ok}">
      <div><b>${d.label}</b><span>${d.dow}</span></div>
      <div class="plan-wx">🌡${d.temp}° · ☔${d.rain_prob}% · 🔥${d.fire}</div>
      <div class="plan-score">${d.score}<small>${tip}</small></div></div>`;
  }).join("") : `<p class="sub">예보를 불러오지 못했어요. 서버 연결 후 다시 시도해 주세요.</p>`;
  $("extSheet").innerHTML = `
    <h3>📅 ${esc(m.name)} 산행 일정</h3>
    <p class="sub">날짜별 날씨·산불 적합도 (기상청 단기예보). 날짜를 눌러 일정을 저장하세요.</p>
    <div id="planList">${rows}</div>
    <div class="btnrow"><button class="btn ghost" data-close="extModal">닫기</button></div>`;
  $("planList").querySelectorAll(".plan-row").forEach((el, i) => el.addEventListener("click", () => {
    S.plans = S.plans || [];
    S.plans.push({ list_no: m.list_no, name: m.name, date: days[i] ? days[i].date : "", label: days[i] ? days[i].label : "" });
    save();
    toast("일정 저장", `${m.name} · ${days[i] ? days[i].label : ""} 산행 일정을 저장했어요`, "📅");
  }));
}

/* ---------------- 숲나들e 연동 (숲 소식 · 치유의숲) ---------------- */
const FOREST_URL = "https://www.foresttrip.go.kr";
function openExt(html) { $("extSheet").innerHTML = html; $("extModal").classList.add("show"); }
function providerButton(provider) {
  const labels = { google: "Google", kakao: "Kakao", naver: "Naver" };
  const aria = { google: "Google로 시작", kakao: "카카오로 시작", naver: "네이버로 시작" };
  const logos = {
    google: `<span class="provider-logo google-logo" aria-hidden="true"><svg viewBox="0 0 24 24"><path fill="#4285F4" d="M22.6 12.2c0-.8-.1-1.5-.2-2.2H12v4.2h5.9c-.3 1.4-1 2.5-2.1 3.3v2.7h3.4c2-1.8 3.4-4.6 3.4-8z"/><path fill="#34A853" d="M12 23c3 0 5.6-1 7.4-2.8L16 17.5c-.9.6-2.2 1-4 1-3.1 0-5.7-2.1-6.7-4.9H1.8v2.8C3.6 20.3 7.5 23 12 23z"/><path fill="#FBBC05" d="M5.3 13.6c-.2-.6-.4-1.3-.4-2s.1-1.4.4-2V6.8H1.8C1.1 8.2.7 9.8.7 11.6s.4 3.4 1.1 4.8l3.5-2.8z"/><path fill="#EA4335" d="M12 4.7c1.6 0 3.1.6 4.2 1.7l3.1-3.1C17.6 1.6 15 0 12 0 7.5 0 3.6 2.7 1.8 6.8l3.5 2.8C6.3 6.8 8.9 4.7 12 4.7z"/></svg></span>`,
    kakao: `<span class="provider-logo kakao-logo" aria-hidden="true"></span>`,
    naver: `<span class="provider-logo naver-logo" aria-hidden="true"></span>`,
  };
  return `<button data-provider="${provider}" aria-label="${aria[provider]}">${logos[provider]}<span>${labels[provider]}로 시작</span></button>`;
}
function authForm(prefix) {
  return `
    <div class="auth-form">
      <div class="social-row social-row-stack" aria-label="소셜 계정으로 계속하기">
        ${providerButton("google")}
        ${providerButton("kakao")}
        ${providerButton("naver")}
      </div>
      <div class="auth-divider"><span>이메일로 계속하기</span></div>
      <input type="email" id="${prefix}Email" placeholder="이메일">
      <input type="password" id="${prefix}Password" placeholder="비밀번호" autocomplete="current-password">
      <div class="auth-actions">
        <button id="${prefix}Create">가입하기</button>
        <button id="${prefix}Login">로그인</button>
      </div>
      <span class="auth-msg" id="${prefix}Msg"></span>
    </div>`;
}
function openAccount() {
  const acct = S.account;
  if (acct) {
    openExt(`
      <h3>🔐 계정 관리</h3>
      <p class="sub">산행 기록과 워치 데이터가 이 계정에 저장돼요.</p>
      <div class="loc-card">
        <div class="row"><span>이메일</span><b>${esc(acct.email || "소셜 계정")}</b></div>
        <div class="row"><span>연결</span><b>${esc((acct.providers || []).join(" · ") || "account")}</b></div>
        <div class="row"><span>프로필</span><b>${esc(S.profile.name || "산친구")} · ${["", "초급", "중급", "상급"][S.profile.fit]}</b></div>
      </div>
      <div class="btnrow">
        <button class="btn ghost" data-close="extModal">닫기</button>
        <button class="btn danger" id="acctLogout">로그아웃</button>
      </div>`);
    $("acctLogout").addEventListener("click", async () => {
      await API.logout();
      $("extModal").classList.remove("show");
      renderMy();
      toast("로그아웃", "이 기기에서는 게스트 모드로 전환됐어요", "🔐");
    });
    return;
  }
  openExt(`
    <div class="account-glass-panel">
      <h3>계정 만들기 또는 로그인</h3>
      <p class="sub">가입하면 워치와 웹에서도 산행 기록을 볼 수 있어요.</p>
      ${authForm("acct")}
      <div class="btnrow"><button class="btn ghost" data-close="extModal">닫기</button></div>
    </div>`);
  $("acctCreate").addEventListener("click", () => emailAuth("register", "acct"));
  $("acctLogin").addEventListener("click", () => emailAuth("login", "acct"));
  qsa("#extSheet .social-row button[data-provider]").forEach((b) => b.addEventListener("click", () => socialAuth(b.dataset.provider)));
}
$("btnAccount").addEventListener("click", openAccount);
function openRest() {
  openExt(`
    <h3>🏕 축령산 치유의숲 · 숲 명상</h3>
    <p class="sub">산림치유 프로그램 추천 (산림복지진흥원 숲나들e)</p>
    <div class="ext-row"><b>장소</b> 전남 장성 축령산 편백숲 치유센터</div>
    <div class="ext-row"><b>효과</b> 편백 피톤치드 · 호흡 명상으로 스트레스·혈압 완화</div>
    <div class="ext-row"><b>추천</b> 최근 산행 패턴상 회복이 필요한 주간이에요</div>
    <p class="sub" style="margin-top:12px">⚠️ <b>실시간 예약 가능 일정·잔여석은 숲나들e에서 확인하세요.</b> 본 화면은 추천이며, 정확한 운영일정은 아래 숲나들e 공식 페이지가 최신입니다.</p>
    <div class="btnrow">
      <a class="btn primary" href="${FOREST_URL}/cms/hm/main/main.do" target="_blank" rel="noopener">숲나들e에서 일정·예약 ↗</a>
      <button class="btn ghost" data-close="extModal">닫기</button>
    </div>`);
}
function openNews() {
  const items = (FM_DATA.news || []).map((n, i) => {
    let meta = "";
    if (typeof n === "object" && (n.date || n.region)) {
      meta = `<small>${esc(n.date || "")}${n.date && n.region ? " · " : ""}${esc(n.region || "")}</small>`;
    }
    return `<div class="news-item" data-i="${i}"><div class="ni-tx">🌿 <b>${esc(n.title || n)}</b>${meta}</div><span>›</span></div>`;
  }).join("");
  openExt(`
    <h3>🌿 이번 주 숲 소식</h3>
    <p class="sub">산림청 · 숲나들e · 국립공원 주간 소식 · 항목을 누르면 자세히</p>
    <div id="newsList">${items}</div>
    <div class="btnrow">
      <a class="btn primary" href="${FOREST_URL}" target="_blank" rel="noopener">숲나들e 바로가기 ↗</a>
      <a class="btn ghost" href="https://www.forest.go.kr" target="_blank" rel="noopener">산림청 소식 ↗</a>
    </div>`);
  $("newsList").addEventListener("click", (e) => {
    const it = e.target.closest(".news-item");
    if (it) openNewsDetail(+it.dataset.i);
  });
}
function openNewsDetail(i) {
  let n = FM_DATA.news[i];
  if (typeof n === "string") n = { title: n, detail: n, url: "https://www.foresttrip.go.kr" };
  openExt(`
    <h3>🌿 ${esc(n.title)}</h3>
    <p class="sub">${esc(n.date || "이번 주")}${n.region ? " · " + esc(n.region) : ""} · 숲 소식</p>
    <p style="font-size:13px;line-height:1.65;margin:6px 0 4px">${esc(n.detail)}</p>
    <div class="btnrow">
      <a class="btn primary" href="${n.url}" target="_blank" rel="noopener">자세히 보기 ↗</a>
      <button class="btn ghost" id="newsBack">← 목록</button>
    </div>`);
  $("newsBack").addEventListener("click", openNews);
}
$("restCard").addEventListener("click", openRest);
$("newsCard").addEventListener("click", openNews);

/* ---------------- 초기화 ---------------- */
async function init() {
  // 백엔드 가용성 감지 — 성공 시 cloud 모드(서버 산행지수·추천·챗·SOS)
  await API.init();
  if (API.mode === "cloud") {
    const dot = $("cloudDot");
    if (dot) { dot.style.display = "inline-flex"; }
  }
  renderHome();
  renderMy();
  renderEvents();
  renderGuards();
  renderLocCard();
  seedChat();
  // 산행 탭은 코스 미선택 상태로 시작(빈 상태 안내). 코스는 홈 추천에서 사용자가 선택.
  // 데모 파라미터(?demo=57)일 때만 스크린샷용으로 기본 코스를 채운다.
  if (DEMO !== null) {
    selectCourse("bukhansan");
    Hike.prog = DEMO / 100;
    Hike.hr = 96;
    Hike.active = true;
    drawProgress(); renderHikeUI();
    const hz = Hike.course.hazards[0];
    $("trailAlert").style.display = "flex";
    $("alertTitle").textContent = `300m 앞 ${hz.type} 구간`;
    $("alertBody").textContent = `${hz.grade} — ${hz.note}`;
    sunsetTick();
  }
  const t = Q.get("t");
  if (t) show(t);
  else if (location.hash) show(location.hash.slice(1));
  const forceFtue = Q.get("ftue") === "1" || Q.get("onboard") === "1";
  if ((forceFtue || !S.profile.set) && !t && DEMO === null && !Q.get("embed")) setTimeout(openOnboard, 600);
}
init(); // NOSONAR

/* PWA */
if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
  navigator.serviceWorker.register("sw.js").catch(() => {}); // NOSONAR
}
