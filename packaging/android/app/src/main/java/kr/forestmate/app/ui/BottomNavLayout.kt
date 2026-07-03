package kr.forestmate.app.ui

object BottomNavLayout {
    fun rootBottomPaddingPx(): Int = 0

    fun tabContentSafeBottomInsetPx(rawInsetPx: Int): Int =
        rawInsetPx.coerceAtLeast(0)
}
