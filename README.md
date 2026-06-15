# ForestMate · 숲길동무

<p align="center">
  <img src="assets/brand/forestmate-logo.png" alt="ForestMate · 숲길동무" width="720">
</p>

<p align="center">
  <strong>산림 공공데이터와 AI로 산행 전 위험을 예측하고, 산행 중 위험 상황을 빠르게 감지하는 산행 안전 동반자.</strong>
</p>

<p align="center">
  <a href="https://github.com/spcx0701/forest-mate/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/spcx0701/forest-mate?sort=date&display_name=tag&color=2D6A4F&logo=github"></a>
  <a href="https://github.com/spcx0701/forest-mate/actions/workflows/ci.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/spcx0701/forest-mate/ci.yml?branch=main&logo=github"></a>
  <a href="https://www.codefactor.io/repository/github/spcx0701/forest-mate"><img alt="CodeFactor" src="https://img.shields.io/codefactor/grade/github/spcx0701/forest-mate?label=code%20quality&logo=codefactor"></a>
  <a href="https://codecov.io/gh/spcx0701/forest-mate"><img alt="Coverage" src="https://img.shields.io/codecov/c/github/spcx0701/forest-mate?label=coverage&logo=codecov"></a>
  <img alt="Top language" src="https://img.shields.io/github/languages/top/spcx0701/forest-mate">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/spcx0701/forest-mate?color=blue"></a>
  <br>
  <a href="https://forestmate.onrender.com/home.html"><img alt="Service" src="https://img.shields.io/badge/service-live-1B4332"></a>
  <a href="https://github.com/spcx0701/forest-mate/releases/latest"><img alt="Android APK" src="https://img.shields.io/badge/Android-APK-3DDC84?logo=android&logoColor=white"></a>
  <a href="packaging/fdroid/README.md"><img alt="F-Droid candidate" src="https://img.shields.io/badge/F--Droid-candidate-1976D2?logo=fdroid&logoColor=white"></a>
  <img alt="PWA ready" src="https://img.shields.io/badge/PWA-ready-5A0FC8?logo=pwa&logoColor=white">
  <img alt="No tracking SDK" src="https://img.shields.io/badge/no_tracking_SDK-verified-0B7A75">
</p>

<p align="center">
  <a href="https://forestmate.onrender.com/index.html"><strong>Web App</strong></a>
  ·
  <a href="https://forestmate.onrender.com/home.html"><strong>Service Intro</strong></a>
  ·
  <a href="https://forestmate.onrender.com/dashboard.html"><strong>Dashboard</strong></a>
  ·
  <a href="https://github.com/spcx0701/forest-mate/releases/latest"><strong>Android APK</strong></a>
  ·
  <a href="https://github.com/spcx0701/forest-mate/releases/latest"><strong>GitHub Release</strong></a>
  ·
  <a href="packaging/fdroid/README.md"><strong>F-Droid Candidate</strong></a>
</p>

<p align="center">
  <img src="assets/readme/forestmate-hero.png" alt="ForestMate 앱 화면 — 홈(산행지수) · 산행(실시간 지도) · 안전(원터치 SOS) · AI동무" width="100%">
</p>

ForestMate(숲길동무)는 산림 공공데이터와 AI 기반 안전 판단 로직을 활용해 **산행 전 코스 선택 → 산행 중 위험 감지·SOS → B2G 관제**까지 잇는 산행 안전 서비스입니다. 통신 음영지역에서는 자동으로 로컬 엔진으로 폴백해 핵심 기능이 끊기지 않습니다.

웹 앱, 관제 대시보드, Android APK를 함께 제공합니다. 백엔드 연결 시 `/api/v1`로 실시간 공공데이터와 산행 기록을 사용하고, 정적 호스팅 환경에서는 로컬 데이터·규칙 기반 폴백으로 주요 화면을 확인할 수 있습니다.

> 「2026년 산림 공공데이터·AI 활용 창업경진대회」 제품 및 서비스 개발 부문 출품 패키지.

## 주요 기능

- **전국 산 카탈로그** — 산림청 산정보 4,600여 개 산을 VWorld 지오코딩으로 좌표화해 검색·즐겨찾기. 현재 위치(GPS)·17개 시도별 탐색.
- **실시간 산행지수** — 기상청 단기예보 + 국립산림과학원 산불위험예보(V2) + 산사태·일몰을 결합. 선택한 산의 위치 격자로 정밀 산출.
- **실제 등산로 지도** — 산림청 등산로 공간정보(2,200여 산, 5만여 구간)를 난이도색 경로로 Leaflet 지도에 표시. 들머리까지 카카오맵/구글맵 길찾기.
- **GPS 산행 추적** — 실제 위치(`watchPosition`)대로 경로·거리 기록(자동 진행 아님), 이동 멈춤+심박 이상 시 **자동 조난 감지** → 보호자·119 전파.
- **AI 숲이** — 규칙 엔진/LLM(RAG) 의도 응답, 식물·버섯 식별 데모.
- **개인화** — 실집계 배지·산행 캘린더·산행 일정 계획(날짜별 적합도)·위치/즐겨찾기 맞춤 알림(Web Push).
- **B2G 관제 대시보드** — 실시간 KPI + WebSocket 피드 + k-익명화 위험 히트맵.

Android 사용자는 GitHub Release에서 APK를 내려받아 설치할 수 있습니다. F-Droid 제출용 소스 빌드 메타데이터도 포함되며, 현재 Android TWA는 `forestmate.onrender.com` hosted service를 열기 때문에 F-Droid 메타데이터에 `NonFreeNet` Anti-Feature를 명시합니다.

## B2G 관제 대시보드

모바일 앱과 함께, 지자체·소방 대상 **실시간 관제 웹**을 제공합니다.

<p align="center">
  <img src="app/screens/dashboard.png" alt="B2G 관제 대시보드" width="92%">
</p>
<p align="center"><sub>실시간 KPI · k-익명화 위험 히트맵 · WebSocket 라이브 피드</sub></p>

## 기술 스택

<p align="center">
  <img alt="FastAPI" src="https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white">
  <img alt="Python 3.12" src="https://img.shields.io/badge/Python_3.12-3776AB?logo=python&logoColor=white">
  <img alt="SQLAlchemy" src="https://img.shields.io/badge/SQLAlchemy-D71F00?logo=sqlalchemy&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white">
  <img alt="Leaflet" src="https://img.shields.io/badge/Leaflet-199900?logo=leaflet&logoColor=white">
  <img alt="PWA" src="https://img.shields.io/badge/PWA-5A0FC8?logo=pwa&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white">
  <img alt="Claude" src="https://img.shields.io/badge/Claude_LLM-D97757?logo=anthropic&logoColor=white">
</p>

- **백엔드** FastAPI · SQLAlchemy(SQLite/PostgreSQL) · Pydantic · pytest · WebSocket
- **프런트** PWA(서비스워커·오프라인·Web Push) · Vanilla JS · Leaflet 지도
- **데이터·AI** 공공데이터포털(기상청·산림청) · VWorld 지오코딩 · Claude(LLM RAG)
- **인프라** Docker · Render · GitHub Actions(CI)

## 아키텍처

```
forest-mate/
├── server/               # FastAPI 백엔드
│   ├── main.py           #   앱 조립 + 정적 프런트(app/) 동일 오리진 서빙
│   ├── config.py         #   설정(.env) — 키 없으면 스냅샷/규칙 폴백
│   ├── db.py, models.py  #   SQLAlchemy (dev SQLite / prod PostgreSQL·연결 실패 시 자동 폴백)
│   ├── adapters/         #   공공데이터 어댑터 (기상청·산불·산악기상 + TTL 캐시 + 폴백)
│   ├── geo.py            #   KMA 격자 변환 + 시도 좌표 (정밀 산행지수·GPS)
│   ├── data/             #   영속 카탈로그(catalog.json) + 등산로 선(trails/{code}.json)
│   ├── services/         #   scoring(산행지수·추천·위험융합) · safety(조난감지·k익명화)
│   │                     #   chat(의도엔진) · llm(Claude RAG) · bus(관제 WS pub/sub)
│   ├── routers/          #   public · hikes(토큰인증) · dashboard(WS)
│   └── tests/            #   pytest 23개 (스코어링·안전·API E2E·WebSocket)
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
docker compose up             # API + PostgreSQL
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
.venv/bin/python -m pytest server/tests -q     # 23 passed
```
스코어링·조난감지·k익명화 단위 테스트 + 기기등록→산행→위험경고→SOS→관제반영 E2E + WebSocket 수신 검증.
CI(`.github/workflows/ci.yml`)가 push·PR마다 pytest와 Docker 빌드를 수행한다.

## 데이터 출처

| 데이터 | 제공 기관 | 본 앱 활용 | 출처 |
|--------|-----------|-----------|------|
| 전국 산정보(표준데이터) | 산림청 | 4,600여 개 산 카탈로그·검색 | [공공데이터포털](https://www.data.go.kr/data/15029183/standard.do) |
| 등산로 공간정보 | 산림청 · 산림빅데이터 | 등산로 선·주요지점 지도 | [산림빅데이터 거래소](https://www.bigdata-forest.kr) |
| 단기예보 | 기상청 | 산행지수·날씨·일정 예보 | [공공데이터포털](https://www.data.go.kr) |
| 산불위험예보 | 국립산림과학원 | 산불 위험도 | [공공데이터포털](https://www.data.go.kr) |
| 산악기상관측망 | 국립산림과학원 | 능선부 기상 특보 | [공공데이터포털](https://www.data.go.kr) |
| 산사태정보 | 산림청 | 산사태 위험등급 | [산사태정보시스템](https://sansatai.forest.go.kr) |
| 국가생물종지식정보 | 국립수목원 | 식물·버섯 식별 | [국가생물종지식정보](http://www.nature.go.kr) |
| 산림복지(숲나들e) | 한국산림복지진흥원 | 치유의숲·휴양림 | [숲나들e](https://www.foresttrip.go.kr) |
| 산악사고 현황 | 소방청 | 위험 구간 안내 | [공공데이터포털](https://www.data.go.kr) |
| 국가지점번호 | 행정안전부 | 위치 표준 좌표 | [공공데이터포털](https://www.data.go.kr) |
| 주소·좌표 변환 | 국토교통부 VWorld | 산 좌표·시군구 지오코딩 | [VWorld](https://www.vworld.kr) |
| 지도 타일 | OpenStreetMap | 실제 지도 렌더 | [OSM](https://www.openstreetmap.org/copyright) |

## 라이선스

이 저장소의 프로젝트 코드, 문서, Android 리소스, 프로젝트 소유 이미지 자산은 별도 표기가 없는 한 Apache License 2.0으로 배포된다. 자세한 내용은 [LICENSE](LICENSE)와 [NOTICE](NOTICE)를 참고한다.
