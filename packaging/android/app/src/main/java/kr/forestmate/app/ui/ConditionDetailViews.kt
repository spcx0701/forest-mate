package kr.forestmate.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kr.forestmate.core.model.HikeIndex

/**
 * 산행지수 카드의 4개 하위 타일(산불·산사태·산악기상·일몰)을 누르면 열리는 상세 시트.
 *
 * `app/condition-details.js` + `app.js`(condition-panel)의 동작을 네이티브로 1:1 포팅:
 * 다크 블루 패널 · 히어로 게이지 · 근거 피드 · 6축 위험 벡터 레이더 · 신호 카드 그리드 ·
 * 출발 전 행동 가이드 · 출처. 라디언트/색상/문구는 디자인 zip과 동일.
 */
object ConditionDetailViews {

    // accent per condition (condition-details.js)
    private const val FIRE = 0xFFFF9F43.toInt()
    private const val LAND = 0xFF74C69D.toInt()
    private const val WX = 0xFF4CC9F0.toInt()
    private const val SUN = 0xFFFFD166.toInt()

    private const val PANEL_INK = 0xFFEAF4FF.toInt()
    private const val PANEL_SUB = 0xB8EAF4FF.toInt()

    data class Metric(val label: String, val value: String, val note: String)
    data class Axis(val label: String, val value: Int, val note: String)
    data class Signal(val label: String, val value: String, val note: String, val level: String)
    data class Tile(val id: String, val label: String, val value: String, val tone: String)
    data class Detail(
        val id: String,
        val icon: String,
        val title: String,
        val heroValue: String,
        val summary: String,
        val accent: Int,
        val metrics: List<Metric>,
        val axes: List<Axis>,
        val cards: List<Signal>,
        val guidance: String,
        val source: String,
        val modeLabel: String,
        val updatedAt: String,
        val score: Int,
    )

    // --- numeric helpers (ported from condition-details.js) -----------------
    private fun clamp(v: Double, lo: Double = 0.0, hi: Double = 100.0) = max(lo, min(hi, v))
    private fun windRisk(wind: Double) = clamp(wind * 12)
    private fun tempBurden(temp: Double) = clamp(abs(temp - 18) * 6)
    private fun mix(a: Double, b: Double, aw: Double = 0.5) = clamp(a * aw + b * (1 - aw))
    private fun fmtWind(w: Double) = if (w % 1.0 != 0.0) "%.1fm/s".format(w) else "${w.toInt()}m/s"

    private fun statusWord(score: Int) = when {
        score >= 80 -> "안정"; score >= 60 -> "주의"; else -> "위험"
    }
    private fun scoreTone(score: Int) = when {
        score >= 80 -> "ok"; score >= 60 -> "mid"; else -> "bad2"
    }
    private fun fireScoreOf(level: String): Int = when {
        level.contains("매우") -> 30
        level.contains("높") -> 45
        level.contains("보통") -> 65
        level.contains("낮") -> 80
        else -> 70
    }

    private fun sunsetMinutes(at: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(at) ?: return null
        val h = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
        val now = java.util.Calendar.getInstance()
        val sunset = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, mm)
            set(java.util.Calendar.SECOND, 0)
        }
        return ((sunset.timeInMillis - now.timeInMillis) / 60000L).toInt()
    }
    private fun sunsetMargin(at: String): String {
        val mins = sunsetMinutes(at) ?: return "일몰 시각 확인 필요"
        if (mins <= 0) return "이미 일몰 이후"
        val h = mins / 60; val m = mins % 60
        return if (h > 0) "${h}시간 ${m.toString().padStart(2, '0')}분 남음" else "${m}분 남음"
    }
    private fun sunsetPressure(at: String): Double {
        val mins = sunsetMinutes(at) ?: return 55.0
        if (mins <= 0) return 100.0
        return clamp(100 - (mins / 240.0) * 100)
    }

    // --- context derived from HikeIndex (falls back to home defaults) --------
    private class Ctx(idx: HikeIndex?) {
        val fireLevel = idx?.fireLevel?.ifBlank { "보통" } ?: "보통"
        val fireScore = fireScoreOf(fireLevel)
        val temp = idx?.temperatureC ?: 18.0
        val wind = idx?.windMps ?: 2.0
        val rain = idx?.rainProbability ?: 10
        val station = idx?.let { it.regionName.ifBlank { it.place } } ?: "산악기상관측망"
        val wxLabel = idx?.weatherLabel?.ifBlank { "관측" } ?: "관측"
        val lsGrade = 5; val lsScore = 82; val lsLabel = "안전"
        val sunsetAt = "19:52"
        val region = idx?.regionName?.ifBlank { "현재 지역" } ?: "현재 지역"
        val place = idx?.let { it.place.ifBlank { it.regionName } } ?: "서울 은평구"
        val score = idx?.score ?: 0
        val live = idx != null
    }

    fun summaryTiles(idx: HikeIndex?): List<Tile> {
        val c = Ctx(idx)
        return listOf(
            Tile("fire", "산불위험", c.fireLevel, scoreTone(c.fireScore)),
            Tile("landslide", "산사태", c.lsLabel, scoreTone(c.lsScore)),
            Tile("weather", "산악기상", "${c.temp.toInt()}°C", scoreTone(((100 - tempBurden(c.temp)).toInt()))),
            Tile("sunset", "일몰", c.sunsetAt, scoreTone((100 - sunsetPressure(c.sunsetAt)).toInt())),
        )
    }

    fun build(id: String, idx: HikeIndex?): Detail {
        val c = Ctx(idx)
        val mode = if (c.live) "LIVE" else "SNAPSHOT"
        val updated = if (c.live) "실시간 API 갱신" else "오프라인 스냅샷"
        val rain = c.rain.toDouble()
        return when (id) {
            "fire" -> {
                val risk = clamp(100.0 - c.fireScore)
                val dry = clamp(100.0 - rain)
                val wind = windRisk(c.wind)
                Detail(
                    "fire", "🔥", "산불위험", c.fireLevel.ifBlank { "확인 필요" },
                    "${c.place} 기준 산불 위험 단계입니다. 마른 낙엽, 강풍, 취사·흡연 여부가 실제 체감 위험을 크게 바꿉니다.",
                    FIRE,
                    listOf(
                        Metric("위험 단계", c.fireLevel, "예보 단계"),
                        Metric("확산 바람", fmtWind(c.wind), if (c.wind >= 7) "강풍 유의" else "보통"),
                        Metric("건조 신호", "${c.rain}% 강수", if (c.rain < 20) "매우 건조" else "완화 가능"),
                    ),
                    listOf(
                        Axis("예보위험", risk.toInt(), c.fireLevel),
                        Axis("건조압력", dry.toInt(), "${c.rain}% 강수"),
                        Axis("확산바람", wind.toInt(), fmtWind(c.wind)),
                        Axis("화기민감", mix(risk, dry, 0.55).toInt(), "취사·흡연 주의"),
                        Axis("신고필요", mix(risk, wind, 0.6).toInt(), "연기·탄 냄새"),
                        Axis("진입통제", mix(risk, 100.0 - c.score, 0.65).toInt(), "통제 안내"),
                    ),
                    listOf(
                        Signal("위험 단계", c.fireLevel, "산불위험예보", if (risk >= 45) "warn" else "safe"),
                        Signal("확산 바람", fmtWind(c.wind), "능선부 민감 신호", if (c.wind >= 7) "warn" else "neutral"),
                        Signal("건조 완화", "${c.rain}%", "강수가 낮을수록 불리", if (c.rain < 20) "warn" else "safe"),
                        Signal("지도 기준", c.region, "시군구/격자 예보", "neutral"),
                        Signal("비교 산", "1곳", "지역/산별 분포", "neutral"),
                        Signal("코스 판단", if (risk >= 45) "대체 권장" else "진행 가능", "출발 전 선택", if (risk >= 45) "warn" else "safe"),
                    ),
                    "방문할 산과 주변 지역의 산불 단계가 높으면 위험이 낮은 다른 산이나 짧은 코스로 바꾸세요.",
                    "국립산림과학원 산불위험예보", mode, updated, c.score,
                )
            }
            "landslide" -> {
                val gradeRisk = clamp((6 - c.lsGrade) * 20.0)
                val slopeRisk = clamp(100.0 - c.lsScore)
                Detail(
                    "landslide", "⛰", "산사태", "${c.lsLabel} · ${c.lsGrade}등급",
                    "${c.place} 주변 사면의 산사태 위험지도와 최근 강우 영향을 함께 봐야 합니다. 계곡길·절개지·낙석 구간에서는 등급이 낮아도 보수적으로 움직이세요.",
                    LAND,
                    listOf(
                        Metric("지도 등급", "${c.lsGrade}등급", "지역 위험지도"),
                        Metric("상태", c.lsLabel, statusWord(c.lsScore)),
                        Metric("강수 영향", "${c.rain}%", if (c.rain >= 30) "최근/예상 강수 주의" else "낮음"),
                    ),
                    listOf(
                        Axis("지도위험", gradeRisk.toInt(), "${c.lsGrade}등급"),
                        Axis("강우압력", rain.toInt(), "예상 강수"),
                        Axis("사면불안", slopeRisk.toInt(), c.lsLabel),
                        Axis("계곡주의", mix(gradeRisk, rain, 0.55).toInt(), "물길 주변"),
                        Axis("낙석주의", mix(gradeRisk, windRisk(c.wind), 0.7).toInt(), "절개지·암릉"),
                        Axis("우회필요", mix(slopeRisk, rain, 0.62).toInt(), "대체 하산로"),
                    ),
                    listOf(
                        Signal("위험지도", "${c.lsGrade}등급", "산사태정보시스템", if (c.lsGrade <= 2) "warn" else "safe"),
                        Signal("상태", c.lsLabel, statusWord(c.lsScore), if (c.lsScore >= 80) "safe" else "warn"),
                        Signal("강수 영향", "${c.rain}%", "최근/예상 강수 신호", if (c.rain >= 30) "warn" else "neutral"),
                        Signal("지도 기준", c.region, "시군구/격자", "neutral"),
                        Signal("비교 산", "1곳", "지역/산별 분포", "neutral"),
                        Signal("코스 판단", if (c.lsGrade <= 2) "대체 권장" else "진행 가능", "출발 전 선택", if (c.lsGrade <= 2) "warn" else "safe"),
                    ),
                    "비 예보가 있거나 전날 비가 왔다면 산사태 등급이 높은 지역의 산은 후보에서 제외하세요.",
                    "산사태정보시스템 위험지도 · 산림청 등산로 위험구간", mode, updated, c.score,
                )
            }
            "weather" -> {
                val wind = windRisk(c.wind); val temp = tempBurden(c.temp)
                val volatility = clamp(100.0 - 64)
                Detail(
                    "weather", "🌦", "산악기상", "${c.temp.toInt()}°C · ${c.wxLabel}",
                    "${c.station} 기준입니다. 산 정상과 능선은 도심보다 춥고 바람이 강해 체감온도가 빠르게 떨어질 수 있습니다.",
                    WX,
                    listOf(
                        Metric("기온", "${c.temp.toInt()}°C", "능선부 기준"),
                        Metric("풍속", fmtWind(c.wind), if (c.wind >= 7) "강풍 유의" else "보통"),
                        Metric("강수확률", "${c.rain}%", if (c.rain >= 30) "우의 준비" else "낮음"),
                    ),
                    listOf(
                        Axis("강풍", wind.toInt(), fmtWind(c.wind)),
                        Axis("비구름", rain.toInt(), "${c.rain}% 강수"),
                        Axis("체감냉각", temp.toInt(), "${c.temp.toInt()}°C"),
                        Axis("시야저하", mix(rain, volatility, 0.55).toInt(), c.wxLabel),
                        Axis("변덕성", volatility.toInt(), "예보 불확실성"),
                        Axis("노면미끄럼", mix(rain, temp, 0.72).toInt(), "암릉·데크"),
                    ),
                    listOf(
                        Signal("기온", "${c.temp.toInt()}°C", c.wxLabel, "neutral"),
                        Signal("풍속", fmtWind(c.wind), if (c.wind >= 7) "강풍 유의" else "보통", if (c.wind >= 7) "warn" else "safe"),
                        Signal("강수확률", "${c.rain}%", if (c.rain >= 30) "우의 준비" else "낮음", if (c.rain >= 30) "warn" else "safe"),
                        Signal("관측소", c.station, "위치 기준", "neutral"),
                        Signal("비교 산", "1곳", "지역/산별 분포", "neutral"),
                        Signal("코스 길이", if (c.rain >= 30 || c.wind >= 7) "짧게" else "보통", "출발 전 선택", if (c.rain >= 30 || c.wind >= 7) "warn" else "safe"),
                    ),
                    "출발 전 방문할 산의 관측소 기준 풍속과 강수확률을 보고 복장과 코스 길이를 정하세요.",
                    "기상청 단기예보 · 산악기상관측망", mode, updated, c.score,
                )
            }
            "sunset" -> {
                val pressure = sunsetPressure(c.sunsetAt)
                val mins = sunsetMinutes(c.sunsetAt)
                val afterDark = mins != null && mins <= 0
                val shortMargin = mins != null && mins < 120
                val nightTransition = if (afterDark) 100.0 else mix(pressure, if (shortMargin) 70.0 else 20.0, 0.7)
                Detail(
                    "sunset", "🌄", "일몰", c.sunsetAt,
                    "${c.place} 기준 일몰 시각입니다. 하산은 정상 도착 시간이 아니라 마지막 갈림길·대중교통·주차장 도착 시간까지 포함해서 판단해야 합니다.",
                    SUN,
                    listOf(
                        Metric("일몰 시각", c.sunsetAt, "지역 기준"),
                        Metric("남은 시간", sunsetMargin(c.sunsetAt), "현재 기기 시간 기준"),
                        Metric("전환 기준", "16시 전", "새 코스 진입 마감"),
                    ),
                    listOf(
                        Axis("시간압박", pressure.toInt(), sunsetMargin(c.sunsetAt)),
                        Axis("하산여유부족", if (shortMargin) 78 else pressure.toInt(), "주차장·교통까지"),
                        Axis("야간전환", nightTransition.toInt(), "시야 저하"),
                        Axis("장비필요", if (shortMargin) 85 else 35, "헤드랜턴·보온"),
                        Axis("갈림길주의", mix(pressure, 60.0, 0.55).toInt(), "하산로 판단"),
                        Axis("교통마감", mix(pressure, if (shortMargin) 72.0 else 34.0, 0.52).toInt(), "귀가 시간"),
                    ),
                    listOf(
                        Signal("일몰", c.sunsetAt, "지역 기준", "neutral"),
                        Signal("남은 시간", sunsetMargin(c.sunsetAt), "기기 시간 기준", if (shortMargin) "warn" else "safe"),
                        Signal("전환 기준", "16시 전", "새 코스 진입 마감", "warn"),
                        Signal("준비물", "헤드랜턴", "보조배터리·보온층", if (shortMargin) "warn" else "neutral"),
                        Signal("비교 산", "1곳", "지역/산별 일몰", "neutral"),
                        Signal("코스 판단", if (shortMargin) "짧게" else "진행 가능", "출발 전 선택", if (shortMargin) "warn" else "safe"),
                    ),
                    "출발 전에 예상 종료 시각이 일몰 1시간 전인지 확인하고, 아니면 더 짧은 코스를 고르세요.",
                    "지역별 일몰 시각 · 현재 위치 기준", mode, updated, c.score,
                )
            }
            else -> throw IllegalArgumentException("unknown condition: $id")
        }
    }

    // --- panel view ---------------------------------------------------------
    fun panel(context: Context, d: Detail, onClose: () -> Unit): View {
        fun dp(v: Float) = Contour.dp(context, v)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF11375F.toInt(), 0xFF12345A.toInt(), 0xFF162F4D.toInt()),
            ).apply { cornerRadii = floatArrayOf(dp(24f).toFloat(), dp(24f).toFloat(), dp(24f).toFloat(), dp(24f).toFloat(), 0f, 0f, 0f, 0f) }
            setPadding(dp(16f), dp(18f), dp(16f), dp(26f))
        }

        // topline: mode + updated pills, close ×
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(pill(context, d.modeLabel))
            addView(pill(context, d.updatedAt).apply {
                (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(8f)
            })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextView(context).apply {
                text = "×"; textSize = 24f; setTextColor(PANEL_INK)
                setPadding(dp(10f), 0, dp(4f), 0)
                setOnClickListener { onClose() }
            })
        })

        // hero: kicker + value | gauge
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f), 0, dp(2f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "${d.icon} ${d.title}"; textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt()); typeface = Contour.black()
                })
                addView(TextView(context).apply {
                    text = d.heroValue; textSize = 30f; setTextColor(0xFFFFFFFF.toInt())
                    typeface = Contour.black(); setPadding(0, dp(4f), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(GaugeView(context, d.score, d.accent), LinearLayout.LayoutParams(dp(76f), dp(76f)))
        })

        root.addView(TextView(context).apply {
            text = d.summary; textSize = 12f; setTextColor(PANEL_SUB)
            setLineSpacing(dp(3f).toFloat(), 1f); setPadding(0, dp(8f), 0, dp(12f))
        })

        // feed chips (metrics as evidence row)
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                d.metrics.forEachIndexed { i, m ->
                    addView(feedChip(context, m, d.accent), LinearLayout.LayoutParams(dp(128f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        if (i > 0) leftMargin = dp(8f)
                    })
                }
            })
        })

        // radar
        root.addView(chartHead(context, "현재 위험 벡터", "높을수록 주의"))
        root.addView(RadarChartView(context, d.axes, d.accent), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200f)).apply {
            topMargin = dp(4f)
        })
        root.addView(axisLegend(context, d.axes))

        // signal cards grid (2-col)
        root.addView(chartHead(context, "신호 카드", "출발 전 점검"))
        root.addView(cardGrid(context, d.cards))

        // guide
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(context, 0x47051426, radiusDp = 16f, stroke = 0x1FFFFFFF, strokeDp = 1f)
            setPadding(dp(13f), dp(12f), dp(13f), dp(12f))
            (layoutParamsOrSet(this)).topMargin = dp(12f)
            addView(TextView(context).apply {
                text = "출발 전 확인"; textSize = 12.5f; setTextColor(0xFFFFFFFF.toInt()); typeface = Contour.black()
            })
            addView(TextView(context).apply {
                text = d.guidance; textSize = 12f; setTextColor(0xD1EAF4FF.toInt())
                setLineSpacing(dp(3f).toFloat(), 1f); setPadding(0, dp(8f), 0, 0)
            })
        })

        root.addView(TextView(context).apply {
            text = d.source; textSize = 10f; typeface = Contour.mono(); setTextColor(0x8AEAF4FF.toInt())
            setPadding(0, dp(12f), 0, 0)
        })
        return root
    }

    private fun layoutParamsOrSet(v: View): LinearLayout.LayoutParams {
        val lp = (v.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        v.layoutParams = lp
        return lp
    }

    private fun pill(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text; textSize = 10f; typeface = Contour.black()
            setTextColor(0xB8EAF4FF.toInt())
            background = Contour.pill(context, 0x1AFFFFFF, stroke = 0x1AFFFFFF, strokeDp = 1f)
            setPadding(Contour.dp(context, 8f), Contour.dp(context, 4f), Contour.dp(context, 8f), Contour.dp(context, 4f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

    private fun chartHead(context: Context, title: String, scale: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Contour.dp(context, 16f), 0, Contour.dp(context, 2f))
            addView(TextView(context).apply {
                text = title; textSize = 13f; setTextColor(0xFFFFFFFF.toInt()); typeface = Contour.black()
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = scale; textSize = 10f; setTextColor(0x9EEAF4FF.toInt())
            })
        }

    private fun feedChip(context: Context, m: Metric, accent: Int): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(context, 0x1FFFFFFF, radiusDp = 14f, stroke = 0x21FFFFFF, strokeDp = 1f)
            setPadding(Contour.dp(context, 10f), Contour.dp(context, 9f), Contour.dp(context, 10f), Contour.dp(context, 9f))
            addView(TextView(context).apply {
                text = m.label; textSize = 9f; typeface = Contour.black(); setTextColor(0xFF102C4D.toInt())
                background = Contour.pill(context, accent)
                setPadding(Contour.dp(context, 6f), Contour.dp(context, 2f), Contour.dp(context, 6f), Contour.dp(context, 2f))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = m.value; textSize = 13f; setTextColor(0xFFFFFFFF.toInt()); typeface = Contour.bold()
                setSingleLine(true); setPadding(0, Contour.dp(context, 6f), 0, 0)
            })
            addView(TextView(context).apply {
                text = m.note; textSize = 10f; setTextColor(PANEL_SUB); setSingleLine(true)
                setPadding(0, Contour.dp(context, 2f), 0, 0)
            })
        }

    private fun axisLegend(context: Context, axes: List<Axis>): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Contour.dp(context, 8f), 0, 0)
            axes.chunked(2).forEach { pair ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    pair.forEach { a ->
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = Contour.round(context, 0x14FFFFFF, radiusDp = 12f, stroke = 0x17FFFFFF, strokeDp = 1f)
                            setPadding(Contour.dp(context, 8f), Contour.dp(context, 7f), Contour.dp(context, 8f), Contour.dp(context, 7f))
                            addView(TextView(context).apply {
                                text = a.label; textSize = 9.5f; typeface = Contour.black(); setTextColor(0xA8EAF4FF.toInt()); setSingleLine(true)
                            })
                            addView(TextView(context).apply {
                                text = a.value.toString(); textSize = 16f; setTextColor(0xFFFFFFFF.toInt())
                                setPadding(0, Contour.dp(context, 3f), 0, 0)
                            })
                            addView(TextView(context).apply {
                                text = a.note; textSize = 9.5f; setTextColor(0xA1EAF4FF.toInt()); setSingleLine(true)
                                setPadding(0, Contour.dp(context, 2f), 0, 0)
                            })
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            rightMargin = Contour.dp(context, 6f); topMargin = Contour.dp(context, 6f)
                        })
                    }
                })
            }
        }

    private fun cardGrid(context: Context, cards: List<Signal>): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Contour.dp(context, 4f), 0, 0)
            cards.chunked(2).forEach { pair ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    pair.forEach { c ->
                        val bg = when (c.level) {
                            "safe" -> 0x3352B788
                            "warn" -> 0x33F4A261
                            else -> 0x1FFFFFFF
                        }
                        addView(LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = Contour.round(context, bg, radiusDp = 15f, stroke = 0x1FFFFFFF, strokeDp = 1f)
                            setPadding(Contour.dp(context, 11f), Contour.dp(context, 11f), Contour.dp(context, 11f), Contour.dp(context, 11f))
                            addView(TextView(context).apply {
                                text = c.label; textSize = 10f; typeface = Contour.black(); setTextColor(0xA3EAF4FF.toInt())
                            })
                            addView(TextView(context).apply {
                                text = c.value; textSize = 19f; setTextColor(0xFFFFFFFF.toInt()); setSingleLine(true)
                                setPadding(0, Contour.dp(context, 5f), 0, 0)
                            })
                            addView(TextView(context).apply {
                                text = c.note; textSize = 10.5f; setTextColor(PANEL_SUB)
                                setLineSpacing(Contour.dp(context, 2f).toFloat(), 1f); setPadding(0, Contour.dp(context, 6f), 0, 0)
                            })
                        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            rightMargin = Contour.dp(context, 8f); topMargin = Contour.dp(context, 8f)
                        })
                    }
                })
            }
        }

    /** 6축 위험 벡터 레이더 (conditionRadarSvg 포팅). */
    class RadarChartView(context: Context, private val axes: List<Axis>, private val accent: Int) : View(context) {
        private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF; strokeWidth = 1.5f }
        private val spoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x26FFFFFF; strokeWidth = 1.2f }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = (accent and 0x00FFFFFF) or 0x66000000 }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = accent; strokeWidth = 2.4f }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = accent }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCEAF4FF.toInt(); textAlign = Paint.Align.CENTER }

        override fun onDraw(canvas: Canvas) {
            val n = axes.size.coerceAtLeast(1)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(width, height) * 0.34f
            val labelR = radius * 1.42f
            label.textSize = min(width, height) * 0.052f

            fun ang(i: Int) = (-Math.PI / 2 + (Math.PI * 2 * i) / n)
            // rings
            for (scale in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                val p = Path()
                for (i in 0 until n) {
                    val a = ang(i)
                    val x = cx + cos(a).toFloat() * radius * scale
                    val y = cy + sin(a).toFloat() * radius * scale
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); canvas.drawPath(p, grid)
            }
            // spokes
            for (i in 0 until n) {
                val a = ang(i)
                canvas.drawLine(cx, cy, cx + cos(a).toFloat() * radius, cy + sin(a).toFloat() * radius, spoke)
            }
            // value polygon
            val shape = Path()
            val pts = ArrayList<Pair<Float, Float>>()
            for (i in 0 until n) {
                val a = ang(i)
                val v = axes[i].value.coerceIn(0, 100) / 100f
                val x = cx + cos(a).toFloat() * radius * v
                val y = cy + sin(a).toFloat() * radius * v
                pts.add(x to y)
                if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
            }
            shape.close()
            canvas.drawPath(shape, fill)
            canvas.drawPath(shape, line)
            pts.forEach { canvas.drawCircle(it.first, it.second, label.textSize * 0.28f, dot) }
            // labels
            for (i in 0 until n) {
                val a = ang(i)
                val x = cx + cos(a).toFloat() * labelR
                val y = cy + sin(a).toFloat() * labelR + label.textSize * 0.35f
                canvas.drawText(axes[i].label, x, y.coerceIn(label.textSize, height - label.textSize), label)
            }
        }
    }

    /** 산행지수 게이지 (accent arc + 중앙 숫자). */
    private class GaugeView(context: Context, private val score: Int, private val accent: Int) : View(context) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x29FFFFFF }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = accent; strokeCap = Paint.Cap.ROUND }
        private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Contour.black() }
        private val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB8EAF4FF.toInt(); textAlign = Paint.Align.CENTER }
        private val oval = RectF()
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val sw = w * 0.12f
            track.strokeWidth = sw; arc.strokeWidth = sw
            val pad = sw / 2f + 1f
            oval.set(pad, pad, w - pad, h - pad)
            canvas.drawArc(oval, 0f, 360f, false, track)
            canvas.drawArc(oval, -90f, score.coerceIn(0, 100) / 100f * 360f, false, arc)
            num.textSize = h * 0.30f
            canvas.drawText(score.toString(), w / 2f, h / 2f - (num.descent() + num.ascent()) / 2f - h * 0.04f, num)
            cap.textSize = h * 0.11f
            canvas.drawText("산행지수", w / 2f, h * 0.74f, cap)
        }
    }
}
