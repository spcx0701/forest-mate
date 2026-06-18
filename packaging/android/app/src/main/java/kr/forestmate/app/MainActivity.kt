package kr.forestmate.app

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import kr.forestmate.app.state.NavigationState
import kr.forestmate.app.state.PhoneTab
import kr.forestmate.app.ui.NativeViews

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var navigation = NavigationState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        setContentView(root)
        render()
    }

    private fun render() {
        root.removeAllViews()
        val (title, body) = bodyFor(navigation.selected)
        root.addView(
            NativeViews.screen(this, title, body),
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
}
