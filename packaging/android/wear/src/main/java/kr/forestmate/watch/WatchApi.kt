package kr.forestmate.watch

import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.HttpResponse
import kr.forestmate.core.api.HttpTransport
import kr.forestmate.core.api.UrlConnectionTransport
import org.json.JSONObject

class WatchApi(
    private val config: ApiConfig,
    private val transport: HttpTransport,
) {
    class ClaimResult(
        @JvmField val watchToken: String,
        @JvmField val hikeId: String,
        @JvmField val courseId: String,
        @JvmField val courseName: String,
        @JvmField val courseKm: Double?,
        @JvmField val courseElev: Int?,
        @JvmField val route: String,
    )

    class UploadResult(
        @JvmField val progress: Double,
        @JvmField val distressLevel: Int,
    )

    fun claim(code: String): ClaimResult {
        val body = JSONObject().put("code", code).toString()
        val json = postJson("/watch/pair/claim", body, bearerToken = null)
        return ClaimResult(
            watchToken = json.getString("watch_token"),
            hikeId = nullableString(json, "hike_id"),
            courseId = nullableString(json, "course_id"),
            courseName = nullableString(json, "course_name"),
            courseKm = nullableDouble(json, "course_km"),
            courseElev = nullableInt(json, "course_elev"),
            route = nullableString(json, "route"),
        )
    }

    fun upload(
        token: String,
        hr: Int?,
        lat: Double?,
        lon: Double?,
        alt: Int?,
        acc: Int?,
        battery: Int?,
    ): UploadResult =
        upload(token, WatchUploadRequest(hr, lat, lon, alt, acc, battery))

    fun upload(token: String, request: WatchUploadRequest): UploadResult {
        val json = postJson(
            "/watch/track",
            request.toJson().toString(),
            token,
        )
        val distress = json.optJSONObject("distress") ?: JSONObject()
        return UploadResult(
            progress = json.optDouble("progress", 0.0),
            distressLevel = distress.optInt("level", 0),
        )
    }

    private fun postJson(path: String, body: String, bearerToken: String?): JSONObject {
        val response = transport.post(config.url(path), body, bearerToken)
        ensureSuccess(response)
        return JSONObject(response.body.ifBlank { "{}" })
    }

    private fun ensureSuccess(response: HttpResponse) {
        if (response.statusCode !in 200..299) {
            error("HTTP ${response.statusCode} ${response.body}")
        }
    }

    companion object {
        @JvmStatic
        fun claim(apiBase: String?, code: String): ClaimResult =
            WatchApi(ApiConfig(apiBase.orEmpty()), UrlConnectionTransport()).claim(code)

        @JvmStatic
        fun upload(
            apiBase: String?,
            token: String,
            hr: Int?,
            lat: Double?,
            lon: Double?,
            alt: Int?,
            acc: Int?,
            battery: Int?,
        ): UploadResult =
            WatchApi(ApiConfig(apiBase.orEmpty()), UrlConnectionTransport())
                .upload(token, hr, lat, lon, alt, acc, battery)

        private fun nullableString(json: JSONObject, key: String): String =
            if (json.has(key) && !json.isNull(key)) json.optString(key, "") else ""

        private fun nullableDouble(json: JSONObject, key: String): Double? =
            if (json.has(key) && !json.isNull(key)) json.optDouble(key) else null

        private fun nullableInt(json: JSONObject, key: String): Int? =
            if (json.has(key) && !json.isNull(key)) json.optInt(key) else null
    }
}

data class WatchUploadRequest(
    val hr: Int?,
    val lat: Double?,
    val lon: Double?,
    val alt: Int?,
    val acc: Int?,
    val battery: Int?,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("alt", alt ?: 0)
            if (hr != null) put("hr", hr)
            if (lat != null) put("lat", lat)
            if (lon != null) put("lon", lon)
            if (acc != null) put("acc", acc)
            if (battery != null) put("battery", battery)
        }
}
