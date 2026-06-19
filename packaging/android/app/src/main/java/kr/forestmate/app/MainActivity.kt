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
import android.view.Gravity
import android.view.View
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
import kr.forestmate.app.ui.Contour
import kr.forestmate.app.ui.NativeViews
import kr.forestmate.app.ui.TrailMapViews
import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.UrlConnectionTransport
import kr.forestmate.core.model.Course
import kr.forestmate.core.model.HikeIndex
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
    private var hikeIndex: HikeIndex? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)
        repository = ForestMateRepository(ApiConfig(store.apiBase), UrlConnectionTransport())
        hikeFlow = hikeFlow.copy(activeHikeId = store.activeHikeId.takeIf { it.isNotBlank() })
        tracking = hikeFlow.activeHikeId != null
        configureMaps()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.appBackground()
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
            setBackgroundColor(Contour.card)
            elevation = Contour.dp(this@MainActivity, 12f).toFloat()
            setPadding(
                Contour.dp(this@MainActivity, 6f),
                Contour.dp(this@MainActivity, 8f),
                Contour.dp(this@MainActivity, 6f),
                Contour.dp(this@MainActivity, 10f),
            )
            PhoneTab.entries.forEach { tab ->
                val button = NativeViews.tabButton(this@MainActivity, tab) {
                    navigation = navigation.select(tab.id)
                    render()
                }
                NativeViews.styleTabSelection(button, tab == navigation.selected)
                addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
        content.addView(indexCard())
        content.addView(NativeViews.primaryButton(this, "산행지수 새로고침") { loadHome(status) })
        content.addView(NativeViews.sectionHeader(this, "AI 맞춤 코스"))
        courses.forEach { course -> content.addView(courseCard(course)) }
    }

    /** Dark-forest index card: score ring + greeting + 2×2 safety sub-index. */
    private fun indexCard(): LinearLayout {
        val card = NativeViews.heroCard(this, dark = true)
        val idx = hikeIndex

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            NativeViews.ScoreRingView(this, idx?.score ?: 0),
            LinearLayout.LayoutParams(Contour.dp(this, 92f), Contour.dp(this, 92f)).apply {
                rightMargin = Contour.dp(this@MainActivity, 16f)
            },
        )
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rightCol.addView(TextView(this).apply {
            text = idx?.label ?: "산행지수를 불러오세요"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        rightCol.addView(TextView(this).apply {
            text = "오늘의 산행지수 · ${idx?.let { it.place.ifBlank { it.regionName } } ?: "위치 확인"}"
            textSize = 11f
            typeface = Contour.mono()
            setTextColor(0xB3FFFFFF.toInt())
            setPadding(0, Contour.dp(this@MainActivity, 4f), 0, Contour.dp(this@MainActivity, 10f))
        })
        rightCol.addView(subIndexGrid(idx))
        row.addView(rightCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun subIndexGrid(idx: HikeIndex?): LinearLayout {
        fun rowOf(a: Pair<String, String>, b: Pair<String, String>) =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(subTile(a.first, a.second), tileLp())
                addView(subTile(b.first, b.second), tileLp())
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(rowOf(
                "산불위험" to (idx?.fireLevel?.ifBlank { "-" } ?: "-"),
                "산악기상" to (idx?.let { "${it.temperatureC.toInt()}°C" } ?: "-"),
            ))
            addView(rowOf(
                "강수확률" to (idx?.let { "${it.rainProbability}%" } ?: "-"),
                "바람" to (idx?.let { "${"%.1f".format(it.windMps)}m/s" } ?: "-"),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = Contour.dp(this@MainActivity, 6f) })
        }
    }

    private fun tileLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Contour.dp(this@MainActivity, 6f)
        }

    private fun subTile(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, 0x1AFFFFFF, radiusDp = 8f)
            setPadding(Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 6f), Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 6f))
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 9.5f
                typeface = Contour.mono()
                setTextColor(0x99FFFFFF.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 12.5f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

    private fun renderHike(content: LinearLayout, status: TextView) {
        status.text = hikeStatusText()
        status.visibility = View.VISIBLE

        val detail = NativeViews.card(this)
        detail.addView(cardTitle(selectedCourse.name))
        detail.addView(metaChips(courseChips(selectedCourse)))
        detail.addView(NativeViews.bodyText(this, courseDetailText(selectedCourse)))
        content.addView(detail)

        val map = TrailMapViews.createMap(this, mapState)
        currentMapView = map
        content.addView(map, mapParams())
        content.addView(NativeViews.captionText(this, "© OpenStreetMap contributors · 추천 경로/위험 마커/GPS 트랙"))

        selectedCourse.hazards.forEach { hazard ->
            content.addView(
                hazardCard(
                    "위험구간 ${hazard.at.percent()} · ${hazard.type} · ${hazard.grade}",
                    hazard.note,
                ),
            )
        }

        content.addView(NativeViews.primaryButton(this, if (tracking) "산행 일시정지" else "산행 시작") { toggleHike(status) })
        content.addView(NativeViews.ghostButton(this, "GPS 데모 이동") { demoGps(status) })
        content.addView(NativeViews.ghostButton(this, "산행 종료") { endHike(status) })
        content.addView(NativeViews.primaryButton(this, "⌚ Galaxy Watch 연결") { pairWatch(status) })
    }

    private fun renderSos(content: LinearLayout, status: TextView) {
        val location = NativeViews.heroCard(this, dark = true)
        location.addView(heroTitle("현재 위치"))
        location.addView(heroLine("국가지점번호", selectedCourse.gridNo))
        location.addView(heroLine("GPS", selectedCourse.gps))
        location.addView(heroLine("관할 119", selectedCourse.fireStation))
        content.addView(location)
        content.addView(NativeViews.captionText(this, "버튼을 누르면 현재 산행 위치와 국가지점번호가 구조기관으로 전달됩니다."))
        content.addView(NativeViews.dangerButton(this, "🆘 SOS 전송") { sendSos(status) })
    }

    private fun renderAi(content: LinearLayout, status: TextView) {
        val card = NativeViews.card(this)
        card.addView(cardTitle("AI 숲이"))
        card.addView(NativeViews.bodyText(this, "날씨, 위험구간, 하산 여유를 물어보세요."))
        val input = styledInput("숲이에게 질문", "오늘 이 코스 안전해?")
        card.addView(input)
        card.addView(NativeViews.primaryButton(this, "묻기") { sendChat(status, input.text.toString()) })
        content.addView(card)
    }

    private fun renderMy(content: LinearLayout, status: TextView) {
        val account = NativeViews.card(this)
        account.addView(cardTitle("계정"))
        val email = styledInput("이메일", store.accountEmail).apply { setSingleLine(true) }
        val password = styledInput("비밀번호", "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        account.addView(email)
        account.addView(password)
        account.addView(NativeViews.primaryButton(this, "가입") { registerAccount(status, email.text.toString(), password.text.toString()) })
        account.addView(NativeViews.ghostButton(this, "로그인") { loginAccount(status, email.text.toString(), password.text.toString()) })
        content.addView(account)

        content.addView(NativeViews.primaryButton(this, "기록/배지 불러오기") { loadSummary(status) })

        val info = NativeViews.card(this)
        info.addView(cardTitle("내 산행"))
        info.addView(NativeViews.bodyText(this, "계정: ${store.accountEmail.ifBlank { "미연결" }}\n기기 등록: ${if (store.deviceToken.isBlank()) "대기" else "완료"}\n워치 코드: ${hikeFlow.watchPairCode ?: "없음"}"))
        content.addView(info)
    }

    // --- Contour-themed view helpers ----------------------------------------

    private fun courseCard(course: Course): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle(course.name))
        card.addView(metaChips(courseChips(course)))
        card.isClickable = true
        card.setOnClickListener {
            selectCourse(course)
            navigation = navigation.select(PhoneTab.HIKE.id)
            lastMessage = "${course.name} 선택됨"
            render()
        }
        return card
    }

    private fun courseChips(course: Course): List<String> =
        listOf(
            "${course.km}km",
            course.level.ifBlank { "난이도 확인" },
            course.crowd.ifBlank { "혼잡 확인" },
        )

    private fun metaChips(labels: List<String>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Contour.dp(this@MainActivity, 6f), 0, Contour.dp(this@MainActivity, 2f))
            labels.forEach { label ->
                addView(
                    NativeViews.chip(this@MainActivity, label),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { rightMargin = Contour.dp(this@MainActivity, 6f) },
                )
            }
        }

    private fun hazardCard(title: String, note: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, Contour.dangerBg, radiusDp = 14f, stroke = 0xFFF5C2C7.toInt(), strokeDp = 1f)
            setPadding(Contour.dp(this@MainActivity, 13f), Contour.dp(this@MainActivity, 11f), Contour.dp(this@MainActivity, 13f), Contour.dp(this@MainActivity, 11f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Contour.dp(this@MainActivity, 6f)
            lp.bottomMargin = Contour.dp(this@MainActivity, 6f)
            layoutParams = lp
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 12.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Contour.dangerInk)
            })
            addView(TextView(this@MainActivity).apply {
                text = note
                textSize = 11.5f
                setTextColor(0xFF7C4A4F.toInt())
                setPadding(0, Contour.dp(this@MainActivity, 3f), 0, 0)
            })
        }

    private fun cardTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15.5f
            setTextColor(Contour.pine)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun heroTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, Contour.dp(this@MainActivity, 6f))
        }

    private fun heroBody(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12.5f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, 0, 0, Contour.dp(this@MainActivity, 10f))
        }

    private fun heroLine(label: String, value: String): TextView =
        TextView(this).apply {
            text = "$label   $value"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, Contour.dp(this@MainActivity, 5f), 0, Contour.dp(this@MainActivity, 5f))
        }

    private fun styledInput(hintText: String, value: String): EditText =
        EditText(this).apply {
            hint = hintText
            setText(value)
            textSize = 14f
            setTextColor(Contour.ink)
            setHintTextColor(0xFF9AA8A0.toInt())
            background = Contour.round(this@MainActivity, Contour.card, radiusDp = 12f, stroke = Contour.line, strokeDp = 1.5f)
            setPadding(Contour.dp(this@MainActivity, 13f), Contour.dp(this@MainActivity, 12f), Contour.dp(this@MainActivity, 13f), Contour.dp(this@MainActivity, 12f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Contour.dp(this@MainActivity, 6f)
            lp.bottomMargin = Contour.dp(this@MainActivity, 6f)
            layoutParams = lp
        }

    private fun mapParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            Contour.dp(this, 240f),
        ).apply {
            topMargin = Contour.dp(this@MainActivity, 6f)
            bottomMargin = Contour.dp(this@MainActivity, 4f)
        }

    private fun loadHome(status: TextView) {
        runApi(status, "산행지수와 추천을 불러오는 중...") {
            val indexResult = repository.hikeIndex()
            if (indexResult is ApiResult.Success) hikeIndex = indexResult.value
            val indexText = messageFor(indexResult) {
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
