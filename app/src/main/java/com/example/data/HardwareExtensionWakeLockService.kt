package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HardwareExtensionWakeLockService : Service() {

    private val CHANNEL_ID = "HardwareExtensionWakeLockChannel"
    private val NOTIFICATION_ID = 202

    companion object {
        private val _isCpuLockHeld = MutableStateFlow(false)
        val isCpuLockHeld: StateFlow<Boolean> = _isCpuLockHeld.asStateFlow()

        private val _isWifiLockHeld = MutableStateFlow(false)
        val isWifiLockHeld: StateFlow<Boolean> = _isWifiLockHeld.asStateFlow()

        private val _activeTags = MutableStateFlow<List<String>>(emptyList())
        val activeTags: StateFlow<List<String>> = _activeTags.asStateFlow()

        fun start(context: Context, tag: String) {
            val currentTags = _activeTags.value.toMutableList()
            if (!currentTags.contains(tag)) {
                currentTags.add(tag)
                _activeTags.value = currentTags
            }
            val intent = Intent(context, HardwareExtensionWakeLockService::class.java).apply {
                putExtra("action", "acquire")
                putExtra("tag", tag)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context, tag: String) {
            val currentTags = _activeTags.value.toMutableList()
            currentTags.remove(tag)
            _activeTags.value = currentTags

            if (currentTags.isEmpty()) {
                val intent = Intent(context, HardwareExtensionWakeLockService::class.java).apply {
                    putExtra("action", "release_all")
                }
                context.startService(intent)
            } else {
                val intent = Intent(context, HardwareExtensionWakeLockService::class.java).apply {
                    putExtra("action", "release")
                    putExtra("tag", tag)
                }
                context.startService(intent)
            }
        }

        fun clearAll(context: Context) {
            _activeTags.value = emptyList()
            val intent = Intent(context, HardwareExtensionWakeLockService::class.java).apply {
                putExtra("action", "release_all")
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HardwareExtension::CpuWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "HardwareExtension::WifiWakeLock"
            )
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL,
                "HardwareExtension::WifiWakeLock"
            )
        }.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "acquire"
        val tag = intent?.getStringExtra("tag") ?: "default_task"

        when (action) {
            "acquire" -> {
                acquireLocks()
            }
            "release" -> {
                if (_activeTags.value.isEmpty()) {
                    releaseLocks()
                    stopSelf()
                }
            }
            "release_all" -> {
                releaseLocks()
                stopSelf()
            }
        }

        val activeCount = _activeTags.value.size
        val tagsString = if (_activeTags.value.isEmpty()) "Active" else _activeTags.value.joinToString(", ")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hardware Extension Shield Active")
            .setContentText("Keeping Wi-Fi & CPU awake for: $tagsString")
            .setSubText("$activeCount active locks")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L) // Safe maximum limit of 10 minutes per acquire to avoid infinite drain
                _isCpuLockHeld.value = true
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
                _isWifiLockHeld.value = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            _isCpuLockHeld.value = false

            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            _isWifiLockHeld.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hardware Extension Wake Lock Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
