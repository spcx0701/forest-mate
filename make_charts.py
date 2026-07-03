# -*- coding: utf-8 -*-
"""숲길동무 제안서·PPT용 차트/다이어그램 생성"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import matplotlib.font_manager as fm

# 한글 폰트
for cand in ["Apple SD Gothic Neo", "AppleGothic", "NanumGothic"]:
    if any(cand in f.name for f in fm.fontManager.ttflist):
        plt.rcParams["font.family"] = cand
        break
plt.rcParams["axes.unicode_minus"] = False

OUT = "/Users/dong9733/Documents/LYT Kit 2/forest-mate/assets/"
PINE, MOSS, LEAF, MINT, PALE = "#1B4332", "#2D6A4F", "#40916C", "#74C69D", "#D8F3DC"
AMBER, RED, INK, SUB = "#F4A261", "#E63946", "#1d2b22", "#6b7f72"

def save(fig, name):
    fig.savefig(OUT + name, dpi=200, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("saved", name)

# ---------------------------------------------------------------- 1. 산악사고
fig, axes = plt.subplots(1, 3, figsize=(11.5, 3.4))
fig.suptitle("산악사고 현황 — 문제의 크기 (소방청 구조활동 분석, 2022–2024)", fontsize=13, fontweight="bold", color=INK, y=1.04)

ax = axes[0]
ax.axis("off")
ax.text(0.5, 0.62, "10,443", ha="center", fontsize=34, fontweight="bold", color=RED)
ax.text(0.5, 0.40, "연평균 산악사고 구조활동(건)", ha="center", fontsize=10.5, color=INK)
ax.text(0.5, 0.22, "3년 누계 31,330건", ha="center", fontsize=9.5, color=SUB)

ax = axes[1]
w = [54.1, 45.9]
ax.pie(w, labels=["주말 54.1%", "평일 45.9%"], colors=[AMBER, PALE],
       startangle=90, counterclock=False, wedgeprops={"width": 0.42, "edgecolor": "w"},
       textprops={"fontsize": 10, "color": INK})
ax.set_title("인명피해의 주말 집중도", fontsize=10.5, color=INK, pad=8)
ax.text(0, 0, "11~15시\n최다 발생", ha="center", va="center", fontsize=9, color=SUB)

ax = axes[2]
cats = ["사망", "부상"]
vals = [325, 6348]
bars = ax.barh(cats, vals, color=[RED, AMBER], height=0.52)
for b, v in zip(bars, vals):
    ax.text(b.get_width() * 1.02, b.get_y() + b.get_height() / 2, f"{v:,}명",
            va="center", fontsize=11, fontweight="bold", color=INK)
ax.set_xlim(0, 7600)
ax.set_title("최근 3년 인명피해 합계", fontsize=10.5, color=INK, pad=8)
ax.spines[["top", "right", "bottom"]].set_visible(False)
ax.tick_params(axis="x", labelsize=8, colors=SUB)
ax.tick_params(axis="y", labelsize=11)
fig.tight_layout()
save(fig, "chart_accident.png")

# ------------------------------------------------------- 2. 연령별 등산 인구
fig, ax = plt.subplots(figsize=(8.6, 3.9))
ages = ["20대", "30대", "40대", "50대", "60대 이상"]
vals = [59, 70, 71, 85, 91]
colors = [MINT, MINT, MINT, LEAF, AMBER]
bars = ax.bar(ages, vals, color=colors, width=0.58)
for b, v in zip(bars, vals):
    ax.text(b.get_x() + b.get_width() / 2, v + 1.5, f"{v}%", ha="center",
            fontsize=11, fontweight="bold", color=INK)
ax.axhline(78, ls="--", lw=1.2, color=SUB)
ax.text(4.45, 79.5, "성인 평균 78% (3,229만 명)", fontsize=9, color=SUB, ha="right")
ax.annotate("산행 인구 최다 = 안전 취약층", xy=(4, 91), xytext=(2.0, 99),
            fontsize=10.5, fontweight="bold", color="#B35309",
            arrowprops={"arrowstyle": "->", "color": "#B35309"})
ax.set_ylim(0, 106)
ax.set_ylabel("월 1회 이상 등산·숲길 체험 비율(%)", fontsize=9.5, color=SUB)
ax.set_title("연령대별 등산·숲길 체험 인구 — 고령층일수록 산에 많이 간다", fontsize=12.5, fontweight="bold", color=INK, pad=10)
ax.spines[["top", "right"]].set_visible(False)
ax.tick_params(labelsize=10)
fig.text(0.01, -0.03, "출처: 산림청 「등산 등 숲길 체험 국민의식 실태조사」(2022)", fontsize=8, color=SUB)
fig.tight_layout()
save(fig, "chart_population.png")

# ---------------------------------------------------------------- 3. 시장 규모
fig, ax = plt.subplots(figsize=(8.6, 3.6))
labels = ["TAM\n국내 아웃도어·등산 시장", "SAM\n산행 디지털 서비스·레저보험·관광", "SOM\n3년차 목표 (MAU 90만, 유료전환 6%)"]
vals = [70000, 4200, 50]
disp = ["약 7.0조 원", "약 4,200억 원", "약 50억 원"]
colors = [PALE, MINT, PINE]
y = [2, 1, 0]
for yi, v, c, d, l in zip(y, vals, colors, disp, labels):
    w = max(v / 70000, 0.018)
    ax.barh(yi, w, color=c, height=0.62, left=0)
    ax.text(w + 0.012, yi, d, va="center", fontsize=11.5, fontweight="bold", color=INK)
    ax.text(-0.012, yi, l, va="center", ha="right", fontsize=9.5, color=INK)
ax.set_xlim(0, 1.25)
ax.set_ylim(-0.55, 2.55)
ax.axis("off")
ax.set_title("시장 규모 (TAM–SAM–SOM)", fontsize=12.5, fontweight="bold", color=INK, pad=12, loc="left")
fig.text(0.01, -0.04, "출처: 업계 보도 기반 자체 추정(2024년 기준, 의류·장비 포함) · SAM은 앱·보험·관광 연계 서비스 추정치", fontsize=8, color=SUB)
fig.tight_layout()
save(fig, "chart_market.png")

# ---------------------------------------------------------------- 4. 매출 전망
fig, ax = plt.subplots(figsize=(8.6, 4.0))
years = ["1차년도\n(2026.7~)", "2차년도\n(2027)", "3차년도\n(2028)"]
sub_, ins_, b2g, ad = [1.2, 6.5, 18.0], [0.8, 4.2, 12.0], [1.5, 6.0, 14.0], [0.5, 1.8, 6.0]
import numpy as np
x = np.arange(3)
b1 = ax.bar(x, sub_, 0.5, label="프리미엄 구독", color=PINE)
b2 = ax.bar(x, ins_, 0.5, bottom=sub_, label="1일 안심보험 제휴", color=LEAF)
b3 = ax.bar(x, b2g, 0.5, bottom=np.array(sub_) + ins_, label="B2G 관제 SaaS", color=MINT)
b4 = ax.bar(x, ad, 0.5, bottom=np.array(sub_) + ins_ + b2g, label="제휴·커머스", color=AMBER)
tot = np.array(sub_) + ins_ + b2g + ad
for xi, t in zip(x, tot):
    ax.text(xi, t + 1.2, f"{t:.0f}억", ha="center", fontsize=12, fontweight="bold", color=INK)
ax2 = ax.twinx()
mau = [8, 35, 90]
ax2.plot(x, mau, "o--", color=RED, lw=2, ms=6)
for xi, m in zip(x, mau):
    ax2.text(xi + 0.07, m + 2, f"MAU {m}만", fontsize=9.5, color=RED, fontweight="bold")
ax2.set_ylim(0, 130)
ax2.axis("off")
ax.set_xticks(x, years, fontsize=10)
ax.set_ylim(0, 60)
ax.set_ylabel("매출(억 원)", fontsize=9.5, color=SUB)
ax.spines[["top", "right"]].set_visible(False)
ax.legend(loc="upper left", fontsize=9, frameon=False)
ax.set_title("3개년 매출 전망 — 수익원 4축", fontsize=12.5, fontweight="bold", color=INK, pad=10)
ax.tick_params(labelsize=9)
fig.tight_layout()
save(fig, "chart_revenue.png")

# ---------------------------------------------------------------- 5. 아키텍처
fig, ax = plt.subplots(figsize=(11.8, 6.4))
ax.set_xlim(0, 118)
ax.set_ylim(0, 64)
ax.axis("off")

def box(x, y, w, h, fc, text, fs=9.5, tc="white", ec="none", bold=True, sub=None):
    p = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.6,rounding_size=1.6",
                       fc=fc, ec=ec, lw=1.2)
    ax.add_patch(p)
    if sub:
        ax.text(x + w / 2, y + h * 0.66, text, ha="center", va="center", fontsize=fs,
                fontweight="bold" if bold else "normal", color=tc)
        ax.text(x + w / 2, y + h * 0.30, sub, ha="center", va="center", fontsize=fs - 2.1, color=tc, alpha=.88)
    else:
        ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=fs,
                fontweight="bold" if bold else "normal", color=tc)

def arrow(x1, y1, x2, y2, c=SUB):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle="-|>", mutation_scale=14, color=c, lw=1.6))

ax.text(1, 61.5, "숲길동무 시스템 구성도", fontsize=14, fontweight="bold", color=INK)

# Layer 1: 공공데이터
ax.text(1, 56.5, "① 산림 공공·빅데이터 (수집·동기화: 일 1회 + 실시간 API)", fontsize=10, fontweight="bold", color=MOSS)
srcs = [("등산로 공간정보", "산림청"), ("산불위험예보 API", "국립산림과학원"), ("산악기상관측망", "국립산림과학원"),
        ("산사태 위험지도", "산림청"), ("생물종지식정보", "국립수목원"), ("휴양림·치유의숲", "산림복지진흥원"),
        ("산악사고 현황", "소방청"), ("국가지점번호", "행정안전부"), ("단기예보 API", "기상청")]
for i, (t, s) in enumerate(srcs):
    box(1 + i * 13, 48, 11.6, 6.6, "#EAF4EC", t, fs=7.6, tc=INK, ec="#BFD9C6", sub=s)

# Layer 2: 파이프라인
box(1, 36.5, 36, 7.5, "#2B5876", "데이터 파이프라인", sub="ETL(Airflow) · 공간DB(PostGIS) · 피처 스토어", fs=10.5)
box(41, 36.5, 76, 7.5, "#264653", "AI 엔진 5종", fs=10.5,
    sub="①코스 추천(하이브리드 필터링)  ②조난위험 예측(시계열 이상탐지)  ③위험도 융합 스코어(XGBoost)  ④식물·독버섯 비전(EfficientNet)  ⑤RAG 숲해설 LLM")
for xx in [10, 60, 95]:
    arrow(xx, 47.4, xx, 44.8)

# Layer 3: 서빙
box(1, 25.5, 116, 7.2, PINE, "서비스 플랫폼 (AWS)", fs=10.5,
    sub="FastAPI 게이트웨이 · 실시간 위치 스트림(WebSocket) · 푸시(FCM/APNs) · 오프라인 지도팩 CDN · k-익명화 처리(위치정보법 준수)")
arrow(30, 35.9, 30, 33.5)
arrow(80, 35.9, 80, 33.5)

# Layer 4: 클라이언트
box(1, 13.5, 36, 7.6, MOSS, "모바일 앱 (Flutter)", sub="iOS·Android 동시 배포 / 온디바이스 AI(독버섯 1차 판별)", fs=10)
box(41, 13.5, 36, 7.6, LEAF, "관제 웹 (React)", sub="지자체·국립공원·소방 B2G 대시보드", fs=10)
box(81, 13.5, 36, 7.6, "#457B9D", "외부 연계", sub="119 신고(소방청) · 보험사 API · 숲나들e 예약", fs=10)
for xx in [19, 59, 99]:
    arrow(xx, 24.9, xx, 21.9)

# Layer 5: users
box(1, 3.5, 36, 6.4, "#F2F7F1", "등산객·가족 (3,229만 산행 인구)", fs=9.5, tc=INK, ec="#CBDCCB")
box(41, 3.5, 36, 6.4, "#F2F7F1", "지자체·국립공원공단·소방", fs=9.5, tc=INK, ec="#CBDCCB")
box(81, 3.5, 36, 6.4, "#F2F7F1", "보험사·아웃도어 브랜드·여행사", fs=9.5, tc=INK, ec="#CBDCCB")
for xx in [19, 59, 99]:
    arrow(xx, 12.9, xx, 10.7)
save(fig, "diagram_arch.png")

# ---------------------------------------------------------------- 6. 골든타임 플로우
fig, ax = plt.subplots(figsize=(11.5, 3.1))
ax.set_xlim(0, 115)
ax.set_ylim(0, 30)
ax.axis("off")
ax.text(1, 27, "AI 조난 대응 골든타임 — 시나리오 흐름", fontsize=13, fontweight="bold", color=INK)
steps = [
    ("① 감지", "이동 멈춤·코스 이탈·심박 이상\nAI 이상패턴 스코어링", MOSS),
    ("② 확인", "앱 푸시·전화 자동 확인\n무응답 시 단계 격상", LEAF),
    ("③ 전파", "보호자 + 119 상황실\n국가지점번호·좌표 자동 전송", AMBER),
    ("④ 구조", "최근접 구조거점 매칭\n관제 대시보드 실시간 추적", RED),
]
for i, (t, s, c) in enumerate(steps):
    box_x = 1 + i * 29
    p = FancyBboxPatch((box_x, 7), 25, 13, boxstyle="round,pad=0.6,rounding_size=1.8", fc=c, ec="none")
    ax.add_patch(p)
    ax.text(box_x + 12.5, 16.4, t, ha="center", fontsize=11.5, fontweight="bold", color="white")
    ax.text(box_x + 12.5, 11.2, s, ha="center", fontsize=8.2, color="white", linespacing=1.5)
    if i < 3:
        ax.add_patch(FancyArrowPatch((box_x + 26.2, 13.5), (box_x + 28.8, 13.5),
                                     arrowstyle="-|>", mutation_scale=16, color=SUB, lw=2))
ax.text(1, 2.2, "기대효과: 신고 공백(자력 신고 불가 상황) 제거 + 위치 특정 시간 단축 → 구조 도달시간 33분 → 23분 (▲30%, 자체 시뮬레이션 목표치)",
        fontsize=9.5, color="#B35309", fontweight="bold")
save(fig, "diagram_golden.png")

# ---------------------------------------------------------------- 7. 로드맵
fig, ax = plt.subplots(figsize=(11.5, 4.4))
ax.set_title("사업화 로드맵 (2026.7 ~ 2028)", fontsize=13, fontweight="bold", color=INK, pad=12, loc="left")
rows = [
    ("정식 출시 (iOS·Android·웹)", 0, 2, PINE),
    ("산림 데이터 커버리지 전국 확대", 1, 5, MOSS),
    ("1일 안심보험 제휴 출시", 2, 4, LEAF),
    ("B2G 관제 시범사업 (지자체 2곳)", 4, 8, AMBER),
    ("다국어 AI(영·중·일) — 인바운드 관광", 6, 10, MINT),
    ("국립공원공단·소방 연계 확대 (B2G 10곳)", 10, 16, "#457B9D"),
    ("시리즈 시드 투자 유치 · 글로벌(일본) PoC", 14, 18, "#264653"),
]
for i, (t, s, e, c) in enumerate(rows):
    y = len(rows) - i
    ax.barh(y, e - s, left=s, height=0.52, color=c)
    ax.text(s + 0.15, y, " " + t, va="center", fontsize=9.3, color="white" if c not in [MINT, PALE] else INK, fontweight="bold")
ax.set_xlim(0, 18)
ax.set_ylim(0.3, len(rows) + 0.9)
ax.set_yticks([])
ticks = [0, 2, 4, 6, 8, 10, 12, 14, 16, 18]
labels = ["26.7", "26.9", "26.11", "27.1", "27.3", "27.5", "27.7", "27.9", "27.11", "28.1+"]
ax.set_xticks(ticks, labels, fontsize=8.5)
ax.grid(axis="x", ls=":", alpha=.4)
ax.spines[["top", "right", "left"]].set_visible(False)
fig.tight_layout()
save(fig, "roadmap.png")

print("ALL DONE")
