(() => {
  const doc = globalThis.document;
  const storageKey = "forestmate.home.lang";
  const urls = {
    ko: {
      local: "home.html?lang=ko",
      absolute: "https://forestmate.onrender.com/home.html?lang=ko",
      currency: "KRW",
    },
    en: {
      local: "home.html?lang=en",
      absolute: "https://forestmate.onrender.com/home.html?lang=en",
      currency: "USD",
    },
  };

  const enTranslation = {
    meta: {
      title: "ForestMate - Forest public data and AI hiking safety platform",
      description: "ForestMate uses forest public data and AI to predict hiking risks before a trip and detect emergencies faster on the trail.",
      imageAlt: "ForestMate mobile app home screen",
    },
    schema: {
      name: "ForestMate",
      alternateName: "숲길동무 ForestMate",
      description: "ForestMate uses forest public data and AI to predict hiking risks before a trip and detect emergencies faster on the trail.",
    },
    brand: {
      name: "ForestMate",
      logoAlt: "ForestMate logo",
    },
    nav: {
      features: "Features",
      download: "Install",
      b2g: "Operations",
      data: "Public Data",
      github: "GitHub",
      cta: "Open App Demo",
      langLabel: "Language selector",
    },
    hero: {
      badge: "🏆 <a href=\"https://www.kofpi.or.kr/notice/notice_01view.do?bb_seq=12456\" target=\"_blank\" rel=\"noopener\">2026 Forest Public Data and AI Startup Competition</a> - Product and Service Development track · <a href=\"https://www.data.go.kr/tcs/puc/selectPublicUseCaseView.do?prcuseCaseSn=1077408\" target=\"_blank\" rel=\"noopener\">data.go.kr use case</a> · <a href=\"https://app.civictech.guide/p/forestmate/r/recQXWFIHBTDJLoZK\" target=\"_blank\" rel=\"noopener\">Civic Tech Guide</a>",
      title: "Beyond records:<br><em>trail data that</em><br>protects lives.",
      lead: "ForestMate combines 10 forest public-data sources with 5 AI engines to support personalized route choice, risk alerts, automatic distress detection, and public-sector response.",
      mobileDemo: "📱 Mobile app demo",
      dashboard: "🖥 Operations dashboard",
      github: "GitHub repository",
      civicGuide: "Civic Tech Guide",
      phoneAlt: "ForestMate app home screen with today's hiking index and recommended routes",
      riskTag: "⚠️ Rockfall risk 300 m ahead - detour ready",
      distressTag: "🛡 Auto distress detection active",
    },
    download: {
      label: "App install links",
      playAria: "Open the Google Play launch page",
      fdroidAria: "Open the F-Droid package page",
      obtainiumAria: "Add ForestMate with Obtainium",
      apkAria: "Download the Android APK from the latest GitHub release",
    },
    stats: {
      hikers: {
        value: "32.29M",
        label: "people hike or visit forest trails at least monthly",
        source: "Korea Forest Service public awareness survey (2022)",
      },
      rescue: {
        value: "10,443 / yr",
        label: "mountain rescue operations, with 325 deaths over 3 years",
        source: "National Fire Agency rescue statistics (2022-2024)",
      },
      fusion: {
        value: "10 data types x 5 AI engines",
        label: "real-time fusion of forest public data",
        source: "KFS, NIFoS, NFA, MOIS, KMA",
      },
    },
    features: {
      kick: "FEATURES",
      title: "Six safety features for every moment of a hike",
      subtitle: "ForestMate is built on forest public data, turning separate datasets into one route-aware safety experience.",
      items: [
        ["Today's hiking index", "Forest-fire danger, landslide grade, mountain weather, and sunset timing are fused into a simple 0-100 readiness score.", "NIFoS API"],
        ["AI route recommendations", "Personalized suggestions account for fitness, injury history, crowding, and more conservative choices for beginners and older hikers.", "Hybrid filtering"],
        ["Real-time risk alerts", "Landslide maps, accident history, and rainfall are fused every 100 meters to warn before dangerous segments and suggest detours.", "Risk-fusion score"],
        ["Auto distress detection & SOS", "Movement pauses and heart-rate anomalies can trigger a confirmation flow and share a national grid location with guardians or responders.", "Time-series anomaly detection"],
        ["AI forest interpreter, Soopi", "Photo-based plant and mushroom identification plus multilingual RAG explanations for local and international hikers.", "Korea National Arboretum corpus"],
        ["Forest healing connection", "Hiking reports and wellness signals can recommend nearby healing forest and recreation programs for recovery.", "Soopnarae integration"],
      ],
    },
    b2g: {
      kick: "FOR GOVERNMENT",
      title: "Municipalities and fire agencies see the same operating picture",
      body: "Anonymized hiking data (k>=50) flows back into the operations dashboard: real-time hiker distribution, segment risk, SOS incident tracking, and demand forecasting. The goal is to shorten rescue golden time by 30%.",
      cta: "🖥 Open dashboard demo",
      items: [
        "<b>Live monitoring</b> - SOS and suspected-distress events, responder-base matching, helicopter routes",
        "<b>Risk prevention</b> - automatic segment alerts and bulk detour guidance",
        "<b>Policy evidence</b> - prioritization data for trail maintenance and safety facilities",
        "<b>Adoption model</b> - annual institutional SaaS license; two pilot projects under discussion",
      ],
    },
    data: {
      kick: "PUBLIC DATA",
      title: "Data sources",
      body: "<b>Forest data</b> - Korea Forest Service trail geospatial data · National Institute of Forest Science forest-fire danger forecast and mountain weather observations · Korea Forest Service landslide risk maps · Korea National Arboretum species knowledge · Korea Forest Welfare Institute Soopnarae · Korea Forestry Promotion Institute forest big-data exchange<br><b>Cross-domain data</b> - National Fire Agency mountain rescue activity data · Ministry of the Interior and Safety national grid reference system · Korea Meteorological Administration short-term forecast OpenAPI<br>Personal location data is processed only within device and guardian consent boundaries. Aggregated statistics are used after k-anonymization (k>=50) for location-privacy compliance.",
    },
    footer: {
      intro: "© 2026 ForestMate - submitted to the <a href=\"https://www.kofpi.or.kr/notice/notice_01view.do?bb_seq=12456\" target=\"_blank\" rel=\"noopener\">2026 Forest Public Data and AI Startup Competition</a> · <a href=\"https://app.civictech.guide/p/forestmate/r/recQXWFIHBTDJLoZK\" target=\"_blank\" rel=\"noopener\">Civic Tech Guide</a>",
      github: "GitHub repository",
      contact: "Contact: spcx0701@gmail.com",
    },
  };

  const resources = { en: { translation: enTranslation } };
  let bootLanguage = "ko";

  const textBindings = [
    [".brand-name", "brand.name"],
    [".navlinks a:nth-child(1)", "nav.features"],
    [".navlinks a:nth-child(2)", "nav.download"],
    [".navlinks a:nth-child(3)", "nav.b2g"],
    [".navlinks a:nth-child(4)", "nav.data"],
    [".navlinks a:nth-child(5)", "nav.github"],
    [".cta-s", "nav.cta"],
    [".hero .lead", "hero.lead"],
    [".btnrow a:nth-child(1)", "hero.mobileDemo"],
    [".btnrow a:nth-child(2)", "hero.dashboard"],
    [".btnrow a:nth-child(3)", "hero.github"],
    [".btnrow a:nth-child(4)", "hero.civicGuide"],
    [".ft1", "hero.riskTag"],
    [".ft2", "hero.distressTag"],
    [".stats .stat:nth-child(1) b", "stats.hikers.value"],
    [".stats .stat:nth-child(1) span", "stats.hikers.label"],
    [".stats .stat:nth-child(1) small", "stats.hikers.source"],
    [".stats .stat:nth-child(2) b", "stats.rescue.value"],
    [".stats .stat:nth-child(2) span", "stats.rescue.label"],
    [".stats .stat:nth-child(2) small", "stats.rescue.source"],
    [".stats .stat:nth-child(3) b", "stats.fusion.value"],
    [".stats .stat:nth-child(3) span", "stats.fusion.label"],
    [".stats .stat:nth-child(3) small", "stats.fusion.source"],
    ["#features .kick", "features.kick"],
    ["#features h2", "features.title"],
    ["#features .sec-sub", "features.subtitle"],
    ["#b2g .kick", "b2g.kick"],
    ["#b2g h3", "b2g.title"],
    ["#b2g p", "b2g.body"],
    ["#b2g .btn", "b2g.cta"],
    ["#data .kick", "data.kick"],
    ["#data h2", "data.title"],
    ["footer .github-label", "footer.github"],
    ["footer .wrap span:nth-child(3)", "footer.contact"],
  ];

  const htmlBindings = [
    [".badge", "hero.badge"],
    [".hero h1", "hero.title"],
    [".datastrip", "data.body"],
    ["footer .wrap span:nth-child(1)", "footer.intro"],
  ];

  const attrBindings = [
    [".brand img", "alt", "brand.logoAlt"],
    [".lang-switch", "aria-label", "nav.langLabel"],
    [".app-links", "aria-label", "download.label"],
    [".app-links a:nth-child(1)", "aria-label", "download.playAria"],
    [".app-links a:nth-child(2)", "aria-label", "download.fdroidAria"],
    [".app-links a:nth-child(3)", "aria-label", "download.obtainiumAria"],
    [".app-links a:nth-child(4)", "aria-label", "download.apkAria"],
    [".phone-shot", "alt", "hero.phoneAlt"],
    [".github-link", "aria-label", "footer.github"],
  ];

  const metaBindings = [
    ['meta[name="description"]', "content", "meta.description"],
    ['meta[property="og:title"]', "content", "meta.title"],
    ['meta[property="og:description"]', "content", "meta.description"],
    ['meta[property="og:image:alt"]', "content", "meta.imageAlt"],
    ['meta[name="twitter:title"]', "content", "meta.title"],
    ['meta[name="twitter:description"]', "content", "meta.description"],
  ];

  function element(selector) {
    return doc.querySelector(selector);
  }

  function textValue(selector) {
    const el = element(selector);
    return el ? el.textContent : "";
  }

  function htmlValue(selector) {
    const el = element(selector);
    return el ? el.innerHTML : "";
  }

  function attrValue(selector, attr) {
    const el = element(selector);
    return el ? el.getAttribute(attr) || "" : "";
  }

  function setText(selector, value) {
    const el = element(selector);
    if (el) el.textContent = value;
  }

  function setHtml(selector, value) {
    const el = element(selector);
    if (el) el.innerHTML = value;
  }

  function setAttr(selector, attr, value) {
    const el = element(selector);
    if (el) el.setAttribute(attr, value);
  }

  function setPath(target, path, value) {
    const parts = path.split(".");
    let cursor = target;
    for (let index = 0; index < parts.length - 1; index += 1) {
      const part = parts[index];
      cursor[part] = cursor[part] || {};
      cursor = cursor[part];
    }
    cursor[parts[parts.length - 1]] = value;
  }

  function readFeatureCards() {
    return [...doc.querySelectorAll("#features .feat")].map((card) => [
      card.querySelector("b")?.textContent || "",
      card.querySelector("p")?.textContent || "",
      card.querySelector(".tag")?.textContent || "",
    ]);
  }

  function readB2gItems() {
    return [...doc.querySelectorAll("#b2g li")].map((item) => item.innerHTML);
  }

  function readStoredLanguage() {
    try {
      return globalThis.localStorage.getItem(storageKey) || "";
    } catch (error) {
      return "";
    }
  }

  function storeLanguage(lng) {
    try {
      globalThis.localStorage.setItem(storageKey, lng);
    } catch (error) {
      void error;
    }
  }

  function readJsonSchema() {
    const schema = element('script[type="application/ld+json"]');
    if (!schema) return {};
    try {
      return JSON.parse(schema.textContent || "{}");
    } catch (error) {
      return {};
    }
  }

  function defaultTranslation() {
    const schema = readJsonSchema();
    const translation = {
      meta: {
        title: doc.title,
        description: attrValue('meta[name="description"]', "content"),
        imageAlt: attrValue('meta[property="og:image:alt"]', "content"),
      },
      schema: {
        name: schema.name || "숲길동무 ForestMate",
        alternateName: schema.alternateName || "ForestMate",
        description: schema.description || attrValue('meta[name="description"]', "content"),
      },
      features: { items: readFeatureCards() },
      b2g: { items: readB2gItems() },
    };

    textBindings.forEach(([selector, key]) => setPath(translation, key, textValue(selector)));
    htmlBindings.forEach(([selector, key]) => setPath(translation, key, htmlValue(selector)));
    attrBindings.forEach(([selector, attr, key]) => setPath(translation, key, attrValue(selector, attr)));
    return translation;
  }

  function languageExists(lng) {
    return Boolean(lng && resources[lng]);
  }

  function i18n() {
    return globalThis.i18next;
  }

  function t(key, options) {
    return i18n().t(key, options);
  }

  function schemaFor(lng) {
    return {
      "@context": "https://schema.org",
      "@type": "SoftwareApplication",
      name: t("schema.name"),
      alternateName: t("schema.alternateName"),
      applicationCategory: "HealthApplication",
      operatingSystem: "Android, iOS, Web",
      url: urls[lng].absolute,
      image: "https://forestmate.onrender.com/screens/home.png",
      description: t("schema.description"),
      sameAs: [
        "https://github.com/spcx0701/forest-mate",
        "https://www.data.go.kr/tcs/puc/selectPublicUseCaseView.do?prcuseCaseSn=1077408",
        "https://app.civictech.guide/p/forestmate/r/recQXWFIHBTDJLoZK",
      ],
      offers: {
        "@type": "Offer",
        price: "0",
        priceCurrency: urls[lng].currency,
      },
      publisher: {
        "@type": "Organization",
        name: "ForestMate",
      },
    };
  }

  function updateFeatureCards() {
    const cards = t("features.items", { returnObjects: true });
    cards.forEach((item, index) => {
      const card = element(`#features .feat:nth-child(${index + 1})`);
      if (!card) return;
      const [title, body, tag] = item;
      const titleEl = card.querySelector("b");
      const bodyEl = card.querySelector("p");
      const tagEl = card.querySelector(".tag");
      if (titleEl) titleEl.textContent = title;
      if (bodyEl) bodyEl.textContent = body;
      if (tagEl) tagEl.textContent = tag;
    });
  }

  function updateB2gItems() {
    const items = t("b2g.items", { returnObjects: true });
    items.forEach((item, index) => {
      setHtml(`#b2g li:nth-child(${index + 1})`, item);
    });
  }

  function updateLanguageLinks(lng) {
    doc.querySelectorAll(".lang-switch a").forEach((link) => {
      const target = link.dataset.lang || "ko";
      const active = target === lng;
      link.classList.toggle("on", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
      link.setAttribute("href", urls[target].local);
    });

    const brand = element(".brand");
    if (brand) brand.setAttribute("href", "home.html");
  }

  function updateHead(lng) {
    doc.documentElement.lang = lng;
    doc.title = t("meta.title");
    setAttr('link[rel="canonical"]', "href", urls[lng].absolute);
    setAttr('meta[property="og:url"]', "content", urls[lng].absolute);
    metaBindings.forEach(([selector, attr, key]) => setAttr(selector, attr, t(key)));

    const schema = element('script[type="application/ld+json"]');
    if (schema) schema.textContent = JSON.stringify(schemaFor(lng), null, 2);
  }

  function applyLanguage(lng, pushUrl = false) {
    textBindings.forEach(([selector, key]) => setText(selector, t(key)));
    htmlBindings.forEach(([selector, key]) => setHtml(selector, t(key)));
    attrBindings.forEach(([selector, attr, key]) => setAttr(selector, attr, t(key)));
    updateFeatureCards();
    updateB2gItems();
    updateLanguageLinks(lng);
    updateHead(lng);
    storeLanguage(lng);

    if (pushUrl) {
      const targetUrl = new URL(urls[lng].local, globalThis.location.href);
      targetUrl.hash = globalThis.location.hash;
      const current = globalThis.location.pathname + globalThis.location.search + globalThis.location.hash;
      const target = targetUrl.pathname + targetUrl.search + targetUrl.hash;
      if (current !== target) {
        globalThis.history.pushState({ lang: lng }, "", targetUrl.pathname + targetUrl.search + targetUrl.hash);
      }
    }
  }

  function initialLanguage() {
    const requested = new URLSearchParams(globalThis.location.search).get("lang");
    if (languageExists(requested)) return requested;
    const stored = readStoredLanguage();
    if (languageExists(stored)) return stored;
    if (doc.documentElement.lang === "en") return "en";
    return "ko";
  }

  function handleLanguageClick(event) {
    const link = event.currentTarget;
    const target = link.dataset.lang || "ko";
    if (!languageExists(target)) return;
    event.preventDefault();
    i18n().changeLanguage(target).then(() => applyLanguage(target, true));
  }

  function handlePopState() {
    const lng = initialLanguage();
    i18n().changeLanguage(lng).then(() => applyLanguage(lng, false));
  }

  function bindLanguageSwitches() {
    doc.querySelectorAll(".lang-switch a").forEach((link) => {
      link.addEventListener("click", handleLanguageClick);
    });
    globalThis.addEventListener("popstate", handlePopState);
  }

  function onI18nReady() {
    applyLanguage(bootLanguage, false);
    bindLanguageSwitches();
  }

  function boot() {
    if (!doc || !i18n()) return;
    resources.ko = { translation: defaultTranslation() };
    bootLanguage = initialLanguage();
    i18n()
      .init({
        lng: bootLanguage,
        fallbackLng: "ko",
        resources,
        interpolation: { escapeValue: false },
      })
      .then(onI18nReady);
  }

  if (doc.readyState === "loading") {
    doc.addEventListener("DOMContentLoaded", boot, { once: true });
  } else {
    boot();
  }
})();
