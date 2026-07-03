package kr.forestmate.core.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DtoParsingTest {
    @Test
    fun parsesHikeIndexResponse() {
        val json = JSONObject(
            """
            {
              "score": 82,
              "label": "good",
              "fire": {"level": "low"},
              "conditions": {"name": "Bukhansan", "weather": {"temp": 18, "label": "clear", "wind": 3, "rain_prob": 10}},
              "place": "Seoul"
            }
            """.trimIndent(),
        )

        val index = JsonParsers.hikeIndex(json)

        assertEquals(82, index.score)
        assertEquals("good", index.label)
        assertEquals("Bukhansan", index.regionName)
        assertEquals("Seoul", index.place)
        assertEquals(18.0, index.temperatureC, 0.01)
    }

    @Test
    fun parsesCourseListResponse() {
        val json = JSONObject(
            """
            {"items":[{"id":"bukhansan","name":"Bukhansan loop","km":4.2,"minutes":95,"route":"trail","hazards":[{"type":"rock","grade":"caution","at":0.5}]}]}
            """.trimIndent(),
        )

        val courses = JsonParsers.courses(json)

        assertEquals(1, courses.size)
        assertEquals("bukhansan", courses[0].id)
        assertEquals(1, courses[0].hazards.size)
    }

    @Test
    fun parsesNativeCourseDetailFieldsForMapsAndRescue() {
        val json = JSONObject(
            """
            {"items":[{
              "id":"bukhansan",
              "name":"북한산 백운대 코스",
              "km":4.2,
              "minutes":190,
              "route":"백운대탐방지원센터 → 백운대 정상",
              "level":"중",
              "level_n":2,
              "crowd":"보통",
              "view":4,
              "peak":"백운대 836m",
              "grid_no":"다사 5683 2741",
              "gps":"37.6584,126.9778",
              "rescue_point":"백운산장 헬기장 620m",
              "fire_station":"서울 종로소방서 산악구조대",
              "elev":[120,180,260,390,480,542,650,770,836],
              "hazards":[{"type":"낙석주의","grade":"산사태 1등급","at":0.62,"note":"우회로 권장"}]
            }]}
            """.trimIndent(),
        )

        val course = JsonParsers.courses(json).single()

        assertEquals("중", course.level)
        assertEquals(2, course.levelN)
        assertEquals("백운대 836m", course.peak)
        assertEquals("다사 5683 2741", course.gridNo)
        assertEquals("37.6584,126.9778", course.gps)
        assertEquals("백운산장 헬기장 620m", course.rescuePoint)
        assertEquals("서울 종로소방서 산악구조대", course.fireStation)
        assertEquals(9, course.elevation.size)
        assertEquals("우회로 권장", course.hazards.single().note)
    }
}
