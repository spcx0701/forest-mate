package kr.forestmate.watch;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class WatchSensorService extends Service implements SensorEventListener, LocationListener {
    private static final String CHANNEL_ID = "forestmate_watch";
    private static final int NOTIFICATION_ID = 1729;
    private static final long UPLOAD_INTERVAL_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uploadRunnable = new Runnable() {
        @Override
        public void run() {
            uploadSnapshot();
            handler.postDelayed(this, UPLOAD_INTERVAL_MS);
        }
    };

    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private String apiBase;
    private String token;
    private Integer lastHr;
    private Integer lastAcc;
    private Integer lastBattery;
    private Double lastLat;
    private Double lastLon;
    private boolean uploading;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        apiBase = stringExtra(intent, MainActivity.KEY_API_BASE,
                prefs.getString(MainActivity.KEY_API_BASE, getString(R.string.default_api_base)));
        token = stringExtra(intent, MainActivity.KEY_TOKEN, prefs.getString(MainActivity.KEY_TOKEN, ""));
        if (token == null || token.length() == 0) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!startAsForeground("센서 준비 중")) {
            stopSelf();
            return START_NOT_STICKY;
        }
        registerSensors();
        handler.removeCallbacks(uploadRunnable);
        handler.post(uploadRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(uploadRunnable);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE && event.values.length > 0) {
            int hr = Math.round(event.values[0]);
            if (hr >= 20 && hr <= 250) {
                lastHr = hr;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && event.values.length >= 3) {
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];
            double magnitude = Math.sqrt(x * x + y * y + z * z);
            lastAcc = Math.min(5000, Math.max(0, (int) Math.round(magnitude * 100)));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        lastLat = location.getLatitude();
        lastLon = location.getLongitude();
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    private void registerSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            if (hasHeartPermission()) {
                Sensor heart = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                if (heart != null) {
                    sensorManager.registerListener(this, heart, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return;
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;
        requestLocation(LocationManager.GPS_PROVIDER);
        requestLocation(LocationManager.NETWORK_PROVIDER);
    }

    private void requestLocation(String provider) {
        try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, 5000L, 2f, this);
            }
        } catch (IllegalArgumentException | SecurityException ignored) {
        }
    }

    private void uploadSnapshot() {
        if (uploading || token == null || token.length() == 0) return;
        updateBattery();
        final Integer hr = lastHr;
        final Double lat = lastLat;
        final Double lon = lastLon;
        final Integer acc = lastAcc;
        final Integer battery = lastBattery;
        if (hr == null && lat == null && lon == null && acc == null) return;

        uploading = true;
        new Thread(() -> {
            try {
                WatchApi.upload(apiBase, token, hr, lat, lon, acc, battery);
                handler.post(() -> updateNotification("전송됨"
                        + (hr != null ? " · " + hr + "bpm" : "")));
            } catch (Exception ex) {
                handler.post(() -> updateNotification("전송 대기 중"));
            } finally {
                uploading = false;
            }
        }).start();
    }

    private boolean startAsForeground(String text) {
        Notification notification = buildNotification(text);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                int type = 0;
                if (hasHeartPermission()) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
                }
                if (type == 0) return false;
                startForeground(NOTIFICATION_ID, notification, type);
            } else if (Build.VERSION.SDK_INT >= 29
                    && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(R.drawable.ic_watch)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void updateBattery() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            lastBattery = Math.min(100, Math.max(0, Math.round(level * 100f / scale)));
        }
    }

    private boolean hasHeartPermission() {
        if (Build.VERSION.SDK_INT >= 36) {
            return hasPermission("android.permission.health.READ_HEART_RATE");
        }
        return hasPermission(Manifest.permission.BODY_SENSORS);
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String stringExtra(Intent intent, String key, String fallback) {
        if (intent == null) return fallback;
        String value = intent.getStringExtra(key);
        return value == null || value.length() == 0 ? fallback : value;
    }
}
