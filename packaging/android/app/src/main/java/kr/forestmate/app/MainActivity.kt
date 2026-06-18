package kr.forestmate.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kr.forestmate.app.state.HikeFlowState
import kr.forestmate.app.state.LatLon
import kr.forestmate.app.state.NavigationState
import kr.forestmate.app.state.PhoneTab
import kr.forestmate.app.state.TrailMapState
import kr.forestmate.app.ui.NativeViews
import kr.forestmate.app.ui.TrailMapViews
import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.UrlConnectionTransport
import kr.forestmate.core.model.Course
import kr.forestmate.core.repo.ForestMateRepository
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var store: PhoneStore
    private lateinit var repository: ForestMateRepository
    private var navigation = NavigationState()
    private var courses: List<Course> = LocalCatalog.courses
    private var selectedCourse: Course = LocalCatalog.courses.first()
    private var hikeFlow = HikeFlowState(selectedCourseId = selectedCourse.id)
    private var mapState = TrailMapState.forCourse(selectedCourse)
    private var currentMapView: MapView? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var tracking = false
    private var lastMessage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)
        repository = ForestMateRepository(ApiConfig(store.apiBase), UrlConnectionTransport())
        hikeFlow = hikeFlow.copy(activeHikeId = store.activeHikeId.takeIf { it.isNotBlank() })
        tracking = hikeFlow.activeHikeId != null
        configureMaps()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        currentMapView?.onResume()
    }

    override fun onPause() {
        currentMapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        currentMapView?.onDetach()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && tracking) {
            startLocationUpdates()
        }
    }

    private fun configureMaps() {
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
    }

    private fun render() {
        currentMapView?.onDetach()
        currentMapView = null
        root.removeAllViews()
        val (title, body) = bodyFor(navigation.selected)
        val content = NativeViews.screen(this, title, body)
        val status = NativeViews.statusText(this, lastMessage)
        content.addView(status)
        addTabContent(content, status, navigation.selected)
        root.addView(
            ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(tabBar(), LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun bodyFor(tab: PhoneTab): Pair<String, String> = when (tab) {
        PhoneTab.HOME -> "오늘의 산행" to "산행지수와 추천 코스를 확인하세요."
        PhoneTab.HIKE -> "산행 지도" to "코스 지도, GPS 트랙, 위험구간, 워치 연결."
        PhoneTab.SOS -> "SOS" to "현재 코스 위치와 국가지점번호로 구조 요청."
        PhoneTab.AI -> "AI 숲이" to "날씨, 위험구간, 하산 여유를 물어보세요."
        PhoneTab.MY -> "My" to "산행 기록, 완등 코스, 배지 진행도."
    }

    private fun tabBar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            PhoneTab.entries.forEach { tab ->
                addView(
                    NativeViews.tabButton(this@MainActivity, tab) {
                        navigation = navigation.select(tab.id)
                        render()
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
        }

    private fun addTabContent(content: LinearLayout, status: TextView, tab: PhoneTab) {
        when (tab) {
            PhoneTab.HOME -> renderHome(content, status)
            PhoneTab.HIKE -> renderHike(content, status)
            PhoneTab.SOS -> renderSos(content, status)
            PhoneTab.AI -> renderAi(content, status)
            PhoneTab.MY -> renderMy(content, status)
        }
    }

    private fun renderHome(content: LinearLayout, status: TextView) {
        content.addView(NativeViews.actionButton(this, "산행지수 새로고침") { loadHome(status) })
        content.addView(NativeViews.sectionText(this, "추천 코스"))
        courses.forEach { course ->
            content.addView(
                NativeViews.actionButton(this, courseButtonText(course)) {
                    selectCourse(course)
                    navigation = navigation.select(PhoneTab.HIKE.id)
                    lastMessage = "${course.name} 선택됨"
                    render()
                },
            )
        }
    }

    private fun renderHike(content: LinearLayout, status: TextView) {
        status.text = hikeStatusText()
        content.addView(NativeViews.sectionText(this, selectedCourse.name))
        content.addView(NativeViews.bodyText(this, courseDetailText(selectedCourse)))
        val map = TrailMapViews.createMap(this, mapState)
        currentMapView = map
        content.addView(map)
        content.addView(NativeViews.captionText(this, "© OpenStreetMap contributors · 추천 경로/위험 마커/GPS 트랙"))
        selectedCourse.hazards.forEach { hazard ->
            content.addView(NativeViews.bodyText(this, "위험구간 ${hazard.at.percent()}: ${hazard.type} · ${hazard.grade}\n${hazard.note}"))
        }
        content.addView(NativeViews.actionButton(this, if (tracking) "산행 일시정지" else "산행 시작") { toggleHike(status) })
        content.addView(NativeViews.actionButton(this, "GPS 데모 이동") { demoGps(status) })
        content.addView(NativeViews.actionButton(this, "산행 종료") { endHike(status) })
        content.addView(NativeViews.actionButton(this, "Galaxy Watch 연결") { pairWatch(status) })
    }

    private fun renderSos(content: LinearLayout, status: TextView) {
        content.addView(NativeViews.bodyText(this, "국가지점번호 ${selectedCourse.gridNo}\n${selectedCourse.gps}\n${selectedCourse.fireStation}"))
        content.addView(NativeViews.actionButton(this, "SOS 전송") { sendSos(status) })
    }

    private fun renderAi(content: LinearLayout, status: TextView) {
        val input = EditText(this).apply {
            hint = "숲이에게 질문"
            setSingleLine(false)
            setText("오늘 이 코스 안전해?")
        }
        content.addView(input)
        content.addView(NativeViews.actionButton(this, "묻기") { sendChat(status, input.text.toString()) })
    }

    private fun renderMy(content: LinearLayout, status: TextView) {
        val email = EditText(this).apply {
            hint = "이메일"
            setSingleLine(true)
            setText(store.accountEmail)
        }
        val password = EditText(this).apply {
            hint = "비밀번호"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        content.addView(NativeViews.sectionText(this, "계정"))
        content.addView(email)
        content.addView(password)
        content.addView(NativeViews.actionButton(this, "가입") { registerAccount(status, email.text.toString(), password.text.toString()) })
        content.addView(NativeViews.actionButton(this, "로그인") { loginAccount(status, email.text.toString(), password.text.toString()) })
        content.addView(NativeViews.actionButton(this, "기록/배지 불러오기") { loadSummary(status) })
        content.addView(NativeViews.bodyText(this, "계정: ${store.accountEmail.ifBlank { "미연결" }}\n기기 등록: ${if (store.deviceToken.isBlank()) "대기" else "완료"}\n워치 코드: ${hikeFlow.watchPairCode ?: "없음"}"))
    }

    private fun loadHome(status: TextView) {
        runApi(status, "산행지수와 추천을 불러오는 중...") {
            val indexText = messageFor(repository.hikeIndex()) {
                "산행지수 ${it.score}점 ${it.label} · ${it.regionName} · ${it.weatherLabel} ${it.temperatureC}°C"
            }
            val remoteCourses = repository.courses()
            if (remoteCourses is ApiResult.Success && remoteCourses.value.isNotEmpty()) {
                courses = remoteCourses.value
                selectedCourse = courses.firstOrNull { it.id == selectedCourse.id } ?: courses.first()
                mapState = TrailMapState.forCourse(selectedCourse)
            }
            "$indexText\n추천 ${courses.size}개 · ${courses.joinToString(" / ") { it.name }}"
        }
    }

    private fun selectCourse(course: Course) {
        selectedCourse = course
        hikeFlow = hikeFlow.copy(selectedCourseId = course.id, activeHikeId = null, progress = 0.0)
        mapState = TrailMapState.forCourse(course)
    }

    private fun toggleHike(status: TextView) {
        if (tracking) {
            tracking = false
            stopLocationUpdates()
            lastMessage = "산행 일시정지 · 현재 진행 ${hikeFlow.progress.percent()}"
            status.text = lastMessage
            render()
            return
        }
        tracking = true
        lastMessage = "GPS 추적 시작 · ${selectedCourse.name}"
        status.text = lastMessage
        startLocationUpdates()
        runApi(status, "서버 산행 체크인 중...") {
            val token = ensureDeviceToken()
            messageFor(repository.startHike(token, selectedCourse.id)) {
                hikeFlow = hikeFlow.started(it.hikeId)
                store.activeHikeId = it.hikeId
                "입산 체크인 완료 · ${selectedCourse.name} · 산행 ID ${it.hikeId.take(8)}"
            }
        }
    }

    private fun demoGps(status: TextView) {
        val nextIndex = mapState.trackPoints.size.coerceAtMost(mapState.routePoints.lastIndex)
        recordLocation(mapState.routePoints[nextIndex])
        status.text = hikeStatusText()
    }

    private fun endHike(status: TextView) {
        tracking = false
        stopLocationUpdates()
        val token = store.deviceToken
        val hikeId = hikeFlow.activeHikeId
        hikeFlow = hikeFlow.copy(activeHikeId = null)
        store.activeHikeId = ""
        if (token.isBlank() || hikeId.isNullOrBlank()) {
            lastMessage = "로컬 산행 종료 · ${mapState.walkedKm.formatKm()}km"
            status.text = lastMessage
            render()
            return
        }
        runApi(status, "산행 기록 저장 중...") {
            messageFor(repository.endHike(token, hikeId)) {
                "산행 종료 · ${mapState.walkedKm.formatKm()}km · 기록 저장"
            }
        }
    }

    private fun pairWatch(status: TextView) {
        runApi(status, "워치 연결 코드 생성 중...") {
            val token = ensureDeviceToken()
            val result = repository.startWatchPairing(token, hikeFlow.activeHikeId)
            messageFor(result) {
                hikeFlow = hikeFlow.paired(it.code)
                "워치 백업 코드 ${it.code} · ${it.expiresIn / 60}분 유효"
            }
        }
    }

    private fun sendSos(status: TextView) {
        runApi(status, "SOS 전송 중...") {
            val token = ensureDeviceToken()
            messageFor(repository.sendSos(token, hikeFlow.activeHikeId)) {
                "SOS ${it.status} · ${it.gridNo} · ${it.station} · ETA ${it.etaMin}분"
            }
        }
    }

    private fun sendChat(status: TextView, rawMessage: String) {
        val message = rawMessage.ifBlank { "오늘 이 코스 안전해?" }
        runApi(status, "숲이 응답 중...") {
            messageFor(
                repository.sendChat(
                    message = message,
                    lang = "ko",
                    courseId = selectedCourse.id,
                    progress = hikeFlow.progress,
                ),
            ) {
                it.reply
            }
        }
    }

    private fun loadSummary(status: TextView) {
        runApi(status, "기록과 배지를 불러오는 중...") {
            val token = store.accountToken.ifBlank { ensureDeviceToken() }
            val summaryText = messageFor(repository.hikeSummary(token)) {
                val badges = it.badges.take(5).joinToString(" / ") { badge ->
                    "${badge.icon}${badge.label} ${if (badge.earned) "달성" else "${badge.progress}/${badge.goal}"}"
                }.ifBlank { "배지 기록 대기" }
                "총 ${it.totalHikes}회 · ${it.totalKm}km · 레벨 ${it.level}\n완등 ${it.distinctCourses}코스 · 방문 지역 ${it.regions}곳\n$badges"
            }
            val logText = messageFor(repository.hikeLog(token)) { logs ->
                if (logs.isEmpty()) "최근 기록 없음" else logs.take(3).joinToString("\n") { "${it.date} ${it.course} ${it.km}km" }
            }
            "$summaryText\n$logText"
        }
    }

    private fun registerAccount(status: TextView, email: String, password: String) {
        runApi(status, "계정 생성 중...") {
            val deviceToken = store.deviceToken.ifBlank { null }
            messageFor(repository.registerAccount(email.trim(), password, deviceToken)) {
                store.accountToken = it.accessToken
                store.accountEmail = it.email
                store.deviceToken = it.deviceToken
                "계정 생성 완료 · ${it.email} · 기록 동기화 ON"
            }
        }
    }

    private fun loginAccount(status: TextView, email: String, password: String) {
        runApi(status, "로그인 중...") {
            val deviceToken = store.deviceToken.ifBlank { null }
            messageFor(repository.loginAccount(email.trim(), password, deviceToken)) {
                store.accountToken = it.accessToken
                store.accountEmail = it.email
                store.deviceToken = it.deviceToken
                "로그인 완료 · ${it.email} · 기록 동기화 ON"
            }
        }
    }

    private fun ensureDeviceToken(): String {
        store.deviceToken.takeIf { it.isNotBlank() }?.let { return it }
        val registration = repository.registerDevice("phone")
        if (registration is ApiResult.Success) {
            store.deviceToken = registration.value.token
            return registration.value.token
        }
        throw IllegalStateException((registration as ApiResult.Failure).displayMessage)
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION)
            }
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = manager
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                recordLocation(LatLon(location.latitude, location.longitude))
            }
        }
        try {
            val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            manager.requestLocationUpdates(provider, 5000L, 5f, locationListener!!)
        } catch (ex: SecurityException) {
            lastMessage = "위치 권한이 필요합니다."
        } catch (ex: IllegalArgumentException) {
            lastMessage = "사용 가능한 위치 공급자가 없습니다."
        }
    }

    private fun stopLocationUpdates() {
        val listener = locationListener ?: return
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: SecurityException) {
            lastMessage = "위치 권한 상태가 변경되어 추적을 멈췄습니다."
        }
        locationListener = null
    }

    private fun recordLocation(point: LatLon) {
        mapState = mapState.recordLocation(point)
        hikeFlow = hikeFlow.updateProgress(mapState.progress)
        val token = store.deviceToken
        val hikeId = hikeFlow.activeHikeId
        if (tracking && token.isNotBlank() && !hikeId.isNullOrBlank()) {
            Thread {
                repository.trackHike(token, hikeId, mapState.progress, lat = point.lat, lon = point.lon)
            }.start()
        }
        if (navigation.selected == PhoneTab.HIKE) {
            runOnUiThread { render() }
        }
    }

    private fun hasLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun runApi(status: TextView, loading: String, block: () -> String) {
        status.text = loading
        Thread {
            val message = try {
                block()
            } catch (ex: Exception) {
                "오프라인 모드: ${ex.message.orEmpty()}"
            }
            runOnUiThread {
                lastMessage = message
                status.text = message
                if (navigation.selected == PhoneTab.HOME || navigation.selected == PhoneTab.HIKE || navigation.selected == PhoneTab.MY) {
                    render()
                }
            }
        }.start()
    }

    private fun <T> messageFor(result: ApiResult<T>, success: (T) -> String): String =
        when (result) {
            is ApiResult.Success -> success(result.value)
            is ApiResult.Failure -> result.displayMessage
        }

    private fun courseButtonText(course: Course): String =
        "${course.name} · ${course.km}km · ${course.level.ifBlank { "난이도 확인" }} · ${course.crowd.ifBlank { "혼잡 확인" }}"

    private fun courseDetailText(course: Course): String =
        "${course.route}\n${course.peak} · ${course.km}km · ${course.minutes}분 · 난이도 ${course.level}\n국가지점번호 ${course.gridNo}\n구조 거점 ${course.rescuePoint}\n관할 ${course.fireStation}"

    private fun hikeStatusText(): String =
        "진행 ${hikeFlow.progress.percent()} · 이동 ${mapState.walkedKm.formatKm()}km · ${if (tracking) "GPS 추적 중" else "대기"} · 워치 ${hikeFlow.watchPairCode ?: "미연결"}"

    private fun Double.percent(): String = "${(this.coerceIn(0.0, 1.0) * 100).toInt()}%"

    private fun Double.formatKm(): String = "%.2f".format(this)

    companion object {
        private const val REQ_LOCATION = 7001
    }
}
