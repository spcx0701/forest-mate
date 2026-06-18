package kr.forestmate.app.state

import kr.forestmate.core.model.Course
import kr.forestmate.core.model.Hazard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailMapStateTest {
    private val course = Course(
        id = "bukhansan",
        name = "북한산 백운대 코스",
        km = 4.2,
        minutes = 190,
        route = "백운대탐방지원센터 → 백운대 정상",
        hazards = listOf(
            Hazard(type = "낙석주의", grade = "산사태 1등급", at = 0.62, note = "우회로 권장"),
        ),
        level = "중",
        levelN = 2,
        crowd = "보통",
        view = 4,
        peak = "백운대 836m",
        gridNo = "다사 5683 2741",
        gps = "37.6584,126.9778",
        rescuePoint = "백운산장 헬기장 620m",
        fireStation = "서울 종로소방서 산악구조대",
        elevation = listOf(120, 180, 260, 390, 480, 542, 650, 770, 836),
    )

    @Test
    fun courseMapContainsRouteMarkersAndHazards() {
        val state = TrailMapState.forCourse(course)

        assertEquals(LatLon(37.6584, 126.9778), state.start)
        assertTrue(state.routePoints.size >= 4)
        assertTrue(state.markers.any { it.kind == TrailMarkerKind.START && it.title.contains("들머리") })
        assertTrue(state.markers.any { it.kind == TrailMarkerKind.SUMMIT && it.title.contains("백운대") })
        assertTrue(state.markers.any { it.kind == TrailMarkerKind.HAZARD && it.title.contains("낙석주의") })
        assertTrue(state.markers.any { it.kind == TrailMarkerKind.RESCUE && it.title.contains("구조") })
    }

    @Test
    fun gpsTrackUpdatesProgressAndDistance() {
        val state = TrailMapState.forCourse(course)
            .recordLocation(LatLon(37.6584, 126.9778))
            .recordLocation(LatLon(37.6640, 126.9840))

        assertTrue(state.walkedKm > 0.7)
        assertTrue(state.progress > 0.15)
        assertTrue(state.trackPoints.size == 2)
    }
}
