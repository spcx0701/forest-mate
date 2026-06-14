/* 숲길동무 데이터 레이어
 * 실서비스에서는 아래 어댑터가 공공데이터 OpenAPI를 호출한다.
 * 데모 빌드는 동일한 스키마의 정적 스냅샷을 반환한다(오프라인 시연 대응).
 *  - 등산로 공간정보: 산림청 (data.go.kr)
 *  - 산불위험예보: 국립산림과학원 forestfire.nifos.go.kr
 *  - 산악기상관측망: 국립산림과학원 mtweather.nifos.go.kr
 *  - 산사태위험지도: 산림청 sansatai.forest.go.kr
 *  - 생물종지식정보: 국립수목원 nature.go.kr
 *  - 산악사고 이력: 소방청 (data.go.kr/data/15083674)
 *  - 국가지점번호: 행정안전부 / 단기예보: 기상청
 */
const FM_DATA = {
  regions: {
    eunpyeong: {
      name: "서울 은평구",
      fire: { level: "낮음", score: 92, src: "국립산림과학원 예보" },
      landslide: { grade: 5, label: "안전", score: 95 },
      weather: { temp: 18, wind: 4.2, rainProb: 10, label: "맑음", score: 88, station: "북한산 관측소 실측" },
      sunsetAt: "19:52", sunsetScore: 45,
    },
    jongno: {
      name: "서울 종로구",
      fire: { level: "낮음", score: 90, src: "국립산림과학원 예보" },
      landslide: { grade: 4, label: "양호", score: 84 },
      weather: { temp: 19, wind: 3.1, rainProb: 20, label: "구름 조금", score: 80, station: "인왕산 관측소 실측" },
      sunsetAt: "19:52", sunsetScore: 45,
    },
    guri: {
      name: "경기 구리시",
      fire: { level: "보통", score: 71, src: "국립산림과학원 예보" },
      landslide: { grade: 4, label: "양호", score: 82 },
      weather: { temp: 20, wind: 6.8, rainProb: 30, label: "흐림", score: 64, station: "아차산 인근 AWS" },
      sunsetAt: "19:51", sunsetScore: 42,
    },
  },

  /* path: SVG 좌표(394×330 viewBox), prog 0~1 보간용 */
  courses: [
    {
      id: "bukhansan", region: "eunpyeong",
      name: "북한산 백운대 코스", route: "백운대탐방지원센터 → 백운대 정상",
      km: 4.2, minutes: 190, level: "중", levelN: 2, crowd: "보통", view: 4,
      peak: "백운대 836m", gridNo: "다사 5683 2741", gps: "37.6584°N, 126.9778°E",
      rescuePoint: "백운산장 헬기장 620m", fireStation: "서울 종로소방서 산악구조대",
      theme: "t1", steep: true,
      elev: [120, 180, 260, 390, 480, 542, 650, 770, 836],
      path: "M38,308 C80,290 110,262 138,236 C160,215 172,190 188,168 C204,146 222,132 240,116 C252,105 258,96 262,84",
      summitXY: [262, 78], startLabel: "탐방지원센터",
      hazards: [
        { at: 0.62, xy: [214, 138], type: "낙석주의", grade: "산사태 1등급", note: "최근 2주 강우 누적 — 우회로(백운산장 방면) 권장" },
        { at: 0.38, xy: [150, 225], type: "급경사", grade: "사고다발 구간", note: "스틱 사용·심박 주의 (소방청 사고이력 학습)" },
      ],
      shelter: { xy: [300, 150], name: "백운산장(대피)" },
    },
    {
      id: "inwangsan", region: "jongno",
      name: "인왕산 자락길 둘레", route: "사직공원 → 수성동계곡",
      km: 2.8, minutes: 100, level: "하", levelN: 1, crowd: "낮음", view: 3,
      peak: "인왕산 338m", gridNo: "다사 5421 2856", gps: "37.5772°N, 126.9610°E",
      rescuePoint: "황학정 진입로 280m", fireStation: "서울 종로소방서",
      theme: "t2", steep: false,
      elev: [60, 95, 140, 180, 210, 196, 170, 150, 130],
      path: "M30,300 C90,280 150,250 200,220 C250,190 290,170 340,150",
      summitXY: [340, 140], startLabel: "사직공원",
      hazards: [
        { at: 0.55, xy: [225, 205], type: "혼잡구간", grade: "주말 정체", note: "성곽길 합류 — 추월 자제" },
      ],
      shelter: { xy: [120, 250], name: "황학정 쉼터" },
    },
    {
      id: "achasan", region: "guri",
      name: "아차산 해맞이 능선", route: "아차산생태공원 → 해맞이광장",
      km: 3.5, minutes: 140, level: "하", levelN: 1, crowd: "보통", view: 5,
      peak: "아차산 287m", gridNo: "마바 1043 1822", gps: "37.5713°N, 127.1030°E",
      rescuePoint: "해맞이광장 헬기포인트", fireStation: "구리소방서",
      theme: "t3", steep: false,
      elev: [40, 80, 130, 170, 210, 240, 262, 280, 287],
      path: "M40,310 C100,295 170,260 220,225 C270,190 310,160 350,120",
      summitXY: [350, 110], startLabel: "생태공원",
      hazards: [
        { at: 0.70, xy: [290, 172], type: "암릉구간", grade: "주의", note: "우천 시 미끄럼 — 난간 이용" },
      ],
      shelter: { xy: [180, 255], name: "대성암 갈림길" },
    },
    {
      id: "dobong", region: "eunpyeong",
      name: "도봉산 신선대 코스", route: "도봉탐방지원센터 → 신선대",
      km: 6.4, minutes: 280, level: "상", levelN: 3, crowd: "높음", view: 5,
      peak: "신선대 726m", gridNo: "다사 6122 3354", gps: "37.6987°N, 127.0114°E",
      rescuePoint: "도봉대피소 410m", fireStation: "도봉소방서 산악구조대",
      theme: "t1", steep: true,
      elev: [110, 190, 300, 420, 510, 600, 660, 700, 726],
      path: "M36,312 C70,280 120,250 160,228 C210,200 250,160 280,120 C295,100 305,88 310,76",
      summitXY: [310, 70], startLabel: "도봉탐방센터",
      hazards: [
        { at: 0.78, xy: [282, 118], type: "Y계곡 암릉", grade: "사고다발", note: "강풍 시 우회 권장 — 보조자일 구간" },
        { at: 0.45, xy: [185, 215], type: "낙석주의", grade: "산사태 2등급", note: "헬멧 권장 구간" },
      ],
      shelter: { xy: [140, 240], name: "도봉대피소" },
    },
  ],

  /* 국립수목원 국가생물종지식정보 스냅샷 (시연용 3종) */
  species: [
    {
      id: "mushroom", label: "버섯 사진", emoji: "🍄",
      name: "개나리광대버섯", sci: "Amanita subjunquillea",
      toxic: true, conf: 87,
      desc: "아마톡신 함유 맹독성 — 소량 섭취로도 치명적이에요. 식용 꾀꼬리버섯과 혼동 사고가 잦아요.",
      action: "절대 채취·섭취 금지. 만졌다면 흐르는 물에 손을 씻어주세요.",
      grad: "radial-gradient(circle at 50% 38%, #e8d9a8 18%, #b99b5e 40%, #5d4a2f 75%, #3d3120)",
    },
    {
      id: "flower", label: "야생화 사진", emoji: "🌸",
      name: "진달래", sci: "Rhododendron mucronulatum",
      toxic: false, conf: 96,
      desc: "이른 봄 산능선을 분홍빛으로 물들이는 한국 대표 봄꽃이에요. 화전 재료로도 쓰여요.",
      action: "국립공원 내 채취는 금지! 눈으로만 즐겨주세요. 독성이 있는 철쭉과 혼동 주의.",
      grad: "radial-gradient(circle at 45% 40%, #ffd3e0 15%, #f49ac1 45%, #b06a8f 80%, #6d3b57)",
    },
    {
      id: "berry", label: "열매 사진", emoji: "🫐",
      name: "미국자리공 열매", sci: "Phytolacca americana",
      toxic: true, conf: 91,
      desc: "포도송이처럼 보이지만 전초에 독이 있어요. 아이들이 산머루로 착각하기 쉬워요.",
      action: "섭취 금지. 어린이 동반 시 특히 주의하세요.",
      grad: "radial-gradient(circle at 50% 42%, #6b4f86 15%, #3d2b57 50%, #221634 85%)",
    },
  ],

  briefings: [
    "주말 11~15시, 산악사고가 가장 집중되는 시간대예요. 이른 아침 산행을 추천해요. (소방청 통계 분석)",
    "하산 시 사고가 등반보다 1.8배 많아요. 스틱으로 무릎 부담을 줄이세요.",
    "고지대 기온은 도심보다 5~8℃ 낮아요. 산악기상관측망 실측 기준 겉옷을 챙기세요.",
    "음영지역 진입 전 오프라인 지도를 미리 받아두세요 — 설정에서 자동 다운로드를 켤 수 있어요.",
  ],

  news: [
    { title: "북한산 진달래 능선 개화 80%", detail: "북한산 대남문~대성문 능선 진달래가 80% 개화했어요. 이번 주말 절정 예상. 혼잡이 예상되니 이른 아침 산행을 권장해요.", url: "https://www.knps.or.kr" },
    { title: "우이령길 예약 잔여 24명", detail: "북한산 우이령길(예약 탐방제)은 하루 정원이 있어요. 오늘 잔여 24명. 국립공원 예약통합시스템에서 신청하세요.", url: "https://reservation.knps.or.kr" },
    { title: "도봉산 Y계곡 우회 권장(강풍)", detail: "산악기상관측망 기준 도봉산 능선 강풍주의보. Y계곡 등 노출 구간은 우회로를 이용하세요.", url: "https://www.forest.go.kr" },
    { title: "치유의숲 6월 프로그램 접수 중", detail: "전국 국립 치유의숲 6월 산림치유 프로그램을 숲나들e에서 접수 중이에요.", url: "https://www.foresttrip.go.kr" },
    { title: "아차산 해맞이광장 보수공사 완료", detail: "아차산 해맞이광장 데크 보수공사가 완료돼 정상 개방됐어요.", url: "https://www.forest.go.kr" },
  ],

  /* AI 다국어 응답 (RAG 데모 — 핵심 의도만) */
  i18n: {
    en: { hello: "Hi! I'm Soop-i, your AI forest guide. Ask me about trails, weather, or plants!", summit: (km, min) => `${km}km left to the summit — about ${min} min at your pace. Sunset has enough margin, but it's windy up there. Bring a jacket! 🧥` },
    zh: { hello: "你好！我是AI森林向导“숲이”。可以问我路线、天气或植物！", summit: (km, min) => `距离山顶还有${km}公里，按您的速度约${min}分钟。日落前时间充足，但山顶风大，请备好外套！🧥` },
    ja: { hello: "こんにちは！AI森ガイドの「スピ」です。コース・天気・植物について聞いてください！", summit: (km, min) => `山頂まで残り${km}km、今のペースで約${min}分です。日没まで余裕がありますが、山頂は風が強いので上着をどうぞ！🧥` },
  },
};

/* ---- 어댑터: 실서비스 전환점 ---- */
const ForestAPI = {
  async getRegion(id) { return FM_DATA.regions[id]; },
  async getCourses(regionId) {
    return regionId ? FM_DATA.courses.filter((c) => c.region === regionId || true) : FM_DATA.courses;
  },
  async identifySpecies(photoId) {
    await new Promise((r) => setTimeout(r, 900)); // 추론 지연 연출
    return FM_DATA.species.find((s) => s.id === photoId);
  },
};
