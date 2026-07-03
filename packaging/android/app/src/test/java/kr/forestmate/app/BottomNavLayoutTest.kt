package kr.forestmate.app

import kr.forestmate.app.ui.BottomNavLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BottomNavLayoutTest {
    @Test
    fun rootDoesNotAddNavigationBarHeightBelowTheTabBar() {
        assertEquals(0, BottomNavLayout.rootBottomPaddingPx())
    }

    @Test
    fun bottomNavContentKeepsTheFullSystemNavigationInset() {
        assertEquals(0, BottomNavLayout.tabContentSafeBottomInsetPx(rawInsetPx = -12))
        assertEquals(14, BottomNavLayout.tabContentSafeBottomInsetPx(rawInsetPx = 14))
        assertEquals(96, BottomNavLayout.tabContentSafeBottomInsetPx(rawInsetPx = 96))
    }

    @Test
    fun mainActivityDoesNotPushTheWholeTabBarAboveAndroidNavigation() {
        val source = mainActivitySource()

        assertTrue(source.contains("BottomNavLayout.rootBottomPaddingPx()"))
        assertTrue(source.contains("BottomNavLayout.tabContentSafeBottomInsetPx("))
        assertFalse(source.contains("setPadding(0, 0, 0, navigationBarHeight())"))
    }

    private fun mainActivitySource(): String {
        val candidates = listOf(
            File("app/src/main/java/kr/forestmate/app/MainActivity.kt"),
            File("src/main/java/kr/forestmate/app/MainActivity.kt"),
            File("packaging/android/app/src/main/java/kr/forestmate/app/MainActivity.kt"),
        )
        return candidates.first { it.exists() }.readText()
    }
}
