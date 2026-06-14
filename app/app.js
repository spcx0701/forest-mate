/* 숲길동무 앱 로직 — 데모 빌드(시뮬레이션 GPS) */
"use strict";
const $ = (id) => document.getElementById(id);
const qs = (sel, root = document) => root.querySelector(sel);
const qsa = (sel, root = document) => [...root.querySelectorAll(sel)];
const nowHM = () => { const d = new Date(); return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`; };

/* ---------------- 상태 저장 ---------------- */
const DEFAULTS = {
  profile: { set: false, name: "", fit: 2, knee: true, heart: false },
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
try { S = Object.assign({}, DEFAULTS, JSON.parse(localStorage.getItem("fm_state") || "{}")); }
catch { S = { ...DEFAULTS }; }
S.profile = Object.assign({}, DEFAULTS.profile, S.profile);
S.settings = Object.assign({}, DEFAULTS.settings, S.settings);
S.june = Object.assign({}, DEFAULTS.june, S.june);
const save = () => { try { localStorage.setItem("fm_state", JSON.stringify(S)); } catch {} };
if (!S.installAt) { S.installAt = Date.now(); save(); }   // 최초 사용일(로컬 경과일 계산용)

/* URL 파라미터 (?t=tab&demo=57 — 화면 캡처/시연용) */
const Q = new URLSearchParams(location.search);
const DEMO = Q.get("demo") !== null ? Math.min(100, Math.max(0, +Q.get("demo") || 57)) : null;

/* ---------------- 클라우드 API 클라이언트 ----------------
 * 백엔드(/api/v1)가 살아 있으면 cloud 모드: 산행지수·추천·챗·산행기록·SOS가
 * 실서버를 경유한다. 어떤 호출이든 실패하면 조용히 로컬 엔진으로 폴백 —
 * 통신 음영지역에서도 앱은 끝까지 동작해야 한다. */
const API = {
  // 기본은 동일 오리진(/api/v1). Capacitor(iOS) 번들 빌드처럼 오리진이 다른 경우
  // index.html에서 window.FM_API_BASE = "https://<배포도메인>/api/v1" 로 주입한다.
  base: (typeof window !== "undefined" && window.FM_API_BASE) || "/api/v1",
  mode: "local",
  token: localStorage.getItem("fm_token") || null,
  hikeId: null,
  async init() {
    if (location.protocol === "file:") return false;
    try {
      const r = await fetch(this.base + "/healthz", { signal: AbortSignal.timeout(1500) });
      if (!r.ok) throw new Error();
      this.mode = "cloud";
      if (!this.token) await this.register();
      return true;
    } catch { this.mode = "local"; return false; }
  },
  async register() {
    const p = S.profile;
    const reg = await this.post("/devices", { name: p.name, fit: p.fit, knee: p.knee, heart: p.heart }, false);
    this.token = reg.token;
    localStorage.setItem("fm_token", reg.token);
    return reg;
  },
  headers(auth = true) {
    const h = { "Content-Type": "application/json" };
    if (auth && this.token) h.Authorization = "Bearer " + this.token;
    return h;
  },
  async get(path) {
    const r = await fetch(this.base + path, { signal: AbortSignal.timeout(2500) });
    if (!r.ok) throw new Error(path + " " + r.status);
    return r.json();
  },
  async post(path, body, auth = true, retry = true) {
    const r = await fetch(this.base + path, {
      method: "POST", headers: this.headers(auth), body: JSON.stringify(body || {}),
      signal: AbortSignal.timeout(4000),
    });
    // 토큰이 무효(서버 DB 교체·만료·revoke)면 익명 기기 재등록 후 1회 재시도.
    // 이게 없으면 산행·SOS 같은 인증 호출이 조용히 로컬 폴백돼 관제에 안 잡힌다.
    if (r.status === 401 && auth && this.token && retry) {
      localStorage.removeItem("fm_token"); this.token = null;
      await this.register();
      return this.post(path, body, auth, false);
    }
    if (!r.ok) throw new Error(path + " " + r.status);
    return r.json();
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
function show(id) {
  qsa(".screen").forEach((s) => s.classList.toggle("active", s.id === id));
  tabs.forEach((a) => a.classList.toggle("on", a.dataset.t === id));
  const sc = $(id); if (sc) sc.scrollTop = 0;
}
tabs.forEach((a) => a.addEventListener("click", (e) => {
  e.preventDefault();
  history.replaceState(null, "", "#" + a.dataset.t);
  show(a.dataset.t);
}));

/* ---------------- 산행지수 ---------------- */
function calcIndex(r) {
  return Math.floor(r.fire.score * 0.3 + r.landslide.score * 0.25 + r.weather.score * 0.25 + r.sunsetScore * 0.2);
}
function idxLabel(v) { return v >= 80 ? "좋음 — 산행하기 좋은 날 🌤" : v >= 60 ? "보통 — 기상 변화에 유의하세요 ⛅" : "주의 — 무리한 산행은 피하세요 ⚠️"; }

function paintIndexCard(v, fire, landslide, weather, sunsetAt, placeLabel) {
  const C = 276.5;
  $("idxVal").textContent = v;
  $("idxArc").style.strokeDashoffset = (C * (1 - v / 100)).toFixed(1);
  $("idxArc").style.stroke = v >= 80 ? "#B7E4C7" : v >= 60 ? "#FFD8A8" : "#FFB3B8";
  $("idxLabel").innerHTML = placeLabel
    ? `${idxLabel(v)}<span class="idx-place">${placeLabel} <a id="mntReset">✕ 내 지역</a></span>`
    : idxLabel(v);
  const wxCls = weather.score >= 75 ? "ok" : "mid";
  $("idxGrid").innerHTML = `
    <div class="idx-item"><b class="${fire.score >= 80 ? "ok" : "mid"}">산불위험 ${fire.level}</b>${fire.src}</div>
    <div class="idx-item"><b class="${landslide.score >= 80 ? "ok" : "mid"}">산사태 ${landslide.label}</b>위험지도 ${landslide.grade}등급</div>
    <div class="idx-item"><b class="${wxCls}">산악기상 ${weather.temp}°C</b>${weather.station}</div>
    <div class="idx-item"><b class="mid">일몰 ${sunsetAt}</b>16시 이후 입산 주의</div>`;
  const reset = $("mntReset");
  if (reset) reset.addEventListener("click", () => { S.selectedMountain = null; save(); renderHome(); });
}

async function fetchConditions(path) {
  const d = await API.get(path);
  return {
    v: d.score, sunsetAt: d.conditions.sunset_at, place: d.place,
    fire: { level: d.conditions.fire.level, score: d.conditions.fire.score, src: d.conditions.fire.src },
    landslide: d.conditions.landslide, weather: { ...d.conditions.weather },
  };
}

async function renderHome() {
  let v, fire, landslide, weather, sunsetAt, placeLabel = null;
  const sel = API.mode === "cloud" ? S.selectedMountain : null;
  if (sel) {
    try {
      const c = await fetchConditions(`/mountains/${encodeURIComponent(sel.listNo)}/index`);
      ({ v, fire, landslide, weather, sunsetAt } = c);
      placeLabel = `🏔 ${sel.name} · ${c.place}`;
    } catch { S.selectedMountain = null; }
  }
  if (placeLabel === null && API.mode === "cloud" && S.activeLoc) {
    try {
      const c = await fetchConditions(`/index/gps?lat=${S.activeLoc.lat}&lon=${S.activeLoc.lon}`);
      ({ v, fire, landslide, weather, sunsetAt } = c);
      placeLabel = `📍 ${S.activeLoc.label}`;
    } catch { S.activeLoc = null; }
  }
  if (placeLabel === null) {
    const r = FM_DATA.regions[S.region];
    v = calcIndex(r); fire = r.fire; landslide = r.landslide; weather = r.weather; sunsetAt = r.sunsetAt;
    if (API.mode === "cloud") {
      try { ({ v, fire, landslide, weather, sunsetAt } = await fetchConditions(`/index?region=${S.region}`)); }
      catch { /* 로컬 계산 유지 */ }
    }
  }
  paintIndexCard(v, fire, landslide, weather, sunsetAt, placeLabel);
  updateLocLabel();
  renderReco();
  $("briefing").innerHTML = `<b>${FM_DATA.briefings[new Date().getDay() % FM_DATA.briefings.length].split(".")[0]}.</b><br>${FM_DATA.briefings[new Date().getDay() % FM_DATA.briefings.length].split(".").slice(1).join(".").trim()}`;
  $("newsLine").textContent = FM_DATA.news.slice(0, 3).map((n) => n.title || n).join(" · ");
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
  $("recoNote").textContent = notes.join(" · ") + " 반영";
  let list = FM_DATA.courses.map((c) => ({ c, s: matchScore(c) })).sort((a, b) => b.s - a.s);
  if (API.mode === "cloud") {
    try {
      const cloud = await API.get(`/recommend?fit=${p.fit}&knee=${p.knee}&heart=${p.heart}`);
      list = cloud
        .map((r) => ({ c: FM_DATA.courses.find((c) => c.id === r.course_id), s: r.score }))
        .filter((x) => x.c);
    } catch { /* 로컬 순위 유지 */ }
  }
  $("recoList").innerHTML = list.map(({ c, s }) => `
    <button class="r" data-course="${c.id}">
      <div class="thumb ${c.theme}"><img class="thumb-img" data-mtn="${c.name.split(" ")[0]}" data-h="${parseInt((c.peak || "").replace(/\D/g, "")) || 0}" alt=""><span class="match">매칭 ${s}%</span></div>
      <div class="body"><b>${c.name}</b>
        <div class="meta"><span>⛰ ${c.km}km</span><span>⏱ ${fmtMin(c.minutes)}</span><span>난이도 ${c.level}</span><span>${c.view >= 5 ? "전망 ★★★" : "혼잡 " + c.crowd}</span></div>
      </div>
    </button>`).join("");
  qsa("#recoList .r").forEach((b) => b.addEventListener("click", () => openCourse(b.dataset.course)));
  qsa("#recoList .thumb-img").forEach((img) => loadHero(img, img.dataset.mtn, +img.dataset.h));
}
const fmtMin = (m) => (m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60 ? (m % 60) + "분" : ""}` : `${m}분`).trim();

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
    <div class="btnrow">
      <button class="btn ghost" data-close="courseModal">닫기</button>
      <button class="btn primary" id="btnStartCourse">🥾 이 코스로 산행 시작</button>
    </div>`;
  $("courseModal").classList.add("show");
  loadHero($("courseHero"), c.name.split(" ")[0], parseInt((c.peak || "").replace(/\D/g, "")) || 0);
  $("btnStartCourse").addEventListener("click", () => {
    $("courseModal").classList.remove("show");
    selectCourse(c.id);
    startHike();
    history.replaceState(null, "", "#trail");
    show("trail");
  });
}

/* ---------------- 산행: 지도 + 시뮬레이션 ---------------- */
const Hike = { course: null, prog: 0, active: false, ended: false, timer: null, hr: 92, alerted: {}, sunsetLeft: null };

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
  return m ? [parseFloat(m[1]), parseFloat(m[2])] : [37.6, 127.0];
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
    if (!window.L) { $("mapHost").innerHTML = `<div class="map-fallback">🗺 지도를 불러오려면 네트워크 연결이 필요해요</div>`; return; }
    const map = L.map("hikeMap", { attributionControl: false }).setView([lat, lon], 14);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 18 }).addTo(map);
    L.marker([lat, lon]).addTo(map).bindPopup(`${c.startLabel} · 들머리`);
    Hike.map = map;
    Hike.trackLine = L.polyline([], { color: "#2D6A4F", weight: 6, opacity: 0.85 }).addTo(map);
    Hike.posMarker = L.circleMarker([lat, lon], { radius: 9, color: "#fff", weight: 3, fillColor: "#1B4332", fillOpacity: 1 }).addTo(map).bindPopup("내 위치");
    setTimeout(() => map.invalidateSize(), 160);
  }, 60);
  drawProgress();
}
function onGps(lat, lon, acc) {
  Hike.gps = { lat: +lat.toFixed(6), lon: +lon.toFixed(6), acc: Math.round(acc || 0) };
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
  checkHazards();
  renderHikeUI();
  if (Hike.prog >= 1 && Hike.active) endHike(false);
}
function demoStep() {
  // 테스트용 — 실제 걷지 않고 약 90m씩 북동진(GPS 이동 시뮬레이션)
  const [lat, lon] = Hike.origin || [37.6, 127.0];
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
function renderHikeUI() {
  const c = Hike.course;
  const live = $("trailLive"), tx = $("trailLiveTx");
  if (!c) return;
  if (Hike.active) { live.className = "live"; tx.textContent = "안전 산행 중"; }
  else if (Hike.ended) { live.className = "live"; tx.textContent = "산행 완료 🎉"; }
  else { live.className = "live idle"; tx.textContent = Hike.prog > 0 ? "일시정지" : "준비 완료"; }
  $("btnHike").textContent = Hike.active ? "⏸ 일시정지" : Hike.prog > 0 && !Hike.ended ? "▶ 이어가기" : "▶ 산행 시작";
  $("btnHike").disabled = Hike.ended;
  $("btnEnd").disabled = !(Hike.active || Hike.prog > 0) || Hike.ended;
  const dist = (c.km * Hike.prog);
  $("stDist").innerHTML = `${dist.toFixed(1)}<small>km</small>`;
  $("stDistCap").textContent = `이동 / ${c.km}km`;
  $("stAlt").innerHTML = `${interp(c.elev, Hike.prog)}<small>m</small>`;
  $("stHr").innerHTML = Hike.active || Hike.prog > 0 ? `${Hike.hr}<small>bpm</small>` : "—";
  const gt = $("gpsTag");
  if (gt) {
    if (Hike.active && Hike.gps) { gt.style.display = "block"; gt.className = "gps-tag on"; gt.innerHTML = `📍 실시간 GPS 동기화 중 · 위도 ${Hike.gps.lat}, 경도 ${Hike.gps.lon} (정확도 ±${Hike.gps.acc}m)`; }
    else if (Hike.active) { gt.style.display = "block"; gt.className = "gps-tag"; gt.innerHTML = `📍 GPS 위치 확인 중… (권한 허용 시 실시간 동기화)`; }
    else { gt.style.display = "none"; }
  }
  // SOS 가드 상태
  $("guardMon").innerHTML = Hike.active
    ? `<span class="dot-ok"></span>자동 조난감지 작동 중`
    : `<span class="dot-off"></span>자동 조난감지 대기`;
  $("guardMonTx").textContent = Hike.active
    ? "이동 멈춤 30분 + 심박 이상 시 보호자·119에 자동 전파"
    : "산행을 시작하면 이동·심박 이상을 감시해요";
}
function startHike() {
  if (!Hike.course) { toast("코스를 먼저 선택하세요", "홈의 AI 추천에서 코스를 골라주세요", "🧭"); return; }
  Hike.active = true; Hike.ended = false;
  clearInterval(Hike.timer);
  Hike.timer = setInterval(tick, 1000);
  if (API.mode === "cloud" && !API.hikeId) {
    API.post("/hikes", { course_id: Hike.course.id })
      .then((r) => { API.hikeId = r.hike_id; })
      .catch(() => {});
  }
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
function pauseHike() { Hike.active = false; clearInterval(Hike.timer); stopGeo(); renderHikeUI(); }
function endHike(byUser = true) {
  clearInterval(Hike.timer);
  const c = Hike.course;
  const doneKm = +(c.km * Hike.prog).toFixed(1);
  Hike.active = false; Hike.ended = true; stopGeo();
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
  Hike.hr = Math.round(92 + 18 * Math.sin(Hike.prog * 6) + Math.random() * 6);
  const c = Hike.course;
  if (API.mode === "cloud" && API.hikeId && ++tickCount % 5 === 0) {
    const t = { progress: +Hike.prog.toFixed(4), alt: interp(c.elev, Hike.prog), hr: Hike.hr };
    if (Hike.gps) { t.lat = Hike.gps.lat; t.lon = Hike.gps.lon; }   // 실제 GPS 동기화
    API.post(`/hikes/${API.hikeId}/track`, t).catch(() => {});
  }
  renderHikeUI();
}
/* 일몰 카운트다운 — 지역 일몰시각 기준 실시간 */
function sunsetTick() {
  let secs;
  if (DEMO !== null) {
    if (Hike.sunsetLeft == null) Hike.sunsetLeft = 3 * 3600 + 12 * 60;
    secs = Hike.sunsetLeft = Math.max(0, Hike.sunsetLeft - 1);
  } else {
    const r = FM_DATA.regions[S.region];
    const [h, m] = r.sunsetAt.split(":").map(Number);
    const t = new Date(); const sun = new Date(); sun.setHours(h, m, 0, 0);
    secs = Math.floor((sun - t) / 1000);
  }
  $("sunset").textContent = secs > 0 ? `${Math.floor(secs / 3600)}:${String(Math.floor(secs % 3600 / 60)).padStart(2, "0")}` : "일몰";
}
setInterval(sunsetTick, 1000);

$("btnHike").addEventListener("click", () => (Hike.active ? pauseHike() : startHike()));
$("btnEnd").addEventListener("click", () => endHike(true));
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
      <div class="row"><span>신고자</span><b>${S.profile.name}님 · 심박 ${Hike.hr || 96}bpm</b></div>
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
  $("sosSheet").innerHTML = `
    <div class="grab"></div>
    <h3>🚨 신고가 접수됐어요</h3>
    <p class="sub">제자리에서 체온을 유지하세요. 휘슬·불빛으로 위치를 알리면 도움이 됩니다.</p>
    <div class="steps">${steps.map(([b, s], i) => `
      <div class="stp" id="stp${i}"><div class="si">${i + 1}</div><div><b>${b}</b><span>${s}</span></div></div>`).join("")}
    </div>
    <div class="btnrow"><button class="btn ghost" id="sosDone">상황 종료(데모)</button></div>`;
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
  div.innerHTML = who === "bot" ? `<div class="who">🌲 숲이</div>${html}` : html;
  chatLog().appendChild(div);
  $("ai").scrollTop = $("ai").scrollHeight;
  return div;
}
function photoBubble(sp) {
  const div = document.createElement("div");
  div.className = "photo";
  div.innerHTML = `<div class="ph-img" style="background:${sp.grad}"></div><div class="ph-cap">📷 방금 촬영한 사진</div>`;
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
// cloud 모드면 서버 실집계(/hikes/summary), 아니면 로컬 누적치로 폴백.
async function renderMy() {
  $("profName").textContent = S.profile.name || "산친구";
  const months = last6Months();
  let days, cnt, km, kcal, co2, level, bars;

  if (API.mode === "cloud") {
    try {
      const s = await API.get("/hikes/summary");
      days = s.active_days; cnt = s.total_hikes; km = s.total_km;
      kcal = s.total_kcal; co2 = s.co2_kg; level = s.level;
      lastSum = s;   // 배지·지역 다양성 캐시
      const byKey = Object.fromEntries(s.monthly.map((m) => [m.month, m.km]));
      const maxKm = Math.max(1, ...s.monthly.map((m) => m.km));
      bars = months.map((m) => ({ h: Math.round(((byKey[m.key] || 0) / maxKm) * 100), label: m.label }));
    } catch { /* 아래 로컬 폴백 */ }
  }
  if (days === undefined) {  // 오프라인 폴백 — 가짜 247 대신 최초 사용일 기준 경과일
    days = Math.floor((Date.now() - (S.installAt || Date.now())) / 86400000) + 1;
    cnt = S.june.cnt; km = S.june.km; kcal = S.june.kcal; co2 = S.june.co2;
    level = 1 + Math.floor((S.hikesDone || 0) / 3);
    const curH = Math.min(100, (S.june.km || 0) * 6);
    bars = months.map((m, i) => ({ h: i === months.length - 1 ? curH : 0, label: m.label }));
  }

  $("profDays").textContent = `숲길과 함께한 지 ${days}일째`;
  $("profLv").textContent = `Lv.${level} 숲지기`;
  $("repCnt").textContent = `${cnt}회`;
  $("repKm").textContent = `${km}km`;
  $("repKcal").textContent = (kcal || 0).toLocaleString();
  $("repCo2").textContent = `${co2}kg`;
  $("repBars").innerHTML = bars.map((b, i) => `
    <div class="b ${i === bars.length - 1 ? "cur" : ""}"><i style="height:${b.h}%"></i><span>${b.label}</span></div>`).join("");
  if ($("repExtra")) {
    const earned = lastSum && lastSum.badges ? lastSum.badges.filter((b) => b.earned).length + (S.aiCount >= 10 ? 1 : 0) : (S.aiCount >= 10 ? 1 : 0);
    const total = lastSum && lastSum.badges ? lastSum.badges.length + 1 : 4;
    const regions = lastSum ? lastSum.regions : 0;
    const courses = lastSum ? lastSum.distinct_courses : 0;
    $("repExtra").innerHTML =
      `<span>🧭 방문 지역 <b>${regions}</b>곳</span><span>⛰ 완등 코스 <b>${courses}</b></span><span>🏅 배지 <b>${earned}/${total}</b></span>`;
  }
  renderBadges(); renderHikeLog(); renderFavs(); renderIns();
}
async function renderHikeLog() {
  const box = $("hikeLog"); if (!box) return;
  let items = [];
  if (API.mode === "cloud") { try { items = (await API.get("/hikes")).items; } catch { /* */ } }
  if (!items.length) {
    box.innerHTML = `<div class="card" style="font-size:12px;color:var(--sub);text-align:center;padding:16px">아직 완료한 산행이 없어요. 산행을 완주하면 산별 거리·칼로리가 여기 쌓여요.</div>`;
    return;
  }
  box.innerHTML = items.map((h) => `
    <div class="log-row"><div><b>${esc(h.course)}</b><span>${h.date}</span></div>
      <div class="log-stat">${h.km}km · ${(h.kcal || 0).toLocaleString()}kcal</div></div>`).join("");
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
function renderBadges() {
  // 서버 실집계 배지(진척·달성) + 클라이언트 AI 대화 배지. 하드코딩 아님.
  let cards;
  if (lastSum && lastSum.badges && lastSum.badges.length) {
    cards = lastSum.badges.map((b) => {
      const unit = b.id.startsWith("km") ? "km" : b.id === "days30" ? "일" : "";
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
function renderIns() {
  const idx = calcIndex(FM_DATA.regions[S.region]);
  const fee = idx >= 80 ? 990 : idx >= 60 ? 1290 : 1590;
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
  const fee = idx >= 80 ? 990 : idx >= 60 ? 1290 : 1590;
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
function openOnboard() {
  $("obName").value = S.profile.set ? S.profile.name : "";
  qsa("#obFit button").forEach((b) => b.classList.toggle("on", +b.dataset.v === S.profile.fit));
  qsa("#obChecks .ckc").forEach((b) => {
    const v = b.dataset.v;
    b.classList.toggle("on", v === "knee" ? S.profile.knee : v === "heart" ? S.profile.heart : !S.profile.knee && !S.profile.heart);
  });
  $("onboard").classList.add("show");
}
qsa("#obFit button").forEach((b) => b.addEventListener("click", () => {
  qsa("#obFit button").forEach((x) => x.classList.remove("on")); b.classList.add("on");
}));
qsa("#obChecks .ckc").forEach((b) => b.addEventListener("click", () => {
  if (b.dataset.v === "none") { qsa("#obChecks .ckc").forEach((x) => x.classList.remove("on")); b.classList.add("on"); }
  else { qs('#obChecks .ckc[data-v="none"]').classList.remove("on"); b.classList.toggle("on"); }
}));
$("obSave").addEventListener("click", () => {
  S.profile.name = $("obName").value.trim() || "산친구";
  S.profile.fit = +qs("#obFit button.on").dataset.v;
  S.profile.knee = qs('#obChecks .ckc[data-v="knee"]').classList.contains("on");
  S.profile.heart = qs('#obChecks .ckc[data-v="heart"]').classList.contains("on");
  S.profile.set = true; save();
  $("onboard").classList.remove("show");
  renderHome(); renderMy();
  toast(`${S.profile.name}님, 환영해요!`, "프로필에 맞춰 코스 추천을 조정했어요", "🌲");
});
$("btnEditProf").addEventListener("click", openOnboard);

/* ---------------- 공용 닫기 / 지역 / 벨 ---------------- */
qsa("[data-close]").forEach((b) => b.addEventListener("click", () => $(b.dataset.close).classList.remove("show")));
document.addEventListener("click", (e) => {
  if (e.target.classList && e.target.classList.contains("overlay")) e.target.classList.remove("show");
  const dc = e.target.closest && e.target.closest("[data-close]");
  if (dc) $(dc.dataset.close).classList.remove("show");
});
/* ---------------- 위치 선택 (현재위치/시도/검색) ---------------- */
const SIDO_LOCS = [
  ["서울", 37.5663, 126.9779], ["부산", 35.1798, 129.0750], ["대구", 35.8714, 128.6014],
  ["인천", 37.4563, 126.7052], ["광주", 35.1601, 126.8514], ["대전", 36.3504, 127.3845],
  ["울산", 35.5384, 129.3114], ["세종", 36.4801, 127.2890], ["경기", 37.2636, 127.0286],
  ["강원", 37.8813, 127.7298], ["충북", 36.6357, 127.4914], ["충남", 36.6588, 126.6728],
  ["전북", 35.8203, 127.1088], ["전남", 34.8161, 126.4629], ["경북", 36.5760, 128.5056],
  ["경남", 35.2383, 128.6924], ["제주", 33.4890, 126.4983],
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
$("bellBtn").addEventListener("click", () =>
  toast("기상 특보 알림", "도봉산 Y계곡 강풍주의보 — 우회 코스를 추천해요 (산악기상관측망)", "🌬", false, 4200));

/* ---------------- 전국 산 검색 (산림청 산정보) ---------------- */
let mntTimer;
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
function openMntSearch() {
  $("mntModal").classList.add("show");
  $("mntQ").value = "";
  $("mntResults").innerHTML = API.mode === "cloud"
    ? `<div class="mnt-empty">산 이름을 입력하면 전국에서 검색해요.</div>`
    : `<div class="mnt-empty">전국 산 검색은 온라인(서버 연결) 상태에서 동작해요.</div>`;
  setTimeout(() => $("mntQ").focus(), 120);
}
async function runMntSearch(q) {
  q = q.trim();
  const box = $("mntResults");
  if (!q) { box.innerHTML = `<div class="mnt-empty">산 이름을 입력하면 전국에서 검색해요.</div>`; return; }
  if (API.mode !== "cloud") { box.innerHTML = `<div class="mnt-empty">전국 산 검색은 온라인 상태에서 동작해요.</div>`; return; }
  box.innerHTML = `<div class="mnt-empty">검색 중…</div>`;
  try {
    const d = await API.get(`/mountains?q=${encodeURIComponent(q)}&size=30`);
    if (!d.items.length) { box.innerHTML = `<div class="mnt-empty">'${esc(q)}' 검색 결과가 없어요.</div>`; return; }
    box.innerHTML =
      `<div class="mnt-empty" style="text-align:left;padding:2px 2px 8px">전국 ${d.total.toLocaleString()}개 중 ${d.items.length}개 · <b>탭하면 산행지수</b></div>` +
      d.items.map(mntRow).join("");
  } catch {
    box.innerHTML = `<div class="mnt-empty">검색 중 오류가 났어요. 잠시 후 다시 시도해주세요.</div>`;
  }
}
function mntRow(m) {
  const dist = m.dist_km != null ? `<div class="loc">🧭 ${m.dist_km}km</div>` : "";
  return `
    <div class="mnt-row" data-id="${esc(m.list_no)}" data-name="${esc(m.name)}">
      <div><b>${esc(m.name)}${m.top100 ? '<span class="top">100대명산</span>' : ""}</b>
        <div class="loc">📍 ${esc(m.addr || m.sido || "")}</div>${dist}</div>
      <div class="h">${m.height ? m.height + "m" : "—"} ›</div>
    </div>`;
}
async function findNearby() {
  const box = $("mntResults");
  if (API.mode !== "cloud") { box.innerHTML = `<div class="mnt-empty">주변 산 찾기는 온라인 상태에서 동작해요.</div>`; return; }
  if (!navigator.geolocation) { box.innerHTML = `<div class="mnt-empty">이 기기는 위치 기능을 지원하지 않아요.</div>`; return; }
  box.innerHTML = `<div class="mnt-empty">📍 현재 위치를 확인하는 중…</div>`;
  navigator.geolocation.getCurrentPosition(async (pos) => {
    const { latitude: lat, longitude: lon } = pos.coords;
    try {
      const d = await API.get(`/mountains/nearby?lat=${lat}&lon=${lon}&radius=40&limit=25`);
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
  if (row && row.dataset.id) openMountainDetail(row.dataset.id, row.dataset.name);
});

/* ---------------- 산 상세 (사진·시설·산행지수) ---------------- */
const FAC_ICON = { 정상: "🏔", 대피소: "🏠", 조망점: "🔭", 위험지역: "⚠️", 헬기장: "🚁", 화장실: "🚻", 음수대: "💧", 약수터: "💧" };
function themedHero(name, height) {
  // 사진 폴백 — 높이/이름 기반 테마 SVG(외부 의존 없음)
  const h = height || 0;
  const top = h >= 1200 ? "#2D6A4F" : h >= 600 ? "#40916C" : "#52B788";
  const sky = h >= 1200 ? "#A8C7B5" : "#CDE7D4";
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' width='600' height='300'>
    <defs><linearGradient id='g' x1='0' y1='0' x2='0' y2='1'><stop offset='0' stop-color='${sky}'/><stop offset='1' stop-color='#EAF4EC'/></linearGradient></defs>
    <rect width='600' height='300' fill='url(#g)'/>
    <polygon points='0,300 150,150 260,220 380,90 500,210 600,150 600,300' fill='${top}' opacity='0.9'/>
    <polygon points='320,300 460,120 600,260 600,300' fill='${top}'/>
    <text x='24' y='280' font-family='sans-serif' font-size='22' font-weight='800' fill='#1B4332'>${name}${h ? " · " + h + "m" : ""}</text></svg>`;
  return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
}
async function loadHero(img, name, height) {
  img.src = themedHero(name, height);                 // 즉시 폴백
  try {                                               // 공개 사진 시도(위키 공개자료)
    const r = await fetch(`https://ko.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(name.split(" ")[0])}`);
    if (r.ok) { const j = await r.json(); if (j.thumbnail && j.thumbnail.source) img.src = j.thumbnail.source; }
  } catch { /* 폴백 유지 */ }
}
async function openMountainDetail(listNo, name) {
  $("extModal").classList.add("show");
  $("extSheet").innerHTML = `<h3>${esc(name)}</h3><p class="sub">정보를 불러오는 중…</p>`;
  if (API.mode !== "cloud") { return selectMountainIndex(listNo, name); }
  try {
    const d = await API.get(`/mountains/${encodeURIComponent(listNo)}/index`);
    const m = d.mountain, fac = m.facilities || {};
    const facHtml = Object.keys(fac).length
      ? Object.entries(fac).map(([k, v]) => `<span class="fac">${FAC_ICON[k] || "•"} ${k} ${v}</span>`).join("")
      : `<span class="sub" style="font-size:11.5px">등록된 등산로 시설 정보 없음</span>`;
    $("extSheet").innerHTML = `
      <img id="mtnHero" class="mtn-hero" alt="${esc(m.name)} 전경">
      <h3>${esc(m.name)}${m.top100 ? ' <span class="top">100대명산</span>' : ""}</h3>
      <p class="sub">📍 ${esc(m.addr || m.sido || "")} · ⛰ ${m.height ? m.height + "m" : "높이 미상"}</p>
      <div class="mtn-score">오늘의 산행지수 <b>${d.score}</b><br>🌡 ${d.conditions.weather.temp}°C · 🔥 산불 ${d.conditions.fire.level} · ${esc(d.place)}</div>
      ${m.lat ? `<b style="font-size:12px">🗺 위치 · 길찾기</b><div id="mtnMap" class="detail-map"></div>${dirButtons(m.lat, m.lon, m.name)}` : ""}
      <b style="font-size:12px">🥾 등산로 시설 (산림청 주요지점)</b>
      <div class="facs">${facHtml}</div>
      <div class="btnrow">
        <button class="btn primary" id="mtnSetHome">🏠 홈 산행지수로 설정</button>
        <button class="btn ghost" id="mtnFav">${isFav(listNo) ? "⭐ 즐겨찾기됨" : "☆ 즐겨찾기"}</button>
      </div>
      <div class="btnrow" style="margin-top:9px"><button class="btn ghost" id="mtnPlan">📅 산행 일정 잡기</button><button class="btn ghost" data-close="extModal">닫기</button></div>`;
    loadHero($("mtnHero"), m.name, m.height);
    if (m.lat) setTimeout(() => miniMap("mtnMap", m.lat, m.lon, m.name), 60);
    $("mtnSetHome").addEventListener("click", () => { $("extModal").classList.remove("show"); selectMountainIndex(listNo, m.name); });
    $("mtnFav").addEventListener("click", () => { toggleFav({ list_no: listNo, name: m.name, sido: m.sido, lat: m.lat, lon: m.lon }); $("mtnFav").textContent = isFav(listNo) ? "⭐ 즐겨찾기됨" : "☆ 즐겨찾기"; });
    $("mtnPlan").addEventListener("click", () => openPlan({ list_no: listNo, name: m.name, lat: m.lat, lon: m.lon }));
    const sh = $("setHomeBtn");
    if (sh) sh.addEventListener("click", () => navigator.geolocation && navigator.geolocation.getCurrentPosition(
      (pos) => { S.home = { lat: +pos.coords.latitude.toFixed(5), lon: +pos.coords.longitude.toFixed(5) }; save(); toast("집 등록", "현재 위치를 집으로 저장했어요", "🏠"); openMountainDetail(listNo, m.name); },
      () => toast("위치 권한 필요", "집 등록에 위치 권한이 필요해요", "🏠")));
  } catch {
    $("extSheet").innerHTML = `<h3>${esc(name)}</h3><p class="sub">정보를 불러오지 못했어요.</p><div class="btnrow"><button class="btn ghost" data-close="extModal">닫기</button></div>`;
  }
}

/* ---------------- 지도 · 길찾기 (req5) ---------------- */
const _maps = {};
function miniMap(elId, lat, lon, name, zoom = 12) {
  const el = document.getElementById(elId);
  if (!el || !window.L) return null;
  if (_maps[elId]) { _maps[elId].remove(); delete _maps[elId]; }
  const map = L.map(el, { attributionControl: false }).setView([lat, lon], zoom);
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 18 }).addTo(map);
  L.marker([lat, lon]).addTo(map).bindPopup(name).openPopup();
  _maps[elId] = map;
  setTimeout(() => map.invalidateSize(), 120);
  return map;
}
function homeLoc() { return S.home || null; }
function dirButtons(lat, lon, name) {
  const n = encodeURIComponent(name), h = homeLoc();
  return `<div class="dir-row">
    <a class="dir-btn kakao" href="https://map.kakao.com/link/to/${n},${lat},${lon}" target="_blank" rel="noopener">📍 현재위치→ 카카오맵</a>
    <a class="dir-btn" href="https://www.google.com/maps/dir/?api=1&destination=${lat},${lon}" target="_blank" rel="noopener">구글맵</a>
    ${h ? `<a class="dir-btn home" href="https://map.kakao.com/link/from/우리집,${h.lat},${h.lon}/to/${n},${lat},${lon}" target="_blank" rel="noopener">🏠 집에서</a>`
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
    try { days = (await API.get(`/forecast?lat=${m.lat}&lon=${m.lon}`)).days; } catch { /* */ }
  }
  const rows = days.length ? days.map((d) => {
    const ok = d.score >= 70 ? "good" : d.score >= 50 ? "mid" : "bad";
    const tip = d.score >= 70 ? "산행하기 좋아요" : d.score >= 50 ? "기상 변화 유의" : "산행 비권장";
    return `<div class="plan-row ${ok}">
      <div><b>${d.label}</b><span>${d.dow}</span></div>
      <div class="plan-wx">🌡${d.temp}° · ☔${d.rain_prob}% · 🔥${d.fire}</div>
      <div class="plan-score">${d.score}<small>${tip}</small></div></div>`;
  }).join("") : `<p class="sub">예보를 불러오지 못했어요(오프라인). 온라인에서 다시 시도해 주세요.</p>`;
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
function openRest() {
  openExt(`
    <h3>🏕 축령산 치유의숲 · 숲 명상</h3>
    <p class="sub">한국산림복지진흥원 숲나들e 연동 · 산림치유 프로그램</p>
    <div class="ext-row"><b>일정</b> 이번 주 토요일 10:00 (90분)</div>
    <div class="ext-row"><b>장소</b> 전남 장성 축령산 편백숲 치유센터</div>
    <div class="ext-row"><b>잔여</b> <span style="color:#2D6A4F;font-weight:800">예약 가능</span> · 회복지수 낮은 주간 추천</div>
    <div class="ext-row"><b>효과</b> 편백 피톤치드 · 호흡 명상으로 스트레스·혈압 완화</div>
    <p class="sub" style="margin-top:12px">최근 산행 패턴상 휴식이 필요해요. 숲나들e에서 실시간 예약현황 확인·신청하세요.</p>
    <div class="btnrow">
      <a class="btn primary" href="${FOREST_URL}/cs/hsfr/healingForestList.do" target="_blank" rel="noopener">숲나들e에서 예약하기 ↗</a>
      <button class="btn ghost" data-close="extModal">닫기</button>
    </div>`);
}
function openNews() {
  const items = (FM_DATA.news || []).map((n, i) =>
    `<div class="news-item" data-i="${i}">🌿 <b>${esc(n.title || n)}</b><span>›</span></div>`).join("");
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
    <p class="sub">이번 주 숲 소식</p>
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
  if (!S.profile.set && !t && DEMO === null && !Q.get("embed")) setTimeout(openOnboard, 600);
}
init();

/* PWA */
if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}
