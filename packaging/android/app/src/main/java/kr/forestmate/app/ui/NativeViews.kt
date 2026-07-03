package kr.forestmate.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kr.forestmate.app.state.PhoneTab

/**
 * Themed component kit for the 등고선 디자인 시스템 (Contour Line Design System).
 *
 * Pure View-based (no Compose) factories that render the same visual language as
 * the web app: elevation-band colours, raised cards, contour motifs, pill chips,
 * and a topographic background. See [Contour] for tokens and
 * `docs/design/contour-design-system.md` for the full system.
 */
object NativeViews {

    private fun dp(c: Context, v: Float) = Contour.dp(c, v)

    // --- Page scaffold -------------------------------------------------------

    /** Scroll content container with screen gutters (background/app bar set by caller). */
    fun screen(context: Context, title: String, body: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18f), dp(context, 10f), dp(context, 18f), dp(context, 24f))
            addView(appBar(context, title, body))
        }

    /** Brand app bar: pine mark + app name + location, then the active screen context. */
    fun appBar(context: Context, title: String, subtitle: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 4f), 0, dp(context, 18f))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        BrandMarkView(context),
                        LinearLayout.LayoutParams(dp(context, 44f), dp(context, 44f)).apply {
                            rightMargin = dp(context, 12f)
                        },
                    )
                    addView(TextView(context).apply {
                        text = "숲길동무"
                        setTextColor(Contour.pine)
                        textSize = 25f
                        typeface = Contour.black()
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = "📍 서울 은평구 ▾"
                        setTextColor(Contour.sub)
                        textSize = 12.5f
                        typeface = Contour.bold()
                        background = Contour.pill(context, Contour.withAlpha(Contour.card, 220), stroke = Contour.cardBorder, strokeDp = 1f)
                        setPadding(dp(context, 10f), dp(context, 7f), dp(context, 10f), dp(context, 7f))
                    })
                },
            )
            addView(
                LinearLayout(context).apply {
                    setPadding(0, dp(context, 22f), 0, 0)
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = title
                        setTextColor(Contour.pine)
                        textSize = 22f
                        typeface = Contour.black()
                    })
                    addView(TextView(context).apply {
                        text = subtitle
                        setTextColor(Contour.muted)
                        textSize = 12.5f
                        typeface = Contour.bold()
                        setPadding(0, dp(context, 5f), 0, 0)
                    })
                },
            )
        }

    // --- Surfaces ------------------------------------------------------------

    /** Raised white card; add children to the returned LinearLayout. */
    fun card(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.cardBackground(context)
            setPadding(dp(context, 16f), dp(context, 16f), dp(context, 16f), dp(context, 16f))
            elevate(this, 4f)
            (layoutParamsOrMargin(this)).bottomMargin = dp(context, 14f)
        }

    /** Dark hero card carrying a contour field overlay (home index, SOS location). */
    fun heroCard(context: Context, dark: Boolean = false): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Contour.contourHero(context, dark)
            setPadding(dp(context, 18f), dp(context, 18f), dp(context, 18f), dp(context, 18f))
            elevate(this, 8f)
            outlineProvider = roundOutline(dp(context, if (dark) 18f else 22f))
            clipToOutline = true
            (layoutParamsOrMargin(this)).bottomMargin = dp(context, 14f)
        }

    // --- Text ----------------------------------------------------------------

    fun sectionText(context: Context, textValue: String): TextView = sectionHeader(context, textValue)

    fun sectionHeader(context: Context, textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            textSize = 16f
            typeface = Contour.black()
            setPadding(dp(context, 2f), dp(context, 18f), 0, dp(context, 8f))
            setTextColor(Contour.pine)
        }

    fun bodyText(context: Context, textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            textSize = 13.5f
            setLineSpacing(dp(context, 3f).toFloat(), 1f)
            setPadding(0, dp(context, 4f), 0, dp(context, 4f))
            setTextColor(Contour.ink)
        }

    fun captionText(context: Context, textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            textSize = 10.5f
            typeface = Contour.mono()
            setPadding(0, dp(context, 4f), 0, dp(context, 8f))
            setTextColor(Contour.muted)
        }

    /** Status line shown as a soft pale pill. */
    fun statusText(context: Context, textValue: String): TextView =
        TextView(context).apply {
            text = textValue
            textSize = 12.5f
            typeface = Contour.bold()
            setTextColor(Contour.chipInk)
            background = Contour.pill(context, Contour.chipBg)
            setPadding(dp(context, 13f), dp(context, 9f), dp(context, 13f), dp(context, 9f))
            (layoutParamsOrMargin(this)).apply {
                topMargin = dp(context, 4f)
                bottomMargin = dp(context, 8f)
            }
            visibility = if (textValue.isBlank()) View.GONE else View.VISIBLE
        }

    /** Small rounded chip — mono label on a soft green pill (survey-instrument feel). */
    fun chip(context: Context, textValue: String, fill: Int = Contour.chipBg, ink: Int = Contour.chipInk): TextView =
        TextView(context).apply {
            text = textValue
            textSize = 10.5f
            typeface = Contour.mono()
            setTextColor(ink)
            background = Contour.pill(context, fill)
            setPadding(dp(context, 10f), dp(context, 5f), dp(context, 10f), dp(context, 5f))
        }

    // --- Buttons -------------------------------------------------------------

    fun primaryButton(context: Context, label: String, onClick: () -> Unit): Button =
        styledButton(context, label, Contour.pine, Contour.card, onClick)

    fun actionButton(context: Context, label: String, onClick: () -> Unit): Button =
        primaryButton(context, label, onClick)

    fun ghostButton(context: Context, label: String, onClick: () -> Unit): Button =
        styledButton(context, label, Contour.card, Contour.pine, onClick, stroke = Contour.line, strokeDp = 1.5f)

    fun warnButton(context: Context, label: String, onClick: () -> Unit): Button =
        styledButton(context, label, Contour.amberBg, Contour.amberInk, onClick, stroke = 0xFFF8D8B5.toInt(), strokeDp = 1f)

    fun dangerButton(context: Context, label: String, onClick: () -> Unit): Button =
        styledButton(context, label, Contour.danger, Contour.card, onClick)

    private fun styledButton(
        context: Context,
        label: String,
        fill: Int,
        textColor: Int,
        onClick: () -> Unit,
        stroke: Int = 0,
        strokeDp: Float = 0f,
    ): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            typeface = Contour.bold()
            setTextColor(textColor)
            stateListAnimator = null
            background = Contour.round(context, fill, radiusDp = 14f, stroke = stroke, strokeDp = strokeDp)
            setPadding(dp(context, 16f), dp(context, 12f), dp(context, 16f), dp(context, 12f))
            minHeight = dp(context, 48f)
            setOnClickListener { onClick() }
            (layoutParamsOrMargin(this)).apply {
                topMargin = dp(context, 5f)
                bottomMargin = dp(context, 5f)
            }
        }

    // --- Bottom navigation ---------------------------------------------------

    fun tabButton(context: Context, tab: PhoneTab, onClick: () -> Unit): Button =
        Button(context).apply {
            text = "${tabIcon(tab)}\n${tab.label}"
            isAllCaps = false
            textSize = 10f
            typeface = Contour.bold()
            gravity = Gravity.CENTER
            background = null
            stateListAnimator = null
            setTextColor(Contour.sub)
            setOnClickListener { onClick() }
        }

    fun styleTabSelection(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) Contour.pine else Contour.sub)
        button.typeface = if (selected) Contour.black() else Contour.bold()
    }

    private fun tabIcon(tab: PhoneTab): String = when (tab) {
        PhoneTab.HOME -> "⛰"
        PhoneTab.HIKE -> "🗺"
        PhoneTab.SOS -> "🆘"
        PhoneTab.AI -> "🌿"
        PhoneTab.MY -> "👤"
    }

    // --- Stats ---------------------------------------------------------------

    /** Four-up stat row container. */
    fun statRow(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            (layoutParamsOrMargin(this)).apply {
                topMargin = dp(context, 8f)
                bottomMargin = dp(context, 8f)
            }
        }

    fun statTile(context: Context, value: String, label: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Contour.round(context, Contour.card, radiusDp = 14f)
            elevate(this, 3f)
            setPadding(dp(context, 6f), dp(context, 10f), dp(context, 6f), dp(context, 10f))
            addView(TextView(context).apply {
                text = value
                textSize = 15f
                typeface = Contour.black()
                setTextColor(Contour.pine)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = label
                textSize = 9.5f
                typeface = Contour.bold()
                setTextColor(Contour.sub)
                gravity = Gravity.CENTER
            })
        }

    // --- Helpers -------------------------------------------------------------

    private fun elevate(view: View, elevationDp: Float) {
        view.elevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            elevationDp,
            view.resources.displayMetrics,
        )
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
        view.clipToOutline = false
    }

    private fun roundOutline(radiusPx: Int): ViewOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx.toFloat())
            }
        }

    /** Ensure the view has MarginLayoutParams and return them for mutation. */
    private fun layoutParamsOrMargin(view: View): ViewGroup.MarginLayoutParams {
        val existing = view.layoutParams
        val mlp = when (existing) {
            is ViewGroup.MarginLayoutParams -> existing
            null -> ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            else -> ViewGroup.MarginLayoutParams(existing)
        }
        view.layoutParams = mlp
        return mlp
    }

    /**
     * Score ring for the home index card — track + a green→blue gradient arc with
     * the score in a large display number, mirroring the design's index ring.
     */
    class ScoreRingView(context: Context, private val score: Int) : View(context) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0x29FFFFFF // white .16
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Contour.black()
        }
        private val oval = RectF()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val sw = w * 0.085f
            track.strokeWidth = sw
            arc.strokeWidth = sw
            val pad = sw / 2f + 1f
            oval.set(pad, pad, w - pad, h - pad)
            arc.shader = android.graphics.LinearGradient(
                0f, 0f, w, h, Contour.neon, Contour.skyBlue, android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawArc(oval, 0f, 360f, false, track)
            val sweep = (score.coerceIn(0, 100) / 100f) * 360f
            canvas.drawArc(oval, -90f, sweep, false, arc)
            label.textSize = h * 0.34f
            val baseline = h / 2f - (label.descent() + label.ascent()) / 2f
            canvas.drawText(score.toString(), w / 2f, baseline, label)
        }
    }

    /** Pine tree brand mark drawn on a rounded pine tile (matches forestmate-mark.svg). */
    class BrandMarkView(context: Context) : View(context) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Contour.pine }
        private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Contour.mint }
        private val rect = RectF()
        private val tree = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            rect.set(0f, 0f, w, h)
            val r = w * 0.30f
            canvas.drawRoundRect(rect, r, r, bg)

            // simple 3-tier pine silhouette
            val cx = w * 0.5f
            tree.reset()
            tree.moveTo(cx, h * 0.20f)
            tree.lineTo(w * 0.72f, h * 0.50f)
            tree.lineTo(w * 0.60f, h * 0.50f)
            tree.lineTo(w * 0.78f, h * 0.70f)
            tree.lineTo(w * 0.28f, h * 0.70f)
            tree.lineTo(w * 0.40f, h * 0.50f)
            tree.lineTo(w * 0.28f, h * 0.50f)
            tree.close()
            canvas.drawPath(tree, fg)
            canvas.drawRect(cx - w * 0.045f, h * 0.66f, cx + w * 0.045f, h * 0.80f, dot)
            canvas.drawCircle(cx, h * 0.24f, w * 0.05f, dot)
        }
    }
}
