package kr.forestmate.app.ui

object BottomNavLayout {
    const val maxVisualSafeBottomInsetDp = 40f

    fun rootBottomPaddingPx(): Int = 0

    fun tabContentSafeBottomInsetPx(rawInsetPx: Int, maxInsetPx: Int): Int =
        rawInsetPx.coerceAtLeast(0).coerceAtMost(maxInsetPx.coerceAtLeast(0))
}
