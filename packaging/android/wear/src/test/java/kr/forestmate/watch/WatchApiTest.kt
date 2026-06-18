package kr.forestmate.watch

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.FakeTransport
import kr.forestmate.core.api.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchApiTest {
    @Test
    fun claimsPairCode() {
        val transport = FakeTransport(
            postResponses = mapOf(
                "https://example.test/api/watch/pair/claim" to HttpResponse(
                    200,
                    """{"watch_token":"w1","hike_id":"h1","course_id":"c1","course_name":"Course","course_km":4.2,"course_elev":836,"route":"Loop"}""",
                ),
            ),
        )
        val api = WatchApi(ApiConfig("https://example.test/api"), transport)

        val result = api.claim("123456")

        assertEquals("w1", result.watchToken)
        assertEquals("Course", result.courseName)
    }
}
