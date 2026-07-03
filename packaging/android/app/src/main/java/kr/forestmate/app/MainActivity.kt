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
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kr.forestmate.app.state.HikeFlowState
import kr.forestmate.app.state.LatLon
import kr.forestmate.app.state.NavigationState
import kr.forestmate.app.state.PhoneTab
import kr.forestmate.app.state.TrailMapState
import kr.forestmate.app.ui.ConditionDetailViews
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
    private var autoLoadedHome = false

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
            setPadding(0, 0, 0, navigationBarHeight())
            // let the raised SOS nav button overflow above the tab bar
            clipChildren = false
            clipToPadding = false
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

    private fun navigationBarHeight(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
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
        PhoneTab.HOME -> "좋음 — 산행하기 좋은 날" to "산행지수와 맞춤 코스를 한눈에 확인하세요."
        PhoneTab.HIKE -> selectedCourse.name to selectedCourse.route
        PhoneTab.SOS -> "안전 요청" to "현재 위치와 국가지점번호를 구조기관에 전달합니다."
        PhoneTab.AI -> "AI 숲해설사 '숲이'" to "위험한 식물, 코스 여유, 날씨를 자연어로 물어보세요."
        PhoneTab.MY -> "내 산행" to "기록, 배지, 안전 이벤트를 모아 봅니다."
    }

    /** Bottom nav — design parity: pine/muted tabs with a raised red SOS button. */
    private fun tabBar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            elevation = Contour.dp(this@MainActivity, 12f).toFloat()
            addView(
                View(this@MainActivity).apply { setBackgroundColor(0xFFE6ECE6.toInt()) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Contour.dp(this@MainActivity, 1f)),
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    setBackgroundColor(0xF5FFFFFF.toInt())
                    setPadding(
                        Contour.dp(this@MainActivity, 6f),
                        Contour.dp(this@MainActivity, 8f),
                        Contour.dp(this@MainActivity, 6f),
                        Contour.dp(this@MainActivity, 10f),
                    )
                    PhoneTab.entries.forEach { tab ->
                        addView(
                            navItem(tab, tab == navigation.selected),
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.BOTTOM },
                        )
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }

    private fun navIcon(tab: PhoneTab): String = when (tab) {
        PhoneTab.HOME -> "🏠"
        PhoneTab.HIKE -> "🧭"
        PhoneTab.SOS -> "SOS"
        PhoneTab.AI -> "💬"
        PhoneTab.MY -> "👤"
    }

    private fun navItem(tab: PhoneTab, selected: Boolean): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            clipChildren = false
            clipToPadding = false
            isClickable = true
            setOnClickListener {
                navigation = navigation.select(tab.id)
                render()
            }
            if (tab == PhoneTab.SOS) {
                addView(
                    sosBadge(),
                    LinearLayout.LayoutParams(Contour.dp(this@MainActivity, 46f), Contour.dp(this@MainActivity, 46f)).apply {
                        topMargin = -Contour.dp(this@MainActivity, 22f)
                        bottomMargin = Contour.dp(this@MainActivity, 1f)
                        gravity = Gravity.CENTER_HORIZONTAL
                    },
                )
                addView(navLabel(tab.label, Contour.danger, selected))
            } else {
                addView(TextView(this@MainActivity).apply {
                    text = navIcon(tab)
                    textSize = 20f
                    gravity = Gravity.CENTER
                })
                addView(navLabel(tab.label, if (selected) Contour.pine else 0xFF9FB0A4.toInt(), selected))
            }
        }

    private fun navLabel(text: String, color: Int, selected: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 9.5f
            setTextColor(color)
            typeface = if (selected) Contour.black() else Contour.bold()
            gravity = Gravity.CENTER
            setPadding(0, Contour.dp(this@MainActivity, 3f), 0, 0)
        }

    /** Raised SOS circle: radial red fill with a white ring (nav a.sosnav .nic). */
    private fun sosBadge(): TextView =
        TextView(this).apply {
            text = "SOS"
            textSize = 13f
            typeface = Contour.black()
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(0xFFE02F3B.toInt(), 0xFFA90F1F.toInt())
                gradientRadius = Contour.dp(this@MainActivity, 30f).toFloat()
                setGradientCenter(0.35f, 0.30f)
                setStroke(Contour.dp(this@MainActivity, 4f), 0xFFFFFFFF.toInt())
            }
            elevation = Contour.dp(this@MainActivity, 8f).toFloat()
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
        content.addView(searchCard())
        content.addView(sectionRow("🤖 AI 맞춤 코스", "체력 중급 · 무릎 주의 이력 반영"))
        content.addView(courseCarousel())
        content.addView(sectionRow("🛡 안전 브리핑", "하산 사고와 날씨 변화를 먼저 확인"))
        content.addView(safetyBriefingCard())
        content.addView(newsCard())
        content.addView(NativeViews.ghostButton(this, "산행지수 새로고침") { loadHome(status) })
        if (!autoLoadedHome) {
            autoLoadedHome = true
            status.post {
                if (navigation.selected == PhoneTab.HOME) loadHome(status)
            }
        }
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
            text = idx?.label ?: "산행하기 좋은 날"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        rightCol.addView(TextView(this).apply {
            text = "오늘의 산행지수 · ${idx?.let { it.place.ifBlank { it.regionName } } ?: "서울 은평구"}"
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
        val tiles = ConditionDetailViews.summaryTiles(idx)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tiles.chunked(2).forEachIndexed { rowIdx, pair ->
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        pair.forEach { addView(subTile(it), tileLp()) }
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        if (rowIdx > 0) topMargin = Contour.dp(this@MainActivity, 6f)
                    },
                )
            }
        }
    }

    private fun tileLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Contour.dp(this@MainActivity, 6f)
        }

    private fun toneColor(tone: String): Int = when (tone) {
        "ok" -> 0xFFB7E4C7.toInt()
        "mid" -> 0xFFFFD8A8.toInt()
        else -> 0xFFFFB3B8.toInt()
    }

    /** Sub-index tile — tap opens the condition detail sheet (산행지수 타일을 눌러 위험 근거 펼치기). */
    private fun subTile(tile: ConditionDetailViews.Tile): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, 0x1AFFFFFF, radiusDp = 8f)
            setPadding(Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 6f), Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 6f))
            isClickable = true
            setOnClickListener { showConditionDetail(tile.id) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = tile.label
                    textSize = 9.5f
                    typeface = Contour.mono()
                    setTextColor(0x99FFFFFF.toInt())
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = "자세히 ›"
                    textSize = 8.5f
                    setTextColor(0x80FFFFFF.toInt())
                })
            })
            addView(TextView(this@MainActivity).apply {
                text = tile.value
                textSize = 12.5f
                setTextColor(toneColor(tile.tone))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, Contour.dp(this@MainActivity, 2f), 0, 0)
            })
        }

    /** Bottom-sheet condition detail dialog (condition-panel parity). */
    private fun showConditionDetail(id: String) {
        val detail = ConditionDetailViews.build(id, hikeIndex)
        val dialog = android.app.Dialog(this)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(ConditionDetailViews.panel(this@MainActivity, detail) { dialog.dismiss() })
        }
        dialog.setContentView(scroll)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0x00000000))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        dialog.show()
    }

    private fun sectionRow(title: String, meta: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Contour.dp(this@MainActivity, 2f), Contour.dp(this@MainActivity, 20f), 0, Contour.dp(this@MainActivity, 9f))
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 20f
                setTextColor(Contour.pine)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = meta
                textSize = 11.5f
                setTextColor(Contour.sub)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

    private fun searchCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, Contour.card, radiusDp = 18f, stroke = 0xFFD7E4D9.toInt(), strokeDp = 1.5f)
            setPadding(Contour.dp(this@MainActivity, 18f), Contour.dp(this@MainActivity, 15f), Contour.dp(this@MainActivity, 18f), Contour.dp(this@MainActivity, 15f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Contour.dp(this@MainActivity, 12f)
            layoutParams = lp
            addView(TextView(this@MainActivity).apply {
                text = "🔍 전국 산 검색"
                textSize = 18f
                setTextColor(Contour.pine)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "산림청 산정보 · 전국 3,400여 개 산"
                textSize = 12.5f
                setTextColor(Contour.sub)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, Contour.dp(this@MainActivity, 6f), 0, 0)
            })
        }

    private fun courseCarousel(): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    courses.forEach { course ->
                        addView(courseCard(course), LinearLayout.LayoutParams(Contour.dp(this@MainActivity, 232f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            rightMargin = Contour.dp(this@MainActivity, 14f)
                        })
                    }
                },
            )
        }

    private fun safetyBriefingCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, 0xFFFFF6EA.toInt(), radiusDp = 18f, stroke = 0xFFFFD6A5.toInt(), strokeDp = 1.2f)
            setPadding(Contour.dp(this@MainActivity, 17f), Contour.dp(this@MainActivity, 15f), Contour.dp(this@MainActivity, 17f), Contour.dp(this@MainActivity, 15f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Contour.dp(this@MainActivity, 14f)
            layoutParams = lp
            addView(TextView(this@MainActivity).apply {
                text = "⚠ 하산 시 사고가 등반보다 1.8배 많아요."
                textSize = 14f
                setTextColor(Contour.cautionInk)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(NativeViews.bodyText(this@MainActivity, "스틱으로 무릎 부담을 줄이고, 급경사 전환 구간에서는 속도를 낮추세요."))
        }

    private fun newsCard(): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle("🌿 이번 주 숲 소식"))
        card.addView(NativeViews.bodyText(this, "국립공원 탐방 예약과 산불·강풍 안내를 함께 확인하세요. 위험 알림은 코스별 안전 브리핑에 반영됩니다."))
        return card
    }

    private fun renderHike(content: LinearLayout, status: TextView) {
        status.text = hikeStatusText()
        status.visibility = View.VISIBLE

        content.addView(directionsCard())

        val map = TrailMapViews.createMap(this, mapState)
        currentMapView = map
        content.addView(map, mapParams())
        content.addView(NativeViews.captionText(this, "© OpenStreetMap contributors · 추천 경로/위험 마커/GPS 트랙 · 오프라인 지도 저장됨"))

        content.addView(hikeMetricRow())

        selectedCourse.hazards.forEach { hazard ->
            content.addView(
                hazardCard(
                    "위험구간 ${hazard.at.percent()} · ${hazard.type} · ${hazard.grade}",
                    hazard.note,
                ),
            )
        }

        content.addView(NativeViews.primaryButton(this, if (tracking) "산행 일시정지" else "산행 시작") { toggleHike(status) })
        content.addView(NativeViews.ghostButton(this, "산행 종료") { endHike(status) })
        content.addView(NativeViews.ghostButton(this, "데모 이동 +90m") { demoGps(status) })
        content.addView(NativeViews.ghostButton(this, "워치 백업 연결") { pairWatch(status) })
    }

    private fun directionsCard(): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle("🧭 들머리까지 가는 길"))
        card.addView(NativeViews.bodyText(this, "탐방지원센터 · ${selectedCourse.route.substringBefore(" → ")}"))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Contour.dp(this@MainActivity, 10f), 0, Contour.dp(this@MainActivity, 6f))
            addView(NativeViews.primaryButton(this@MainActivity, "현재위치→카카오맵") { lastMessage = "길찾기 앱을 열 준비 중입니다."; render() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Contour.dp(this@MainActivity, 8f)
            })
            addView(NativeViews.ghostButton(this@MainActivity, "구글맵") { lastMessage = "길찾기 앱을 열 준비 중입니다."; render() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(actions)
        card.addView(NativeViews.captionText(this, "도착하면 산행 시작을 눌러 GPS 추적을 켜세요."))
        return card
    }

    private fun hikeMetricRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(metricTile(mapState.walkedKm.formatKm() + "km", "이동 / ${selectedCourse.km}km"), metricLp())
            addView(metricTile("${selectedCourse.elevation.firstOrNull() ?: 120}m", "현재 고도"), metricLp())
            addView(metricTile(hikeFlow.watchPairCode ?: "-", "심박(워치)"), metricLp())
            addView(metricTile("1:22", "일몰까지"), metricLp())
        }

    private fun metricLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Contour.dp(this@MainActivity, 8f)
            bottomMargin = Contour.dp(this@MainActivity, 14f)
        }

    private fun metricTile(value: String, label: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Contour.round(this@MainActivity, Contour.card, radiusDp = 14f)
            setPadding(Contour.dp(this@MainActivity, 7f), Contour.dp(this@MainActivity, 12f), Contour.dp(this@MainActivity, 7f), Contour.dp(this@MainActivity, 12f))
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 15f
                setTextColor(Contour.pine)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 9.5f
                setTextColor(Contour.sub)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
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
        content.addView(photoQuestionCard())
        content.addView(chatBubble("길에서 봤는데, 이 버섯 먹어도 돼?", fromUser = true))
        content.addView(aiRiskCard())
        content.addView(chatBubble("백운대 정상까지 얼마나 남았어?", fromUser = true))
        content.addView(chatBubble("남은 거리 1.8km, 지금 페이스라면 약 55분 뒤 도착해요. 일몰까지 여유는 있지만 정상 부근 바람이 강하니 겉옷을 준비하세요.", fromUser = false))

        val inputCard = NativeViews.card(this)
        val input = styledInput("숲이에게 질문", "오늘 이 코스 안전해?")
        inputCard.addView(input)
        inputCard.addView(NativeViews.primaryButton(this, "묻기") { sendChat(status, input.text.toString()) })
        content.addView(inputCard)
    }

    private fun photoQuestionCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val photo = FrameLayout(this@MainActivity).apply {
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF7E6A3D.toInt(), 0xFFE8D684.toInt(), 0xFF4B3821.toInt()),
                ).apply { cornerRadius = Contour.dp(this@MainActivity, 18f).toFloat() }
                addView(TextView(this@MainActivity).apply {
                    text = "📷 방금 촬영한 사진"
                    textSize = 13f
                    setTextColor(Contour.sub)
                    background = Contour.round(this@MainActivity, Contour.card, radiusDp = 0f)
                    setPadding(Contour.dp(this@MainActivity, 14f), Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 14f), Contour.dp(this@MainActivity, 8f))
                }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            }
            addView(photo, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Contour.dp(this@MainActivity, 160f)).apply {
                bottomMargin = Contour.dp(this@MainActivity, 12f)
            })
        }

    private fun chatBubble(text: String, fromUser: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setLineSpacing(Contour.dp(this@MainActivity, 3f).toFloat(), 1f)
            setTextColor(if (fromUser) 0xFFFFFFFF.toInt() else Contour.ink)
            background = Contour.round(this@MainActivity, if (fromUser) 0xFF2D7555.toInt() else Contour.card, radiusDp = 18f)
            setPadding(Contour.dp(this@MainActivity, 18f), Contour.dp(this@MainActivity, 14f), Contour.dp(this@MainActivity, 18f), Contour.dp(this@MainActivity, 14f))
            val lp = LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.72f).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = if (fromUser) Gravity.END else Gravity.START
            lp.bottomMargin = Contour.dp(this@MainActivity, 12f)
            layoutParams = lp
        }

    private fun aiRiskCard(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, 0xFFFFECEE.toInt(), radiusDp = 18f, stroke = 0xFFEF3D4C.toInt(), strokeDp = 1.5f)
            setPadding(Contour.dp(this@MainActivity, 18f), Contour.dp(this@MainActivity, 15f), Contour.dp(this@MainActivity, 18f), Contour.dp(this@MainActivity, 15f))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Contour.dp(this@MainActivity, 16f)
            layoutParams = lp
            addView(TextView(this@MainActivity).apply {
                text = "🚫 개나리광대버섯 가능성 높음"
                textSize = 15f
                setTextColor(0xFFC7252D.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(NativeViews.bodyText(this@MainActivity, "아마톡신 함유 맹독성 버섯과 유사합니다. 소량 섭취도 위험할 수 있어요."))
            addView(TextView(this@MainActivity).apply {
                text = "AI 판별 신뢰도 87% · 국립수목원 자료 대조"
                textSize = 12f
                setTextColor(0xFF8B4A52.toInt())
                setPadding(0, Contour.dp(this@MainActivity, 8f), 0, Contour.dp(this@MainActivity, 8f))
            })
            addView(TextView(this@MainActivity).apply {
                text = "⚠ 절대 채취·섭취 금지. 만졌다면 흐르는 물에 손을 씻어주세요."
                textSize = 13f
                setTextColor(0xFFC7252D.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

    private fun renderMy(content: LinearLayout, status: TextView) {
        content.addView(dashboardSummaryCard())
        content.addView(safetyEventsCard())

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

    private fun dashboardSummaryCard(): LinearLayout {
        val card = NativeViews.heroCard(this, dark = true)
        card.addView(heroTitle("실시간 안전 이벤트"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(darkStat("1", "SOS 훈련"), darkStatLp())
            addView(darkStat("7", "위험 감지"), darkStatLp())
            addView(darkStat("23분", "평균 도착"), darkStatLp())
        }
        card.addView(row)
        card.addView(heroBody("개인 위치는 k-익명화 기준으로만 안전 분석에 반영됩니다."))
        return card
    }

    private fun darkStatLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = Contour.dp(this@MainActivity, 8f)
        }

    private fun darkStat(value: String, label: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, 0x1AFFFFFF, radiusDp = 12f)
            setPadding(Contour.dp(this@MainActivity, 9f), Contour.dp(this@MainActivity, 10f), Contour.dp(this@MainActivity, 9f), Contour.dp(this@MainActivity, 10f))
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 19f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                setTextColor(0xB3FFFFFF.toInt())
            })
        }

    private fun safetyEventsCard(): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle("구간별 위험도"))
        card.addView(eventLine("인수봉 동면 슬랩", "높음 81", "강풍 9m/s · 사고다발"))
        card.addView(eventLine("Y계곡 암릉", "높음 76", "낙석·정체"))
        card.addView(eventLine("백운대 정상부", "주의 58", "혼잡·일몰임박"))
        return card
    }

    private fun eventLine(zone: String, grade: String, reason: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Contour.dp(this@MainActivity, 10f), 0, Contour.dp(this@MainActivity, 10f))
            addView(TextView(this@MainActivity).apply {
                text = "$zone · $grade"
                textSize = 13.5f
                setTextColor(if (grade.startsWith("높음")) Contour.dangerInk else Contour.cautionInk)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = reason
                textSize = 11.5f
                setTextColor(Contour.sub)
            })
        }

    // --- Contour-themed view helpers ----------------------------------------

    /** AI 맞춤 코스 card: signature-gradient hero strip + match badge + grid coord. */
    private fun courseCard(course: Course): LinearLayout {
        val radius = Contour.dp(this, 18f)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.round(this@MainActivity, Contour.card, radiusDp = 18f, stroke = Contour.cardBorder, strokeDp = 1f)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius.toFloat())
                }
            }
            clipToOutline = true
            elevation = Contour.dp(this@MainActivity, 4f).toFloat()
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Contour.dp(this@MainActivity, 14f)
            layoutParams = lp
        }

        val hero = FrameLayout(this).apply { background = Contour.courseHeroStrip(this@MainActivity) }
        hero.addView(
            TextView(this).apply {
                text = if (course.view > 0) "매칭 ${course.view}%" else "AI 추천"
                textSize = 10f
                typeface = Contour.mono()
                setTextColor(0xFFFFFFFF.toInt())
                background = Contour.pill(this@MainActivity, 0x47000000)
                setPadding(Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 4f), Contour.dp(this@MainActivity, 8f), Contour.dp(this@MainActivity, 4f))
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                leftMargin = Contour.dp(this@MainActivity, 10f)
                topMargin = Contour.dp(this@MainActivity, 10f)
            },
        )
        if (course.gridNo.isNotBlank()) {
            hero.addView(
                TextView(this).apply {
                    text = "국가지점번호 ${course.gridNo}"
                    textSize = 9f
                    typeface = Contour.mono()
                    setTextColor(0xE6FFFFFF.toInt())
                },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = Contour.dp(this@MainActivity, 12f)
                    bottomMargin = Contour.dp(this@MainActivity, 9f)
                },
            )
        }
        card.addView(hero, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Contour.dp(this, 96f)))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Contour.dp(this@MainActivity, 14f), Contour.dp(this@MainActivity, 12f), Contour.dp(this@MainActivity, 14f), Contour.dp(this@MainActivity, 14f))
        }
        body.addView(cardTitle(course.name))
        body.addView(TextView(this).apply {
            text = courseMetaLine(course)
            textSize = 11.5f
            typeface = Contour.mono()
            setTextColor(Contour.sub)
            setPadding(0, Contour.dp(this@MainActivity, 7f), 0, 0)
        })
        card.addView(body)

        card.isClickable = true
        card.setOnClickListener {
            selectCourse(course)
            navigation = navigation.select(PhoneTab.HIKE.id)
            lastMessage = "${course.name} 선택됨"
            render()
        }
        return card
    }

    private fun courseMetaLine(course: Course): String {
        val time = if (course.minutes >= 60) "${course.minutes / 60}시간${(course.minutes % 60).let { if (it > 0) "${it}분" else "" }}" else "${course.minutes}분"
        val level = course.level.ifBlank { "확인" }
        return "▲ ${course.km}km   ◷ $time   ● 난이도 $level"
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
            val healthResult = repository.health()
            if (healthResult is ApiResult.Failure) {
                return@runApi homeFallbackMessage()
            }
            val indexResult = repository.hikeIndex()
            if (indexResult is ApiResult.Success) {
                hikeIndex = indexResult.value
            } else {
                return@runApi homeFallbackMessage()
            }
            val remoteCourses = repository.courses()
            if (remoteCourses is ApiResult.Success && remoteCourses.value.isNotEmpty()) {
                courses = remoteCourses.value
                selectedCourse = courses.firstOrNull { it.id == selectedCourse.id } ?: courses.first()
                mapState = TrailMapState.forCourse(selectedCourse)
            } else if (remoteCourses is ApiResult.Failure) {
                return@runApi "추천 코스는 저장된 목록으로 표시 중입니다."
            }
            ""
        }
    }

    private fun homeFallbackMessage(): String =
        "최신 데이터를 불러오지 못해 저장된 코스를 보여줍니다."

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
        status.visibility = if (loading.isBlank()) View.GONE else View.VISIBLE
        Thread {
            val message = try {
                block()
            } catch (ex: Exception) {
                "요청을 완료하지 못했습니다. 잠시 후 다시 시도해주세요."
            }
            runOnUiThread {
                lastMessage = message
                status.text = message
                status.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
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

    private fun hikeStatusText(): String =
        "진행 ${hikeFlow.progress.percent()} · 이동 ${mapState.walkedKm.formatKm()}km · ${if (tracking) "GPS 추적 중" else "대기"} · 워치 ${hikeFlow.watchPairCode ?: "미연결"}"

    private fun Double.percent(): String = "${(this.coerceIn(0.0, 1.0) * 100).toInt()}%"

    private fun Double.formatKm(): String = "%.2f".format(this)

    companion object {
        private const val REQ_LOCATION = 7001
    }
}
