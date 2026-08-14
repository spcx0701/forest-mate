package kr.forestmate.app.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kr.forestmate.app.DesignCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeViewsTest {
    @Test
    fun actionButtonsRunTheProvidedClickHandler() {
        val context = RuntimeEnvironment.getApplication()
        var clicks = 0

        val buttons = listOf(
            NativeViews.primaryButton(context, "primary") { clicks += 1 },
            NativeViews.ghostButton(context, "ghost") { clicks += 1 },
            NativeViews.warnButton(context, "warn") { clicks += 1 },
            NativeViews.dangerButton(context, "danger") { clicks += 1 },
        )

        buttons.forEach { it.performClick() }

        assertEquals(buttons.size, clicks)
    }

    @Test
    fun screenUsesTheProvidedEnglishCopy() {
        val context = RuntimeEnvironment.getApplication()

        val screen = NativeViews.screen(context, "Trail ready", "Check conditions", DesignCopy.english)
        val text = collectText(screen).joinToString("\n")

        assertTrue(text.contains("ForestMate"))
        assertTrue(text.contains("Eunpyeong, Seoul"))
        assertFalse(Regex("[가-힣]").containsMatchIn(text))
    }

    private fun collectText(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { collectText(view.getChildAt(it)) }
            else -> emptyList()
        }
}
