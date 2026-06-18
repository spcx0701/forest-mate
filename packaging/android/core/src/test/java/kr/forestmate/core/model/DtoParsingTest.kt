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
}
