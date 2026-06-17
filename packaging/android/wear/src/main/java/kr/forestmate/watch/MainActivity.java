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
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final String PREFS = "forestmate_watch";
    static final String KEY_API_BASE = "apiBase";
    static final String KEY_TOKEN = "watchToken";
    static final String KEY_HIKE_ID = "hikeId";
    static final String KEY_COURSE_ID = "courseId";

    private SharedPreferences prefs;
    private EditText codeInput;
    private TextView status;
    private Button pairButton;
    private Button stopButton;

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
        renderSavedState();
    }

    private FrameLayout buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(R.color.forest_deep));

        root.addView(new TrailMapView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        codeInput = new EditText(this);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setTextColor(color(R.color.forest_text));
        codeInput.setHintTextColor(0x88F4F7F2);
        codeInput.setTextSize(18);
        codeInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeInput.setHint("백업 코드");
        codeInput.setSingleLine(true);
        codeInput.setIncludeFontPadding(false);
        codeInput.setMinHeight(0);
        codeInput.setMinimumHeight(0);
        codeInput.setPadding(dp(8), 0, dp(8), 0);
        codeInput.setBackground(round(0x2215B87A, color(R.color.forest_mint), dp(1)));
        codeInput.setVisibility(View.GONE);
        root.addView(codeInput, frameLpFixedHeight(dp(48), dp(125), dp(48), dp(28)));

        pairButton = new Button(this);
        pairButton.setAllCaps(false);
        pairButton.setText("시작");
        pairButton.setTextColor(color(R.color.forest_deep));
        pairButton.setTextSize(8);
        pairButton.setTypeface(Typeface.DEFAULT_BOLD);
        tuneCompactButton(pairButton);
        pairButton.setBackground(round(color(R.color.forest_mint), 0x00000000, 0));
        pairButton.setOnClickListener(v -> handlePairOrStart());
        pairButton.setOnLongClickListener(v -> {
            showBackupPairing();
            return true;
        });

        stopButton = new Button(this);
        stopButton.setAllCaps(false);
        stopButton.setText("SOS");
        stopButton.setTextColor(color(R.color.forest_text));
        stopButton.setTextSize(8);
        stopButton.setTypeface(Typeface.DEFAULT_BOLD);
        tuneCompactButton(stopButton);
        stopButton.setBackground(round(0x3315B87A, 0x44B7E4C7, dp(1)));
        stopButton.setOnClickListener(v -> status.setText("SOS는 길게 눌러 전송합니다."));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(pairButton, weightBox());
        controls.addView(stopButton, weightBox());
        FrameLayout.LayoutParams controlsLp = frameLp(dp(46), 0, dp(46), dp(16));
        controlsLp.gravity = Gravity.BOTTOM;
        root.addView(controls, controlsLp);

        status = new TextView(this);
        status.setTextColor(color(R.color.forest_text));
        status.setTextSize(8);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0, 1.15f);
        status.setSingleLine(false);
        status.setIncludeFontPadding(false);
        status.setBackground(round(0xEE0B2417, 0x44B7E4C7, dp(1)));
        status.setTextColor(0xFFE6F6EA);
        status.setPadding(dp(7), dp(4), dp(7), dp(4));
        status.setVisibility(View.GONE);
        root.addView(status, frameLpFixedHeight(dp(42), dp(106), dp(42), dp(26)));

        return root;
    }

    private void handlePairOrStart() {
        String savedToken = prefs.getString(KEY_TOKEN, "");
        if (savedToken.length() > 0) {
            startSensorService(savedToken);
            status.setVisibility(View.VISIBLE);
            status.setText("센서 전송 중입니다.");
            return;
        }

        if (codeInput.getVisibility() != View.VISIBLE) {
            status.setVisibility(View.VISIBLE);
            status.setText("휴대폰 산행 대기 중");
            return;
        }

        String code = codeInput.getText().toString().trim();
        if (code.length() != 6) {
            status.setVisibility(View.VISIBLE);
            status.setText("6자리 코드를 입력하세요");
            return;
        }

        pairButton.setEnabled(false);
        status.setText("페어링 중...");
        new Thread(() -> {
            try {
                WatchApi.ClaimResult result = WatchApi.claim(apiBase(), code);
                prefs.edit()
                        .putString(KEY_TOKEN, result.watchToken)
                        .putString(KEY_HIKE_ID, result.hikeId)
                        .putString(KEY_COURSE_ID, result.courseId)
                        .apply();
                runOnUiThread(() -> {
                    pairButton.setEnabled(true);
                    codeInput.setText("");
                    codeInput.setVisibility(View.GONE);
                    renderSavedState();
                    startSensorService(result.watchToken);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    pairButton.setEnabled(true);
                    status.setVisibility(View.VISIBLE);
                    status.setText("연결 실패: " + shortMessage(ex));
                });
            }
        }).start();
    }

    private void showBackupPairing() {
        codeInput.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        pairButton.setText("연결");
        status.setText("백업 코드 6자리 입력");
    }

    private void disconnect() {
        stopService(new Intent(this, WatchSensorService.class));
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_HIKE_ID)
                .remove(KEY_COURSE_ID)
                .apply();
        renderSavedState();
    }

    private void renderSavedState() {
        String token = prefs.getString(KEY_TOKEN, "");
        if (token.length() > 0) {
            pairButton.setText("공유");
            status.setVisibility(View.VISIBLE);
            status.setText("산행 기록 동기화됨\n" + prefs.getString(KEY_COURSE_ID, ""));
        } else {
            pairButton.setText("시작");
            status.setVisibility(View.GONE);
        }
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

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private FrameLayout.LayoutParams frameLp(int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(left, top, right, bottom);
        return lp;
    }

    private FrameLayout.LayoutParams frameLpFixedHeight(int left, int top, int right, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        );
        lp.setMargins(left, top, right, 0);
        return lp;
    }

    private LinearLayout.LayoutParams weightBox() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(25), 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private void tuneCompactButton(Button button) {
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(color(R.color.forest_text));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout metric(String value, String label, int textColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2), dp(4), dp(2), dp(4));
        box.setBackground(round(0xCC0B2417, 0x22B7E4C7, dp(1)));
        TextView v = label(value, 13, true);
        v.setTextColor(textColor);
        TextView l = label(label, 8, true);
        l.setTextColor(0xAAF4F7F2);
        box.addView(v, matchWidth());
        box.addView(l, matchWidth());
        return box;
    }

    private GradientDrawable round(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(18));
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
        return message.length() > 70 ? message.substring(0, 70) : message;
    }

    private static final class TrailMapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        TrailMapView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float s = Math.min(w, h);
            float ox = (w - s) / 2f;
            float oy = (h - s) / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF071B11);
            canvas.drawCircle(w / 2f, h / 2f, s / 2f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(s * 0.006f);
            paint.setColor(0x663B7650);
            for (int i = 0; i < 4; i++) {
                path.reset();
                float y = oy + s * (0.2f + i * 0.18f);
                path.moveTo(ox - s * 0.1f, y);
                path.cubicTo(ox + s * 0.2f, y - s * 0.11f, ox + s * 0.45f,
                        y + s * 0.07f, ox + s * 1.1f, y - s * 0.08f);
                canvas.drawPath(path, paint);
            }

            path.reset();
            path.moveTo(ox + s * 0.30f, oy + s * 0.86f);
            path.cubicTo(ox + s * 0.33f, oy + s * 0.68f, ox + s * 0.48f,
                    oy + s * 0.60f, ox + s * 0.55f, oy + s * 0.50f);
            path.cubicTo(ox + s * 0.64f, oy + s * 0.38f, ox + s * 0.68f,
                    oy + s * 0.22f, ox + s * 0.86f, oy + s * 0.10f);
            paint.setStrokeWidth(s * 0.045f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xAA1C3828);
            canvas.drawPath(path, paint);
            paint.setStrokeWidth(s * 0.020f);
            paint.setColor(0xFF74C69D);
            canvas.drawPath(path, paint);
            paint.setColor(0xFFD7F9B1);
            paint.setStrokeWidth(s * 0.022f);
            path.reset();
            path.moveTo(ox + s * 0.30f, oy + s * 0.86f);
            path.cubicTo(ox + s * 0.33f, oy + s * 0.68f, ox + s * 0.48f,
                    oy + s * 0.60f, ox + s * 0.55f, oy + s * 0.50f);
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFF4F7F2);
            path.reset();
            path.moveTo(ox + s * 0.55f, oy + s * 0.45f);
            path.lineTo(ox + s * 0.58f, oy + s * 0.53f);
            path.lineTo(ox + s * 0.55f, oy + s * 0.51f);
            path.lineTo(ox + s * 0.52f, oy + s * 0.53f);
            path.close();
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(s * 0.007f);
            paint.setColor(0xFFB7E4C7);
            canvas.drawArc(ox + s * 0.03f, oy + s * 0.03f, ox + s * 0.97f, oy + s * 0.97f,
                    -90, 130, false, paint);

            drawOverlay(canvas, ox, oy, s);
        }

        private void drawOverlay(Canvas canvas, float ox, float oy, float s) {
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);

            drawTextFit(canvas, "북한산 백운대", ox + s * 0.50f, oy + s * 0.22f,
                    s * 0.064f, s * 0.54f, 0xFFF4F7F2, true);
            drawPill(canvas, ox + s * 0.32f, oy + s * 0.255f,
                    ox + s * 0.68f, oy + s * 0.325f, 0xAA0B2F1D, 0x3315B87A);
            drawTextFit(canvas, "경로 수신 36%", ox + s * 0.50f, oy + s * 0.303f,
                    s * 0.035f, s * 0.30f, 0xFFB7E4C7, true);

            drawPill(canvas, ox + s * 0.29f, oy + s * 0.365f,
                    ox + s * 0.71f, oy + s * 0.555f, 0xD906140D, 0x55B7E4C7);
            drawTextFit(canvas, "↗", ox + s * 0.365f, oy + s * 0.460f,
                    s * 0.080f, s * 0.11f, 0xFFD7F9B1, true);
            drawTextFit(canvas, "220m 우측", ox + s * 0.53f, oy + s * 0.445f,
                    s * 0.060f, s * 0.29f, 0xFFF4F7F2, true);
            drawTextFit(canvas, "백운대 방향", ox + s * 0.53f, oy + s * 0.500f,
                    s * 0.034f, s * 0.29f, 0xCCF4F7F2, true);
            drawTextFit(canvas, "코스 8m · 정상", ox + s * 0.50f, oy + s * 0.535f,
                    s * 0.032f, s * 0.34f, 0xFFFFD166, true);

            drawMetric(canvas, ox + s * 0.19f, oy + s * 0.585f,
                    s * 0.18f, s * 0.135f, "139", "bpm", 0xFFFF9FA7);
            drawMetric(canvas, ox + s * 0.81f, oy + s * 0.585f,
                    s * 0.18f, s * 0.135f, "NE", "312°", 0xFFD7F9B1);
            drawPill(canvas, ox + s * 0.31f, oy + s * 0.645f,
                    ox + s * 0.69f, oy + s * 0.755f, 0xCC0B2417, 0x33B7E4C7);
            drawTextFit(canvas, "2.7 km 남음", ox + s * 0.50f, oy + s * 0.695f,
                    s * 0.046f, s * 0.31f, 0xFFF4F7F2, true);
            drawTextFit(canvas, "고도 612 m", ox + s * 0.50f, oy + s * 0.735f,
                    s * 0.031f, s * 0.31f, 0xCCF4F7F2, true);
        }

        private void drawMetric(Canvas canvas, float cx, float cy, float width, float height,
                                String value, String label, int valueColor) {
            drawPill(canvas, cx - width / 2f, cy - height / 2f,
                    cx + width / 2f, cy + height / 2f, 0xCC0B2417, 0x33B7E4C7);
            drawTextFit(canvas, value, cx, cy - height * 0.02f,
                    height * 0.43f, width * 0.72f, valueColor, true);
            drawTextFit(canvas, label, cx, cy + height * 0.32f,
                    height * 0.24f, width * 0.72f, 0xBBF4F7F2, true);
        }

        private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                              int fill, int stroke) {
            rect.set(left, top, right, bottom);
            float radius = Math.min(rect.width(), rect.height()) * 0.36f;
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
