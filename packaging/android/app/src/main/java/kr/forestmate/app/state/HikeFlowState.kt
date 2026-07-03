package kr.forestmate.app.state

data class HikeFlowState(
    val selectedCourseId: String? = null,
    val activeHikeId: String? = null,
    val progress: Double = 0.0,
    val watchPairCode: String? = null,
) {
    fun started(hikeId: String): HikeFlowState =
        copy(activeHikeId = hikeId, progress = 0.0)

    fun updateProgress(value: Double): HikeFlowState =
        copy(progress = value.coerceIn(0.0, 1.0))

    fun paired(code: String): HikeFlowState =
        copy(watchPairCode = code)
}
