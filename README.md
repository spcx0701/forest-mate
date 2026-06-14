<p align="center">
  <img src="assets/brand/forestmate-logo.png" alt="ForestMate · 숲길동무" width="720">
</p>

<p align="center">
  <strong>산림 공공데이터와 AI로 산행 전 위험을 예측하고, 산행 중 위험 상황을 빠르게 감지하는 산행 안전 동반자.</strong>
</p>

<p align="center">
  <a href="https://forestmate.onrender.com/home.html">
    <img alt="Service live" src="https://img.shields.io/badge/service-live-1B4332?style=for-the-badge">
  </a>
  <a href="https://forestmate.onrender.com/index.html">
    <img alt="PWA ready" src="https://img.shields.io/badge/PWA-ready-2D6A4F?style=for-the-badge">
  </a>
  <a href="https://github.com/spcx0701/forest-mate/releases/latest">
    <img alt="Android APK" src="https://img.shields.io/badge/Android-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  </a>
  <a href="packaging/fdroid/README.md">
    <img alt="F-Droid candidate" src="https://img.shields.io/badge/F--Droid-candidate-1976D2?style=for-the-badge&logo=fdroid&logoColor=white">
  </a>
  <img alt="No tracking SDK" src="https://img.shields.io/badge/no_tracking_SDK-verified-0B7A75?style=for-the-badge">
  <img alt="License Apache 2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue?style=for-the-badge">
</p>

<p align="center">
  <a href="https://forestmate.onrender.com/index.html"><strong>Web App</strong></a>
  ·
  <a href="https://forestmate.onrender.com/home.html"><strong>Service Intro</strong></a>
  ·
  <a href="https://forestmate.onrender.com/dashboard.html"><strong>Dashboard</strong></a>
  ·
  <a href="https://github.com/spcx0701/forest-mate/releases/latest/download/forestmate-android-v1.0.0.apk"><strong>Android APK</strong></a>
  ·
  <a href="https://github.com/spcx0701/forest-mate/releases/latest"><strong>GitHub Release</strong></a>
  ·
  <a href="packaging/fdroid/README.md"><strong>F-Droid Candidate</strong></a>
</p>

<p align="center">
  <img src="assets/readme/forestmate-hero.png" alt="ForestMate app preview showing hiking safety, trail risk alerts, SOS, and dashboard views" width="100%">
</p>

## 개요

ForestMate(숲길동무)는 산림 공공데이터 10종과 AI를 융합한 산행 안전 플랫폼이다. 맞춤 추천, 위험 경고, 조난 자동 감지, B2G 관제를 하나의 PWA·Android TWA·FastAPI 서비스로 연결한다.

데모 목업이 아니라 실제 백엔드와 클라이언트로 동작한다. 앱·관제는 `/api/v1`의 클라이언트이며, 백엔드가 없으면 로컬 엔진으로 폴백해 통신 음영지역 시나리오를 다룬다.

Android APK는 Play Store가 아닌 직접 배포 파일이다. 설치 시 Android 설정에서 "알 수 없는 앱 설치" 허용이 필요할 수 있다. F-Droid용 배포는 APK 직접 업로드가 아니라 `.fdroid.yml` 기준 소스 빌드로 준비되어 있으며, 기본 Android 빌드는 `forestmate.onrender.com` hosted service를 여는 TWA라서 F-Droid 메타데이터에 `NonFreeNet` Anti-Feature를 명시한다.

> 「2026년 산림 공공데이터·AI 활용 창업경진대회」 제품 및 서비스 개발 부문 출품 패키지.

## 아키텍처

```
forest-mate/
├── server/               # FastAPI 백엔드
│   ├── main.py           #   앱 조립 + 정적 프런트(app/) 동일 오리진 서빙
│   ├── config.py         #   설정(.env) — 키 없으면 스냅샷/규칙 폴백
│   ├── db.py, models.py  #   SQLAlchemy (dev SQLite / prod PostgreSQL+PostGIS)
│   ├── adapters/         #   공공데이터 어댑터 (기상청·산불·산악기상 + TTL 캐시 + 폴백)
│   ├── services/         #   scoring(산행지수·추천·위험융합) · safety(조난감지·k익명화)
│   │                     #   chat(의도엔진) · llm(Claude RAG) · bus(관제 WS pub/sub)
│   ├── routers/          #   public · hikes(토큰인증) · dashboard(WS)
│   └── tests/            #   pytest 20개 (스코어링·안전·API E2E·WebSocket)
├── app/                  # 클라이언트 (정적 호스팅 가능, 백엔드와 동일 오리진 권장)
│   ├── home.html         #   서비스 소개 랜딩
│   ├── index.html        #   모바일 PWA — cloud 모드 시 LIVE 배지
│   ├── dashboard.html    #   B2G 관제 — 실시간 KPI + WebSocket 피드
│   ├── app.js            #   API 클라이언트(헬스체크 감지·실패 시 로컬 폴백) + 앱 로직
│   └── data.js, sw.js    #   로컬 폴백 데이터 / 오프라인 서비스워커
├── deploy → Dockerfile · docker-compose.yml · render.yaml(무료 호스팅) · .env.example
├── packaging/            # 배포 빌드 — android(TWA APK·Bubblewrap) · ios(Capacitor)
├── deliverables/         # 기획서 DOCX · 발표 PPTX
├── legal/                # 개인정보처리방침 · 이용약관
└── store/                # 스토어 등록 메타데이터 + 스토어_제출_런북.md
```

## 실행

### 풀스택(백엔드 + 프런트, 권장)
```bash
cd forest-mate
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn server.main:app --port 5181
```
- 랜딩 http://localhost:5181/home.html · 앱 http://localhost:5181/index.html · 관제 http://localhost:5181/dashboard.html
- API 문서(OpenAPI) http://localhost:5181/docs

### Docker
```bash
cp .env.example .env          # 키 입력(없어도 폴백 동작)
docker compose up             # API + PostgreSQL(PostGIS)
```

### 정적만(백엔드 없이)
`app/`를 정적 호스팅하면 로컬 엔진으로 단독 동작(LIVE 배지 없음, 관제는 시뮬레이션).

## 동작 모드 (키 유무로 자동 전환 — 코드 수정 불필요)

| 기능 | 키 없음(기본) | 키 설정 시 |
|------|--------------|-----------|
| 산행지수·기상·산불 | 공공데이터 **스냅샷**(실 API와 동일 스키마) | `DATA_GO_KR_KEY` → **실 공공데이터 API** |
| AI 숲이 챗 | **규칙 기반 의도 엔진** | `ANTHROPIC_API_KEY` → **Claude RAG**(공공데이터 근거 주입) |
| 프런트 | 로컬 폴백 엔진 | 백엔드 감지 시 **cloud 모드**(서버 경유) |

LLM 모드는 `server/services/llm.py`에서 Claude Messages API를 호출한다 — 고정 시스템 프롬프트(지식베이스)에 prompt caching, 요청별 실시간 컨텍스트(기상·코스·위험)는 user 메시지로 주입. 모델 `claude-opus-4-8`.

## 핵심 도메인 로직(서버가 단일 출처)

- **산행지수** `services/scoring.py` — 산불0.3+산사태0.25+기상0.25+일몰0.2
- **코스 추천** — 체력·무릎·심혈관·혼잡·기상 가중(설명가능 규칙)
- **위험 융합** — 정적 위험(산사태등급·사고이력) × 실시간 기상. 운영은 XGBoost 서빙으로 교체(규칙은 폴백)
- **조난 감지** `services/safety.py` — 이동 정지 30분+ → level1, 심박 이상 동반 → level2(즉시 전파). **서버측 판정**이라 앱 종료·통신 두절 시에도 마지막 데이터로 평가
- **k-익명화** — 관제는 개인 위치 대신 군집(50인↑) 통계만 표출(위치정보법)

## 테스트
```bash
.venv/bin/python -m pytest server/tests -q     # 20 passed
```
스코어링·조난감지·k익명화 단위 테스트 + 기기등록→산행→위험경고→SOS→관제반영 E2E + WebSocket 수신 검증.
CI(`.github/workflows/forestmate-ci.yml`)가 push마다 pytest + Docker 빌드/헬스체크 smoke를 수행한다.

## 상용 전환 체크리스트
1. `DATABASE_URL`을 PostgreSQL(+PostGIS)로, 등산로 ETL로 전국 코스 적재
2. `DATA_GO_KR_KEY`·`ANTHROPIC_API_KEY` 발급·설정 → 실 데이터/LLM 활성
3. `K_ANONYMITY=50`, HTTPS, CORS 화이트리스트
4. 수평 확장 시 `services/bus.py`를 Redis Pub/Sub로, SOS를 119 신고 API·FCM 워커로 팬아웃
5. **배포**: `render.yaml`로 무료 배포(공개 HTTPS) → Android는 `packaging/android`의 TWA APK로 직접 배포, iOS는 Capacitor(App Store)로 빌드·등록. **전체 절차는 [store/스토어_제출_런북.md](store/스토어_제출_런북.md)** 참고
6. DOCX 노란 칸(팀명·URL·실적) 교체 후 공고 HWP 양식 제출

## 시연·캡처용 파라미터
`index.html?t=trail&demo=57` · `dashboard.html?demo=1`(시계 14:07) · `index.html?embed=1`(랜딩 iframe용)

## 데이터 출처
산림청 등산로 공간정보 · 국립산림과학원 산불위험예보/산악기상관측망 · 산사태정보시스템 · 국립수목원 국가생물종지식정보 · 한국산림복지진흥원 숲나들e · 산림빅데이터 거래소 · 소방청 산악사고 현황 · 행정안전부 국가지점번호 · 기상청 단기예보

## 라이선스

이 저장소의 프로젝트 코드, 문서, Android 리소스, 프로젝트 소유 이미지 자산은 별도 표기가 없는 한 Apache License 2.0으로 배포된다. 자세한 내용은 [LICENSE](LICENSE)와 [NOTICE](NOTICE)를 참고한다.
