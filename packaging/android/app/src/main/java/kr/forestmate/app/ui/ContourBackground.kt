package kr.forestmate.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * 등고선(contour) field — the signature motif of the design system.
 *
 * Ports the exact generator from `ForestMate System.dc.html` (system 1 · 등고선):
 * organic, irregular concentric ridge lines like a real topographic survey, not
 * perfect circles. Each ring's radius wobbles via sin/cos harmonics with a
 * per-ring seed, and the field is vertically squashed (×0.82).
 *
 * Used as the cream app background (ink lines, low alpha) and as a ghost overlay
 * on dark/gradient hero surfaces (white lines).
 */
class ContourBackground(
    private val lineColor: Int,
    private val lineAlpha: Int = 28,
    private val rings: Int = 13,
    private val step: Float = 16f,
    private val amp: Float = 13f,
    private val cxFraction: Float = 0.76f,
    private val cyFraction: Float = 0.22f,
    private val strokeWidthDp: Float = 1.1f,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = lineColor
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val cx = w * cxFraction
        val cy = h * cyFraction
        // scale ring geometry to the surface so it fills nicely on any size
        val unit = (minOf(w, h) / 360f).coerceAtLeast(0.4f)
        paint.strokeWidth = (strokeWidthDp * unit).coerceAtLeast(1f)

        for (i in 0 until rings) {
            val baseR = (16f + i * step) * unit
            val seed = i * 1.7f
            paint.alpha = (lineAlpha * (1f - i * 0.015f)).toInt().coerceIn(4, 255)
            buildRing(cx, cy, baseR, amp * unit, seed)
            canvas.drawPath(path, paint)
        }
    }

    /** Mirrors the design's `ring(cx, cy, baseR, amp, seed)` — N=70 samples. */
    private fun buildRing(cx: Float, cy: Float, baseR: Float, amp: Float, seed: Float) {
        path.reset()
        val n = 70
        for (k in 0..n) {
            val a = k.toFloat() / n * Math.PI.toFloat() * 2f
            val r = baseR +
                amp * sin(a * 3f + seed) +
                amp * 0.45f * cos(a * 5f + seed * 1.4f)
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r * 0.82f
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
