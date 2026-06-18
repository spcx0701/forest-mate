package kr.forestmate.watch

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kr.forestmate.core.api.ApiConfig
import kr.forestmate.core.api.UrlConnectionTransport
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WatchSensorService : Service(), SensorEventListener, LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private val uploadRunnable = Runnable {
        uploadTick()
    }

    private lateinit var prefs: SharedPreferences
    private var sensorManager: SensorManager? = null
    private var locationManager: LocationManager? = null
    private var apiBase: String = ""
    private var token: String = ""
    private var lastHr: Int? = null
    private var lastAcc: Int? = null
    private var lastBattery: Int? = null
    private var lastAlt: Int? = null
    private var lastHeading: Int? = null
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var uploading = false
    private var lastPersistAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        apiBase = stringExtra(
            intent,
            MainActivity.KEY_API_BASE,
            prefs.getString(MainActivity.KEY_API_BASE, getString(R.string.default_api_base))
                ?: getString(R.string.default_api_base),
        )
        token = stringExtra(intent, MainActivity.KEY_TOKEN, prefs.getString(MainActivity.KEY_TOKEN, "").orEmpty())
        if (token.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startAsForeground("센서 준비 중")) {
            stopSelf()
            return START_NOT_STICKY
        }
        prefs.edit().putBoolean(MainActivity.KEY_STREAMING, true).apply()
        registerSensors()
        handler.removeCallbacks(uploadRunnable)
        handler.post(uploadRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(uploadRunnable)
        sensorManager?.unregisterListener(this)
        try {
            locationManager?.removeUpdates(this)
        } catch (_: SecurityException) {
            locationManager = null
        }
        prefs.edit().putBoolean(MainActivity.KEY_STREAMING, false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                if (event.values.isNotEmpty()) {
                    val hr = event.values[0].roundToInt()
                    if (hr in 20..250) lastHr = hr
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (event.values.size >= 3) {
                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val z = event.values[2].toDouble()
                    val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
                    lastAcc = min(5000, max(0, (magnitude * 100).roundToInt()))
                }
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                if (event.values.size >= 3) {
                    val rotation = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    val heading = Math.toDegrees(orientation[0].toDouble()).roundToInt()
                    lastHeading = (heading + 360) % 360
                }
            }
        }
        persistSensorSnapshot(force = false)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy changes are not surfaced in the watch UI.
    }

    override fun onLocationChanged(location: Location) {
        lastLat = location.latitude
        lastLon = location.longitude
        if (location.hasAltitude()) {
            lastAlt = location.altitude.roundToInt()
        }
        persistSensorSnapshot(force = true)
    }

    override fun onProviderEnabled(provider: String) {
        // The next location callback refreshes the watch snapshot.
    }

    override fun onProviderDisabled(provider: String) {
        // Existing coordinates remain as the last known trail position.
    }

    private fun uploadTick() {
        uploadSnapshot()
        handler.postDelayed(uploadRunnable, UPLOAD_INTERVAL_MS)
    }

    private fun registerSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        sensorManager?.let { manager ->
            if (hasHeartPermission()) {
                manager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
                    manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
        requestLocation(LocationManager.GPS_PROVIDER)
        requestLocation(LocationManager.NETWORK_PROVIDER)
    }

    private fun requestLocation(provider: String) {
        val manager = locationManager ?: return
        try {
            if (manager.isProviderEnabled(provider)) {
                manager.requestLocationUpdates(provider, 5000L, 2f, this)
            }
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: SecurityException) {
            return
        }
    }

    private fun uploadSnapshot() {
        if (uploading || token.isBlank()) return
        updateBattery()
        persistSensorSnapshot(force = true)
        val hr = lastHr
        val lat = lastLat
        val lon = lastLon
        val alt = lastAlt
        val acc = lastAcc
        val battery = lastBattery
        if (hr == null && lat == null && lon == null && acc == null) return

        uploading = true
        Thread {
            try {
                val result = WatchApi(ApiConfig(apiBase), UrlConnectionTransport()).upload(
                    token,
                    WatchUploadRequest(hr, lat, lon, alt, acc, battery),
                )
                prefs.edit()
                    .putFloat(MainActivity.KEY_LAST_PROGRESS, result.progress.toFloat())
                    .putInt(MainActivity.KEY_LAST_DISTRESS_LEVEL, result.distressLevel)
                    .putLong(MainActivity.KEY_LAST_UPLOAD_AT, System.currentTimeMillis())
                    .apply()
                handler.post { updateNotification("전송됨" + (hr?.let { " · ${it}bpm" } ?: "")) }
            } catch (_: Exception) {
                handler.post { updateNotification("전송 대기 중") }
            } finally {
                uploading = false
            }
        }.start()
    }

    private fun startAsForeground(text: String): Boolean {
        val notification = buildNotification(text)
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                var type = 0
                if (hasHeartPermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                if (type == 0) return false
                startForeground(NOTIFICATION_ID, notification, type)
            } else if (Build.VERSION.SDK_INT >= 29 && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_watch)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.createNotificationChannel(channel)
    }

    private fun updateBattery() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            lastBattery = min(100, max(0, (level * 100f / scale).roundToInt()))
        }
    }

    private fun persistSensorSnapshot(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 1000L) return
        lastPersistAt = now
        prefs.edit().apply {
            putOrRemove(MainActivity.KEY_LAST_HR, lastHr)
            putOrRemove(MainActivity.KEY_LAST_BATTERY, lastBattery)
            putOrRemove(MainActivity.KEY_LAST_ALT, lastAlt)
            putOrRemove(MainActivity.KEY_LAST_ACC, lastAcc)
            putOrRemove(MainActivity.KEY_LAST_HEADING, lastHeading)
            if (lastLat != null) {
                putString(MainActivity.KEY_LAST_LAT, lastLat.toString())
            } else {
                remove(MainActivity.KEY_LAST_LAT)
            }
            if (lastLon != null) {
                putString(MainActivity.KEY_LAST_LON, lastLon.toString())
            } else {
                remove(MainActivity.KEY_LAST_LON)
            }
        }.apply()
    }

    private fun SharedPreferences.Editor.putOrRemove(key: String, value: Int?) {
        if (value == null) {
            remove(key)
        } else {
            putInt(key, value)
        }
    }

    private fun hasHeartPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 36) {
            hasPermission("android.permission.health.READ_HEART_RATE")
        } else {
            hasPermission(Manifest.permission.BODY_SENSORS)
        }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun stringExtra(intent: Intent?, key: String, fallback: String): String {
        val value = intent?.getStringExtra(key)
        return if (value.isNullOrEmpty()) fallback else value
    }

    companion object {
        private const val CHANNEL_ID = "forestmate_watch"
        private const val NOTIFICATION_ID = 1729
        private const val UPLOAD_INTERVAL_MS = 5000L
    }
}
