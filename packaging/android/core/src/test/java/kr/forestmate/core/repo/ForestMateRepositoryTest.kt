package kr.forestmate.core.repo

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.FakeTransport
import kr.forestmate.core.api.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestMateRepositoryTest {
    @Test
    fun loadsIndexAndCourses() {
        val transport = FakeTransport(
            getResponses = mapOf(
                "https://example.test/api/index" to HttpResponse(
                    200,
                    """{"score":75,"label":"ok","conditions":{"name":"Region","weather":{"temp":12,"label":"wind","wind":4,"rain_prob":30}}}""",
                ),
                "https://example.test/api/courses" to HttpResponse(
                    200,
                    """{"items":[{"id":"c1","name":"Course","km":3.0,"minutes":70,"route":"Loop","hazards":[]}]}""",
                ),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val index = repo.hikeIndex()
        val courses = repo.courses()

        assertEquals(75, successValue(index).score)
        assertEquals("c1", successValue(courses)[0].id)
    }

    @Test
    fun sendsBearerTokenForWatchLatest() {
        val transport = FakeTransport(
            getResponses = mapOf(
                "https://example.test/api/watch/latest?hike_id=h1" to HttpResponse(200, """{"connected":true,"hr":88}"""),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val result = repo.watchLatest(deviceToken = "device-token", hikeId = "h1")

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer device-token", transport.lastAuthorizationHeader)
    }

    @Test
    fun registersDeviceAndStartsHike() {
        val transport = FakeTransport(
            postResponses = mapOf(
                "https://example.test/api/devices" to HttpResponse(201, """{"device_id":"d1","token":"t1","name":"phone"}"""),
                "https://example.test/api/hikes" to HttpResponse(201, """{"hike_id":"h1","course_id":"c1"}"""),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val device = repo.registerDevice("phone")
        val hike = repo.startHike("t1", "c1")

        assertEquals("t1", successValue(device).token)
        assertEquals("h1", successValue(hike).hikeId)
    }

    @Test
    fun sendsChatAndSosRequests() {
        val transport = FakeTransport(
            postResponses = mapOf(
                "https://example.test/api/chat" to HttpResponse(
                    200,
                    """{"reply":"safe","intent":"weather","engine":"rules","sources":["weather"]}""",
                ),
                "https://example.test/api/sos" to HttpResponse(
                    201,
                    """{"sos_id":"s1","status":"dispatched","grid_no":"ABC","gps":"37.0N 127.0E","station":"119","eta_min":12}""",
                ),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val chat = repo.sendChat(message = "weather", lang = "ko", courseId = "c1", progress = 0.2)
        val sos = repo.sendSos(deviceToken = "t1", hikeId = "h1")

        assertEquals("safe", successValue(chat).reply)
        assertEquals("s1", successValue(sos).sosId)
    }

    private fun <T> successValue(result: ApiResult<T>): T =
        (result as ApiResult.Success<T>).value
}
