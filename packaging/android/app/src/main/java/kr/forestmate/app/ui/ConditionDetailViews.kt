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
import kr.forestmate.app.AppLanguage
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
    private const val EN_SPREAD_WIND = "Spread wind"
    private const val EN_STRONG_WIND_CAUTION = "Strong-wind caution"
    private const val EN_COMPARED_PEAKS = "Compared peaks"
    private const val EN_REGIONAL_DISTRIBUTION = "Regional distribution"
    private const val EN_ROUTE_CALL = "Route call"
    private const val EN_BEFORE_DEPARTURE = "Before departure"
    private const val KO_REGIONAL_DISTRIBUTION = "지역/산별 분포"
    private const val KO_BEFORE_DEPARTURE = "출발 전 선택"

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

    private fun statusWord(score: Int, language: AppLanguage = AppLanguage.KOREAN) = when {
        language == AppLanguage.ENGLISH && score >= 80 -> "Stable"
        language == AppLanguage.ENGLISH && score >= 60 -> "Caution"
        language == AppLanguage.ENGLISH -> "Risky"
        score >= 80 -> "안정"
        score >= 60 -> "주의"
        else -> "위험"
    }
    private fun scoreTone(score: Int) = when {
        score >= 80 -> "ok"; score >= 60 -> "mid"; else -> "bad2"
    }
    private fun fireScoreOf(level: String): Int = when {
        level.contains("Very", ignoreCase = true) -> 30
        level.contains("High", ignoreCase = true) -> 45
        level.contains("Moderate", ignoreCase = true) -> 65
        level.contains("Low", ignoreCase = true) -> 80
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
    private fun sunsetMargin(at: String, language: AppLanguage = AppLanguage.KOREAN): String {
        val mins = sunsetMinutes(at) ?: return if (language == AppLanguage.ENGLISH) "Sunset time needs checking" else "일몰 시각 확인 필요"
        if (mins <= 0) return if (language == AppLanguage.ENGLISH) "Already after sunset" else "이미 일몰 이후"
        val h = mins / 60; val m = mins % 60
        if (language == AppLanguage.ENGLISH) return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m left" else "${m}m left"
        return if (h > 0) "${h}시간 ${m.toString().padStart(2, '0')}분 남음" else "${m}분 남음"
    }
    private fun sunsetPressure(at: String): Double {
        val mins = sunsetMinutes(at) ?: return 55.0
        if (mins <= 0) return 100.0
        return clamp(100 - (mins / 240.0) * 100)
    }

    // --- context derived from HikeIndex (falls back to home defaults) --------
    private class Ctx(idx: HikeIndex?, val language: AppLanguage = AppLanguage.KOREAN) {
        val fireLevel = localizeFireLevel(idx?.fireLevel?.ifBlank { "보통" } ?: "보통", language)
        val fireScore = fireScoreOf(fireLevel)
        val temp = idx?.temperatureC ?: 18.0
        val wind = idx?.windMps ?: 2.0
        val rain = idx?.rainProbability ?: 10
        val station = idx?.let { it.regionName.ifBlank { it.place } } ?: if (language == AppLanguage.ENGLISH) "Mountain weather station" else "산악기상관측망"
        val wxLabel = localizeWeather(idx?.weatherLabel?.ifBlank { "관측" } ?: "관측", language)
        val lsGrade = 5; val lsScore = 82; val lsLabel = if (language == AppLanguage.ENGLISH) "Safe" else "안전"
        val sunsetAt = "19:52"
        val region = idx?.regionName?.ifBlank { if (language == AppLanguage.ENGLISH) "Current region" else "현재 지역" } ?: if (language == AppLanguage.ENGLISH) "Current region" else "현재 지역"
        val place = idx?.let { it.place.ifBlank { it.regionName } } ?: if (language == AppLanguage.ENGLISH) "Eunpyeong, Seoul" else "서울 은평구"
        val score = idx?.score ?: 0
        val live = idx != null
    }

    private fun localizeFireLevel(level: String, language: AppLanguage): String =
        if (language == AppLanguage.KOREAN) level else when {
            level.contains("매우") -> "Very high"
            level.contains("높") -> "High"
            level.contains("보통") -> "Moderate"
            level.contains("낮") -> "Low"
            else -> level
        }

    private fun localizeWeather(label: String, language: AppLanguage): String =
        if (language == AppLanguage.KOREAN) label else when {
            label.contains("맑") -> "Clear"
            label.contains("흐") -> "Cloudy"
            label.contains("비") -> "Rain"
            label.contains("눈") -> "Snow"
            label.contains("관측") -> "Observed"
            else -> label
        }

    fun summaryTiles(idx: HikeIndex?, language: AppLanguage = AppLanguage.KOREAN): List<Tile> {
        val c = Ctx(idx, language)
        return listOf(
            Tile("fire", tr(c, "산불위험", "Wildfire"), c.fireLevel, scoreTone(c.fireScore)),
            Tile("landslide", tr(c, "산사태", "Landslide"), c.lsLabel, scoreTone(c.lsScore)),
            Tile("weather", tr(c, "산악기상", "Mountain wx"), "${c.temp.toInt()}°C", scoreTone((100 - tempBurden(c.temp)).toInt())),
            Tile("sunset", tr(c, "일몰", "Sunset"), c.sunsetAt, scoreTone((100 - sunsetPressure(c.sunsetAt)).toInt())),
        )
    }

    fun build(id: String, idx: HikeIndex?, language: AppLanguage = AppLanguage.KOREAN): Detail {
        val c = Ctx(idx, language)
        return when (id) {
            "fire" -> buildFire(c)
            "landslide" -> buildLandslide(c)
            "weather" -> buildWeather(c)
            "sunset" -> buildSunset(c)
            else -> throw IllegalArgumentException("unknown condition: $id")
        }
    }

    private fun tr(c: Ctx, ko: String, en: String): String =
        if (c.language == AppLanguage.ENGLISH) en else ko

    private fun modeLabel(c: Ctx) = if (c.live) "LIVE" else "SNAPSHOT"

    private fun updatedAt(c: Ctx): String =
        if (c.live) tr(c, "실시간 API 갱신", "Updated from live API") else tr(c, "오프라인 스냅샷", "Offline snapshot")

    private fun rainText(c: Ctx, suffix: String = tr(c, "강수", "rain")) = "${c.rain}% $suffix"

    private fun strongWindNote(c: Ctx) =
        if (c.wind >= 7) tr(c, "강풍 유의", EN_STRONG_WIND_CAUTION) else tr(c, "보통", "Moderate")

    private fun buildFire(c: Ctx): Detail {
        val risk = clamp(100.0 - c.fireScore)
        val dry = clamp(100.0 - c.rain)
        val wind = windRisk(c.wind)
        return Detail(
            "fire", "🔥", tr(c, "산불위험", "Wildfire"), c.fireLevel.ifBlank { tr(c, "확인 필요", "Needs check") },
            tr(
                c,
                "${c.place} 기준 산불 위험 단계입니다. 마른 낙엽, 강풍, 취사·흡연 여부가 실제 체감 위험을 크게 바꿉니다.",
                "Wildfire risk for ${c.place}. Dry leaves, strong wind, cooking, and smoking can change the felt risk quickly.",
            ),
            FIRE,
            listOf(
                Metric(tr(c, "위험 단계", "Risk level"), c.fireLevel, tr(c, "예보 단계", "Forecast tier")),
                Metric(tr(c, "확산 바람", EN_SPREAD_WIND), fmtWind(c.wind), strongWindNote(c)),
                Metric(tr(c, "건조 신호", "Dry signal"), rainText(c), if (c.rain < 20) tr(c, "매우 건조", "Very dry") else tr(c, "완화 가능", "May ease")),
            ),
            listOf(
                Axis(tr(c, "예보위험", "Forecast"), risk.toInt(), c.fireLevel),
                Axis(tr(c, "건조압력", "Dryness"), dry.toInt(), rainText(c)),
                Axis(tr(c, "확산바람", EN_SPREAD_WIND), wind.toInt(), fmtWind(c.wind)),
                Axis(tr(c, "화기민감", "Fire care"), mix(risk, dry, 0.55).toInt(), tr(c, "취사·흡연 주의", "No cooking/smoking")),
                Axis(tr(c, "신고필요", "Report need"), mix(risk, wind, 0.6).toInt(), tr(c, "연기·탄 냄새", "Smoke or burnt smell")),
                Axis(tr(c, "진입통제", "Access limit"), mix(risk, 100.0 - c.score, 0.65).toInt(), tr(c, "통제 안내", "Closure notices")),
            ),
            listOf(
                Signal(tr(c, "위험 단계", "Risk level"), c.fireLevel, tr(c, "산불위험예보", "Wildfire forecast"), if (risk >= 45) "warn" else "safe"),
                Signal(tr(c, "확산 바람", EN_SPREAD_WIND), fmtWind(c.wind), tr(c, "능선부 민감 신호", "Ridge-sensitive signal"), if (c.wind >= 7) "warn" else "neutral"),
                Signal(tr(c, "건조 완화", "Dry relief"), "${c.rain}%", tr(c, "강수가 낮을수록 불리", "Lower rain is worse"), if (c.rain < 20) "warn" else "safe"),
                Signal(tr(c, "지도 기준", "Map area"), c.region, tr(c, "시군구/격자 예보", "District/grid forecast"), "neutral"),
                Signal(tr(c, "비교 산", EN_COMPARED_PEAKS), "1", tr(c, KO_REGIONAL_DISTRIBUTION, EN_REGIONAL_DISTRIBUTION), "neutral"),
                Signal(tr(c, "코스 판단", EN_ROUTE_CALL), if (risk >= 45) tr(c, "대체 권장", "Use alternative") else tr(c, "진행 가능", "Proceed"), tr(c, KO_BEFORE_DEPARTURE, EN_BEFORE_DEPARTURE), if (risk >= 45) "warn" else "safe"),
            ),
            tr(
                c,
                "방문할 산과 주변 지역의 산불 단계가 높으면 위험이 낮은 다른 산이나 짧은 코스로 바꾸세요.",
                "If the wildfire level is high near your mountain, switch to a lower-risk mountain or a shorter route.",
            ),
            tr(c, "국립산림과학원 산불위험예보", "National Institute of Forest Science wildfire forecast"),
            modeLabel(c), updatedAt(c), c.score,
        )
    }

    private fun buildLandslide(c: Ctx): Detail {
        val rain = c.rain.toDouble()
        val gradeRisk = clamp((6 - c.lsGrade) * 20.0)
        val slopeRisk = clamp(100.0 - c.lsScore)
        return Detail(
            "landslide", "⛰", tr(c, "산사태", "Landslide"), tr(c, "${c.lsLabel} · ${c.lsGrade}등급", "${c.lsLabel} · grade ${c.lsGrade}"),
            tr(
                c,
                "${c.place} 주변 사면의 산사태 위험지도와 최근 강우 영향을 함께 봐야 합니다. 계곡길·절개지·낙석 구간에서는 등급이 낮아도 보수적으로 움직이세요.",
                "Check both the landslide risk map and recent rainfall around ${c.place}. Move conservatively in valleys, cut slopes, and rockfall areas.",
            ),
            LAND,
            listOf(
                Metric(tr(c, "지도 등급", "Map grade"), tr(c, "${c.lsGrade}등급", "Grade ${c.lsGrade}"), tr(c, "지역 위험지도", "Regional risk map")),
                Metric(tr(c, "상태", "Status"), c.lsLabel, statusWord(c.lsScore, c.language)),
                Metric(tr(c, "강수 영향", "Rain impact"), "${c.rain}%", if (c.rain >= 30) tr(c, "최근/예상 강수 주의", "Recent/expected rain") else tr(c, "낮음", "Low")),
            ),
            listOf(
                Axis(tr(c, "지도위험", "Map risk"), gradeRisk.toInt(), tr(c, "${c.lsGrade}등급", "Grade ${c.lsGrade}")),
                Axis(tr(c, "강우압력", "Rain load"), rain.toInt(), tr(c, "예상 강수", "Expected rain")),
                Axis(tr(c, "사면불안", "Slope"), slopeRisk.toInt(), c.lsLabel),
                Axis(tr(c, "계곡주의", "Valley care"), mix(gradeRisk, rain, 0.55).toInt(), tr(c, "물길 주변", "Near water paths")),
                Axis(tr(c, "낙석주의", "Rockfall"), mix(gradeRisk, windRisk(c.wind), 0.7).toInt(), tr(c, "절개지·암릉", "Cut slopes/ridges")),
                Axis(tr(c, "우회필요", "Detour"), mix(slopeRisk, rain, 0.62).toInt(), tr(c, "대체 하산로", "Alternate descent")),
            ),
            listOf(
                Signal(tr(c, "위험지도", "Risk map"), tr(c, "${c.lsGrade}등급", "Grade ${c.lsGrade}"), tr(c, "산사태정보시스템", "Landslide information system"), if (c.lsGrade <= 2) "warn" else "safe"),
                Signal(tr(c, "상태", "Status"), c.lsLabel, statusWord(c.lsScore, c.language), if (c.lsScore >= 80) "safe" else "warn"),
                Signal(tr(c, "강수 영향", "Rain impact"), "${c.rain}%", tr(c, "최근/예상 강수 신호", "Recent/expected rain"), if (c.rain >= 30) "warn" else "neutral"),
                Signal(tr(c, "지도 기준", "Map area"), c.region, tr(c, "시군구/격자", "District/grid"), "neutral"),
                Signal(tr(c, "비교 산", EN_COMPARED_PEAKS), "1", tr(c, KO_REGIONAL_DISTRIBUTION, EN_REGIONAL_DISTRIBUTION), "neutral"),
                Signal(tr(c, "코스 판단", EN_ROUTE_CALL), if (c.lsGrade <= 2) tr(c, "대체 권장", "Use alternative") else tr(c, "진행 가능", "Proceed"), tr(c, KO_BEFORE_DEPARTURE, EN_BEFORE_DEPARTURE), if (c.lsGrade <= 2) "warn" else "safe"),
            ),
            tr(
                c,
                "비 예보가 있거나 전날 비가 왔다면 산사태 등급이 높은 지역의 산은 후보에서 제외하세요.",
                "If rain is forecast or fell yesterday, remove high-landslide-grade mountains from the candidate list.",
            ),
            tr(c, "산사태정보시스템 위험지도 · 산림청 등산로 위험구간", "Landslide information system risk map · Korea Forest Service trail hazard segments"),
            modeLabel(c), updatedAt(c), c.score,
        )
    }

    private fun buildWeather(c: Ctx): Detail {
        val rain = c.rain.toDouble()
        val wind = windRisk(c.wind)
        val temp = tempBurden(c.temp)
        val volatility = clamp(100.0 - 64)
        return Detail(
            "weather", "🌦", tr(c, "산악기상", "Mountain weather"), "${c.temp.toInt()}°C · ${c.wxLabel}",
            tr(
                c,
                "${c.station} 기준입니다. 산 정상과 능선은 도심보다 춥고 바람이 강해 체감온도가 빠르게 떨어질 수 있습니다.",
                "Based on ${c.station}. Summits and ridges can be colder and windier than the city, so felt temperature may drop fast.",
            ),
            WX,
            listOf(
                Metric(tr(c, "기온", "Temp"), "${c.temp.toInt()}°C", tr(c, "능선부 기준", "Ridge baseline")),
                Metric(tr(c, "풍속", "Wind"), fmtWind(c.wind), strongWindNote(c)),
                Metric(tr(c, "강수확률", "Rain chance"), "${c.rain}%", if (c.rain >= 30) tr(c, "우의 준비", "Pack rain gear") else tr(c, "낮음", "Low")),
            ),
            listOf(
                Axis(tr(c, "강풍", "Strong wind"), wind.toInt(), fmtWind(c.wind)),
                Axis(tr(c, "비구름", "Rain cloud"), rain.toInt(), rainText(c)),
                Axis(tr(c, "체감냉각", "Wind chill"), temp.toInt(), "${c.temp.toInt()}°C"),
                Axis(tr(c, "시야저하", "Low visibility"), mix(rain, volatility, 0.55).toInt(), c.wxLabel),
                Axis(tr(c, "변덕성", "Volatility"), volatility.toInt(), tr(c, "예보 불확실성", "Forecast uncertainty")),
                Axis(tr(c, "노면미끄럼", "Slippery path"), mix(rain, temp, 0.72).toInt(), tr(c, "암릉·데크", "Rock/deck areas")),
            ),
            listOf(
                Signal(tr(c, "기온", "Temp"), "${c.temp.toInt()}°C", c.wxLabel, "neutral"),
                Signal(tr(c, "풍속", "Wind"), fmtWind(c.wind), strongWindNote(c), if (c.wind >= 7) "warn" else "safe"),
                Signal(tr(c, "강수확률", "Rain chance"), "${c.rain}%", if (c.rain >= 30) tr(c, "우의 준비", "Pack rain gear") else tr(c, "낮음", "Low"), if (c.rain >= 30) "warn" else "safe"),
                Signal(tr(c, "관측소", "Station"), c.station, tr(c, "위치 기준", "Location baseline"), "neutral"),
                Signal(tr(c, "비교 산", EN_COMPARED_PEAKS), "1", tr(c, KO_REGIONAL_DISTRIBUTION, EN_REGIONAL_DISTRIBUTION), "neutral"),
                Signal(tr(c, "코스 길이", "Route length"), if (c.rain >= 30 || c.wind >= 7) tr(c, "짧게", "Shorten") else tr(c, "보통", "Normal"), tr(c, KO_BEFORE_DEPARTURE, EN_BEFORE_DEPARTURE), if (c.rain >= 30 || c.wind >= 7) "warn" else "safe"),
            ),
            tr(
                c,
                "출발 전 방문할 산의 관측소 기준 풍속과 강수확률을 보고 복장과 코스 길이를 정하세요.",
                "Before leaving, check wind speed and rain chance for the mountain station and choose clothing and route length accordingly.",
            ),
            tr(c, "기상청 단기예보 · 산악기상관측망", "KMA short-term forecast · mountain weather observation network"),
            modeLabel(c), updatedAt(c), c.score,
        )
    }

    private fun buildSunset(c: Ctx): Detail {
        val pressure = sunsetPressure(c.sunsetAt)
        val margin = sunsetMargin(c.sunsetAt, c.language)
        val mins = sunsetMinutes(c.sunsetAt)
        val afterDark = mins != null && mins <= 0
        val shortMargin = mins != null && mins < 120
        val nightTransition = if (afterDark) 100.0 else mix(pressure, if (shortMargin) 70.0 else 20.0, 0.7)
        return Detail(
            "sunset", "🌄", tr(c, "일몰", "Sunset"), c.sunsetAt,
            tr(
                c,
                "${c.place} 기준 일몰 시각입니다. 하산은 정상 도착 시간이 아니라 마지막 갈림길·대중교통·주차장 도착 시간까지 포함해서 판단해야 합니다.",
                "Sunset time for ${c.place}. Judge by the final junction, transit, and parking arrival time, not only summit arrival.",
            ),
            SUN,
            listOf(
                Metric(tr(c, "일몰 시각", "Sunset"), c.sunsetAt, tr(c, "지역 기준", "Regional baseline")),
                Metric(tr(c, "남은 시간", "Time left"), margin, tr(c, "현재 기기 시간 기준", "Based on device time")),
                Metric(tr(c, "전환 기준", "Turnaround"), tr(c, "16시 전", "Before 16:00"), tr(c, "새 코스 진입 마감", "New-route cutoff")),
            ),
            listOf(
                Axis(tr(c, "시간압박", "Time pressure"), pressure.toInt(), margin),
                Axis(tr(c, "하산여유부족", "Descent margin"), if (shortMargin) 78 else pressure.toInt(), tr(c, "주차장·교통까지", "Parking/transit included")),
                Axis(tr(c, "야간전환", "Night shift"), nightTransition.toInt(), tr(c, "시야 저하", "Visibility drop")),
                Axis(tr(c, "장비필요", "Gear need"), if (shortMargin) 85 else 35, tr(c, "헤드랜턴·보온", "Headlamp/insulation")),
                Axis(tr(c, "갈림길주의", "Junction care"), mix(pressure, 60.0, 0.55).toInt(), tr(c, "하산로 판단", "Descent decisions")),
                Axis(tr(c, "교통마감", "Transit cutoff"), mix(pressure, if (shortMargin) 72.0 else 34.0, 0.52).toInt(), tr(c, "귀가 시간", "Return time")),
            ),
            listOf(
                Signal(tr(c, "일몰", "Sunset"), c.sunsetAt, tr(c, "지역 기준", "Regional baseline"), "neutral"),
                Signal(tr(c, "남은 시간", "Time left"), margin, tr(c, "기기 시간 기준", "Device time"), if (shortMargin) "warn" else "safe"),
                Signal(tr(c, "전환 기준", "Turnaround"), tr(c, "16시 전", "Before 16:00"), tr(c, "새 코스 진입 마감", "New-route cutoff"), "warn"),
                Signal(tr(c, "준비물", "Gear"), tr(c, "헤드랜턴", "Headlamp"), tr(c, "보조배터리·보온층", "Battery and warm layer"), if (shortMargin) "warn" else "neutral"),
                Signal(tr(c, "비교 산", EN_COMPARED_PEAKS), "1", tr(c, "지역/산별 일몰", "Regional sunset"), "neutral"),
                Signal(tr(c, "코스 판단", EN_ROUTE_CALL), if (shortMargin) tr(c, "짧게", "Shorten") else tr(c, "진행 가능", "Proceed"), tr(c, KO_BEFORE_DEPARTURE, EN_BEFORE_DEPARTURE), if (shortMargin) "warn" else "safe"),
            ),
            tr(
                c,
                "출발 전에 예상 종료 시각이 일몰 1시간 전인지 확인하고, 아니면 더 짧은 코스를 고르세요.",
                "Before departure, confirm the expected finish time is at least one hour before sunset. Otherwise choose a shorter route.",
            ),
            tr(c, "지역별 일몰 시각 · 현재 위치 기준", "Regional sunset time · current-location baseline"),
            modeLabel(c), updatedAt(c), c.score,
        )
    }

    // --- panel view ---------------------------------------------------------
    fun panel(context: Context, d: Detail, onClose: () -> Unit, language: AppLanguage = AppLanguage.KOREAN): View {
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
            addView(
                GaugeView(context, d.score, d.accent, if (language == AppLanguage.ENGLISH) "Index" else "산행지수"),
                LinearLayout.LayoutParams(dp(76f), dp(76f)),
            )
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
        root.addView(
            chartHead(
                context,
                if (language == AppLanguage.ENGLISH) "Current risk vector" else "현재 위험 벡터",
                if (language == AppLanguage.ENGLISH) "Higher means more caution" else "높을수록 주의",
            ),
        )
        root.addView(RadarChartView(context, d.axes, d.accent), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200f)).apply {
            topMargin = dp(4f)
        })
        root.addView(axisLegend(context, d.axes))

        // signal cards grid (2-col)
        root.addView(
            chartHead(
                context,
                if (language == AppLanguage.ENGLISH) "Signal cards" else "신호 카드",
                if (language == AppLanguage.ENGLISH) EN_BEFORE_DEPARTURE else "출발 전 점검",
            ),
        )
        root.addView(cardGrid(context, d.cards))

        // guide
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(context, 0x47051426, radiusDp = 16f, stroke = 0x1FFFFFFF, strokeDp = 1f)
            setPadding(dp(13f), dp(12f), dp(13f), dp(12f))
            (layoutParamsOrSet(this)).topMargin = dp(12f)
            addView(TextView(context).apply {
                text = if (language == AppLanguage.ENGLISH) EN_BEFORE_DEPARTURE else "출발 전 확인"; textSize = 12.5f; setTextColor(0xFFFFFFFF.toInt()); typeface = Contour.black()
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
    private class GaugeView(context: Context, private val score: Int, private val accent: Int, private val caption: String) : View(context) {
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
            canvas.drawText(caption, w / 2f, h * 0.74f, cap)
        }
    }
}
