/* 숲길동무 오프라인 서비스워커 — 음영지역 대비 전체 자산 캐시 (네트워크 우선) */
const CACHE = "forestmate-v7";
const ASSETS = [
  "./index.html", "./app.css", "./app.js", "./data.js",
  "./dashboard.html", "./home.html",
  "./manifest.json", "./icon-192.png", "./icon-512.png",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

/* Web Push 수신 → 알림 표시 */
self.addEventListener("push", (e) => {
  let d = {};
  try { d = e.data.json(); } catch { d = { body: e.data && e.data.text() }; }
  e.waitUntil(self.registration.showNotification(d.title || "숲길동무", {
    body: d.body || "", icon: "./icon-192.png", badge: "./icon-192.png",
    data: { url: d.url || "/" }, vibrate: [80, 40, 80],
  }));
});
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.url) || "/";
  e.waitUntil(clients.matchAll({ type: "window" }).then((ws) => {
    for (const w of ws) { if ("focus" in w) return w.focus(); }
    return clients.openWindow(url);
  }));
});

/* 온라인: 항상 최신 / 오프라인(음영지역): 캐시 폴백 */
self.addEventListener("fetch", (e) => {
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
