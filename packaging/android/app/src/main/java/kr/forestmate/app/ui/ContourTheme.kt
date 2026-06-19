package kr.forestmate.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue

/**
 * 등고선(Contour Line) Design System — Kotlin tokens + drawable primitives.
 *
 * Ported 1:1 from `ForestMate System.dc.html` → system **1 · 등고선 / CONTOUR**
 * (the mobile default elevation): cream "paper" surfaces, organic ink-green
 * contour texture, and the green→teal→blue gradient reserved for "what matters
 * now". See `docs/design/contour-design-system.md`.
 */
object Contour {
    // --- Paper surfaces (CONTOUR light elevation) ---------------------------
    val paper = 0xFFF6F1E6.toInt()
    val paperEnd = 0xFFECE4D2.toInt()
    val card = 0xFFFFFDF7.toInt()
    val cardBorder = 0xFFE6DFCD.toInt()
    val tabFade = 0xFFF2EAD8.toInt()

    // --- Ink & text ---------------------------------------------------------
    val ink = 0xFF16291F.toInt()        // primary heading ink-green
    val inkAlt = 0xFF1D2B22.toInt()
    val sub = 0xFF54604F.toInt()        // secondary text
    val body = 0xFF6A7163.toInt()
    val muted = 0xFF8A8A7E.toInt()      // captions / mono labels
    val muted2 = 0xFF9A9686.toInt()
    val borderPill = 0xFFD8D0BD.toInt()

    // --- Brand spectrum (signature gradient: green → teal → blue) -----------
    val green = 0xFF1C8A4E.toInt()
    val teal = 0xFF22A29A.toInt()
    val blue = 0xFF2E7FD6.toInt()
    val neon = 0xFF46E59C.toInt()
    val cyan = 0xFF3FD6E8.toInt()
    val skyBlue = 0xFF3FB6E8.toInt()

    // --- Contour strokes ----------------------------------------------------
    val contourInk = 0xFF163E2A.toInt()
    val ghost = 0xFFFFFFFF.toInt()

    // --- Dark index / HUD forest --------------------------------------------
    val index1 = 0xFF15402B.toInt()
    val index2 = 0xFF13513A.toInt()
    val night = 0xFF0A1410.toInt()

    // --- Chips --------------------------------------------------------------
    val chipBg = 0xFFDCEFE2.toInt()
    val chipInk = 0xFF1C8A4E.toInt()

    // --- Caution / safety briefing ------------------------------------------
    val cautionBg = 0xFFFCF4E3.toInt()
    val cautionBorder = 0xFFEFDFB8.toInt()
    val cautionIcon = 0xFFE8A33D.toInt()
    val cautionInk = 0xFF9A6A12.toInt()
    val cautionBody = 0xFF6B5320.toInt()
    val difficulty = 0xFFC8822A.toInt()

    // --- Danger / SOS -------------------------------------------------------
    val danger = 0xFFE0322B.toInt()
    val dangerBright = 0xFFFF5B4B.toInt()
    val dangerInk = 0xFFD8281F.toInt()
    val dangerBg = 0xFFFDECEC.toInt()

    // --- Backward-compatible aliases (semantic names used across the kit) ----
    val pine = ink           // headings
    val moss = green
    val leaf = teal
    val mint = neon
    val sage = 0xFFB7E4C7.toInt()
    val pale = chipBg
    val deep = index1
    val abyss = night
    val bg = paper
    val tile = tabFade
    val line = cardBorder
    val amber = cautionIcon
    val amberInk = cautionInk
    val amberBg = cautionBg
    val sky = blue

    // --- Type ---------------------------------------------------------------
    // Space Grotesk (display) / JetBrains Mono (indicators) are approximated
    // with platform families until the brand fonts are bundled as resources.
    fun bold(): Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    fun black(): Typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    fun regular(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    fun mono(): Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        ).toInt()

    // --- Drawables ----------------------------------------------------------

    /** Raised paper card with the system radius and a warm hairline border. */
    fun cardBackground(context: Context, fill: Int = card): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, 18f).toFloat()
            setStroke(dp(context, 1f), cardBorder)
        }

    /** Rounded fill with optional stroke. */
    fun round(
        context: Context,
        fill: Int,
        radiusDp: Float = 14f,
        stroke: Int = 0,
        strokeDp: Float = 0f,
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeDp > 0f) setStroke(dp(context, strokeDp), stroke)
        }

    fun pill(context: Context, fill: Int, stroke: Int = 0, strokeDp: Float = 0f): GradientDrawable =
        round(context, fill, radiusDp = 999f, stroke = stroke, strokeDp = strokeDp)

    /** Signature green → teal → blue gradient ("what matters now"). */
    fun signatureGradient(context: Context, radiusDp: Float = 18f): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(green, teal, blue),
        ).apply { cornerRadius = dp(context, radiusDp).toFloat() }

    /** Dark forest index/HUD gradient (#15402B → #13513A). */
    fun indexGradient(context: Context, radiusDp: Float = 22f): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(index1, index2),
        ).apply { cornerRadius = dp(context, radiusDp).toFloat() }

    // legacy names
    fun heroGradient(context: Context, radiusDp: Float = 22f) = signatureGradient(context, radiusDp)
    fun deepGradient(context: Context, radiusDp: Float = 18f) = indexGradient(context, radiusDp)

    /**
     * Hero surface with a ghost contour field over a gradient base — the
     * signature 등고선 look. dark = dark forest index card; light = signature
     * green→blue gradient (course hero strips / RIDGELINE accents).
     */
    fun contourHero(context: Context, dark: Boolean = true): LayerDrawable {
        val base = if (dark) indexGradient(context) else signatureGradient(context)
        val field = ContourBackground(
            lineColor = ghost,
            lineAlpha = if (dark) 46 else 40,
            rings = 14,
            step = 16f,
            amp = 13f,
            cxFraction = 0.88f,
            cyFraction = 0.18f,
        )
        return LayerDrawable(arrayOf(base, field))
    }

    /** App background: cream paper gradient with a faint ink contour field. */
    fun appBackground(): LayerDrawable {
        val cream = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(paper, paperEnd),
        )
        val field = ContourBackground(
            lineColor = contourInk,
            lineAlpha = 30,
            rings = 15,
            step = 17f,
            amp = 15f,
            cxFraction = 0.84f,
            cyFraction = 0.08f,
        )
        return LayerDrawable(arrayOf(cream, field))
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
