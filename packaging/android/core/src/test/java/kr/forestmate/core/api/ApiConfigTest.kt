package kr.forestmate.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiConfigTest {
    @Test
    fun normalizesBaseUrlAndBuildsPaths() {
        val config = ApiConfig("https://forestmate.onrender.com/api/v1/")

        assertEquals("https://forestmate.onrender.com/api/v1", config.baseUrl)
        assertEquals("https://forestmate.onrender.com/api/v1/index", config.url("/index"))
        assertEquals(
            "https://forestmate.onrender.com/api/v1/watch/latest?hike_id=abc",
            config.url("watch/latest?hike_id=abc"),
        )
    }

    @Test
    fun blankBaseFallsBackToProductionApi() {
        val config = ApiConfig(" ")

        assertEquals("https://forestmate.onrender.com/api/v1", config.baseUrl)
    }
}
