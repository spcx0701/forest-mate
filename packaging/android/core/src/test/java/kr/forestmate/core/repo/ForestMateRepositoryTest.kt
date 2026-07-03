package kr.forestmate.core.repo

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.FakeTransport
import kr.forestmate.core.api.HttpResponse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestMateRepositoryTest {
    @Test
    fun checksBackendHealth() {
        val transport = FakeTransport(
            getResponses = mapOf(
                "https://example.test/api/healthz" to HttpResponse(
                    200,
                    """{"status":"ok","service":"forestmate-api","live_data":true}""",
                ),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val health = successValue(repo.health())

        assertEquals("ok", health.getString("status"))
        assertTrue(health.getBoolean("live_data"))
    }

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
        val deviceAuthValue = fixtureAuthValue("device")
        val transport = FakeTransport(
            getResponses = mapOf(
                "https://example.test/api/watch/latest?hike_id=h1" to HttpResponse(200, """{"connected":true,"hr":88}"""),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val result = repo.watchLatest(deviceToken = deviceAuthValue, hikeId = "h1")

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer $deviceAuthValue", transport.lastAuthorizationHeader)
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

    @Test
    fun sendsGpsCoordinatesWhenTrackingHike() {
        val transport = FakeTransport(
            postResponses = mapOf(
                "https://example.test/api/hikes/h1/track" to HttpResponse(
                    200,
                    """{"progress":0.2,"distress":{"level":0}}""",
                ),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val result = repo.trackHike(deviceToken = "t1", hikeId = "h1", progress = 0.2, lat = 37.1, lon = 127.2)

        assertTrue(result is ApiResult.Success)
        val body = JSONObject(transport.lastPostBody ?: "{}")
        assertEquals(37.1, body.getDouble("lat"), 0.01)
        assertEquals(127.2, body.getDouble("lon"), 0.01)
        assertEquals("Bearer t1", transport.lastAuthorizationHeader)
    }

    @Test
    fun registersAndLogsInEmailAccountWithLinkedDeviceToken() {
        val accountAuthValue = fixtureAuthValue("account")
        val deviceAuthValue = fixtureAuthValue("device")
        val loginPhrase = fixturePhrase()
        val authJson = JSONObject()
            .put("access_token", accountAuthValue)
            .put("expires_in", 3600)
            .put("device_token", deviceAuthValue)
            .put(
                "user",
                JSONObject()
                    .put("id", "u1")
                    .put("email", "hiker@example.com")
                    .put("providers", listOf("local"))
                    .put("profile", JSONObject().put("name", "산친구").put("fit", 2).put("knee", false).put("heart", false)),
            )
            .toString()
        val transport = FakeTransport(
            postResponses = mapOf(
                "https://example.test/api/auth/register" to HttpResponse(201, authJson),
                "https://example.test/api/auth/login" to HttpResponse(200, authJson),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val registered = repo.registerAccount("hiker@example.com", loginPhrase, deviceAuthValue)
        val loggedIn = repo.loginAccount("hiker@example.com", loginPhrase, deviceAuthValue)

        assertEquals(accountAuthValue, successValue(registered).accessToken)
        assertEquals(deviceAuthValue, successValue(loggedIn).deviceToken)
        assertEquals("hiker@example.com", successValue(loggedIn).email)
    }

    @Test
    fun surfacesNetworkFailureAsRetryableFailure() {
        val transport = FakeTransport(
            getResponses = mapOf(
                "https://example.test/api/healthz" to HttpResponse(
                    0,
                    "네트워크 연결 실패: Read timed out",
                ),
            ),
        )
        val repo = ForestMateRepository(ApiConfig("https://example.test/api"), transport)

        val result = repo.health()
        val failure = result as ApiResult.Failure

        assertTrue(failure.isRetryable)
        assertEquals("네트워크 연결 실패: Read timed out", failure.displayMessage)
    }

    private fun fixtureAuthValue(scope: String): String =
        listOf("fixture", scope, "value").joinToString("-")

    private fun fixturePhrase(): String =
        listOf("fixture", "login", "phrase").joinToString("-")

    private fun <T> successValue(result: ApiResult<T>): T =
        (result as ApiResult.Success<T>).value
}
