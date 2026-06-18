package kr.forestmate.app.state

data class PhoneState(
    val deviceToken: String = "",
    val selectedCourseId: String? = null,
    val activeHikeId: String? = null,
    val progress: Double = 0.0,
    val watchPairCode: String? = null,
) {
    val hasDeviceToken: Boolean get() = deviceToken.isNotBlank()

    fun selectCourse(courseId: String): PhoneState =
        copy(selectedCourseId = courseId, activeHikeId = null, progress = 0.0)

    fun clearDeviceToken(): PhoneState =
        copy(deviceToken = "")
}
