package kr.forestmate.app

import java.util.Locale
import kr.forestmate.app.state.PhoneTab
import kr.forestmate.core.model.Course

private const val COPY_HOME_INDEX_DEFAULT = "home.index.default"
private const val COPY_LOCATION_SHORT = "location.short"
private const val COPY_COURSE_LEVEL_UNKNOWN = "course.level.unknown"
private const val COPY_COURSE_META = "course.meta"
internal const val COPY_LANGUAGE_KOREAN = "language.korean"
internal const val COPY_LANGUAGE_ENGLISH = "language.english"

enum class AppLanguage(val storedValue: String, val chatLang: String) {
    KOREAN("ko", "ko"),
    ENGLISH("en", "en"),
    ;

    companion object {
        fun fromStoredValue(value: String?): AppLanguage =
            when (value?.trim()?.lowercase(Locale.US)) {
                "en", "english" -> ENGLISH
                else -> KOREAN
            }

        fun fromLanguageTag(tag: String?): AppLanguage =
            if (tag?.lowercase(Locale.US)?.startsWith("en") == true) ENGLISH else KOREAN
    }
}

data class AppCopy(
    val language: AppLanguage,
    val tabLabels: List<String>,
    val strings: Map<String, String>,
) {
    val userFacingStrings: List<String>
        get() = tabLabels + strings.values

    fun text(key: String): String =
        strings[key] ?: error("Missing app copy: $key")

    fun format(key: String, vararg args: Any?): String =
        String.format(Locale.US, text(key), *args)

    fun tabLabel(tab: PhoneTab): String = tabLabels[tab.ordinal]

    fun hikingIndexLabel(remoteLabel: String?): String {
        val value = remoteLabel?.takeIf { it.isNotBlank() } ?: text(COPY_HOME_INDEX_DEFAULT)
        if (language == AppLanguage.KOREAN) return value
        return when {
            value.contains("좋") -> "Good for hiking"
            value.contains("주의") -> "Use caution"
            value.contains("위험") -> "High risk"
            value.contains("보통") -> "Moderate conditions"
            else -> text(COPY_HOME_INDEX_DEFAULT)
        }
    }

    fun placeName(remotePlace: String?, remoteRegion: String?): String {
        val value = remotePlace?.ifBlank { remoteRegion.orEmpty() } ?: remoteRegion.orEmpty()
        if (value.isBlank()) return text(COPY_LOCATION_SHORT)
        if (language == AppLanguage.KOREAN) return value
        return if (value.contains("서울") || value.contains("은평")) text(COPY_LOCATION_SHORT) else value
    }

    fun courseMeta(course: Course): String {
        val time = duration(course.minutes)
        val level = course.level.ifBlank { text(COPY_COURSE_LEVEL_UNKNOWN) }
        return format(COPY_COURSE_META, course.km, time, level)
    }

    fun duration(minutes: Int): String =
        if (language == AppLanguage.KOREAN) {
            if (minutes >= 60) {
                val mins = minutes % 60
                "${minutes / 60}시간${if (mins > 0) "${mins}분" else ""}"
            } else {
                "${minutes}분"
            }
        } else {
            if (minutes >= 60) {
                val mins = minutes % 60
                "${minutes / 60}h${if (mins > 0) " ${mins}m" else ""}"
            } else {
                "${minutes}m"
            }
        }

    fun lowHighGrade(value: String): Boolean =
        if (language == AppLanguage.KOREAN) value.startsWith("높음") else value.startsWith("High")
}

object DesignCopy {
    val korean = AppCopy(
        language = AppLanguage.KOREAN,
        tabLabels = listOf("홈", "산행", "안전", "AI동무", "마이"),
        strings = mapOf(
            "brand.name" to "숲길동무",
            "location.label" to "📍 서울 은평구 ▾",
            COPY_LOCATION_SHORT to "서울 은평구",
            "screen.home.title" to "좋음 — 산행하기 좋은 날",
            "screen.home.subtitle" to "산행지수와 맞춤 코스를 한눈에 확인하세요.",
            "screen.sos.title" to "안전 요청",
            "screen.sos.subtitle" to "현재 위치와 국가지점번호를 구조기관에 전달합니다.",
            "screen.ai.title" to "AI 숲해설사 '숲이'",
            "screen.ai.subtitle" to "위험한 식물, 코스 여유, 날씨를 자연어로 물어보세요.",
            "screen.my.title" to "내 산행",
            "screen.my.subtitle" to "기록, 배지, 안전 이벤트를 모아 봅니다.",
            "home.ai.title" to "🤖 AI 맞춤 코스",
            "home.ai.meta" to "체력 중급 · 무릎 주의 이력 반영",
            "home.safety.title" to "🛡 안전 브리핑",
            "home.safety.meta" to "하산 사고와 날씨 변화를 먼저 확인",
            "home.refresh" to "산행지수 새로고침",
            COPY_HOME_INDEX_DEFAULT to "산행하기 좋은 날",
            "home.index.line" to "오늘의 산행지수 · %s",
            "home.search.title" to "🔍 전국 산 검색",
            "home.search.meta" to "산림청 산정보 · 전국 3,400여 개 산",
            "home.safety.head" to "⚠ 하산 시 사고가 등반보다 1.8배 많아요.",
            "home.safety.body" to "스틱으로 무릎 부담을 줄이고, 급경사 전환 구간에서는 속도를 낮추세요.",
            "home.news.title" to "🌿 이번 주 숲 소식",
            "home.news.body" to "국립공원 탐방 예약과 산불·강풍 안내를 함께 확인하세요. 위험 알림은 코스별 안전 브리핑에 반영됩니다.",
            "detail.more" to "자세히 ›",
            "map.caption" to "© OpenStreetMap contributors · 추천 경로/위험 마커/GPS 트랙 · 오프라인 지도 저장됨",
            "map.route.title" to "추천 등산 경로",
            "map.track.title" to "GPS 트랙",
            "hike.hazard.title" to "위험구간 %s · %s · %s",
            "hike.button.pause" to "산행 일시정지",
            "hike.button.start" to "산행 시작",
            "hike.button.end" to "산행 종료",
            "hike.button.demo" to "데모 이동 +90m",
            "hike.button.watch" to "워치 백업 연결",
            "directions.title" to "🧭 들머리까지 가는 길",
            "directions.body" to "탐방지원센터 · %s",
            "directions.kakao" to "현재위치→카카오맵",
            "directions.google" to "구글맵",
            "directions.preparing" to "길찾기 앱을 열 준비 중입니다.",
            "directions.caption" to "도착하면 산행 시작을 눌러 GPS 추적을 켜세요.",
            "metric.distance" to "이동 / %.1fkm",
            "metric.altitude" to "현재 고도",
            "metric.heart" to "심박(워치)",
            "metric.sunset" to "일몰까지",
            "sos.location" to "현재 위치",
            "sos.grid" to "국가지점번호",
            "sos.station" to "관할 119",
            "sos.caption" to "버튼을 누르면 현재 산행 위치와 국가지점번호가 구조기관으로 전달됩니다.",
            "sos.send" to "🆘 SOS 전송",
            "ai.sample.user1" to "길에서 봤는데, 이 버섯 먹어도 돼?",
            "ai.sample.user2" to "백운대 정상까지 얼마나 남았어?",
            "ai.sample.assistant2" to "남은 거리 1.8km, 지금 페이스라면 약 55분 뒤 도착해요. 일몰까지 여유는 있지만 정상 부근 바람이 강하니 겉옷을 준비하세요.",
            "ai.input.hint" to "숲이에게 질문",
            "ai.input.default" to "오늘 이 코스 안전해?",
            "ai.ask" to "묻기",
            "ai.photo" to "📷 방금 촬영한 사진",
            "ai.risk.title" to "🚫 개나리광대버섯 가능성 높음",
            "ai.risk.body" to "아마톡신 함유 맹독성 버섯과 유사합니다. 소량 섭취도 위험할 수 있어요.",
            "ai.risk.confidence" to "AI 판별 신뢰도 87% · 국립수목원 자료 대조",
            "ai.risk.warning" to "⚠ 절대 채취·섭취 금지. 만졌다면 흐르는 물에 손을 씻어주세요.",
            "my.account.title" to "계정",
            "my.email" to "이메일",
            "my.password" to "비밀번호",
            "my.signup" to "가입",
            "my.login" to "로그인",
            "my.load" to "기록/배지 불러오기",
            "my.summary.title" to "내 산행",
            "my.account.label" to "계정",
            "my.device.label" to "기기 등록",
            "my.watch.label" to "워치 코드",
            "my.disconnected" to "미연결",
            "my.device.pending" to "대기",
            "my.device.complete" to "완료",
            "my.watch.none" to "없음",
            "my.safety.title" to "실시간 안전 이벤트",
            "my.stat.sos" to "SOS 훈련",
            "my.stat.risk" to "위험 감지",
            "my.stat.arrival" to "평균 도착",
            "my.stat.arrival.value" to "23분",
            "my.privacy" to "개인 위치는 k-익명화 기준으로만 안전 분석에 반영됩니다.",
            "my.risk.section" to "구간별 위험도",
            "event.zone1" to "인수봉 동면 슬랩",
            "event.grade1" to "높음 81",
            "event.reason1" to "강풍 9m/s · 사고다발",
            "event.zone2" to "Y계곡 암릉",
            "event.grade2" to "높음 76",
            "event.reason2" to "낙석·정체",
            "event.zone3" to "백운대 정상부",
            "event.grade3" to "주의 58",
            "event.reason3" to "혼잡·일몰임박",
            "language.title" to "언어",
            "language.body" to "앱 화면 언어를 선택하세요. AI 질문도 선택한 언어로 전송됩니다.",
            COPY_LANGUAGE_KOREAN to "한국어",
            COPY_LANGUAGE_ENGLISH to "English",
            "language.current" to "현재 언어: %s",
            "course.match" to "매칭 %d%%",
            "course.ai" to "AI 추천",
            "course.grid" to "국가지점번호 %s",
            "course.selected" to "%s 선택됨",
            COPY_COURSE_META to "▲ %.1fkm   ◷ %s   ● 난이도 %s",
            COPY_COURSE_LEVEL_UNKNOWN to "확인",
            "status.home.loading" to "산행지수와 추천을 불러오는 중...",
            "status.home.stored" to "추천 코스는 저장된 목록으로 표시 중입니다.",
            "status.home.fallback" to "최신 데이터를 불러오지 못해 저장된 코스를 보여줍니다.",
            "status.hike.paused" to "산행 일시정지 · 현재 진행 %s",
            "status.hike.gps" to "GPS 추적 시작 · %s",
            "status.hike.checkin.loading" to "서버 산행 체크인 중...",
            "status.hike.checkin.done" to "입산 체크인 완료 · %s · 산행 ID %s",
            "status.hike.local.end" to "로컬 산행 종료 · %skm",
            "status.hike.save.loading" to "산행 기록 저장 중...",
            "status.hike.saved" to "산행 종료 · %skm · 기록 저장",
            "status.watch.loading" to "워치 연결 코드 생성 중...",
            "status.watch.code" to "워치 백업 코드 %s · %d분 유효",
            "status.sos.loading" to "SOS 전송 중...",
            "status.sos.done" to "SOS %s · %s · %s · ETA %d분",
            "status.chat.loading" to "숲이 응답 중...",
            "status.summary.loading" to "기록과 배지를 불러오는 중...",
            "summary.badge.earned" to "달성",
            "summary.badge.progress" to "%s/%s",
            "summary.badges.empty" to "배지 기록 대기",
            "summary.text" to "총 %d회 · %skm · 레벨 %d\n완등 %d코스 · 방문 지역 %d곳\n%s",
            "summary.logs.empty" to "최근 기록 없음",
            "status.account.creating" to "계정 생성 중...",
            "status.account.created" to "계정 생성 완료 · %s · 기록 동기화 ON",
            "status.login.loading" to "로그인 중...",
            "status.login.done" to "로그인 완료 · %s · 기록 동기화 ON",
            "status.location.permission" to "위치 권한이 필요합니다.",
            "status.location.provider" to "사용 가능한 위치 공급자가 없습니다.",
            "status.location.changed" to "위치 권한 상태가 변경되어 추적을 멈췄습니다.",
            "status.request.failed" to "요청을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.",
            "status.hike" to "진행 %s · 이동 %skm · %s · 워치 %s",
            "status.tracking" to "GPS 추적 중",
            "status.waiting" to "대기",
        ),
    )

    val english = AppCopy(
        language = AppLanguage.ENGLISH,
        tabLabels = listOf("Home", "Hike", "Safety", "AI Guide", "My"),
        strings = mapOf(
            "brand.name" to "ForestMate",
            "location.label" to "📍 Eunpyeong, Seoul ▾",
            COPY_LOCATION_SHORT to "Eunpyeong, Seoul",
            "screen.home.title" to "Good — ready for a hike",
            "screen.home.subtitle" to "Check today's hiking index and personalized routes at a glance.",
            "screen.sos.title" to "Safety Request",
            "screen.sos.subtitle" to "Share your current position and national grid number with rescue teams.",
        ) + mapOf(
            "screen.ai.title" to "AI forest guide",
            "screen.ai.subtitle" to "Ask about risky plants, route margin, and weather in natural language.",
            "screen.my.title" to "My hikes",
            "screen.my.subtitle" to "Review records, badges, and safety events.",
            "home.ai.title" to "🤖 AI route picks",
            "home.ai.meta" to "Intermediate fitness · knee caution history applied",
            "home.safety.title" to "🛡 Safety Briefing",
            "home.safety.meta" to "Check descent accidents and weather shifts first",
        ) + mapOf(
            "home.refresh" to "Refresh hiking index",
            COPY_HOME_INDEX_DEFAULT to "Good for hiking",
            "home.index.line" to "Today's hiking index · %s",
            "home.search.title" to "🔍 Search mountains nationwide",
            "home.search.meta" to "Korea Forest Service mountain data · 3,400+ mountains",
            "home.safety.head" to "⚠ Descent accidents are 1.8x more common than ascent accidents.",
            "home.safety.body" to "Use poles to reduce knee load and slow down before steep transitions.",
        ) + mapOf(
            "home.news.title" to "🌿 Forest updates this week",
            "home.news.body" to "Check national park reservations together with wildfire and strong-wind alerts. Risk alerts are reflected in each route briefing.",
            "detail.more" to "Details ›",
            "map.caption" to "© OpenStreetMap contributors · Recommended route / risk markers / GPS track · Offline map saved",
            "map.route.title" to "Recommended hiking route",
            "map.track.title" to "GPS track",
            "hike.hazard.title" to "Risk segment %s · %s · %s",
        ) + mapOf(
            "hike.button.pause" to "Pause hike",
            "hike.button.start" to "Start hike",
            "hike.button.end" to "End hike",
            "hike.button.demo" to "Demo move +90m",
            "hike.button.watch" to "Pair watch backup",
            "directions.title" to "🧭 Directions to the trailhead",
            "directions.body" to "Trail support center · %s",
            "directions.kakao" to "Current location → KakaoMap",
        ) + mapOf(
            "directions.google" to "Google Maps",
            "directions.preparing" to "Preparing to open a directions app.",
            "directions.caption" to "When you arrive, tap Start hike to turn on GPS tracking.",
            "metric.distance" to "Moved / %.1fkm",
            "metric.altitude" to "Current altitude",
            "metric.heart" to "Heart rate (watch)",
            "metric.sunset" to "To sunset",
        ) + mapOf(
            "sos.location" to "Current location",
            "sos.grid" to "National grid number",
            "sos.station" to "Rescue station",
            "sos.caption" to "Tap the button to send your hiking position and national grid number to rescue teams.",
            "sos.send" to "🆘 Send SOS",
            "ai.sample.user1" to "I found this mushroom on the trail. Is it edible?",
            "ai.sample.user2" to "How far is it to Baegundae summit?",
        ) + mapOf(
            "ai.sample.assistant2" to "You have 1.8km left. At your current pace, arrival is about 55 minutes away. There is still daylight, but winds near the summit are strong, so pack an outer layer.",
            "ai.input.hint" to "Ask Soopi",
            "ai.input.default" to "Is this route safe today?",
            "ai.ask" to "Ask",
            "ai.photo" to "📷 Just-captured photo",
            "ai.risk.title" to "🚫 High chance of death cap mushroom",
            "ai.risk.body" to "It resembles a highly poisonous amatoxin mushroom. Even a small amount can be dangerous.",
        ) + mapOf(
            "ai.risk.confidence" to "AI ID confidence 87% · checked against Korea National Arboretum data",
            "ai.risk.warning" to "⚠ Do not pick or eat it. If touched, wash your hands with running water.",
            "my.account.title" to "Account",
            "my.email" to "Email",
            "my.password" to "Password",
            "my.signup" to "Sign up",
            "my.login" to "Log in",
            "my.load" to "Load records and badges",
        ) + mapOf(
            "my.summary.title" to "My hikes",
            "my.account.label" to "Account",
            "my.device.label" to "Device registration",
            "my.watch.label" to "Watch code",
            "my.disconnected" to "Not connected",
            "my.device.pending" to "Pending",
            "my.device.complete" to "Done",
            "my.watch.none" to "None",
        ) + mapOf(
            "my.safety.title" to "Live safety events",
            "my.stat.sos" to "SOS drills",
            "my.stat.risk" to "Risks detected",
            "my.stat.arrival" to "Avg arrival",
            "my.stat.arrival.value" to "23m",
            "my.privacy" to "Personal location is used for safety analysis only after k-anonymization.",
            "my.risk.section" to "Risk by segment",
        ) + mapOf(
            "event.zone1" to "Insubong east slab",
            "event.grade1" to "High 81",
            "event.reason1" to "Strong wind 9m/s · frequent accidents",
            "event.zone2" to "Y Valley rocky ridge",
            "event.grade2" to "High 76",
            "event.reason2" to "Rockfall · congestion",
            "event.zone3" to "Baegundae summit area",
            "event.grade3" to "Caution 58",
        ) + mapOf(
            "event.reason3" to "Crowding · sunset approaching",
            "language.title" to "Language",
            "language.body" to "Choose the app display language. AI questions are sent in the selected language too.",
            COPY_LANGUAGE_KOREAN to "Korean",
            COPY_LANGUAGE_ENGLISH to "English",
            "language.current" to "Current language: %s",
            "course.match" to "Match %d%%",
            "course.ai" to "AI pick",
        ) + mapOf(
            "course.grid" to "National grid %s",
            "course.selected" to "%s selected",
            COPY_COURSE_META to "▲ %.1fkm   ◷ %s   ● Difficulty %s",
            COPY_COURSE_LEVEL_UNKNOWN to "Check",
            "status.home.loading" to "Loading hiking index and recommendations...",
            "status.home.stored" to "Showing saved route recommendations.",
            "status.home.fallback" to "Could not load the latest data, so saved routes are shown.",
        ) + mapOf(
            "status.hike.paused" to "Hike paused · current progress %s",
            "status.hike.gps" to "GPS tracking started · %s",
            "status.hike.checkin.loading" to "Checking in this hike on the server...",
            "status.hike.checkin.done" to "Trail check-in complete · %s · hike ID %s",
            "status.hike.local.end" to "Local hike ended · %skm",
            "status.hike.save.loading" to "Saving hike record...",
            "status.hike.saved" to "Hike ended · %skm · record saved",
        ) + mapOf(
            "status.watch.loading" to "Creating watch pairing code...",
            "status.watch.code" to "Watch backup code %s · valid for %d minutes",
            "status.sos.loading" to "Sending SOS...",
            "status.sos.done" to "SOS %s · %s · %s · ETA %d min",
            "status.chat.loading" to "Soopi is answering...",
            "status.summary.loading" to "Loading records and badges...",
            "summary.badge.earned" to "earned",
        ) + mapOf(
            "summary.badge.progress" to "%s/%s",
            "summary.badges.empty" to "Badge history pending",
            "summary.text" to "%d hikes · %skm · level %d\n%d completed routes · %d visited regions\n%s",
            "summary.logs.empty" to "No recent records",
            "status.account.creating" to "Creating account...",
            "status.account.created" to "Account created · %s · record sync ON",
            "status.login.loading" to "Logging in...",
        ) + mapOf(
            "status.login.done" to "Logged in · %s · record sync ON",
            "status.location.permission" to "Location permission is required.",
            "status.location.provider" to "No available location provider.",
            "status.location.changed" to "Location permission changed, so tracking stopped.",
            "status.request.failed" to "Could not complete the request. Please try again shortly.",
            "status.hike" to "Progress %s · moved %skm · %s · watch %s",
            "status.tracking" to "GPS tracking",
            "status.waiting" to "Waiting",
        ),
    )

    val tabLabels: List<String> = korean.tabLabels
    val userFacingStrings: List<String> = korean.userFacingStrings

    fun forLanguage(language: AppLanguage): AppCopy =
        when (language) {
            AppLanguage.KOREAN -> korean
            AppLanguage.ENGLISH -> english
        }
}
