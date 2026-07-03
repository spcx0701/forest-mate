package kr.forestmate.core.model

data class HikeIndex(
    val score: Int,
    val label: String,
    val regionName: String,
    val place: String,
    val temperatureC: Double,
    val weatherLabel: String,
    val windMps: Double,
    val rainProbability: Int,
    val fireLevel: String,
)

data class Course(
    val id: String,
    val name: String,
    val km: Double,
    val minutes: Int,
    val route: String,
    val hazards: List<Hazard>,
    val level: String = "",
    val levelN: Int = 0,
    val crowd: String = "",
    val view: Int = 0,
    val peak: String = "",
    val gridNo: String = "",
    val gps: String = "",
    val rescuePoint: String = "",
    val fireStation: String = "",
    val elevation: List<Int> = emptyList(),
)

data class Hazard(
    val type: String,
    val grade: String,
    val at: Double,
    val note: String = "",
)

data class DeviceRegistration(val deviceId: String, val token: String, val name: String)
data class AuthSession(
    val accessToken: String,
    val deviceToken: String,
    val userId: String,
    val email: String,
    val expiresIn: Int,
)
data class HikeStart(val hikeId: String, val courseId: String)
data class TrackUpdate(val progress: Double, val distressLevel: Int)
data class WatchPairCode(val code: String, val expiresIn: Int, val hikeId: String?)
data class ChatReply(val reply: String, val intent: String, val engine: String, val sources: List<String>)
data class SosReceipt(
    val sosId: String,
    val status: String,
    val gridNo: String,
    val gps: String,
    val station: String,
    val etaMin: Int,
)
data class Badge(
    val id: String,
    val icon: String,
    val label: String,
    val earned: Boolean,
    val progress: Double,
    val goal: Double,
)
data class HikeSummary(
    val totalHikes: Int,
    val totalKm: Double,
    val totalKcal: Int,
    val level: Int,
    val activeDays: Int = 0,
    val distinctCourses: Int = 0,
    val regions: Int = 0,
    val badges: List<Badge> = emptyList(),
)
data class HikeLogItem(val course: String, val km: Double, val kcal: Int, val date: String)
