package kr.forestmate.app.state

import org.junit.Assert.assertEquals
import org.junit.Test

class HikeFlowStateTest {
    @Test
    fun startingHikeStoresActiveHike() {
        val state = HikeFlowState(selectedCourseId = "c1")

        val next = state.started(hikeId = "h1")

        assertEquals("h1", next.activeHikeId)
        assertEquals(0.0, next.progress, 0.01)
    }

    @Test
    fun progressIsClamped() {
        assertEquals(1.0, HikeFlowState().updateProgress(2.0).progress, 0.01)
        assertEquals(0.0, HikeFlowState().updateProgress(-1.0).progress, 0.01)
    }
}
