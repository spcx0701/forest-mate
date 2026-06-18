package kr.forestmate.app

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kr.forestmate.app.state.HikeFlowState
import kr.forestmate.app.state.NavigationState
import kr.forestmate.app.state.PhoneTab
import kr.forestmate.app.ui.NativeViews
import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.UrlConnectionTransport
import kr.forestmate.core.repo.ForestMateRepository

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var store: PhoneStore
    private lateinit var repository: ForestMateRepository
    private var navigation = NavigationState()
    private var hikeFlow = HikeFlowState(selectedCourseId = "bukhansan")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)
        repository = ForestMateRepository(ApiConfig(store.apiBase), UrlConnectionTransport())
        hikeFlow = hikeFlow.copy(activeHikeId = store.activeHikeId.takeIf { it.isNotBlank() })
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)
        render()
    }

    private fun render() {
        root.removeAllViews()
        val (title, body) = bodyFor(navigation.selected)
        val content = NativeViews.screen(this, title, body)
        val status = NativeViews.statusText(this, "")
        content.addView(status)
        addActions(content, status, navigation.selected)
        root.addView(
            content,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                PhoneTab.entries.forEach { tab ->
                    addView(
                        NativeViews.tabButton(this@MainActivity, tab) {
                            navigation = navigation.select(tab.id)
                            render()
                        },
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f,
                        ),
                    )
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun bodyFor(tab: PhoneTab): Pair<String, String> = when (tab) {
        PhoneTab.HOME -> "Today" to "Loading hiking index and recommendations..."
        PhoneTab.HIKE -> "Hike" to "Select a course and start tracking."
        PhoneTab.SOS -> "SOS" to "Hold to send an emergency request."
        PhoneTab.AI -> "AI Companion" to "Ask about weather, hazards, routes, or plants."
        PhoneTab.MY -> "My" to "Hike history and badges."
    }

    private fun addActions(content: LinearLayout, status: TextView, tab: PhoneTab) {
        when (tab) {
            PhoneTab.HOME -> {
                content.addView(NativeViews.actionButton(this, "Refresh") { loadHome(status) })
            }

            PhoneTab.HIKE -> {
                status.text = "Course: ${hikeFlow.selectedCourseId ?: "none"}\nHike: ${hikeFlow.activeHikeId ?: "not started"}"
                content.addView(NativeViews.actionButton(this, "Register device") { registerDevice(status) })
                content.addView(NativeViews.actionButton(this, "Start hike") { startHike(status) })
            }

            PhoneTab.SOS -> {
                content.addView(NativeViews.actionButton(this, "Send SOS") { sendSos(status) })
            }

            PhoneTab.AI -> {
                val input = EditText(this).apply {
                    hint = "Ask ForestMate"
                    setSingleLine(false)
                    setText("weather")
                }
                content.addView(input)
                content.addView(NativeViews.actionButton(this, "Ask") {
                    sendChat(status, input.text.toString())
                })
            }

            PhoneTab.MY -> {
                content.addView(NativeViews.actionButton(this, "Load summary") { loadSummary(status) })
            }
        }
    }

    private fun loadHome(status: TextView) {
        runApi(status, "Loading hiking index...") {
            val index = repository.hikeIndex()
            val courses = repository.courses()
            val indexText = messageFor(index) { "score ${it.score} (${it.label})" }
            val coursesText = messageFor(courses) { "${it.size} courses" }
            "Today: $indexText\nRoutes: $coursesText"
        }
    }

    private fun registerDevice(status: TextView) {
        runApi(status, "Registering device...") {
            messageFor(repository.registerDevice("phone")) {
                store.deviceToken = it.token
                "Registered ${it.name.ifBlank { it.deviceId }}"
            }
        }
    }

    private fun startHike(status: TextView) {
        val token = store.deviceToken
        if (token.isBlank()) {
            status.text = "Register a device first."
            return
        }
        val courseId = hikeFlow.selectedCourseId ?: "bukhansan"
        runApi(status, "Starting hike...") {
            messageFor(repository.startHike(token, courseId)) {
                hikeFlow = hikeFlow.started(it.hikeId)
                store.activeHikeId = it.hikeId
                "Started hike ${it.hikeId}"
            }
        }
    }

    private fun sendSos(status: TextView) {
        val token = store.deviceToken
        if (token.isBlank()) {
            status.text = "Register a device before SOS."
            return
        }
        runApi(status, "Sending SOS...") {
            messageFor(repository.sendSos(token, hikeFlow.activeHikeId)) {
                "SOS ${it.status}: ${it.station}, ETA ${it.etaMin} min"
            }
        }
    }

    private fun sendChat(status: TextView, rawMessage: String) {
        val message = rawMessage.ifBlank { "weather" }
        runApi(status, "Asking ForestMate...") {
            messageFor(
                repository.sendChat(
                    message = message,
                    lang = "ko",
                    courseId = hikeFlow.selectedCourseId,
                    progress = hikeFlow.progress,
                ),
            ) {
                it.reply
            }
        }
    }

    private fun loadSummary(status: TextView) {
        val token = store.deviceToken
        if (token.isBlank()) {
            status.text = "Register a device to load history."
            return
        }
        runApi(status, "Loading summary...") {
            messageFor(repository.hikeSummary(token)) {
                "Total hikes ${it.totalHikes}, ${it.totalKm} km, level ${it.level}"
            }
        }
    }

    private fun runApi(status: TextView, loading: String, block: () -> String) {
        status.text = loading
        Thread {
            val message = try {
                block()
            } catch (ex: Exception) {
                "Network error: ${ex.message.orEmpty()}"
            }
            runOnUiThread { status.text = message }
        }.start()
    }

    private fun <T> messageFor(result: ApiResult<T>, success: (T) -> String): String =
        when (result) {
            is ApiResult.Success -> success(result.value)
            is ApiResult.Failure -> result.displayMessage
        }
}
