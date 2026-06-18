package kr.forestmate.app.state

import kr.forestmate.core.model.Course
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

enum class TrailMarkerKind {
    START,
    SUMMIT,
    HAZARD,
    RESCUE,
    POSITION,
}

data class TrailMarker(
    val kind: TrailMarkerKind,
    val title: String,
    val subtitle: String,
    val point: LatLon,
)

data class TrailMapState(
    val courseId: String,
    val start: LatLon,
    val routePoints: List<LatLon>,
    val markers: List<TrailMarker>,
    val trackPoints: List<LatLon> = emptyList(),
    val walkedKm: Double = 0.0,
    val progress: Double = 0.0,
) {
    fun recordLocation(point: LatLon): TrailMapState {
        val newTrack = trackPoints + point
        val stepKm = trackPoints.lastOrNull()?.let { haversineKm(it, point) } ?: 0.0
        val nextWalked = walkedKm + if (stepKm in 0.0..5.0) stepKm else 0.0
        val routeKm = routeDistanceKm().takeIf { it > 0.0 } ?: 1.0
        val nextProgress = (nextWalked / routeKm).coerceIn(0.0, 1.0)
        val positionMarker = TrailMarker(
            kind = TrailMarkerKind.POSITION,
            title = "현재 위치",
            subtitle = "GPS 트랙 ${"%.2f".format(nextWalked)}km",
            point = point,
        )
        return copy(
            markers = markers.filterNot { it.kind == TrailMarkerKind.POSITION } + positionMarker,
            trackPoints = newTrack,
            walkedKm = nextWalked,
            progress = nextProgress,
        )
    }

    fun routeDistanceKm(): Double =
        routePoints.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }

    companion object {
        fun forCourse(course: Course): TrailMapState {
            val start = parseGps(course.gps) ?: LatLon(37.6584, 126.9778)
            val route = routeFor(course, start)
            val markers = mutableListOf<TrailMarker>()
            val startLabel = course.route.substringBefore("→").trim().ifBlank { "들머리" }
            markers += TrailMarker(
                kind = TrailMarkerKind.START,
                title = "$startLabel · 들머리",
                subtitle = course.route,
                point = start,
            )
            markers += TrailMarker(
                kind = TrailMarkerKind.SUMMIT,
                title = course.peak.ifBlank { course.name },
                subtitle = "${course.km}km · ${course.minutes}분",
                point = route.last(),
            )
            for (hazard in course.hazards) {
                markers += TrailMarker(
                    kind = TrailMarkerKind.HAZARD,
                    title = hazard.type,
                    subtitle = listOf(hazard.grade, hazard.note).filter { it.isNotBlank() }.joinToString(" · "),
                    point = interpolate(route, hazard.at),
                )
            }
            if (course.rescuePoint.isNotBlank()) {
                markers += TrailMarker(
                    kind = TrailMarkerKind.RESCUE,
                    title = "구조 거점",
                    subtitle = course.rescuePoint,
                    point = interpolate(route, 0.72),
                )
            }
            return TrailMapState(
                courseId = course.id,
                start = start,
                routePoints = route,
                markers = markers,
            )
        }

        private fun parseGps(raw: String): LatLon? {
            val value = raw.trim()
            val comma = Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""").find(value)
            if (comma != null) return LatLon(comma.groupValues[1].toDouble(), comma.groupValues[2].toDouble())
            val labeled = Regex("""(-?\d+(?:\.\d+)?)[^\d.-]*N[^\d.-]+(-?\d+(?:\.\d+)?)[^\d.-]*E?""", RegexOption.IGNORE_CASE).find(value)
            if (labeled != null) return LatLon(labeled.groupValues[1].toDouble(), labeled.groupValues[2].toDouble())
            return null
        }

        private fun routeFor(course: Course, start: LatLon): List<LatLon> {
            val offsets = routeOffsets(course.id, course.km)
            return offsets.map { (northKm, eastKm) -> start.offset(northKm, eastKm) }
        }

        private fun routeOffsets(id: String, km: Double): List<Pair<Double, Double>> =
            when (id) {
                "bukhansan" -> listOf(0.0 to 0.0, 0.55 to 0.18, 1.18 to 0.52, 2.05 to 0.9, max(2.75, km * 0.68) to 1.18)
                "inwangsan" -> listOf(0.0 to 0.0, 0.4 to -0.15, 0.95 to -0.05, 1.55 to 0.22, max(2.05, km * 0.78) to 0.38)
                "achasan" -> listOf(0.0 to 0.0, 0.5 to 0.25, 1.05 to 0.7, 1.75 to 1.0, max(2.45, km * 0.72) to 1.22)
                "dobong" -> listOf(0.0 to 0.0, 0.82 to 0.12, 1.75 to 0.52, 2.9 to 0.85, max(4.4, km * 0.7) to 1.03)
                else -> listOf(0.0 to 0.0, (km * 0.25) to 0.1, (km * 0.55) to 0.32, (km * 0.85) to 0.48)
            }

        private fun LatLon.offset(northKm: Double, eastKm: Double): LatLon {
            val latDelta = northKm / 111.0
            val lonDelta = eastKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.25))
            return LatLon(lat + latDelta, lon + lonDelta)
        }

        private fun interpolate(route: List<LatLon>, progress: Double): LatLon {
            if (route.isEmpty()) return LatLon(37.6584, 126.9778)
            if (route.size == 1) return route.first()
            val target = progress.coerceIn(0.0, 1.0) * (route.size - 1)
            val i = min(route.size - 2, target.toInt())
            val r = target - i
            val a = route[i]
            val b = route[i + 1]
            return LatLon(
                lat = a.lat + (b.lat - a.lat) * r,
                lon = a.lon + (b.lon - a.lon) * r,
            )
        }

        private fun haversineKm(a: LatLon, b: LatLon): Double {
            val r = 6371.0
            val p1 = Math.toRadians(a.lat)
            val p2 = Math.toRadians(b.lat)
            val dp = Math.toRadians(b.lat - a.lat)
            val dl = Math.toRadians(b.lon - a.lon)
            val x = sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * sin(dl / 2).let { it * it }
            return (2 * r * asin(sqrt(x)) * 100.0).roundToInt() / 100.0
        }
    }
}
