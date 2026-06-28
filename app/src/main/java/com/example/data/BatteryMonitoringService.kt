package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BatteryMonitoringService : Service() {

    private var batteryReceiver: BroadcastReceiver? = null
    private var lastNotifiedLevel: Int? = null

    companion object {
        private val _batteryLevel = MutableStateFlow(100)
        val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

        private val _chargingState = MutableStateFlow("Discharging")
        val chargingState: StateFlow<String> = _chargingState.asStateFlow()

        private val _temperature = MutableStateFlow(25.0f) // in Celsius
        val temperature: StateFlow<Float> = _temperature.asStateFlow()

        private val _voltage = MutableStateFlow(4000) // in mV
        val voltage: StateFlow<Int> = _voltage.asStateFlow()

        private val _health = MutableStateFlow("Good")
        val health: StateFlow<String> = _health.asStateFlow()

        val notificationThreshold = MutableStateFlow(20) // Default 20%
        val isServiceRunning = MutableStateFlow(false)
        
        // Callback to viewmodel for audit logging
        var onBatteryUpdateCallback: ((Int, String) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning.value = true
        createNotificationChannel()
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring active", "Exposing battery telemetry to desktop API")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        isServiceRunning.value = false
        unregisterBatteryReceiver()
        super.onDestroy()
    }

    private val NOTIFICATION_ID = 404
    private val CHANNEL_ID = "battery_monitoring_channel"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Battery Telemetry Service"
            val descriptionText = "Monitors real-time battery status and alerts below threshold"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun triggerThresholdNotification(currentLevel: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Battery Threshold Alert")
            .setContentText("Warning: Battery level dropped to $currentLevel% (Threshold: ${notificationThreshold.value}%)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID + 1, alertNotification)
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val pct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
                    
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val statusStr = if (isCharging) "Charging" else "Discharging"

                    val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
                    val volt = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                    val healthInt = it.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                    val healthStr = when (healthInt) {
                        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                        else -> "Unknown"
                    }

                    _batteryLevel.value = pct
                    _chargingState.value = statusStr
                    _temperature.value = temp
                    _voltage.value = volt
                    _health.value = healthStr

                    // Check notification threshold
                    val thresh = notificationThreshold.value
                    if (pct <= thresh && statusStr == "Discharging") {
                        if (lastNotifiedLevel == null || pct < lastNotifiedLevel!!) {
                            triggerThresholdNotification(pct)
                            lastNotifiedLevel = pct
                        }
                    } else {
                        // Reset notification latch if charging or above threshold
                        if (pct > thresh || statusStr == "Charging") {
                            lastNotifiedLevel = null
                        }
                    }

                    // Trigger callback to Viewmodel for real-time audit logging and exposing to API
                    onBatteryUpdateCallback?.invoke(pct, statusStr)
                }
            }
        }
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            unregisterReceiver(it)
            batteryReceiver = null
        }
    }
}
