package kr.forestmate.core.model

import org.json.JSONArray
import org.json.JSONObject

object JsonParsers {
    fun hikeIndex(json: JSONObject): HikeIndex {
        val conditions = json.optJSONObject("conditions") ?: JSONObject()
        val weather = conditions.optJSONObject("weather") ?: JSONObject()
        val fire = json.optJSONObject("fire") ?: JSONObject()
        return HikeIndex(
            score = json.optInt("score", 0),
            label = json.optString("label", ""),
            regionName = conditions.optString("name", ""),
            place = json.optString("place", ""),
            temperatureC = weather.optDouble("temp", 0.0),
            weatherLabel = weather.optString("label", ""),
            windMps = weather.optDouble("wind", 0.0),
            rainProbability = weather.optInt("rain_prob", 0),
            fireLevel = fire.optString("level", ""),
        )
    }

    fun courses(json: JSONObject): List<Course> {
        val array = json.optJSONArray("items") ?: JSONArray()
        return courseArray(array)
    }

    fun courseArray(array: JSONArray): List<Course> {
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Course(
                id = item.optString("id", ""),
                name = item.optString("name", ""),
                km = item.optDouble("km", 0.0),
                minutes = item.optInt("minutes", 0),
                route = item.optString("route", ""),
                hazards = hazards(item.optJSONArray("hazards") ?: JSONArray()),
                level = item.optString("level", ""),
                levelN = item.optInt("level_n", item.optInt("levelN", 0)),
                crowd = item.optString("crowd", ""),
                view = item.optInt("view", 0),
                peak = item.optString("peak", ""),
                gridNo = item.optString("grid_no", item.optString("gridNo", "")),
                gps = item.optString("gps", ""),
                rescuePoint = item.optString("rescue_point", item.optString("rescuePoint", "")),
                fireStation = item.optString("fire_station", item.optString("fireStation", "")),
                elevation = intList(item.optJSONArray("elev") ?: item.optJSONArray("elevation") ?: JSONArray()),
            )
        }
    }

    fun device(json: JSONObject): DeviceRegistration =
        DeviceRegistration(
            deviceId = json.optString("device_id", ""),
            token = json.optString("token", ""),
            name = json.optString("name", ""),
        )

    fun authSession(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: JSONObject()
        return AuthSession(
            accessToken = json.optString("access_token", ""),
            deviceToken = json.optString("device_token", ""),
            userId = user.optString("id", ""),
            email = user.optString("email", ""),
            expiresIn = json.optInt("expires_in", 0),
        )
    }

    fun hikeStart(json: JSONObject): HikeStart =
        HikeStart(
            hikeId = json.optString("hike_id", ""),
            courseId = json.optString("course_id", ""),
        )

    fun trackUpdate(json: JSONObject): TrackUpdate {
        val distress = json.optJSONObject("distress") ?: JSONObject()
        return TrackUpdate(
            progress = json.optDouble("progress", 0.0),
            distressLevel = distress.optInt("level", 0),
        )
    }

    fun watchPairCode(json: JSONObject): WatchPairCode =
        WatchPairCode(
            code = json.optString("code", ""),
            expiresIn = json.optInt("expires_in", 0),
            hikeId = if (json.isNull("hike_id")) null else json.optString("hike_id", ""),
        )

    fun chatReply(json: JSONObject): ChatReply {
        val sources = json.optJSONArray("sources") ?: JSONArray()
        return ChatReply(
            reply = json.optString("reply", ""),
            intent = json.optString("intent", ""),
            engine = json.optString("engine", ""),
            sources = (0 until sources.length()).map { sources.optString(it, "") },
        )
    }

    fun sosReceipt(json: JSONObject): SosReceipt =
        SosReceipt(
            sosId = json.optString("sos_id", ""),
            status = json.optString("status", ""),
            gridNo = json.optString("grid_no", ""),
            gps = json.optString("gps", ""),
            station = json.optString("station", ""),
            etaMin = json.optInt("eta_min", 0),
        )

    fun hikeSummary(json: JSONObject): HikeSummary =
        HikeSummary(
            totalHikes = json.optInt("total_hikes", 0),
            totalKm = json.optDouble("total_km", 0.0),
            totalKcal = json.optInt("total_kcal", 0),
            level = json.optInt("level", 1),
            activeDays = json.optInt("active_days", 0),
            distinctCourses = json.optInt("distinct_courses", 0),
            regions = json.optInt("regions", 0),
            badges = badges(json.optJSONArray("badges") ?: JSONArray()),
        )

    fun hikeLog(json: JSONObject): List<HikeLogItem> {
        val items = json.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            HikeLogItem(
                course = item.optString("course", ""),
                km = item.optDouble("km", 0.0),
                kcal = item.optInt("kcal", 0),
                date = item.optString("date", ""),
            )
        }
    }

    private fun hazards(array: JSONArray): List<Hazard> =
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Hazard(
                type = item.optString("type", ""),
                grade = item.optString("grade", ""),
                at = item.optDouble("at", 0.0),
                note = item.optString("note", ""),
            )
        }

    private fun intList(array: JSONArray): List<Int> =
        (0 until array.length()).map { i -> array.optInt(i, 0) }

    private fun badges(array: JSONArray): List<Badge> =
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Badge(
                id = item.optString("id", ""),
                icon = item.optString("icon", ""),
                label = item.optString("label", ""),
                earned = item.optBoolean("earned", false),
                progress = item.optDouble("progress", 0.0),
                goal = item.optDouble("goal", 0.0),
            )
        }
}
