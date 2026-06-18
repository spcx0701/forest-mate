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
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Course(
                id = item.optString("id", ""),
                name = item.optString("name", ""),
                km = item.optDouble("km", 0.0),
                minutes = item.optInt("minutes", 0),
                route = item.optString("route", ""),
                hazards = hazards(item.optJSONArray("hazards") ?: JSONArray()),
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
            )
        }
}
