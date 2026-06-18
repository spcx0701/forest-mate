package kr.forestmate.app.state

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStateTest {
    @Test
    fun onlyKnownTabsCanBeSelected() {
        assertEquals(PhoneTab.HOME, NavigationState().select("missing").selected)
        assertEquals(PhoneTab.AI, NavigationState().select("ai").selected)
    }
}
