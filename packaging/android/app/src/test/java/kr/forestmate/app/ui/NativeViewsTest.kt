package kr.forestmate.app.ui

import org.junit.Assert.assertEquals
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
}
