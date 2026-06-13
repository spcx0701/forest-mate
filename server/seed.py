"""정적 시드 데이터 — 코스·지역·생물종.

코스는 산림청 등산로 공간정보의 발췌 스냅샷이다. 운영에서는 etl(load_trails)로
전국 등산로를 DB에 적재하고, 이 모듈은 통합 테스트 픽스처로만 쓴다.
지역의 (nx, ny)는 기상청 단기예보 격자 좌표, (sgg)는 산불위험예보 시군구 코드.
"""

REGIONS: dict[str, dict] = {
    "eunpyeong": {
        "name": "서울 은평구", "nx": 59, "ny": 127, "sgg": "11380",
        "sunset_at": "19:52",
        "snapshot": {  # 키가 없을 때 쓰는 폴백 (실 API와 동일 스키마)
            "fire": {"level": "낮음", "score": 92, "src": "국립산림과학원 산불위험예보(스냅샷)"},
            "landslide": {"grade": 5, "label": "안전", "score": 95},
            "weather": {"temp": 18.0, "wind": 4.2, "rain_prob": 10, "label": "맑음",
                        "score": 88, "station": "북한산 관측소"},
            "sunset_score": 45,
        },
    },
    "jongno": {
        "name": "서울 종로구", "nx": 60, "ny": 127, "sgg": "11110",
        "sunset_at": "19:52",
        "snapshot": {
            "fire": {"level": "낮음", "score": 90, "src": "국립산림과학원 산불위험예보(스냅샷)"},
            "landslide": {"grade": 4, "label": "양호", "score": 84},
            "weather": {"temp": 19.0, "wind": 3.1, "rain_prob": 20, "label": "구름 조금",
                        "score": 80, "station": "인왕산 관측소"},
            "sunset_score": 45,
        },
    },
    "guri": {
        "name": "경기 구리시", "nx": 62, "ny": 127, "sgg": "41310",
        "sunset_at": "19:51",
        "snapshot": {
            "fire": {"level": "보통", "score": 71, "src": "국립산림과학원 산불위험예보(스냅샷)"},
            "landslide": {"grade": 4, "label": "양호", "score": 82},
            "weather": {"temp": 20.0, "wind": 6.8, "rain_prob": 30, "label": "흐림",
                        "score": 64, "station": "아차산 인근 AWS"},
            "sunset_score": 42,
        },
    },
}

COURSES: list[dict] = [
    {
        "id": "bukhansan", "region": "eunpyeong",
        "name": "북한산 백운대 코스", "route": "백운대탐방지원센터 → 백운대 정상",
        "km": 4.2, "minutes": 190, "level": "중", "level_n": 2, "crowd": "보통", "view": 4,
        "peak": "백운대 836m", "grid_no": "다사 5683 2741", "gps": "37.6584,126.9778",
        "rescue_point": "백운산장 헬기장 620m", "fire_station": "서울 종로소방서 산악구조대",
        "steep": True,
        "elev": [120, 180, 260, 390, 480, 542, 650, 770, 836],
        "hazards": [
            {"at": 0.62, "type": "낙석주의", "grade": "산사태 1등급",
             "note": "최근 2주 강우 누적 — 우회로(백운산장 방면) 권장"},
            {"at": 0.38, "type": "급경사", "grade": "사고다발 구간",
             "note": "스틱 사용·심박 주의 (소방청 사고이력 학습)"},
        ],
    },
    {
        "id": "inwangsan", "region": "jongno",
        "name": "인왕산 자락길 둘레", "route": "사직공원 → 수성동계곡",
        "km": 2.8, "minutes": 100, "level": "하", "level_n": 1, "crowd": "낮음", "view": 3,
        "peak": "인왕산 338m", "grid_no": "다사 5421 2856", "gps": "37.5772,126.9610",
        "rescue_point": "황학정 진입로 280m", "fire_station": "서울 종로소방서",
        "steep": False,
        "elev": [60, 95, 140, 180, 210, 196, 170, 150, 130],
        "hazards": [
            {"at": 0.55, "type": "혼잡구간", "grade": "주말 정체", "note": "성곽길 합류 — 추월 자제"},
        ],
    },
    {
        "id": "achasan", "region": "guri",
        "name": "아차산 해맞이 능선", "route": "아차산생태공원 → 해맞이광장",
        "km": 3.5, "minutes": 140, "level": "하", "level_n": 1, "crowd": "보통", "view": 5,
        "peak": "아차산 287m", "grid_no": "마바 1043 1822", "gps": "37.5713,127.1030",
        "rescue_point": "해맞이광장 헬기포인트", "fire_station": "구리소방서",
        "steep": False,
        "elev": [40, 80, 130, 170, 210, 240, 262, 280, 287],
        "hazards": [
            {"at": 0.70, "type": "암릉구간", "grade": "주의", "note": "우천 시 미끄럼 — 난간 이용"},
        ],
    },
    {
        "id": "dobong", "region": "eunpyeong",
        "name": "도봉산 신선대 코스", "route": "도봉탐방지원센터 → 신선대",
        "km": 6.4, "minutes": 280, "level": "상", "level_n": 3, "crowd": "높음", "view": 5,
        "peak": "신선대 726m", "grid_no": "다사 6122 3354", "gps": "37.6987,127.0114",
        "rescue_point": "도봉대피소 410m", "fire_station": "도봉소방서 산악구조대",
        "steep": True,
        "elev": [110, 190, 300, 420, 510, 600, 660, 700, 726],
        "hazards": [
            {"at": 0.78, "type": "Y계곡 암릉", "grade": "사고다발", "note": "강풍 시 우회 권장 — 보조자일 구간"},
            {"at": 0.45, "type": "낙석주의", "grade": "산사태 2등급", "note": "헬멧 권장 구간"},
        ],
    },
]

SPECIES: list[dict] = [
    {
        "id": "mushroom", "name": "개나리광대버섯", "sci": "Amanita subjunquillea",
        "toxic": True, "confidence": 87,
        "desc": "아마톡신 함유 맹독성 — 소량 섭취로도 치명적. 식용 꾀꼬리버섯과 혼동 사고가 잦다.",
        "action": "절대 채취·섭취 금지. 만졌다면 흐르는 물에 손을 씻을 것.",
    },
    {
        "id": "flower", "name": "진달래", "sci": "Rhododendron mucronulatum",
        "toxic": False, "confidence": 96,
        "desc": "이른 봄 산능선을 물들이는 한국 대표 봄꽃. 화전 재료로도 쓰인다.",
        "action": "국립공원 내 채취 금지. 독성이 있는 철쭉과 혼동 주의.",
    },
    {
        "id": "berry", "name": "미국자리공 열매", "sci": "Phytolacca americana",
        "toxic": True, "confidence": 91,
        "desc": "포도송이처럼 보이지만 전초에 독이 있다. 산머루로 착각하기 쉽다.",
        "action": "섭취 금지. 어린이 동반 시 특히 주의.",
    },
]

# RAG 지식베이스 — LLM 모드에서 시스템 프롬프트에 주입되는 공공데이터 근거 문서
KNOWLEDGE_NOTES = """\
[산악사고 통계 — 소방청 구조활동(2022–2024)] 연평균 구조활동 10,443건(3년 누계 31,330건),
사망 325명·부상 6,348명. 인명피해의 54.1%가 주말 발생, 11~15시 최다.
[등산 인구 — 산림청 국민의식 실태조사(2022)] 성인 78%(3,229만 명)가 월 1회 이상 등산·숲길 체험.
60대 이상 91%로 전 연령 최고 — 고령층이 핵심 안전 취약층.
[하산 안전] 하산 사고가 등반보다 잦다. 스틱 사용으로 무릎 부담 완화 권장.
[고지대 기상] 산악 기온은 도심보다 5~8℃ 낮다. 산악기상관측망 실측치를 기준으로 보온 준비.
[독버섯] 개나리광대버섯 등 아마톡신류는 소량으로도 치명적. 야생 버섯은 채취·섭취 금지.
"""
