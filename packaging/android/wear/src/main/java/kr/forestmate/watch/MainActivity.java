package kr.forestmate.watch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final String PREFS = "forestmate_watch";
    static final String KEY_API_BASE = "apiBase";
    static final String KEY_TOKEN = "watchToken";
    static final String KEY_HIKE_ID = "hikeId";
    static final String KEY_COURSE_ID = "courseId";
    static final String KEY_COURSE_NAME = "courseName";
    static final String KEY_COURSE_KM = "courseKm";
    static final String KEY_COURSE_ELEV = "courseElev";
    static final String KEY_ROUTE = "route";
    static final String KEY_STREAMING = "streaming";
    static final String KEY_LAST_HR = "lastHr";
    static final String KEY_LAST_BATTERY = "lastBattery";
    static final String KEY_LAST_LAT = "lastLat";
    static final String KEY_LAST_LON = "lastLon";
    static final String KEY_LAST_ALT = "lastAlt";
    static final String KEY_LAST_ACC = "lastAcc";
    static final String KEY_LAST_HEADING = "lastHeading";
    static final String KEY_LAST_PROGRESS = "lastProgress";
    static final String KEY_LAST_DISTRESS_LEVEL = "lastDistressLevel";
    static final String KEY_LAST_UPLOAD_AT = "lastUploadAt";

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderState();
            refreshHandler.postDelayed(this, 1000L);
        }
    };

    private SharedPreferences prefs;
    private WatchFaceView face;
    private EditText codeInput;
    private Button primaryButton;
    private Button secondaryButton;
    private boolean backupPairingVisible;
    private String transientMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String apiBase = getIntent().getStringExtra(KEY_API_BASE);
        if (apiBase != null && apiBase.trim().length() > 0) {
            prefs.edit().putString(KEY_API_BASE, apiBase.trim()).apply();
        }

        setContentView(buildUi());
        requestMissingPermissions();
        renderState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private FrameLayout buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.forest_deep));

        face = new WatchFaceView(this);
        root.addView(face, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        codeInput = new EditText(this);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setTextColor(color(R.color.forest_text));
        codeInput.setHintTextColor(0x88F4F7F2);
        codeInput.setTextSize(17);
        codeInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeInput.setHint("백업 코드");
        codeInput.setSingleLine(true);
        codeInput.setIncludeFontPadding(false);
        codeInput.setPadding(dp(8), 0, dp(8), 0);
        codeInput.setBackground(round(0xEE0B2417, color(R.color.forest_mint), dp(1), dp(14)));
        codeInput.setVisibility(View.GONE);
        root.addView(codeInput, centerInputLayout());

        primaryButton = compactButton();
        primaryButton.setBackground(round(color(R.color.forest_mint), 0, 0, dp(14)));
        primaryButton.setTextColor(color(R.color.forest_deep));
        primaryButton.setOnClickListener(v -> handlePrimaryAction());

        secondaryButton = compactButton();
        secondaryButton.setBackground(round(0xEE0B2417, 0x66B7E4C7, dp(1), dp(14)));
        secondaryButton.setTextColor(color(R.color.forest_text));
        secondaryButton.setOnClickListener(v -> handleSecondaryAction());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(primaryButton, controlButtonLayout());
        controls.addView(secondaryButton, controlButtonLayout());
        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28),
                Gravity.BOTTOM
        );
        controlsLp.setMargins(dp(48), 0, dp(48), dp(15));
        root.addView(controls, controlsLp);

        return root;
    }

    private void handlePrimaryAction() {
        String token = prefs.getString(KEY_TOKEN, "");
        if (token.length() > 0) {
            if (prefs.getBoolean(KEY_STREAMING, false)) {
                stopSensorService();
                transientMessage = "센서 전송을 멈췄습니다";
            } else {
                startSensorService(token);
                transientMessage = "심박·GPS 전송 시작";
            }
            renderState();
            return;
        }

        if (!backupPairingVisible) {
            transientMessage = "휴대폰 산행 기록을 기다리는 중입니다";
            renderState();
            return;
        }

        String code = codeInput.getText().toString().trim();
        if (code.length() != 6) {
            transientMessage = "6자리 백업 코드를 입력하세요";
            renderState();
            return;
        }

        primaryButton.setEnabled(false);
        transientMessage = "백업 연결 중...";
        renderState();
        new Thread(() -> {
            try {
                WatchApi.ClaimResult result = WatchApi.claim(apiBase(), code);
                SharedPreferences.Editor editor = prefs.edit()
                        .putString(KEY_TOKEN, result.watchToken)
                        .putString(KEY_HIKE_ID, safe(result.hikeId))
                        .putString(KEY_COURSE_ID, safe(result.courseId))
                        .putString(KEY_COURSE_NAME, safe(result.courseName))
                        .putString(KEY_ROUTE, safe(result.route));
                if (result.courseKm != null) {
                    editor.putFloat(KEY_COURSE_KM, result.courseKm.floatValue());
                }
                if (result.courseElev != null) {
                    editor.putInt(KEY_COURSE_ELEV, result.courseElev);
                }
                editor.apply();
                runOnUiThread(() -> {
                    primaryButton.setEnabled(true);
                    backupPairingVisible = false;
                    codeInput.setText("");
                    codeInput.setVisibility(View.GONE);
                    transientMessage = "워치가 산행 기록에 연결됐습니다";
                    startSensorService(result.watchToken);
                    renderState();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    primaryButton.setEnabled(true);
                    transientMessage = "연결 실패: " + shortMessage(ex);
                    renderState();
                });
            }
        }).start();
    }

    private void handleSecondaryAction() {
        String token = prefs.getString(KEY_TOKEN, "");
        if (token.length() > 0) {
            disconnect();
            transientMessage = "워치 연결을 해제했습니다";
            renderState();
            return;
        }
        backupPairingVisible = !backupPairingVisible;
        codeInput.setVisibility(backupPairingVisible ? View.VISIBLE : View.GONE);
        if (backupPairingVisible) {
            codeInput.requestFocus();
            transientMessage = "휴대폰의 백업 코드를 입력합니다";
        } else {
            codeInput.setText("");
            transientMessage = "";
        }
        renderState();
    }

    private void disconnect() {
        stopSensorService();
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
                .apply();
    }

    private void renderState() {
        String token = prefs.getString(KEY_TOKEN, "");
        boolean connected = token.length() > 0;
        boolean streaming = prefs.getBoolean(KEY_STREAMING, false);
        if (connected) {
            primaryButton.setText(streaming ? "중지" : "전송");
            secondaryButton.setText("해제");
        } else if (backupPairingVisible) {
            primaryButton.setText("연결");
            secondaryButton.setText("취소");
        } else {
            primaryButton.setText("대기");
            secondaryButton.setText("백업");
        }
        face.setSnapshot(readSnapshot(connected, streaming));
    }

    private WatchSnapshot readSnapshot(boolean connected, boolean streaming) {
        WatchSnapshot snapshot = new WatchSnapshot();
        snapshot.connected = connected;
        snapshot.streaming = streaming;
        snapshot.backupVisible = backupPairingVisible;
        snapshot.courseName = prefs.getString(KEY_COURSE_NAME, "");
        if (snapshot.courseName.length() == 0) {
            snapshot.courseName = prefs.getString(KEY_COURSE_ID, "");
        }
        snapshot.route = prefs.getString(KEY_ROUTE, "");
        snapshot.courseKm = prefs.getFloat(KEY_COURSE_KM, 0f);
        snapshot.courseElev = prefs.getInt(KEY_COURSE_ELEV, 0);
        snapshot.hr = prefs.getInt(KEY_LAST_HR, 0);
        snapshot.battery = prefs.getInt(KEY_LAST_BATTERY, -1);
        snapshot.alt = prefs.getInt(KEY_LAST_ALT, 0);
        snapshot.acc = prefs.getInt(KEY_LAST_ACC, 0);
        snapshot.heading = prefs.getInt(KEY_LAST_HEADING, -1);
        snapshot.progress = prefs.getFloat(KEY_LAST_PROGRESS, 0f);
        snapshot.distressLevel = prefs.getInt(KEY_LAST_DISTRESS_LEVEL, 0);
        snapshot.hasGps = prefs.contains(KEY_LAST_LAT) && prefs.contains(KEY_LAST_LON);
        snapshot.lastUploadAt = prefs.getLong(KEY_LAST_UPLOAD_AT, 0L);
        snapshot.message = transientMessage;
        return snapshot;
    }

    private void startSensorService(String token) {
        Intent intent = new Intent(this, WatchSensorService.class);
        intent.putExtra(KEY_API_BASE, apiBase());
        intent.putExtra(KEY_TOKEN, token);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopSensorService() {
        stopService(new Intent(this, WatchSensorService.class));
        prefs.edit().putBoolean(KEY_STREAMING, false).apply();
    }

    private String apiBase() {
        return prefs.getString(KEY_API_BASE, getString(R.string.default_api_base));
    }

    private void requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Build.VERSION.SDK_INT >= 36
                ? "android.permission.health.READ_HEART_RATE"
                : Manifest.permission.BODY_SENSORS);
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(missing, Manifest.permission.ACTIVITY_RECOGNITION);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), 11);
        }
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    private FrameLayout.LayoutParams centerInputLayout() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34),
                Gravity.CENTER
        );
        lp.setMargins(dp(62), 0, dp(62), 0);
        return lp;
    }

    private LinearLayout.LayoutParams controlButtonLayout() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private Button compactButton() {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setTextSize(9);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private GradientDrawable round(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, stroke);
        }
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int color(int resId) {
        return getResources().getColor(resId, getTheme());
    }

    private String shortMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.length() == 0) return "unknown";
        return message.length() > 58 ? message.substring(0, 58) : message;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class WatchSnapshot {
        boolean connected;
        boolean streaming;
        boolean backupVisible;
        boolean hasGps;
        String courseName = "";
        String route = "";
        String message = "";
        float courseKm;
        int courseElev;
        int hr;
        int battery = -1;
        int alt;
        int acc;
        int heading = -1;
        float progress;
        int distressLevel;
        long lastUploadAt;
    }

    private static final class WatchFaceView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private WatchSnapshot snapshot = new WatchSnapshot();

        WatchFaceView(Context context) {
            super(context);
        }

        void setSnapshot(WatchSnapshot snapshot) {
            this.snapshot = snapshot;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float s = Math.min(w, h);
            float ox = (w - s) / 2f;
            float oy = (h - s) / 2f;

            drawMapBase(canvas, ox, oy, s);
            drawRoute(canvas, ox, oy, s);
            drawHeader(canvas, ox, oy, s);
            drawGuidance(canvas, ox, oy, s);
            drawMetrics(canvas, ox, oy, s);
            drawFooter(canvas, ox, oy, s);
        }

        private void drawMapBase(Canvas canvas, float ox, float oy, float s) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF071B11);
            canvas.drawCircle(ox + s / 2f, oy + s / 2f, s / 2f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(s * 0.006f);
            paint.setColor(0x553B7650);
            for (int i = 0; i < 5; i++) {
                path.reset();
                float y = oy + s * (0.17f + i * 0.15f);
                path.moveTo(ox + s * 0.05f, y);
                path.cubicTo(ox + s * 0.22f, y - s * 0.08f, ox + s * 0.52f,
                        y + s * 0.08f, ox + s * 0.95f, y - s * 0.05f);
                canvas.drawPath(path, paint);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.009f);
            paint.setColor(0x66316B4F);
            canvas.drawCircle(ox + s / 2f, oy + s / 2f, s * 0.405f, paint);
            canvas.drawCircle(ox + s / 2f, oy + s / 2f, s * 0.275f, paint);
        }

        private void drawRoute(Canvas canvas, float ox, float oy, float s) {
            path.reset();
            path.moveTo(ox + s * 0.24f, oy + s * 0.80f);
            path.cubicTo(ox + s * 0.33f, oy + s * 0.68f, ox + s * 0.42f,
                    oy + s * 0.66f, ox + s * 0.50f, oy + s * 0.53f);
            path.cubicTo(ox + s * 0.58f, oy + s * 0.40f, ox + s * 0.68f,
                    oy + s * 0.34f, ox + s * 0.80f, oy + s * 0.20f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(s * 0.052f);
            paint.setColor(0xAA0B2417);
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(s * 0.024f);
            paint.setColor(0xFF74C69D);
            canvas.drawPath(path, paint);

            float progress = Math.max(0f, Math.min(1f, snapshot.progress));
            if (progress > 0f) {
                path.reset();
                path.moveTo(ox + s * 0.24f, oy + s * 0.80f);
                path.cubicTo(ox + s * (0.24f + 0.26f * progress), oy + s * (0.80f - 0.27f * progress),
                        ox + s * (0.34f + 0.23f * progress), oy + s * (0.72f - 0.21f * progress),
                        ox + s * (0.50f + 0.30f * progress), oy + s * (0.53f - 0.33f * progress));
                paint.setStrokeWidth(s * 0.026f);
                paint.setColor(0xFFD7F9B1);
                canvas.drawPath(path, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFF4F7F2);
            path.reset();
            path.moveTo(ox + s * 0.505f, oy + s * 0.465f);
            path.lineTo(ox + s * 0.535f, oy + s * 0.535f);
            path.lineTo(ox + s * 0.505f, oy + s * 0.515f);
            path.lineTo(ox + s * 0.475f, oy + s * 0.535f);
            path.close();
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.009f);
            paint.setColor(snapshot.streaming ? 0xFFB7E4C7 : 0x5574C69D);
            canvas.drawArc(ox + s * 0.055f, oy + s * 0.055f, ox + s * 0.945f, oy + s * 0.945f,
                    -90, Math.max(24f, 360f * progress), false, paint);
        }

        private void drawHeader(Canvas canvas, float ox, float oy, float s) {
            String title = snapshot.courseName.length() > 0 ? snapshot.courseName : "숲길동무";
            drawTextFit(canvas, title, ox + s * 0.50f, oy + s * 0.185f,
                    s * 0.061f, s * 0.54f, 0xFFF4F7F2, true);

            String state;
            int stateColor;
            if (snapshot.streaming) {
                state = ageText();
                stateColor = 0xFFB7E4C7;
            } else if (snapshot.connected) {
                state = "연결됨 · 전송 대기";
                stateColor = 0xFFD7F9B1;
            } else if (snapshot.backupVisible) {
                state = "백업 연결";
                stateColor = 0xFFFFD166;
            } else {
                state = "휴대폰 산행 대기";
                stateColor = 0xFFB7E4C7;
            }
            drawPill(canvas, ox + s * 0.315f, oy + s * 0.218f,
                    ox + s * 0.685f, oy + s * 0.288f, 0xCC0B2417, 0x3315B87A);
            drawTextFit(canvas, state, ox + s * 0.50f, oy + s * 0.265f,
                    s * 0.033f, s * 0.31f, stateColor, true);
        }

        private void drawGuidance(Canvas canvas, float ox, float oy, float s) {
            drawPill(canvas, ox + s * 0.255f, oy + s * 0.335f,
                    ox + s * 0.745f, oy + s * 0.535f, 0xE506140D, 0x55B7E4C7);

            drawTextFit(canvas, arrowForHeading(), ox + s * 0.355f, oy + s * 0.455f,
                    s * 0.085f, s * 0.12f, 0xFFD7F9B1, true);
            drawTextFit(canvas, nextDistanceText(), ox + s * 0.555f, oy + s * 0.430f,
                    s * 0.056f, s * 0.28f, 0xFFF4F7F2, true);
            drawTextFit(canvas, routeText(), ox + s * 0.555f, oy + s * 0.482f,
                    s * 0.033f, s * 0.28f, 0xDDF4F7F2, true);
            drawTextFit(canvas, safetyText(), ox + s * 0.50f, oy + s * 0.520f,
                    s * 0.030f, s * 0.39f, snapshot.distressLevel > 0 ? 0xFFFF9FA7 : 0xFFFFD166, true);
        }

        private void drawMetrics(Canvas canvas, float ox, float oy, float s) {
            drawMetric(canvas, ox + s * 0.205f, oy + s * 0.615f,
                    s * 0.18f, s * 0.128f, hrText(), "bpm", 0xFFFF9FA7);
            drawMetric(canvas, ox + s * 0.795f, oy + s * 0.615f,
                    s * 0.18f, s * 0.128f, compassText(), degreeText(), 0xFFD7F9B1);

            drawPill(canvas, ox + s * 0.315f, oy + s * 0.600f,
                    ox + s * 0.685f, oy + s * 0.720f, 0xCC0B2417, 0x33B7E4C7);
            drawTextFit(canvas, remainingText(), ox + s * 0.50f, oy + s * 0.655f,
                    s * 0.042f, s * 0.30f, 0xFFF4F7F2, true);
            drawTextFit(canvas, altitudeText(), ox + s * 0.50f, oy + s * 0.695f,
                    s * 0.030f, s * 0.30f, 0xCCF4F7F2, true);

            drawMetric(canvas, ox + s * 0.205f, oy + s * 0.755f,
                    s * 0.18f, s * 0.105f, snapshot.hasGps ? "GPS" : "--", snapshot.hasGps ? "고정" : "대기", 0xFFB7E4C7);
            drawMetric(canvas, ox + s * 0.795f, oy + s * 0.755f,
                    s * 0.18f, s * 0.105f, batteryText(), "배터리", 0xFFF4F7F2);
        }

        private void drawFooter(Canvas canvas, float ox, float oy, float s) {
            String footer = snapshot.message;
            if (footer.length() == 0) {
                footer = snapshot.connected
                        ? "워치 센서가 산행 기록에 저장됩니다"
                        : "백업 코드는 직접 설치 연결에 사용";
            }
            drawTextFit(canvas, footer, ox + s * 0.50f, oy + s * 0.845f,
                    s * 0.030f, s * 0.58f, 0xDDF4F7F2, true);
        }

        private String nextDistanceText() {
            if (!snapshot.connected) return "폰 산행 대기";
            if (snapshot.courseKm <= 0f) return "경로 수신 중";
            float left = Math.max(0f, snapshot.courseKm * (1f - snapshot.progress));
            if (left >= 1f) return String.format("%.1f km 남음", left);
            return Math.max(20, Math.round(left * 1000f)) + " m 남음";
        }

        private String remainingText() {
            if (snapshot.courseKm <= 0f) return snapshot.streaming ? "전송 중" : "경로 대기";
            return String.format("%.1f / %.1f km", snapshot.courseKm * snapshot.progress, snapshot.courseKm);
        }

        private String routeText() {
            if (snapshot.route.length() == 0) return snapshot.connected ? "산행 코스" : "휴대폰 앱과 동기화";
            String[] stops = snapshot.route.split("→");
            return stops.length > 0 ? stops[Math.min(1, stops.length - 1)].trim() + " 방향" : snapshot.route;
        }

        private String safetyText() {
            if (snapshot.distressLevel >= 2) return "조난 위험 · 즉시 확인";
            if (snapshot.distressLevel == 1) return "이동 정지 주의";
            if (!snapshot.connected) return "연결되면 심박·GPS 표시";
            return snapshot.acc > 0 ? "움직임 감지 · 정상" : "코스 정상";
        }

        private String altitudeText() {
            if (snapshot.alt > 0) return "고도 " + snapshot.alt + " m";
            if (snapshot.courseElev > 0) return "정상 " + snapshot.courseElev + " m";
            return "고도 대기";
        }

        private String hrText() {
            return snapshot.hr > 0 ? String.valueOf(snapshot.hr) : "--";
        }

        private String batteryText() {
            return snapshot.battery >= 0 ? snapshot.battery + "%" : "--";
        }

        private String compassText() {
            if (snapshot.heading < 0) return "NE";
            String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            int index = Math.round(snapshot.heading / 45f) % points.length;
            return points[index];
        }

        private String degreeText() {
            return snapshot.heading >= 0 ? snapshot.heading + "°" : "312°";
        }

        private String arrowForHeading() {
            if (snapshot.heading < 0) return "↗";
            int sector = Math.round(snapshot.heading / 45f) % 8;
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            return arrows[sector];
        }

        private String ageText() {
            if (snapshot.lastUploadAt <= 0L) return "센서 전송 중";
            long age = Math.max(0L, (System.currentTimeMillis() - snapshot.lastUploadAt) / 1000L);
            return age <= 3 ? "방금 전송" : age + "초 전송";
        }

        private void drawMetric(Canvas canvas, float cx, float cy, float width, float height,
                                String value, String label, int valueColor) {
            drawPill(canvas, cx - width / 2f, cy - height / 2f,
                    cx + width / 2f, cy + height / 2f, 0xCC0B2417, 0x33B7E4C7);
            drawTextFit(canvas, value, cx, cy - height * 0.02f,
                    height * 0.42f, width * 0.74f, valueColor, true);
            drawTextFit(canvas, label, cx, cy + height * 0.32f,
                    height * 0.23f, width * 0.78f, 0xBBF4F7F2, true);
        }

        private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                              int fill, int stroke) {
            rect.set(left, top, right, bottom);
            float radius = Math.min(rect.width(), rect.height()) * 0.34f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (stroke != 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, rect.height() * 0.025f));
                paint.setColor(stroke);
                canvas.drawRoundRect(rect, radius, radius, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        private void drawTextFit(Canvas canvas, String text, float x, float baseline,
                                 float textSize, float maxWidth, int color, boolean bold) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(textSize);
            float measured = paint.measureText(text);
            if (measured > maxWidth) {
                paint.setTextSize(textSize * maxWidth / measured);
            }
            canvas.drawText(text, x, baseline, paint);
        }
    }
}
