(function initHeroImages(root, factory) {
  function heroProxyUrl(name, height = 0) {
    const pageName = String(name || "").trim().split(/\s+/)[0] || "산";
    const h = Math.max(0, Math.round(Number(height) || 0));
    return `/api/v1/mountain-hero?name=${encodeURIComponent(pageName)}&height=${encodeURIComponent(h)}`;
  }

  const api = factory(root, heroProxyUrl);
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.ForestMateHeroImages = api;
})(typeof globalThis === "object" ? globalThis : this, function heroImagesFactory(root, heroProxyUrl) {
  const HTML_ENTITIES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => HTML_ENTITIES[c]);

  function themedHero(name, height = 0) {
    const h = Number(height) || 0;
    const title = esc(name || "산");
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

  async function loadHeroImage(img, name, height) {
    const fallback = themedHero(name, height);
    if (!img) return fallback;

    const restoreFallback = () => {
      img.onerror = null;
      img.src = fallback;
    };
    img.onerror = restoreFallback;
    img.src = fallback;

    const proxied = heroProxyUrl(name, height);
    img.onerror = restoreFallback;
    img.src = proxied;
    return proxied;
  }

  return { themedHero, heroProxyUrl, loadHeroImage };
});
