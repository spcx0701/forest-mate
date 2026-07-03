package kr.forestmate.app.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SosHoldStateTest {
    @Test
    fun confirmsAfterRequiredHoldTime() {
        val state = SosHoldState(startedAtMs = 1000L, requiredMs = 1800L)

        assertFalse(state.update(nowMs = 2500L).confirmed)
        assertTrue(state.update(nowMs = 2800L).confirmed)
    }
}
