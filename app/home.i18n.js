(() => {
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

  const resources = {
    ko: {
      translation: {
        meta: {
          title: "숲길동무 ForestMate — 산림 공공데이터·AI 산행 안전 플랫폼",
          description: "숲길동무 ForestMate는 산림 공공데이터와 AI로 산행 전 위험을 예측하고 산행 중 조난·위험 상황을 빠르게 감지하는 산행 안전 플랫폼입니다.",
          imageAlt: "숲길동무 ForestMate 모바일 앱 홈 화면",
        },
        schema: {
          name: "숲길동무 ForestMate",
          alternateName: "ForestMate",
          description: "산림 공공데이터와 AI로 산행 전 위험을 예측하고 산행 중 위험 상황을 빠르게 감지하는 산행 안전 플랫폼입니다.",
        },
        brand: {
          name: "숲길동무",
          logoAlt: "숲길동무 ForestMate 로고",
        },
        nav: {
          features: "핵심 기능",
          download: "설치 링크",
          b2g: "관제(B2G)",
          data: "공공데이터",
          github: "GitHub",
          cta: "앱 데모 열기",
          langLabel: "언어 선택",
        },
        hero: {
          badge: "🏆 <a href=\"https://www.kofpi.or.kr/notice/notice_01view.do?bb_seq=12456\" target=\"_blank\" rel=\"noopener\">2026 산림 공공데이터·AI 활용 창업경진대회</a> — 제품 및 서비스 개발 부문 · <a href=\"https://www.data.go.kr/tcs/puc/selectPublicUseCaseView.do?prcuseCaseSn=1077408\" target=\"_blank\" rel=\"noopener\">공공데이터포털 활용사례</a>",
          title: "기록을 넘어,<br><em>생명을 지키는</em><br>산행 데이터로.",
          lead: "산림 공공데이터 10종과 AI 5종 엔진이 맞춤 추천 → 위험 경고 → 조난 자동 감지 → 관제 대응까지, 산행의 전 과정을 지킵니다.",
          mobileDemo: "📱 모바일 앱 데모",
          dashboard: "🖥 관제 대시보드(B2G)",
          github: "GitHub 저장소",
          phoneAlt: "숲길동무 앱 홈 화면: 오늘의 산행지수와 추천 산행 코스",
          riskTag: "⚠️ 300m 앞 낙석 주의 — 우회로 안내",
          distressTag: "🛡 자동 조난감지 작동 중",
        },
        download: {
          label: "앱 설치 링크",
          playAria: "Google Play 출시 페이지 열기",
          fdroidAria: "F-Droid 패키지 페이지 열기",
          obtainiumAria: "Obtainium으로 ForestMate 추가",
          apkAria: "최신 GitHub 릴리스에서 Android APK 내려받기",
        },
        stats: {
          hikers: {
            value: "3,229만",
            label: "월 1회 이상 등산·숲길 체험 인구",
            source: "산림청 국민의식 실태조사(2022)",
          },
          rescue: {
            value: "연 10,443건",
            label: "산악사고 구조활동 (사망 325명/3년)",
            source: "소방청 구조활동 통계(2022–2024)",
          },
          fusion: {
            value: "10종 × AI 5종",
            label: "산림 공공데이터 실시간 융합",
            source: "산림청·국립산림과학원·소방청·행안부·기상청",
          },
        },
        features: {
          kick: "FEATURES",
          title: "산행의 모든 순간을 지키는 6가지 기능",
          subtitle: "모든 기능이 산림 공공데이터 위에서 동작합니다 — 단순 조회가 아닌 교차 도메인 융합.",
          items: [
            ["오늘의 산행지수", "산불위험예보 + 산사태등급 + 산악기상 실측 + 일몰시각을 0~100점 하나로. ‘갈까 말까’가 한눈에.", "국립산림과학원 API"],
            ["AI 맞춤 코스 추천", "체력·부상 이력·혼잡 예측을 반영한 개인화 추천. 고령자·초보자에게는 더 보수적으로.", "하이브리드 필터링"],
            ["위험구간 실시간 경고", "산사태지도 × 사고이력 × 강우를 100m 구간마다 융합 — 위험구간 300m 전 푸시·우회로 안내.", "위험도 융합 스코어"],
            ["조난 자동 감지 · SOS", "이동 멈춤·심박 이상을 AI가 감지, 무응답 시 보호자·119에 국가지점번호로 자동 전파.", "시계열 이상탐지"],
            ["AI 숲해설사 ‘숲이’", "사진 한 장으로 독버섯·식물 판별(온디바이스), 4개 언어 RAG 해설로 외국인 관광객까지.", "국립수목원 도감 학습"],
            ["산림치유 연계", "산행 리포트·건강 데이터 기반으로 치유의숲·휴양림 프로그램을 추천하고 바로 예약.", "숲나들e 연동"],
          ],
        },
        b2g: {
          kick: "FOR GOVERNMENT",
          title: "지자체·소방이 같은 화면을 봅니다",
          body: "앱이 모은 익명(k≥50) 산행 데이터가 관제 대시보드로 환류됩니다. 실시간 산행자 분포, 구간 위험도, SOS 사건 추적, 입산 수요예측까지 — 구조 골든타임을 30% 단축하는 것이 목표입니다.",
          cta: "🖥 관제 대시보드 데모 열기",
          items: [
            "<b>실시간 관제</b> — SOS·조난의심 사건, 구조거점 매칭, 헬기 동선",
            "<b>위험 예방</b> — 구간 위험도 자동 경보, 우회 안내 일괄 발송",
            "<b>정책 데이터</b> — 숲길 정비·안전시설 투자 우선순위 근거 제공",
            "<b>도입 모델</b> — 기관당 연 라이선스(SaaS), 시범사업 2곳 협의 중",
          ],
        },
        data: {
          kick: "PUBLIC DATA",
          title: "데이터 출처",
          body: "<b>산림분야</b> — 산림청 등산로 공간정보 · 국립산림과학원 산불위험예보/산악기상관측망 · 산림청 산사태위험지도 · 국립수목원 국가생물종지식정보 · 한국산림복지진흥원 숲나들e · 한국임업진흥원 산림빅데이터 거래소<br><b>융복합(타 분야)</b> — 소방청 전국 산악사고 구조활동 현황 · 행정안전부 국가지점번호 · 기상청 단기예보 OpenAPI<br>개인 위치정보는 단말·보호자 동의 범위에서만 처리하며, 통계는 k-익명화(k≥50) 후 활용합니다 (위치정보법 준수).",
        },
        footer: {
          intro: "© 2026 숲길동무 ForestMate — <a href=\"https://www.kofpi.or.kr/notice/notice_01view.do?bb_seq=12456\" target=\"_blank\" rel=\"noopener\">2026년 산림 공공데이터·AI 활용 창업경진대회</a> 출품작",
          github: "GitHub 저장소",
          contact: "문의: spcx0701@gmail.com",
        },
      },
    },
    en: {
      translation: {
        meta: {
          title: "ForestMate — Forest public data and AI hiking safety platform",
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
          badge: "🏆 <a href=\"https://www.kofpi.or.kr/notice/notice_01view.do?bb_seq=12456\" target=\"_blank\" rel=\"noopener\">2026 Forest Public Data and AI Startup Competition</a> — Product and Service Development track · <a href=\"https://www.data.go.kr/tcs/puc/selectPublicUseCaseView.do?prcuseCaseSn=1077408\" target=\"_blank\" rel=\"noopener\">data.go.kr use case</a>",
          title: "Beyond records:<br><em>trail data that</em><br>protects lives.",
          lead: "ForestMate combines 10 forest public-data sources with 5 AI engines to support personalized route choice, risk alerts, automatic distress detection, and public-sector response.",
          mobileDemo: "📱 Mobile app demo",
          dashboard: "🖥 Operations dashboard",
          github: "GitHub repository",
          phoneAlt: "ForestMate app home screen with today's hiking index and recommended routes",
          riskTag: "⚠️ Rockfall risk 300 m ahead — detour ready",
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
            "<b>Live monitoring</b> — SOS and suspected-distress events, responder-base matching, helicopter routes",
            "<b>Risk prevention</b> — automatic segment alerts and bulk detour guidance",
            "<b>Policy evidence</b> — prioritization data for trail maintenance and safety facilities",
            "<b>Adoption model</b> — annual institutional SaaS license; two pilot projects under discussion",
          ],
        },
        data: {
          kick: "PUBLIC DATA",
          title: "Data sources",
          body: "<b>Forest data</b> — Korea Forest Service trail geospatial data · National Institute of Forest Science forest-fire danger forecast and mountain weather observations · Korea Forest Service landslide risk maps · Korea National Arboretum species knowledge · Korea Forest Welfare Institute Soopnarae · Korea Forestry Promotion Institute forest big-data exchange<br><b>Cross-domain data</b> — National Fire Agency mountain rescue activity data · Ministry of the Interior and Safety national grid reference system · Korea Meteorological Administration short-term forecast OpenAPI<br>Personal location data is processed only within device and guardian consent boundaries. Aggregated statistics are used after k-anonymization (k>=50) for location-privacy compliance.",
        },
        footer: {
          intro: "© 2026 ForestMate — submitted to the <a href=\"https://www.kofpi.or.kr/notice/notice_01view.do?bb_seq=12456\" target=\"_blank\" rel=\"noopener\">2026 Forest Public Data and AI Startup Competition</a>",
          github: "GitHub repository",
          contact: "Contact: spcx0701@gmail.com",
        },
      },
    },
  };

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

  function setText(selector, value) {
    const el = document.querySelector(selector);
    if (el) el.textContent = value;
  }

  function setHtml(selector, value) {
    const el = document.querySelector(selector);
    if (el) el.innerHTML = value;
  }

  function setAttr(selector, attr, value) {
    const el = document.querySelector(selector);
    if (el) el.setAttribute(attr, value);
  }

  function schemaFor(lng) {
    return {
      "@context": "https://schema.org",
      "@type": "SoftwareApplication",
      name: i18next.t("schema.name"),
      alternateName: i18next.t("schema.alternateName"),
      applicationCategory: "HealthApplication",
      operatingSystem: "Android, iOS, Web",
      url: urls[lng].absolute,
      image: "https://forestmate.onrender.com/screens/home.png",
      description: i18next.t("schema.description"),
      sameAs: [
        "https://github.com/spcx0701/forest-mate",
        "https://www.data.go.kr/tcs/puc/selectPublicUseCaseView.do?prcuseCaseSn=1077408",
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
    const cards = i18next.t("features.items", { returnObjects: true });
    cards.forEach((item, index) => {
      const card = document.querySelector(`#features .feat:nth-child(${index + 1})`);
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
    const items = i18next.t("b2g.items", { returnObjects: true });
    items.forEach((item, index) => {
      setHtml(`#b2g li:nth-child(${index + 1})`, item);
    });
  }

  function updateLanguageLinks(lng) {
    document.querySelectorAll(".lang-switch a").forEach((link) => {
      const target = link.dataset.lang || "ko";
      const active = target === lng;
      link.classList.toggle("on", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
      link.setAttribute("href", urls[target].local);
    });

    const brand = document.querySelector(".brand");
    if (brand) brand.setAttribute("href", "home.html");
  }

  function updateHead(lng) {
    document.documentElement.lang = lng;
    document.title = i18next.t("meta.title");
    setAttr('link[rel="canonical"]', "href", urls[lng].absolute);
    setAttr('meta[property="og:url"]', "content", urls[lng].absolute);
    metaBindings.forEach(([selector, attr, key]) => setAttr(selector, attr, i18next.t(key)));

    const schema = document.querySelector('script[type="application/ld+json"]');
    if (schema) schema.textContent = JSON.stringify(schemaFor(lng), null, 2);
  }

  function applyLanguage(lng, pushUrl = false) {
    textBindings.forEach(([selector, key]) => setText(selector, i18next.t(key)));
    htmlBindings.forEach(([selector, key]) => setHtml(selector, i18next.t(key)));
    attrBindings.forEach(([selector, attr, key]) => setAttr(selector, attr, i18next.t(key)));
    updateFeatureCards();
    updateB2gItems();
    updateLanguageLinks(lng);
    updateHead(lng);

    try {
      window.localStorage.setItem("forestmate.home.lang", lng);
    } catch {}

    if (pushUrl) {
      const targetUrl = new URL(urls[lng].local, window.location.href);
      targetUrl.hash = window.location.hash;
      const current = window.location.pathname + window.location.search + window.location.hash;
      const target = targetUrl.pathname + targetUrl.search + targetUrl.hash;
      if (current !== target) {
        window.history.pushState({ lang: lng }, "", targetUrl.pathname + targetUrl.search + targetUrl.hash);
      }
    }
  }

  function initialLanguage() {
    const requested = new URLSearchParams(window.location.search).get("lang");
    if (requested && resources[requested]) return requested;
    try {
      const stored = window.localStorage.getItem("forestmate.home.lang");
      if (stored && resources[stored]) return stored;
    } catch {}
    if (document.documentElement.lang === "en") return "en";
    return "ko";
  }

  function bindLanguageSwitches() {
    document.querySelectorAll(".lang-switch a").forEach((link) => {
      link.addEventListener("click", (event) => {
        const target = link.dataset.lang || "ko";
        if (!resources[target]) return;
        event.preventDefault();
        i18next.changeLanguage(target).then(() => applyLanguage(target, true));
      });
    });

    window.addEventListener("popstate", () => {
      const lng = initialLanguage();
      i18next.changeLanguage(lng).then(() => applyLanguage(lng, false));
    });
  }

  function boot() {
    if (!window.i18next) return;

    const lng = initialLanguage();
    i18next
      .init({
        lng,
        fallbackLng: "ko",
        resources,
        interpolation: { escapeValue: false },
      })
      .then(() => {
        applyLanguage(lng, false);
        bindLanguageSwitches();
      });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot, { once: true });
  } else {
    boot();
  }
})();
