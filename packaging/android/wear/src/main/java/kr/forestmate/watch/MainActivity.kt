package kr.forestmate.watch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import kotlin.math.cos
import kotlin.math.sin
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.UrlConnectionTransport

class MainActivity : Activity() {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable {
        refreshTick()
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var face: WatchFaceView
    private lateinit var codeInput: EditText
    private lateinit var primaryButton: Button
    private lateinit var secondaryButton: Button
    private var backupPairingVisible = false
    private var transientMessage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        intent.getStringExtra(KEY_API_BASE)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            prefs.edit().putString(KEY_API_BASE, it).apply()
        }

        setContentView(buildUi())
        requestMissingPermissions()
        renderState()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun refreshTick() {
        renderState()
        refreshHandler.postDelayed(refreshRunnable, 1000L)
    }

    private fun buildUi(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.forest_cream))
        }

        face = WatchFaceView(this)
        root.addView(
            face,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        codeInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            gravity = Gravity.CENTER
            setTextColor(color(R.color.forest_ink))
            setHintTextColor(0x9954604F.toInt())
            textSize = 17f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            hint = "백업 코드"
            setSingleLine(true)
            includeFontPadding = false
            setPadding(dp(8), 0, dp(8), 0)
            background = round(color(R.color.forest_card), color(R.color.forest_green), dp(1), dp(14))
            visibility = View.GONE
        }
        root.addView(codeInput, centerInputLayout())

        primaryButton = compactButton().apply {
            background = round(color(R.color.forest_green), 0, 0, dp(14))
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { handlePrimaryAction() }
        }

        secondaryButton = compactButton().apply {
            background = round(color(R.color.forest_card), color(R.color.forest_green), dp(1), dp(14))
            setTextColor(color(R.color.forest_ink))
            setOnClickListener { handleSecondaryAction() }
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(primaryButton, controlButtonLayout())
            addView(secondaryButton, controlButtonLayout())
        }
        val controlsLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(28),
            Gravity.BOTTOM,
        ).apply {
            setMargins(dp(48), 0, dp(48), dp(15))
        }
        root.addView(controls, controlsLp)
        return root
    }

    private fun handlePrimaryAction() {
        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        if (token.isNotEmpty()) {
            if (prefs.getBoolean(KEY_STREAMING, false)) {
                stopSensorService()
                transientMessage = "센서 전송을 멈췄습니다"
            } else {
                startSensorService(token)
                transientMessage = "심박·GPS 전송 시작"
            }
            renderState()
            return
        }

        if (!backupPairingVisible) {
            transientMessage = "휴대폰 산행 기록을 기다리는 중입니다"
            renderState()
            return
        }

        val code = codeInput.text.toString().trim()
        if (code.length != 6) {
            transientMessage = "6자리 백업 코드를 입력하세요"
            renderState()
            return
        }

        primaryButton.isEnabled = false
        transientMessage = "백업 연결 중..."
        renderState()
        Thread {
            try {
                val result = WatchApi(ApiConfig(apiBase()), UrlConnectionTransport()).claim(code)
                val editor = prefs.edit()
                    .putString(KEY_TOKEN, result.watchToken)
                    .putString(KEY_HIKE_ID, result.hikeId)
                    .putString(KEY_COURSE_ID, result.courseId)
                    .putString(KEY_COURSE_NAME, result.courseName)
                    .putString(KEY_ROUTE, result.route)
                result.courseKm?.let { editor.putFloat(KEY_COURSE_KM, it.toFloat()) }
                result.courseElev?.let { editor.putInt(KEY_COURSE_ELEV, it) }
                editor.apply()
                runOnUiThread {
                    primaryButton.isEnabled = true
                    backupPairingVisible = false
                    codeInput.setText("")
                    codeInput.visibility = View.GONE
                    transientMessage = "워치가 산행 기록에 연결됐습니다"
                    startSensorService(result.watchToken)
                    renderState()
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    primaryButton.isEnabled = true
                    transientMessage = "연결 실패: ${shortMessage(ex)}"
                    renderState()
                }
            }
        }.start()
    }

    private fun handleSecondaryAction() {
        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        if (token.isNotEmpty()) {
            disconnect()
            transientMessage = "워치 연결을 해제했습니다"
            renderState()
            return
        }
        backupPairingVisible = !backupPairingVisible
        codeInput.visibility = if (backupPairingVisible) View.VISIBLE else View.GONE
        if (backupPairingVisible) {
            codeInput.requestFocus()
            transientMessage = "휴대폰의 백업 코드를 입력합니다"
        } else {
            codeInput.setText("")
            transientMessage = ""
        }
        renderState()
    }

    private fun disconnect() {
        stopSensorService()
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_HIKE_ID)
            .remove(KEY_COURSE_ID)
            .remove(KEY_COURSE_NAME)
            .remove(KEY_COURSE_KM)
            .remove(KEY_COURSE_ELEV)
            .remove(KEY_ROUTE)
            .remove(KEY_LAST_PROGRESS)
            .remove(KEY_LAST_DISTRESS_LEVEL)
            .remove(KEY_LAST_UPLOAD_AT)
            .apply()
    }

    private fun renderState() {
        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        val connected = token.isNotEmpty()
        val streaming = prefs.getBoolean(KEY_STREAMING, false)
        if (connected) {
            primaryButton.text = if (streaming) "중지" else "전송"
            secondaryButton.text = "해제"
        } else if (backupPairingVisible) {
            primaryButton.text = "연결"
            secondaryButton.text = "취소"
        } else {
            primaryButton.text = "대기"
            secondaryButton.text = "백업"
        }
        face.setSnapshot(readSnapshot(connected, streaming))
    }

    private fun readSnapshot(connected: Boolean, streaming: Boolean): WatchSnapshot =
        WatchSnapshot(
            connected = connected,
            streaming = streaming,
            backupVisible = backupPairingVisible,
            courseName = prefs.getString(KEY_COURSE_NAME, "").orEmpty()
                .ifBlank { prefs.getString(KEY_COURSE_ID, "").orEmpty() },
            route = prefs.getString(KEY_ROUTE, "").orEmpty(),
            courseKm = prefs.getFloat(KEY_COURSE_KM, 0f),
            courseElev = prefs.getInt(KEY_COURSE_ELEV, 0),
            hr = prefs.getInt(KEY_LAST_HR, 0),
            battery = prefs.getInt(KEY_LAST_BATTERY, -1),
            alt = prefs.getInt(KEY_LAST_ALT, 0),
            acc = prefs.getInt(KEY_LAST_ACC, 0),
            heading = prefs.getInt(KEY_LAST_HEADING, -1),
            progress = prefs.getFloat(KEY_LAST_PROGRESS, 0f),
            distressLevel = prefs.getInt(KEY_LAST_DISTRESS_LEVEL, 0),
            hasGps = prefs.contains(KEY_LAST_LAT) && prefs.contains(KEY_LAST_LON),
            lastUploadAt = prefs.getLong(KEY_LAST_UPLOAD_AT, 0L),
            message = transientMessage,
        )

    private fun startSensorService(token: String) {
        val intent = Intent(this, WatchSensorService::class.java)
            .putExtra(KEY_API_BASE, apiBase())
            .putExtra(KEY_TOKEN, token)
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSensorService() {
        stopService(Intent(this, WatchSensorService::class.java))
        prefs.edit().putBoolean(KEY_STREAMING, false).apply()
    }

    private fun apiBase(): String =
        prefs.getString(KEY_API_BASE, getString(R.string.default_api_base)) ?: getString(R.string.default_api_base)

    private fun requestMissingPermissions() {
        val missing = mutableListOf<String>()
        addIfMissing(
            missing,
            if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS,
        )
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION)
        addIfMissing(missing, Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 11)
        }
    }

    private fun addIfMissing(missing: MutableList<String>, permission: String) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission)
        }
    }

    private fun centerInputLayout(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(34),
            Gravity.CENTER,
        ).apply {
            setMargins(dp(62), 0, dp(62), 0)
        }

    private fun controlButtonLayout(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }

    private fun compactButton(): Button =
        Button(this).apply {
            isAllCaps = false
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            includeFontPadding = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(2), 0, dp(2), 0)
        }

    private fun round(fill: Int, stroke: Int, strokeWidth: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun color(resId: Int): Int =
        getColor(resId)

    private fun shortMessage(ex: Exception): String {
        val message = ex.message.orEmpty().ifBlank { "unknown" }
        return if (message.length > 58) message.substring(0, 58) else message
    }

    data class WatchSnapshot(
        val connected: Boolean = false,
        val streaming: Boolean = false,
        val backupVisible: Boolean = false,
        val hasGps: Boolean = false,
        val courseName: String = "",
        val route: String = "",
        val message: String = "",
        val courseKm: Float = 0f,
        val courseElev: Int = 0,
        val hr: Int = 0,
        val battery: Int = -1,
        val alt: Int = 0,
        val acc: Int = 0,
        val heading: Int = -1,
        val progress: Float = 0f,
        val distressLevel: Int = 0,
        val lastUploadAt: Long = 0L,
    )

    class WatchFaceView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arc = RectF()
        private var snapshot = WatchSnapshot()

        fun setSnapshot(next: WatchSnapshot) {
            snapshot = next
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val metrics = faceMetrics()
            drawBackground(canvas, metrics)
            drawProgress(canvas, metrics)
            drawTitle(canvas, metrics)
            drawHeartRate(canvas, metrics)
            drawStatus(canvas, metrics)
        }

        private fun faceMetrics(): FaceMetrics {
            val w = width.toFloat()
            val h = height.toFloat()
            return FaceMetrics(
                w = w,
                h = h,
                radius = minOf(w, h) / 2f - dp(8).toFloat(),
                cx = w / 2f,
                cy = h / 2f,
            )
        }

        private val contourPath = Path()

        private fun drawBackground(canvas: Canvas, metrics: FaceMetrics) {
            // cream "paper" face
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = context.getColor(R.color.forest_cream)
            canvas.drawRect(0f, 0f, metrics.w, metrics.h, paint)

            // 등고선 motif: organic ink contour rings (ported generator).
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = context.getColor(R.color.contour_ink)
            val unit = metrics.radius / 90f
            for (i in 0 until 7) {
                paint.alpha = (30 * (1f - i * 0.04f)).toInt().coerceIn(8, 255)
                buildContourRing(metrics.cx + metrics.radius * 0.28f, metrics.cy - metrics.radius * 0.30f, (14f + i * 13f) * unit, 11f * unit, i * 1.7f)
                canvas.drawPath(contourPath, paint)
            }
            paint.alpha = 255
        }

        private fun buildContourRing(cx: Float, cy: Float, baseR: Float, amp: Float, seed: Float) {
            contourPath.reset()
            val n = 60
            for (k in 0..n) {
                val a = k.toFloat() / n * Math.PI.toFloat() * 2f
                val r = baseR + amp * sin(a * 3f + seed) + amp * 0.45f * cos(a * 5f + seed * 1.4f)
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r * 0.82f
                if (k == 0) contourPath.moveTo(x, y) else contourPath.lineTo(x, y)
            }
            contourPath.close()
        }

        private fun drawProgress(canvas: Canvas, metrics: FaceMetrics) {
            arc.set(
                metrics.cx - metrics.radius,
                metrics.cy - metrics.radius,
                metrics.cx + metrics.radius,
                metrics.cy + metrics.radius,
            )
            // faint track
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(8).toFloat()
            paint.shader = null
            paint.color = 0x1F143C28
            canvas.drawArc(arc, 0f, 360f, false, paint)
            // green → blue progress arc
            paint.strokeCap = Paint.Cap.ROUND
            paint.shader = LinearGradient(
                arc.left, arc.top, arc.right, arc.bottom,
                context.getColor(R.color.forest_green), context.getColor(R.color.forest_blue),
                Shader.TileMode.CLAMP,
            )
            canvas.drawArc(arc, -90f, snapshot.progress.coerceIn(0f, 1f) * 360f, false, paint)
            paint.shader = null
            paint.strokeCap = Paint.Cap.BUTT
        }

        private fun drawTitle(canvas: Canvas, metrics: FaceMetrics) {
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.color = context.getColor(R.color.forest_text)
            paint.textSize = dp(13).toFloat()
            canvas.drawText(if (snapshot.connected) "ForestMate" else "대기 중", metrics.cx, dp(42).toFloat(), paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = dp(10).toFloat()
            val course = snapshot.courseName.ifBlank { if (snapshot.backupVisible) "백업 코드 입력" else "휴대폰 산행 대기" }
            canvas.drawText(course, metrics.cx, dp(65).toFloat(), paint)
            if (snapshot.route.isNotBlank()) {
                canvas.drawText(snapshot.route, metrics.cx, dp(80).toFloat(), paint)
            }
        }

        private fun drawHeartRate(canvas: Canvas, metrics: FaceMetrics) {
            paint.textSize = dp(20).toFloat()
            paint.typeface = Typeface.DEFAULT_BOLD
            val hrText = if (snapshot.hr > 0) "${snapshot.hr} bpm" else "-- bpm"
            canvas.drawText(hrText, metrics.cx, metrics.cy + dp(8), paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = dp(9).toFloat()
            val gpsText = if (snapshot.hasGps) "GPS" else "GPS 대기"
            val batteryText = if (snapshot.battery >= 0) "${snapshot.battery}%" else "--%"
            canvas.drawText("$gpsText · alt ${snapshot.alt} · $batteryText", metrics.cx, metrics.cy + dp(28), paint)
        }

        private fun drawStatus(canvas: Canvas, metrics: FaceMetrics) {
            val status = snapshot.message.ifBlank {
                if (snapshot.streaming) "전송 중" else if (snapshot.connected) "전송 준비" else "백업 연결 가능"
            }
            paint.color = if (snapshot.distressLevel > 0) 0xFFFFD166.toInt() else context.getColor(R.color.forest_mint)
            paint.textSize = dp(9).toFloat()
            canvas.drawText(status, metrics.cx, metrics.h - dp(52).toFloat(), paint)
        }

        private fun dp(value: Int): Int =
            (value * resources.displayMetrics.density + 0.5f).toInt()

        private data class FaceMetrics(
            val w: Float,
            val h: Float,
            val radius: Float,
            val cx: Float,
            val cy: Float,
        )
    }

    companion object {
        const val PREFS = "forestmate_watch"
        const val KEY_API_BASE = "apiBase"
        const val KEY_TOKEN = "watchToken"
        const val KEY_HIKE_ID = "hikeId"
        const val KEY_COURSE_ID = "courseId"
        const val KEY_COURSE_NAME = "courseName"
        const val KEY_COURSE_KM = "courseKm"
        const val KEY_COURSE_ELEV = "courseElev"
        const val KEY_ROUTE = "route"
        const val KEY_STREAMING = "streaming"
        const val KEY_LAST_HR = "lastHr"
        const val KEY_LAST_BATTERY = "lastBattery"
        const val KEY_LAST_LAT = "lastLat"
        const val KEY_LAST_LON = "lastLon"
        const val KEY_LAST_ALT = "lastAlt"
        const val KEY_LAST_ACC = "lastAcc"
        const val KEY_LAST_HEADING = "lastHeading"
        const val KEY_LAST_PROGRESS = "lastProgress"
        const val KEY_LAST_DISTRESS_LEVEL = "lastDistressLevel"
        const val KEY_LAST_UPLOAD_AT = "lastUploadAt"
    }
}
