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
import kr.forestmate.app.ui.BottomNavLayout
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
    private var usingLocalCatalog = true
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
    private val appLanguage: AppLanguage
        get() = resolveLanguage()
    private val copy: AppCopy
        get() = DesignCopy.forLanguage(appLanguage)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)
        syncLocalCatalogForLanguage(resetMap = true)
        repository = ForestMateRepository(ApiConfig(store.apiBase), UrlConnectionTransport())
        hikeFlow = hikeFlow.copy(activeHikeId = store.activeHikeId.takeIf { it.isNotBlank() })
        tracking = hikeFlow.activeHikeId != null
        configureMaps()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.appBackground()
            setPadding(0, 0, 0, BottomNavLayout.rootBottomPaddingPx())
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

    private fun resolveLanguage(): AppLanguage {
        val stored = store.languageOverride
        return if (stored.isNotBlank()) AppLanguage.fromStoredValue(stored) else AppLanguage.fromLanguageTag(deviceLanguageTag())
    }

    private fun deviceLanguageTag(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]?.toLanguageTag().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale.toLanguageTag()
        }

    private fun syncLocalCatalogForLanguage(resetMap: Boolean = false) {
        if (!usingLocalCatalog) return
        val localized = LocalCatalog.coursesFor(appLanguage)
        val selectedId = selectedCourse.id
        courses = localized
        selectedCourse = localized.firstOrNull { it.id == selectedId } ?: localized.first()
        if (resetMap || mapState.courseId != selectedCourse.id || mapState.language != appLanguage) {
            mapState = TrailMapState.forCourse(selectedCourse, appLanguage)
        }
    }

    private fun setLanguage(language: AppLanguage) {
        store.languageOverride = language.storedValue
        usingLocalCatalog = true
        syncLocalCatalogForLanguage(resetMap = true)
        lastMessage = ""
        render()
    }

    private fun navigationBarHeight(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun bottomNavSafeInset(): Int =
        BottomNavLayout.tabContentSafeBottomInsetPx(
            navigationBarHeight(),
            Contour.dp(this, BottomNavLayout.maxVisualSafeBottomInsetDp),
        )

    private fun render() {
        syncLocalCatalogForLanguage()
        currentMapView?.onDetach()
        currentMapView = null
        root.removeAllViews()
        val (title, body) = bodyFor(navigation.selected)
        val content = NativeViews.screen(this, title, body, copy)
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
        PhoneTab.HOME -> copy.text("screen.home.title") to copy.text("screen.home.subtitle")
        PhoneTab.HIKE -> selectedCourse.name to selectedCourse.route
        PhoneTab.SOS -> copy.text("screen.sos.title") to copy.text("screen.sos.subtitle")
        PhoneTab.AI -> copy.text("screen.ai.title") to copy.text("screen.ai.subtitle")
        PhoneTab.MY -> copy.text("screen.my.title") to copy.text("screen.my.subtitle")
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
                        Contour.dp(this@MainActivity, 10f) + bottomNavSafeInset(),
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
                addView(navLabel(copy.tabLabel(tab), Contour.danger, selected))
            } else {
                addView(TextView(this@MainActivity).apply {
                    text = navIcon(tab)
                    textSize = 20f
                    gravity = Gravity.CENTER
                })
                addView(navLabel(copy.tabLabel(tab), if (selected) Contour.pine else 0xFF9FB0A4.toInt(), selected))
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
        content.addView(sectionRow(copy.text("home.ai.title"), copy.text("home.ai.meta")))
        content.addView(courseCarousel())
        content.addView(sectionRow(copy.text("home.safety.title"), copy.text("home.safety.meta")))
        content.addView(safetyBriefingCard())
        content.addView(newsCard())
        content.addView(NativeViews.ghostButton(this, copy.text("home.refresh")) { loadHome(status) })
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
            text = copy.hikingIndexLabel(idx?.label)
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        rightCol.addView(TextView(this).apply {
            text = copy.format("home.index.line", idx?.let { copy.placeName(it.place, it.regionName) } ?: copy.text("location.short"))
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
        val tiles = ConditionDetailViews.summaryTiles(idx, appLanguage)
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
                    text = copy.text("detail.more")
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
        val detail = ConditionDetailViews.build(id, hikeIndex, appLanguage)
        val dialog = android.app.Dialog(this)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(ConditionDetailViews.panel(this@MainActivity, detail, { dialog.dismiss() }, appLanguage))
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
                text = copy.text("home.search.title")
                textSize = 18f
                setTextColor(Contour.pine)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = copy.text("home.search.meta")
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
                text = copy.text("home.safety.head")
                textSize = 14f
                setTextColor(Contour.cautionInk)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(NativeViews.bodyText(this@MainActivity, copy.text("home.safety.body")))
        }

    private fun newsCard(): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle(copy.text("home.news.title")))
        card.addView(NativeViews.bodyText(this, copy.text("home.news.body")))
        return card
    }

    private fun renderHike(content: LinearLayout, status: TextView) {
        status.text = hikeStatusText()
        status.visibility = View.VISIBLE

        content.addView(directionsCard())

        val map = TrailMapViews.createMap(this, mapState)
        currentMapView = map
        content.addView(map, mapParams())
        content.addView(NativeViews.captionText(this, copy.text("map.caption")))

        content.addView(hikeMetricRow())

        selectedCourse.hazards.forEach { hazard ->
            content.addView(
                hazardCard(
                    copy.format("hike.hazard.title", hazard.at.percent(), hazard.type, hazard.grade),
                    hazard.note,
                ),
            )
        }

        content.addView(NativeViews.primaryButton(this, if (tracking) copy.text("hike.button.pause") else copy.text("hike.button.start")) { toggleHike(status) })
        content.addView(NativeViews.ghostButton(this, copy.text("hike.button.end")) { endHike(status) })
        content.addView(NativeViews.ghostButton(this, copy.text("hike.button.demo")) { demoGps(status) })
        content.addView(NativeViews.ghostButton(this, copy.text("hike.button.watch")) { pairWatch(status) })
    }

    private fun directionsCard(): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle(copy.text("directions.title")))
        card.addView(NativeViews.bodyText(this, copy.format("directions.body", selectedCourse.route.substringBefore(" → "))))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Contour.dp(this@MainActivity, 10f), 0, Contour.dp(this@MainActivity, 6f))
            addView(NativeViews.primaryButton(this@MainActivity, copy.text("directions.kakao")) { lastMessage = copy.text("directions.preparing"); render() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = Contour.dp(this@MainActivity, 8f)
            })
            addView(NativeViews.ghostButton(this@MainActivity, copy.text("directions.google")) { lastMessage = copy.text("directions.preparing"); render() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        card.addView(actions)
        card.addView(NativeViews.captionText(this, copy.text("directions.caption")))
        return card
    }

    private fun hikeMetricRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(metricTile(mapState.walkedKm.formatKm() + "km", copy.format("metric.distance", selectedCourse.km)), metricLp())
            addView(metricTile("${selectedCourse.elevation.firstOrNull() ?: 120}m", copy.text("metric.altitude")), metricLp())
            addView(metricTile(hikeFlow.watchPairCode ?: "-", copy.text("metric.heart")), metricLp())
            addView(metricTile("1:22", copy.text("metric.sunset")), metricLp())
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
        location.addView(heroTitle(copy.text("sos.location")))
        location.addView(heroLine(copy.text("sos.grid"), selectedCourse.gridNo))
        location.addView(heroLine("GPS", selectedCourse.gps))
        location.addView(heroLine(copy.text("sos.station"), selectedCourse.fireStation))
        content.addView(location)
        content.addView(NativeViews.captionText(this, copy.text("sos.caption")))
        content.addView(NativeViews.dangerButton(this, copy.text("sos.send")) { sendSos(status) })
    }

    private fun renderAi(content: LinearLayout, status: TextView) {
        content.addView(photoQuestionCard())
        content.addView(chatBubble(copy.text("ai.sample.user1"), fromUser = true))
        content.addView(aiRiskCard())
        content.addView(chatBubble(copy.text("ai.sample.user2"), fromUser = true))
        content.addView(chatBubble(copy.text("ai.sample.assistant2"), fromUser = false))

        val inputCard = NativeViews.card(this)
        val input = styledInput(copy.text("ai.input.hint"), copy.text("ai.input.default"))
        inputCard.addView(input)
        inputCard.addView(NativeViews.primaryButton(this, copy.text("ai.ask")) { sendChat(status, input.text.toString()) })
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
                    text = copy.text("ai.photo")
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
                text = copy.text("ai.risk.title")
                textSize = 15f
                setTextColor(0xFFC7252D.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(NativeViews.bodyText(this@MainActivity, copy.text("ai.risk.body")))
            addView(TextView(this@MainActivity).apply {
                text = copy.text("ai.risk.confidence")
                textSize = 12f
                setTextColor(0xFF8B4A52.toInt())
                setPadding(0, Contour.dp(this@MainActivity, 8f), 0, Contour.dp(this@MainActivity, 8f))
            })
            addView(TextView(this@MainActivity).apply {
                text = copy.text("ai.risk.warning")
                textSize = 13f
                setTextColor(0xFFC7252D.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

    private fun renderMy(content: LinearLayout, status: TextView) {
        content.addView(dashboardSummaryCard())
        content.addView(safetyEventsCard())
        content.addView(languageCard())

        val account = NativeViews.card(this)
        account.addView(cardTitle(copy.text("my.account.title")))
        val email = styledInput(copy.text("my.email"), store.accountEmail).apply { setSingleLine(true) }
        val password = styledInput(copy.text("my.password"), "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        account.addView(email)
        account.addView(password)
        account.addView(NativeViews.primaryButton(this, copy.text("my.signup")) { registerAccount(status, email.text.toString(), password.text.toString()) })
        account.addView(NativeViews.ghostButton(this, copy.text("my.login")) { loginAccount(status, email.text.toString(), password.text.toString()) })
        content.addView(account)

        content.addView(NativeViews.primaryButton(this, copy.text("my.load")) { loadSummary(status) })

        val info = NativeViews.card(this)
        info.addView(cardTitle(copy.text("my.summary.title")))
        info.addView(
            NativeViews.bodyText(
                this,
                "${copy.text("my.account.label")}: ${store.accountEmail.ifBlank { copy.text("my.disconnected") }}\n" +
                    "${copy.text("my.device.label")}: ${if (store.deviceToken.isBlank()) copy.text("my.device.pending") else copy.text("my.device.complete")}\n" +
                    "${copy.text("my.watch.label")}: ${hikeFlow.watchPairCode ?: copy.text("my.watch.none")}",
            ),
        )
        content.addView(info)
    }

    private fun languageCard(): LinearLayout {
        val card = NativeViews.card(this)
        card.addView(cardTitle(copy.text("language.title")))
        card.addView(NativeViews.bodyText(this, copy.text("language.body")))
        card.addView(NativeViews.captionText(this, copy.format("language.current", copy.text(if (appLanguage == AppLanguage.ENGLISH) "language.english" else "language.korean"))))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                if (appLanguage == AppLanguage.KOREAN) {
                    NativeViews.primaryButton(this@MainActivity, copy.text("language.korean")) {}
                } else {
                    NativeViews.ghostButton(this@MainActivity, copy.text("language.korean")) { setLanguage(AppLanguage.KOREAN) }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = Contour.dp(this@MainActivity, 8f)
                },
            )
            addView(
                if (appLanguage == AppLanguage.ENGLISH) {
                    NativeViews.primaryButton(this@MainActivity, copy.text("language.english")) {}
                } else {
                    NativeViews.ghostButton(this@MainActivity, copy.text("language.english")) { setLanguage(AppLanguage.ENGLISH) }
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        card.addView(row)
        return card
    }

    private fun dashboardSummaryCard(): LinearLayout {
        val card = NativeViews.heroCard(this, dark = true)
        card.addView(heroTitle(copy.text("my.safety.title")))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(darkStat("1", copy.text("my.stat.sos")), darkStatLp())
            addView(darkStat("7", copy.text("my.stat.risk")), darkStatLp())
            addView(darkStat(copy.text("my.stat.arrival.value"), copy.text("my.stat.arrival")), darkStatLp())
        }
        card.addView(row)
        card.addView(heroBody(copy.text("my.privacy")))
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
        card.addView(cardTitle(copy.text("my.risk.section")))
        card.addView(eventLine(copy.text("event.zone1"), copy.text("event.grade1"), copy.text("event.reason1")))
        card.addView(eventLine(copy.text("event.zone2"), copy.text("event.grade2"), copy.text("event.reason2")))
        card.addView(eventLine(copy.text("event.zone3"), copy.text("event.grade3"), copy.text("event.reason3")))
        return card
    }

    private fun eventLine(zone: String, grade: String, reason: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Contour.dp(this@MainActivity, 10f), 0, Contour.dp(this@MainActivity, 10f))
            addView(TextView(this@MainActivity).apply {
                text = "$zone · $grade"
                textSize = 13.5f
                setTextColor(if (copy.lowHighGrade(grade)) Contour.dangerInk else Contour.cautionInk)
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
                text = if (course.view > 0) copy.format("course.match", course.view) else copy.text("course.ai")
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
                    text = copy.format("course.grid", course.gridNo)
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
            lastMessage = copy.format("course.selected", course.name)
            render()
        }
        return card
    }

    private fun courseMetaLine(course: Course): String = copy.courseMeta(course)

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
        runApi(status, copy.text("status.home.loading")) {
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
                if (appLanguage == AppLanguage.ENGLISH) {
                    usingLocalCatalog = true
                    syncLocalCatalogForLanguage(resetMap = true)
                } else {
                    usingLocalCatalog = false
                    courses = remoteCourses.value
                    selectedCourse = courses.firstOrNull { it.id == selectedCourse.id } ?: courses.first()
                    mapState = TrailMapState.forCourse(selectedCourse, appLanguage)
                }
            } else if (remoteCourses is ApiResult.Failure) {
                usingLocalCatalog = true
                return@runApi copy.text("status.home.stored")
            }
            ""
        }
    }

    private fun homeFallbackMessage(): String =
        copy.text("status.home.fallback")

    private fun selectCourse(course: Course) {
        selectedCourse = course
        hikeFlow = hikeFlow.copy(selectedCourseId = course.id, activeHikeId = null, progress = 0.0)
        mapState = TrailMapState.forCourse(course, appLanguage)
    }

    private fun toggleHike(status: TextView) {
        if (tracking) {
            tracking = false
            stopLocationUpdates()
            lastMessage = copy.format("status.hike.paused", hikeFlow.progress.percent())
            status.text = lastMessage
            render()
            return
        }
        tracking = true
        lastMessage = copy.format("status.hike.gps", selectedCourse.name)
        status.text = lastMessage
        startLocationUpdates()
        runApi(status, copy.text("status.hike.checkin.loading")) {
            val token = ensureDeviceToken()
            messageFor(repository.startHike(token, selectedCourse.id)) {
                hikeFlow = hikeFlow.started(it.hikeId)
                store.activeHikeId = it.hikeId
                copy.format("status.hike.checkin.done", selectedCourse.name, it.hikeId.take(8))
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
            lastMessage = copy.format("status.hike.local.end", mapState.walkedKm.formatKm())
            status.text = lastMessage
            render()
            return
        }
        runApi(status, copy.text("status.hike.save.loading")) {
            messageFor(repository.endHike(token, hikeId)) {
                copy.format("status.hike.saved", mapState.walkedKm.formatKm())
            }
        }
    }

    private fun pairWatch(status: TextView) {
        runApi(status, copy.text("status.watch.loading")) {
            val token = ensureDeviceToken()
            val result = repository.startWatchPairing(token, hikeFlow.activeHikeId)
            messageFor(result) {
                hikeFlow = hikeFlow.paired(it.code)
                copy.format("status.watch.code", it.code, it.expiresIn / 60)
            }
        }
    }

    private fun sendSos(status: TextView) {
        runApi(status, copy.text("status.sos.loading")) {
            val token = ensureDeviceToken()
            messageFor(repository.sendSos(token, hikeFlow.activeHikeId)) {
                copy.format("status.sos.done", it.status, it.gridNo, it.station, it.etaMin)
            }
        }
    }

    private fun sendChat(status: TextView, rawMessage: String) {
        val message = rawMessage.ifBlank { copy.text("ai.input.default") }
        runApi(status, copy.text("status.chat.loading")) {
            messageFor(
                repository.sendChat(
                    message = message,
                    lang = appLanguage.chatLang,
                    courseId = selectedCourse.id,
                    progress = hikeFlow.progress,
                ),
            ) {
                it.reply
            }
        }
    }

    private fun loadSummary(status: TextView) {
        runApi(status, copy.text("status.summary.loading")) {
            val token = store.accountToken.ifBlank { ensureDeviceToken() }
            val summaryText = messageFor(repository.hikeSummary(token)) {
                val badges = it.badges.take(5).joinToString(" / ") { badge ->
                    val state = if (badge.earned) copy.text("summary.badge.earned") else copy.format("summary.badge.progress", badge.progress, badge.goal)
                    "${badge.icon}${badge.label} $state"
                }.ifBlank { copy.text("summary.badges.empty") }
                copy.format("summary.text", it.totalHikes, it.totalKm, it.level, it.distinctCourses, it.regions, badges)
            }
            val logText = messageFor(repository.hikeLog(token)) { logs ->
                if (logs.isEmpty()) copy.text("summary.logs.empty") else logs.take(3).joinToString("\n") { "${it.date} ${it.course} ${it.km}km" }
            }
            "$summaryText\n$logText"
        }
    }

    private fun registerAccount(status: TextView, email: String, password: String) {
        runApi(status, copy.text("status.account.creating")) {
            val deviceToken = store.deviceToken.ifBlank { null }
            messageFor(repository.registerAccount(email.trim(), password, deviceToken)) {
                store.accountToken = it.accessToken
                store.accountEmail = it.email
                store.deviceToken = it.deviceToken
                copy.format("status.account.created", it.email)
            }
        }
    }

    private fun loginAccount(status: TextView, email: String, password: String) {
        runApi(status, copy.text("status.login.loading")) {
            val deviceToken = store.deviceToken.ifBlank { null }
            messageFor(repository.loginAccount(email.trim(), password, deviceToken)) {
                store.accountToken = it.accessToken
                store.accountEmail = it.email
                store.deviceToken = it.deviceToken
                copy.format("status.login.done", it.email)
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
            lastMessage = copy.text("status.location.permission")
        } catch (ex: IllegalArgumentException) {
            lastMessage = copy.text("status.location.provider")
        }
    }

    private fun stopLocationUpdates() {
        val listener = locationListener ?: return
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: SecurityException) {
            lastMessage = copy.text("status.location.changed")
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
                copy.text("status.request.failed")
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
        copy.format(
            "status.hike",
            hikeFlow.progress.percent(),
            mapState.walkedKm.formatKm(),
            if (tracking) copy.text("status.tracking") else copy.text("status.waiting"),
            hikeFlow.watchPairCode ?: copy.text("my.disconnected"),
        )

    private fun Double.percent(): String = "${(this.coerceIn(0.0, 1.0) * 100).toInt()}%"

    private fun Double.formatKm(): String = "%.2f".format(this)

    companion object {
        private const val REQ_LOCATION = 7001
    }
}
