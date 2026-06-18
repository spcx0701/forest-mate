package kr.forestmate.app.state

data class SosHoldState(
    val startedAtMs: Long,
    val requiredMs: Long = 1800L,
    val confirmed: Boolean = false,
) {
    fun update(nowMs: Long): SosHoldState =
        copy(confirmed = nowMs - startedAtMs >= requiredMs)
}
