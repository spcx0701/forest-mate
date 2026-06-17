/* 숲길동무 오프라인 서비스워커 — 음영지역 대비 전체 자산 캐시 (네트워크 우선) */
const CACHE = "forestmate-v35";
const ASSETS = [
  "./index.html", "./vendor/leaflet/leaflet.css?v=20260618-local-leaflet", "./vendor/leaflet/leaflet.js?v=20260618-local-leaflet",
  "./vendor/leaflet/images/marker-icon.png", "./vendor/leaflet/images/marker-icon-2x.png", "./vendor/leaflet/images/marker-shadow.png", "./vendor/leaflet/images/layers.png", "./vendor/leaflet/images/layers-2x.png",
  "./app.css?v=20260618-modal-bounce", "./app.js?v=20260618-local-leaflet", "./condition-details.js?v=20260617-leaflet-map", "./hero-images.js?v=20260618-hero-proxy", "./data.js",
  "./dashboard.html", "./home.html", "./home.i18n.js?v=20260618-civic-badges",
  "./manifest.json", "./icon-192.png", "./icon-512.png",
];

globalThis.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)));
  globalThis.skipWaiting();
});

globalThis.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => globalThis.clients.claim())
  );
});

/* Web Push 수신 → 알림 표시 */
globalThis.addEventListener("push", (e) => {
  let d = {};
  try { d = e.data.json(); } catch { d = { body: e.data?.text() }; }
  e.waitUntil(globalThis.registration.showNotification(d.title || "숲길동무", {
    body: d.body || "", icon: "./icon-192.png", badge: "./icon-192.png",
    data: { url: d.url || "/" }, vibrate: [80, 40, 80],
  }));
});
globalThis.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const url = e.notification.data?.url || "/";
  e.waitUntil(globalThis.clients.matchAll({ type: "window" }).then((ws) => {
    for (const w of ws) { if ("focus" in w) return w.focus(); }
    return globalThis.clients.openWindow(url);
  }));
});

/* 네트워크 연결 시 항상 최신 / 음영지역에서는 캐시 폴백 */
globalThis.addEventListener("fetch", (e) => {
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request))
  );
});
