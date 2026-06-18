package kr.forestmate.app.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneStateTest {
    @Test
    fun selectingCourseResetsHikeProgress() {
        val state = PhoneState(selectedCourseId = "old", activeHikeId = "h1", progress = 0.8)

        val next = state.selectCourse("new")

        assertEquals("new", next.selectedCourseId)
        assertEquals(null, next.activeHikeId)
        assertEquals(0.0, next.progress, 0.01)
    }

    @Test
    fun clearingDeviceTokenDoesNotClearWatchState() {
        val state = PhoneState(deviceToken = "bad", watchPairCode = "123456")

        val next = state.clearDeviceToken()

        assertEquals("", next.deviceToken)
        assertEquals("123456", next.watchPairCode)
        assertFalse(next.hasDeviceToken)
    }
}
