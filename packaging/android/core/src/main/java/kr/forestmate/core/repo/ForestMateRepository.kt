package kr.forestmate.core.repo

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.ApiResult
import kr.forestmate.core.api.HttpResponse
import kr.forestmate.core.api.HttpTransport
import kr.forestmate.core.model.AuthSession
import kr.forestmate.core.model.ChatReply
import kr.forestmate.core.model.Course
import kr.forestmate.core.model.DeviceRegistration
import kr.forestmate.core.model.HikeLogItem
import kr.forestmate.core.model.HikeIndex
import kr.forestmate.core.model.HikeStart
import kr.forestmate.core.model.HikeSummary
import kr.forestmate.core.model.JsonParsers
import kr.forestmate.core.model.SosReceipt
import kr.forestmate.core.model.TrackUpdate
import kr.forestmate.core.model.WatchPairCode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class ForestMateRepository(
    private val config: ApiConfig,
    private val transport: HttpTransport,
) {
    fun health(): ApiResult<JSONObject> =
        parse(transport.get(config.url("/healthz"))) { it }

    fun hikeIndex(): ApiResult<HikeIndex> =
        parse(transport.get(config.url("/index"))) { JsonParsers.hikeIndex(it) }

    fun courses(): ApiResult<List<Course>> =
        parseCourses(transport.get(config.url("/courses")))

    fun watchLatest(deviceToken: String, hikeId: String?): ApiResult<JSONObject> {
        val suffix = if (hikeId.isNullOrBlank()) "/watch/latest" else "/watch/latest?hike_id=$hikeId"
        return parse(transport.get(config.url(suffix), bearerToken = deviceToken)) { it }
    }

    fun registerDevice(name: String): ApiResult<DeviceRegistration> {
        val body = JSONObject()
            .put("name", name)
            .put("fit", "normal")
            .put("knee", false)
            .put("heart", false)
        return parse(transport.post(config.url("/devices"), body.toString())) { JsonParsers.device(it) }
    }

    fun registerAccount(email: String, password: String, deviceToken: String?): ApiResult<AuthSession> {
        val body = authBody(email, password, deviceToken)
        return parse(transport.post(config.url("/auth/register"), body.toString())) { JsonParsers.authSession(it) }
    }

    fun loginAccount(email: String, password: String, deviceToken: String?): ApiResult<AuthSession> {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
        if (!deviceToken.isNullOrBlank()) body.put("device_token", deviceToken)
        return parse(transport.post(config.url("/auth/login"), body.toString())) { JsonParsers.authSession(it) }
    }

    fun startHike(deviceToken: String, courseId: String): ApiResult<HikeStart> {
        val body = JSONObject().put("course_id", courseId)
        return parse(transport.post(config.url("/hikes"), body.toString(), bearerToken = deviceToken)) {
            JsonParsers.hikeStart(it)
        }
    }

    fun trackHike(
        deviceToken: String,
        hikeId: String,
        progress: Double,
        alt: Int? = null,
        hr: Int? = null,
        lat: Double? = null,
        lon: Double? = null,
    ): ApiResult<TrackUpdate> {
        val body = JSONObject().put("progress", progress)
        if (alt != null) body.put("alt", alt)
        if (hr != null) body.put("hr", hr)
        if (lat != null) body.put("lat", lat)
        if (lon != null) body.put("lon", lon)
        return parse(transport.post(config.url("/hikes/$hikeId/track"), body.toString(), bearerToken = deviceToken)) {
            JsonParsers.trackUpdate(it)
        }
    }

    fun endHike(deviceToken: String, hikeId: String): ApiResult<JSONObject> =
        parse(transport.post(config.url("/hikes/$hikeId/end"), "{}", bearerToken = deviceToken)) { it }

    fun startWatchPairing(deviceToken: String, hikeId: String?): ApiResult<WatchPairCode> {
        val body = JSONObject()
        if (!hikeId.isNullOrBlank()) body.put("hike_id", hikeId)
        return parse(transport.post(config.url("/watch/pair/start"), body.toString(), bearerToken = deviceToken)) {
            JsonParsers.watchPairCode(it)
        }
    }

    fun sendSos(deviceToken: String, hikeId: String?): ApiResult<SosReceipt> {
        val body = JSONObject().put("note", "native android")
        if (!hikeId.isNullOrBlank()) body.put("hike_id", hikeId)
        return parse(transport.post(config.url("/sos"), body.toString(), bearerToken = deviceToken)) {
            JsonParsers.sosReceipt(it)
        }
    }

    fun sendChat(message: String, lang: String, courseId: String?, progress: Double): ApiResult<ChatReply> {
        val body = JSONObject()
            .put("message", message)
            .put("lang", lang)
            .put("progress", progress)
        if (!courseId.isNullOrBlank()) body.put("course_id", courseId)
        return parse(transport.post(config.url("/chat"), body.toString())) { JsonParsers.chatReply(it) }
    }

    fun hikeSummary(deviceToken: String): ApiResult<HikeSummary> =
        parse(transport.get(config.url("/hikes/summary"), bearerToken = deviceToken)) { JsonParsers.hikeSummary(it) }

    fun hikeLog(deviceToken: String): ApiResult<List<HikeLogItem>> =
        parse(transport.get(config.url("/hikes"), bearerToken = deviceToken)) { JsonParsers.hikeLog(it) }

    private fun authBody(email: String, password: String, deviceToken: String?): JSONObject {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("name", "산친구")
            .put("fit", 2)
            .put("knee", false)
            .put("heart", false)
        if (!deviceToken.isNullOrBlank()) body.put("device_token", deviceToken)
        return body
    }

    private fun <T> parse(response: HttpResponse, mapper: (JSONObject) -> T): ApiResult<T> {
        if (response.statusCode !in 200..299) {
            return ApiResult.Failure(response.statusCode, response.body.ifBlank { "request failed" })
        }
        return try {
            ApiResult.Success(mapper(JSONObject(response.body.ifBlank { "{}" })))
        } catch (ex: JSONException) {
            ApiResult.Failure(0, "invalid response", ex)
        }
    }

    private fun parseCourses(response: HttpResponse): ApiResult<List<Course>> {
        if (response.statusCode !in 200..299) {
            return ApiResult.Failure(response.statusCode, response.body.ifBlank { "request failed" })
        }
        return try {
            when (val root = JSONTokener(response.body.ifBlank { "[]" }).nextValue()) {
                is JSONArray -> ApiResult.Success(JsonParsers.courseArray(root))
                is JSONObject -> ApiResult.Success(JsonParsers.courses(root))
                else -> ApiResult.Failure(0, "invalid response")
            }
        } catch (ex: JSONException) {
            ApiResult.Failure(0, "invalid response", ex)
        }
    }
}
