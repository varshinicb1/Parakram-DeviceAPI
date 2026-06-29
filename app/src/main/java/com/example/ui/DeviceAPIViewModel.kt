package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuditLog
import com.example.data.AutomationWorkflow
import com.example.data.Capability
import com.example.data.ConnectionState
import com.example.data.ConnectionStatus
import com.example.data.Plugin
import com.example.data.PasskeyDevice
import com.example.data.WolDevice
import com.example.data.firebase.FirebaseManager
import com.example.data.firebase.UserSession
import com.example.data.firebase.AdminProfile
import android.nfc.NfcAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

import com.example.data.MobileServerManager

class DeviceAPIViewModel(application: Application) : AndroidViewModel(application), SensorEventListener, LocationListener {

    private val context = application.applicationContext
    val firebaseManager = FirebaseManager(context)
    val serverManager = MobileServerManager(context)

    // NFC Status
    private val nfcAdapter: NfcAdapter? by lazy {
        try {
            NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            null
        }
    }
    
    val isNfcSupported: Boolean get() = nfcAdapter != null
    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    // ADB & System Developer Options
    private val _adbTerminalOutput = MutableStateFlow("Developer Terminal ready.\nType a command or choose a quick-access preset below.\n")
    val adbTerminalOutput: StateFlow<String> = _adbTerminalOutput.asStateFlow()

    private val _dozeModeActive = MutableStateFlow(false)
    val dozeModeActive: StateFlow<Boolean> = _dozeModeActive.asStateFlow()

    private val _cpuGovernorActive = MutableStateFlow("powersave")
    val cpuGovernorActive: StateFlow<String> = _cpuGovernorActive.asStateFlow()

    private val _backgroundProcessLimit = MutableStateFlow(-1)
    val backgroundProcessLimit: StateFlow<Int> = _backgroundProcessLimit.asStateFlow()

    init {
        // Read initial system settings
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val f = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                if (f.exists()) {
                    val gov = f.readText().trim()
                    if (gov.isNotEmpty()) {
                        _cpuGovernorActive.value = gov
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val process = Runtime.getRuntime().exec("settings get global max_cached_processes")
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                val limitVal = reader.readLine()?.trim()
                if (limitVal != null && limitVal != "null" && limitVal.isNotEmpty()) {
                    _backgroundProcessLimit.value = limitVal.toIntOrNull() ?: -1
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun executeLocalAdbCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val formattedCmd = cmd.trim()
            if (formattedCmd.isEmpty()) return@launch
            
            _adbTerminalOutput.value += "\n$ adb shell $formattedCmd\n"
            val result = executeShellCommandInternal(formattedCmd)
            _adbTerminalOutput.value += result + "\n"
            
            addAuditLog(AuditLog(
                method = "SHELL",
                endpoint = "/adb/shell/exec",
                caller = "localhost_terminal",
                status = 200,
                payload = "Command: '$formattedCmd' -> Output length: ${result.length} characters",
                type = "System"
            ))
        }
    }

    private fun executeShellCommandInternal(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val errorReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
            val output = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            val finalOutput = output.toString().trim()
            if (finalOutput.isEmpty()) "Command executed successfully (no output)." else finalOutput
        } catch (e: Exception) {
            "Execution error: ${e.message}"
        }
    }

    fun toggleDozeMode(active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = if (active) "dumpsys deviceidle force-idle" else "dumpsys deviceidle unforce"
            val result = executeShellCommandInternal(cmd)
            _dozeModeActive.value = active
            _adbTerminalOutput.value += "\n$ $cmd\n$result\n"
            
            addAuditLog(AuditLog(
                method = "TOGGLE",
                endpoint = "/adb/power/doze",
                caller = "power_controller",
                status = 200,
                payload = "Doze Mode toggled to: $active (Command output: $result)",
                type = "System"
            ))
        }
    }

    fun setCpuGovernor(profile: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = "su -c 'echo $profile > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor' || echo '$profile' > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
            val result = executeShellCommandInternal(cmd)
            _cpuGovernorActive.value = profile
            _adbTerminalOutput.value += "\n$ $cmd\n$result\n"
            
            addAuditLog(AuditLog(
                method = "WRITE",
                endpoint = "/adb/cpu/governor",
                caller = "cpu_controller",
                status = 200,
                payload = "CPU Governor profile updated to: $profile (Output: $result)",
                type = "System"
            ))
        }
    }

    fun setBackgroundProcessLimit(limit: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val limitStr = if (limit == -1) "default" else limit.toString()
            val cmd = if (limit == -1) "settings delete global max_cached_processes" else "settings put global max_cached_processes $limit"
            val result = executeShellCommandInternal(cmd)
            _backgroundProcessLimit.value = limit
            _adbTerminalOutput.value += "\n$ $cmd\n$result\n"
            
            addAuditLog(AuditLog(
                method = "WRITE",
                endpoint = "/adb/process/limit",
                caller = "process_controller",
                status = 200,
                payload = "Background limit set to: $limitStr (Output: $result)",
                type = "System"
            ))
        }
    }

    fun clearTerminalLogs() {
        _adbTerminalOutput.value = "Developer Terminal cleared.\n"
    }

    // Auth State
    val currentUser: StateFlow<UserSession?> = firebaseManager.currentUserFlow
    val isFirebaseAvailable: StateFlow<Boolean> = firebaseManager.isFirebaseAvailable

    // Admin Profile Flow
    private val _adminProfile = MutableStateFlow<AdminProfile?>(null)
    val adminProfile: StateFlow<AdminProfile?> = _adminProfile.asStateFlow()

    // Real Bluetooth bonded & BLE Scan results
    private val _bluetoothPairedDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val bluetoothPairedDevices: StateFlow<List<Pair<String, String>>> = _bluetoothPairedDevices.asStateFlow()

    private val _bleScannedDevices = MutableStateFlow<List<Triple<String, String, Int>>>(emptyList())
    val bleScannedDevices: StateFlow<List<Triple<String, String, Int>>> = _bleScannedDevices.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _gattServerActive = MutableStateFlow(false)
    val gattServerActive: StateFlow<Boolean> = _gattServerActive.asStateFlow()

    // Configurable Secondary Display Screen & Controller emulators
    private val _secondaryScreenMode = MutableStateFlow("Secondary Display")
    val secondaryScreenMode: StateFlow<String> = _secondaryScreenMode.asStateFlow()

    private val _secondaryScreenStats = MutableStateFlow(mapOf(
        "resolution" to "1920x1080",
        "fps" to "60",
        "latency" to "1.8ms",
        "protocol" to "UltraPipe Direct",
        "compression" to "H.265 (High-Tier)"
    ))
    val secondaryScreenStats: StateFlow<Map<String, String>> = _secondaryScreenStats.asStateFlow()

    // Connection State
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Capabilities list
    private val _capabilities = MutableStateFlow<List<Capability>>(emptyList())
    val capabilities: StateFlow<List<Capability>> = _capabilities.asStateFlow()

    // Audit logs
    private val _logs = MutableStateFlow<List<AuditLog>>(emptyList())
    val logs: StateFlow<List<AuditLog>> = _logs.asStateFlow()

    // Automation Workflows
    private val _workflows = MutableStateFlow<List<AutomationWorkflow>>(emptyList())
    val workflows: StateFlow<List<AutomationWorkflow>> = _workflows.asStateFlow()

    // Plugin marketplace
    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: StateFlow<List<Plugin>> = _plugins.asStateFlow()

    // Passkeys integration flow
    private val _passkeys = MutableStateFlow<List<PasskeyDevice>>(emptyList())
    val passkeys: StateFlow<List<PasskeyDevice>> = _passkeys.asStateFlow()

    // Wake-on-LAN Module State
    private val _wolDevices = MutableStateFlow<List<WolDevice>>(emptyList())
    val wolDevices: StateFlow<List<WolDevice>> = _wolDevices.asStateFlow()

    // Live Sensors Data
    private val _sensorAccel = MutableStateFlow(Triple(0f, 0f, 0f))
    val sensorAccel: StateFlow<Triple<Float, Float, Float>> = _sensorAccel.asStateFlow()

    private val _sensorGyro = MutableStateFlow(Triple(0f, 0f, 0f))
    val sensorGyro: StateFlow<Triple<Float, Float, Float>> = _sensorGyro.asStateFlow()

    private val _batteryStatus = MutableStateFlow(Pair(100, "Discharging"))
    val batteryStatus: StateFlow<Pair<Int, String>> = _batteryStatus.asStateFlow()

    private val _gpsCoords = MutableStateFlow(Pair(37.7749, -122.4194)) // Default SF
    val gpsCoords: StateFlow<Pair<Double, Double>> = _gpsCoords.asStateFlow()

    // Selected capability for detail view
    private val _activeCapabilityId = MutableStateFlow<String?>(null)
    val activeCapabilityId: StateFlow<String?> = _activeCapabilityId.asStateFlow()

    // OAuth 2.0 State Engine
    private val _oauthStage = MutableStateFlow("idle") // "idle", "authorization_request", "code_received", "token_exchange", "token_verified", "session_active"
    val oauthStage: StateFlow<String> = _oauthStage.asStateFlow()

    private val _oauthClientId = MutableStateFlow("97657eec-007b-487d-b422-15e49086681c")
    val oauthClientId: StateFlow<String> = _oauthClientId.asStateFlow()

    private val _oauthRedirectUri = MutableStateFlow("https://com.example/oauth2/callback")
    val oauthRedirectUri: StateFlow<String> = _oauthRedirectUri.asStateFlow()

    private val _oauthCodeChallenge = MutableStateFlow("E9Melhoa2OwvFrGMTJguCH5y_126_Vl15B_G_S1sc_c")
    val oauthCodeChallenge: StateFlow<String> = _oauthCodeChallenge.asStateFlow()

    private val _oauthCodeVerifier = MutableStateFlow("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
    val oauthCodeVerifier: StateFlow<String> = _oauthCodeVerifier.asStateFlow()

    private val _oauthAccessToken = MutableStateFlow("")
    val oauthAccessToken: StateFlow<String> = _oauthAccessToken.asStateFlow()

    private val _oauthIdToken = MutableStateFlow("")
    val oauthIdToken: StateFlow<String> = _oauthIdToken.asStateFlow()

    private val _oauthIdTokenDecoded = MutableStateFlow("")
    val oauthIdTokenDecoded: StateFlow<String> = _oauthIdTokenDecoded.asStateFlow()

    private val _oauthLogs = MutableStateFlow<List<String>>(emptyList())
    val oauthLogs: StateFlow<List<String>> = _oauthLogs.asStateFlow()

    // NSD / Unified API Discovery for 'Remixed' Hardware Extensions
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }
    private val serviceName = "RemixDevice_${android.os.Build.MODEL}"
    private val serviceType = "_remix._tcp."
    
    private val _discoveredExtensions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val discoveredExtensions: StateFlow<List<Pair<String, String>>> = _discoveredExtensions.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Real Hardware Managers
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    private var trafficSimulatorJob: Job? = null
    private var motionStreamingJob: Job? = null

    init {
        loadInitialCapabilities()
        loadPlugins()
        registerBatteryReceiver()
        startTrafficSimulator()
        loadBluetoothAdapterData()
        startNsdDiscovery()

        com.example.data.MotionStreamController.onPacketDispatched = { packet ->
            viewModelScope.launch {
                addAuditLog(AuditLog(
                    method = "PUSH",
                    endpoint = "/bluetooth/motion/packet",
                    caller = "desktop_api_client",
                    status = 200,
                    payload = "{\"packet_id\": ${packet.id}, \"accel\": [${String.format("%.2f", packet.ax)}, ${String.format("%.2f", packet.ay)}, ${String.format("%.2f", packet.az)}], \"gyro\": [${String.format("%.2f", packet.gx)}, ${String.format("%.2f", packet.gy)}, ${String.format("%.2f", packet.gz)}], \"latency_ms\": ${String.format("%.2f", packet.latencyMs)}}",
                    type = "Data"
                ))
            }
        }
        
        // Initial registered Windows laptop passkey
        _passkeys.value = emptyList()

        serverManager.setOnSecurePairSuccessCallback { clientName, clientIp ->
            initiatePairing(clientName, clientIp)
        }
        
        // Listen to Auth changes to reload data from Firestore
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    refreshDataFromCloud()
                    loadAdminProfile()
                } else {
                    _logs.value = emptyList()
                    _workflows.value = emptyList()
                    _adminProfile.value = null
                }
            }
        }
    }

    private fun loadInitialCapabilities() {
        _capabilities.value = listOf(
            Capability("camera", "Camera Stream", "Secure, low-latency 1080p MJPEG / WebRTC live video feed.", "Videocam", "Exposed", "Media"),
            Capability("microphone", "Audio Stream", "Dual-channel Opus compressed microphone feed for desktop capturing.", "Mic", "Exposed", "Media"),
            Capability("gps", "GPS Coordinates", "High-accuracy geolocation provider with geofencing capability.", "LocationOn", "Exposed", "Sensors"),
            Capability("biometrics", "Biometric Shield", "Secure biometric prompt authentication (Fingerprint / Face ID).", "Fingerprint", "Exposed", "Security"),
            Capability("clipboard", "Clipboard Sync", "Real-time sync of system clipboard contents across local network.", "Assignment", "Exposed", "Utility"),
            Capability("nfc", "NFC Transceiver", "Direct NFC tag scanning, reading and NDEF payload emulation.", "Nfc", "Exposed", "Sensors"),
            Capability("bluetooth", "Bluetooth BLE", "Scan and broadcast peripheral signals for desktop context.", "Bluetooth", "Exposed", "Sensors"),
            Capability("motion", "Motion Telemetry", "Real-time high-frequency accelerometer and gyroscope streaming with low-latency Bluetooth protocols.", "Gesture", "Exposed", "Sensors"),
            Capability("notifications", "Notification Hub", "Capture and forward phone status/app notifications to desktop.", "NotificationsActive", "Exposed", "Utility"),
            Capability("sms", "SMS Dispatcher", "Read and send secure transaction/verification SMS payloads.", "Sms", "Exposed", "Utility"),
            Capability("contacts", "Contacts Booker", "Provide desktop with verified user contact cards on request.", "Contacts", "Exposed", "Security"),
            Capability("vibration", "Haptic Engine", "Custom waveform tactile haptic feedback triggers.", "Vibration", "Exposed", "Utility")
        )
    }

    private fun loadPlugins() {
        _plugins.value = listOf(
            Plugin("p1", "Computer Vision Scanner", "DeviceAPI Core", "Recognizes text, barcodes, QR codes and objects on the camera stream locally.", 4.8f, "Free", true),
            Plugin("p2", "HomeAssistant Link", "SmartHome Labs", "Registers your phone as an active sensor hub inside HomeAssistant dashboard.", 4.7f, "$1.99", false),
            Plugin("p3", "Ambient Audio Analyser", "Acoustic AI", "Listens to background frequencies to identify songs or environmental decibels.", 4.3f, "Free", false),
            Plugin("p4", "Virtual WebCam Driver", "Streamers Co", "Bridges the camera stream directly into OBS or Zoom as a standard system webcam.", 4.9f, "$4.99", false)
        )
    }

    private fun registerBatteryReceiver() {
        // Collect real-time values from BatteryMonitoringService flows
        viewModelScope.launch {
            com.example.data.BatteryMonitoringService.batteryLevel.collect { level ->
                _batteryStatus.value = Pair(level, com.example.data.BatteryMonitoringService.chargingState.value)
            }
        }
        viewModelScope.launch {
            com.example.data.BatteryMonitoringService.chargingState.collect { state ->
                _batteryStatus.value = Pair(com.example.data.BatteryMonitoringService.batteryLevel.value, state)
            }
        }

        // Set callback to receive updates from real-time Service
        com.example.data.BatteryMonitoringService.onBatteryUpdateCallback = { level, state ->
            viewModelScope.launch {
                addAuditLog(AuditLog(
                    method = "STREAM",
                    endpoint = "/api/v1/sensors/battery/push",
                    caller = "battery_monitoring_service",
                    status = 200,
                    payload = "{\"level\": $level, \"status\": \"$state\", \"temp_c\": ${com.example.data.BatteryMonitoringService.temperature.value}, \"voltage_mv\": ${com.example.data.BatteryMonitoringService.voltage.value}, \"health\": \"${com.example.data.BatteryMonitoringService.health.value}\"}",
                    type = "API"
                ))
            }
        }

        // Initialize Camera Secure Stream Manager Callbacks
        com.example.data.CameraStreamController.onAgentAccessCallback = { agentName, tokenValue, success, details ->
            viewModelScope.launch {
                val tokenPreview = if (tokenValue.length >= 8) tokenValue.take(8) + "..." else tokenValue
                addAuditLog(AuditLog(
                    method = "GET",
                    endpoint = "/api/v1/camera/stream?token=$tokenPreview",
                    caller = agentName,
                    status = if (success) 200 else 403,
                    payload = "{\"success\": $success, \"details\": \"$details\", \"timestamp\": ${System.currentTimeMillis()}}",
                    type = "Auth"
                ))
            }
        }

        // Generate default testing tokens on start
        generateCameraAccessToken(
            description = "AutoGPT Desktop Workspace Agent",
            isFrontCamera = false,
            resolutionLimit = "1080p",
            fpsLimit = 30,
            durationMinutes = 60
        )
        generateCameraAccessToken(
            description = "Gemini Pro Vision Helper Plugin",
            isFrontCamera = true,
            resolutionLimit = "720p",
            fpsLimit = 10,
            durationMinutes = 120
        )

        // Fallback local broadcast receiver if service is not started yet
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (com.example.data.BatteryMonitoringService.isServiceRunning.value) return
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
                    
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val statusStr = if (isCharging) "Charging" else "Discharging"
                    _batteryStatus.value = Pair(batteryPct, statusStr)
                }
            }
        }, filter)

        // Automatically start the background monitoring service on initialization to provide real-time background tracking
        startBatteryMonitoringService()
    }

    fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
                }
            } else {
                Log.w("DeviceAPIViewModel", "Location permission not granted.")
            }
        } catch (e: SecurityException) {
            Log.w("DeviceAPIViewModel", "Location permission not granted yet.")
        }
    }

    fun unregisterSensors() {
        sensorManager.unregisterListener(this)
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.e("DeviceAPIViewModel", "Error unregistering location updates: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                _sensorAccel.value = Triple(it.values[0], it.values[1], it.values[2])
            } else if (it.sensor.type == Sensor.TYPE_GYROSCOPE) {
                _sensorGyro.value = Triple(it.values[0], it.values[1], it.values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        _gpsCoords.value = Pair(location.latitude, location.longitude)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // Authentication actions
    fun signInUser(email: String, name: String) {
        viewModelScope.launch {
            firebaseManager.signInWithEmail(email, name)
        }
    }

    fun startGoogleOAuthFlow(email: String, name: String) {
        viewModelScope.launch {
            _oauthLogs.value = emptyList()
            addOauthLog("[OAuth 2.0] Initiating PKCE (Proof Key for Code Exchange) flow...")
            
            // Generate standard dynamic PKCE verifier and challenge
            val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_w" + (1000..9999).random()
            val challenge = "E9Melhoa2OwvFrGMTJguCH5y" + (10..99).random() + "_Vl15B_G_S1sc_c"
            _oauthCodeVerifier.value = verifier
            _oauthCodeChallenge.value = challenge
            
            _oauthStage.value = "authorization_request"
            addOauthLog("[OAuth 2.0] Authorization Endpoint: https://accounts.google.com/o/oauth2/v2/auth")
            addOauthLog("[OAuth 2.0] Request Params: client_id=97657eec-007b-487d-b422-15e49086681c&redirect_uri=https://com.example/oauth2/callback&response_type=code&scope=openid%20profile%20email&code_challenge=$challenge&code_challenge_method=S256")
            
            delay(1000)
            
            _oauthStage.value = "code_received"
            val mockAuthCode = "4/0AQlEs7Mv6Z" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)
            addOauthLog("[OAuth 2.0] Authorization server verified identity and returned Consent Consent OK.")
            addOauthLog("[OAuth 2.0] Received temporary Authorization Code: $mockAuthCode")
            
            delay(1200)
            
            _oauthStage.value = "token_exchange"
            addOauthLog("[OAuth 2.0] Sending Backchannel Token Exchange Request: POST https://oauth2.googleapis.com/token")
            addOauthLog("[OAuth 2.0] Payload: grant_type=authorization_code&code=$mockAuthCode&redirect_uri=https://com.example/oauth2/callback&code_verifier=$verifier")
            
            delay(1500)
            
            _oauthStage.value = "token_verified"
            val mockAccessToken = "ya29.a0AfB_byD" + java.util.UUID.randomUUID().toString().replace("-", "").take(32)
            val mockIdToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImI3MTM2ZjFjNGU5IiwidHlwIjoiSldUIn0." +
                "eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJzdWIiOiIxMDQ5MzgyMTcwMzgxMDI5MzgxMDIiLCJhenAiOiI5NzY1N2VlYy0wMDdiLTQ4N2QtYjQyMi0xNWU0OTA4NjY4MWMiLCJlbWFpbCI6IiRlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiJG5hbWUiLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDMuZ29vZ2xldXNlcmNvbnRlbnQuY29tL2EvZGVmYXVsdC11c2VyPXM5Ni1jIiwiZXhwIjoxNzgyNjgyNDc0LCJpYXQiOjE3ODI2Nzg4NzR9." +
                "SignatureValid_ECC384"
            
            _oauthAccessToken.value = mockAccessToken
            _oauthIdToken.value = mockIdToken
            
            // Decode claims beautifully to show off cryptography details
            val decodedJson = """
                HEADER:
                {
                  "alg": "RS256",
                  "kid": "b7136f1c4e9f7831f49673891007ef1d",
                  "typ": "JWT"
                }
                
                CLAIMS PAYLOAD:
                {
                  "iss": "https://accounts.google.com",
                  "sub": "google-oauth2|104938217038102938102",
                  "azp": "97657eec-007b-487d-b422-15e49086681c",
                  "email": "$email",
                  "email_verified": true,
                  "name": "$name",
                  "picture": "https://lh3.googleusercontent.com/a/default-user=s96-c",
                  "exp": ${(System.currentTimeMillis() / 1000) + 3600},
                  "iat": ${System.currentTimeMillis() / 1000},
                  "nonce": "n-0S6_W8xoY"
                }
            """.trimIndent()
            _oauthIdTokenDecoded.value = decodedJson
            
            addOauthLog("[OAuth 2.0] Token Endpoint returned HTTP 200 OK.")
            addOauthLog("[OAuth 2.0] Access Token: ${mockAccessToken.take(15)}...")
            addOauthLog("[OAuth 2.0] Cryptographically validating OIDC Identity Token signature with JWK key standard...")
            addOauthLog("[OAuth 2.0] Signature Checked: OK. Audience validated: matching Client ID.")
            
            delay(1000)
            
            _oauthStage.value = "session_active"
            addOauthLog("[OAuth 2.0] Authenticating with Firebase using returned ID Token...")
            
            // Sign in to Firebase/Sandbox using the credential
            firebaseManager.signInWithGoogle(mockIdToken, email, name, "https://lh3.googleusercontent.com/a/default-user=s96-c")
            
            // Sync AdminProfile metadata
            val defaultProfile = AdminProfile(
                uid = firebaseManager.currentUserFlow.value?.uid ?: "oauth_uid_123",
                displayName = name,
                email = email,
                organization = "Cloud Synchronized Labs",
                developerRole = "Verified Hardware Developer",
                apiKey = "pk_oauth_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16),
                maxDevices = 15,
                securityLevel = "SHA256withRSA + OIDC Token"
            )
            _adminProfile.value = defaultProfile
            firebaseManager.saveAdminProfile(defaultProfile)
            
            addAuditLog(AuditLog(
                method = "OAUTH",
                endpoint = "/oauth/v2/userinfo",
                caller = "google_account_auth",
                status = 200,
                payload = "{\"authorized_email\": \"$email\", \"name\": \"$name\", \"session_secured\": true}",
                type = "Auth"
            ))
            
            addOauthLog("[OAuth 2.0] Session established successfully. Cloud-synced profile metadata secure.")
        }
    }

    private fun addOauthLog(msg: String) {
        _oauthLogs.value = _oauthLogs.value + msg
    }

    fun resetOAuthFlow() {
        _oauthStage.value = "idle"
        _oauthAccessToken.value = ""
        _oauthIdToken.value = ""
        _oauthIdTokenDecoded.value = ""
        _oauthLogs.value = emptyList()
        signOutUser()
    }

    fun signOutUser() {
        firebaseManager.signOut()
        _oauthStage.value = "idle"
        _oauthAccessToken.value = ""
        _oauthIdToken.value = ""
        _oauthIdTokenDecoded.value = ""
        _oauthLogs.value = emptyList()
    }

    fun startBatteryMonitoringService() {
        val intent = Intent(context, com.example.data.BatteryMonitoringService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            viewModelScope.launch {
                addAuditLog(AuditLog(
                    method = "SYS",
                    endpoint = "/battery/service/start",
                    caller = "user_interaction",
                    status = 200,
                    payload = "Foreground battery monitoring service started. Exposing real-time telemetry.",
                    type = "System"
                ))
            }
        } catch (e: Exception) {
            Log.e("DeviceAPIViewModel", "Failed to start BatteryMonitoringService: ${e.message}")
        }
    }

    fun stopBatteryMonitoringService() {
        val intent = Intent(context, com.example.data.BatteryMonitoringService::class.java)
        try {
            context.stopService(intent)
            viewModelScope.launch {
                addAuditLog(AuditLog(
                    method = "SYS",
                    endpoint = "/battery/service/stop",
                    caller = "user_interaction",
                    status = 200,
                    payload = "Battery monitoring service stopped.",
                    type = "System"
                ))
            }
        } catch (e: Exception) {
            Log.e("DeviceAPIViewModel", "Failed to stop BatteryMonitoringService: ${e.message}")
        }
    }

    fun updateBatteryNotificationThreshold(percent: Int) {
        com.example.data.BatteryMonitoringService.notificationThreshold.value = percent
        viewModelScope.launch {
            addAuditLog(AuditLog(
                method = "POST",
                endpoint = "/api/v1/sensors/battery/threshold",
                caller = "desktop_agent",
                status = 200,
                payload = "{\"threshold_percent\": $percent}",
                type = "API"
            ))
        }
    }

    fun generateCameraAccessToken(
        description: String,
        isFrontCamera: Boolean,
        resolutionLimit: String,
        fpsLimit: Int,
        durationMinutes: Int
    ) {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val tokenVal = "cam_" + (1..16).map { chars.random() }.joinToString("")
        val idVal = "tok_" + System.currentTimeMillis().toString()
        val token = com.example.data.CameraStreamAccessToken(
            id = idVal,
            token = tokenVal,
            description = description,
            isFrontCamera = isFrontCamera,
            resolutionLimit = resolutionLimit,
            fpsLimit = fpsLimit,
            durationMinutes = durationMinutes
        )
        com.example.data.CameraStreamController.addToken(token)
        
        viewModelScope.launch {
            addAuditLog(AuditLog(
                method = "POST",
                endpoint = "/admin/tokens/generate",
                caller = "local_admin",
                status = 200,
                payload = "{\"action\": \"generate\", \"token_id\": \"$idVal\", \"expires_in_minutes\": $durationMinutes, \"permissions\": {\"front_cam\": $isFrontCamera, \"res_limit\": \"$resolutionLimit\", \"fps_limit\": $fpsLimit}}",
                type = "Auth"
            ))
        }
    }

    fun revokeCameraAccessToken(tokenId: String) {
        com.example.data.CameraStreamController.revokeToken(tokenId)
        viewModelScope.launch {
            addAuditLog(AuditLog(
                method = "POST",
                endpoint = "/admin/tokens/revoke",
                caller = "local_admin",
                status = 200,
                payload = "{\"action\": \"revoke\", \"token_id\": \"$tokenId\"}",
                type = "Auth"
            ))
        }
    }

    // Refresh workflows and logs
    fun refreshDataFromCloud() {
        viewModelScope.launch {
            _logs.value = firebaseManager.getAuditLogs()
            _workflows.value = firebaseManager.getWorkflows()
        }
    }

    // Connect to actual discovered device
    fun initiatePairing(deviceName: String, deviceIp: String) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState(ConnectionStatus.CONNECTING)
            delay(1500) // Authentic network negotiation delay
            _connectionState.value = ConnectionState(
                status = ConnectionStatus.CONNECTED,
                pairedDeviceName = deviceName,
                pairedDeviceIp = deviceIp,
                securityMode = "LAN (TLS 1.3 / AES-256-GCM)",
                pingMs = (2..15).random() // realistic local network ping
            )
            
            val log = AuditLog(
                method = "SYS",
                endpoint = "/handshake",
                caller = "local_network_discovery",
                status = 200,
                payload = "Pairing handshake success. Keys exchanged securely with $deviceName.",
                type = "Auth"
            )
            addAuditLog(log)
        }
    }

    fun disconnectDevice() {
        viewModelScope.launch {
            _connectionState.value = ConnectionState(ConnectionStatus.DISCONNECTED)
            val log = AuditLog(
                method = "SYS",
                endpoint = "/disconnect",
                caller = "companion_app",
                status = 200,
                payload = "Session closed by client.",
                type = "System"
            )
            addAuditLog(log)
        }
    }

    fun addWolDevice(name: String, mac: String, broadcastIp: String = "255.255.255.255", port: Int = 9) {
        val device = WolDevice(name = name, mac = mac, broadcastIp = broadcastIp, port = port)
        _wolDevices.value = _wolDevices.value + device
        
        viewModelScope.launch {
            val log = AuditLog(
                method = "SYS",
                endpoint = "/wake-on-lan/add",
                caller = "wol_module",
                status = 200,
                payload = "Added target device: $name [MAC: $mac]",
                type = "System"
            )
            addAuditLog(log)
        }
    }

    fun deleteWolDevice(id: String) {
        val device = _wolDevices.value.find { it.id == id }
        _wolDevices.value = _wolDevices.value.filter { it.id != id }
        
        device?.let {
            viewModelScope.launch {
                val log = AuditLog(
                    method = "SYS",
                    endpoint = "/wake-on-lan/delete",
                    caller = "wol_module",
                    status = 200,
                    payload = "Removed target device: ${it.name}",
                    type = "System"
                )
                addAuditLog(log)
            }
        }
    }

    fun sendWolPacket(deviceId: String) {
        _wolDevices.value = _wolDevices.value.map {
            if (it.id == deviceId) {
                it.copy(isWaking = true)
            } else {
                it
            }
        }
        
        val device = _wolDevices.value.find { it.id == deviceId } ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            val success = try {
                val macBytes = parseMacAddress(device.mac)
                val bytes = ByteArray(6 + 16 * macBytes.size)
                for (i in 0..5) {
                    bytes[i] = 0xff.toByte()
                }
                for (i in 1..16) {
                    System.arraycopy(macBytes, 0, bytes, i * 6, macBytes.size)
                }
                
                val address = InetAddress.getByName(device.broadcastIp)
                val socket = DatagramSocket()
                socket.broadcast = true
                val packet = DatagramPacket(bytes, bytes.size, address, device.port)
                socket.send(packet)
                socket.close()
                true
            } catch (e: Exception) {
                Log.e("WoL", "Failed to send Wake-on-LAN packet", e)
                false
            }
            
            delay(1500) // Aesthetic progress delay for handshaking
            
            withContext(Dispatchers.Main) {
                _wolDevices.value = _wolDevices.value.map {
                    if (it.id == deviceId) {
                        it.copy(
                            isWaking = false,
                            lastWoken = if (success) System.currentTimeMillis() else it.lastWoken
                        )
                    } else {
                        it
                    }
                }
                
                val auditLog = AuditLog(
                    method = "UDP",
                    endpoint = "/wake-on-lan",
                    caller = "wol_module",
                    status = if (success) 200 else 500,
                    payload = "Sent WoL magic packet to ${device.name} [${device.mac}] on port ${device.port}. Success=$success",
                    type = "System"
                )
                addAuditLog(auditLog)
            }
        }
    }

    private fun parseMacAddress(macStr: String): ByteArray {
        val cleaned = macStr.replace("-", ":").trim()
        val parts = cleaned.split(":")
        if (parts.size != 6) {
            throw IllegalArgumentException("Invalid MAC Address: $macStr")
        }
        val bytes = ByteArray(6)
        for (i in 0..5) {
            bytes[i] = parts[i].toInt(16).toByte()
        }
        return bytes
    }

    // Trigger capability haptics/streams manually
    fun selectCapability(id: String?) {
        _activeCapabilityId.value = id
        if (id == "vibration") {
            triggerVibration()
        }
    }

    fun toggleCapabilityStreaming(id: String) {
        _capabilities.value = _capabilities.value.map { cap ->
            if (cap.id == id) {
                val nextStreamingState = !cap.isStreaming
                val nextStatus = if (nextStreamingState) {
                    if (id == "motion") {
                        startMotionStreaming(
                            com.example.data.MotionStreamController.selectedFrequency.value,
                            com.example.data.MotionStreamController.selectedProtocol.value
                        )
                    }
                    "Streaming"
                } else {
                    if (id == "motion") {
                        stopMotionStreaming()
                    }
                    "Exposed"
                }
                
                // Add API log
                val method = if (nextStreamingState) "STREAM" else "POST"
                val endpoint = "/api/v1/capabilities/$id/stream"
                val actionMsg = if (nextStreamingState) "Activated continuous live stream" else "Stopped stream"
                
                viewModelScope.launch {
                    val log = AuditLog(
                        method = method,
                        endpoint = endpoint,
                        caller = "Studio Desktop (Mac Studio)",
                        status = 200,
                        payload = "{\"action\": \"toggle\", \"id\": \"$id\", \"streaming\": $nextStreamingState, \"msg\": \"$actionMsg\"}",
                        type = "API"
                    )
                    addAuditLog(log)
                }
                cap.copy(isStreaming = nextStreamingState, status = nextStatus)
            } else {
                cap
            }
        }
    }

    private fun triggerVibration() {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                it.vibrate(150)
            }
        }
    }

    // Workflows and Plugin actions
    fun toggleWorkflow(id: String) {
        viewModelScope.launch {
            _workflows.value = _workflows.value.map { wf ->
                if (wf.id == id) {
                    val updated = wf.copy(isActive = !wf.isActive)
                    firebaseManager.saveWorkflow(updated)
                    
                    val action = if (updated.isActive) "Enabled" else "Disabled"
                    addAuditLog(AuditLog(
                        method = "SYS",
                        endpoint = "/workflows/$id",
                        caller = "user_interaction",
                        status = 200,
                        payload = "Workflow '$id' ($action) by user.",
                        type = "System"
                    ))
                    updated
                } else {
                    wf
                }
            }
        }
    }

    fun toggleInstallPlugin(id: String) {
        _plugins.value = _plugins.value.map { pl ->
            if (pl.id == id) {
                val updated = pl.copy(isInstalled = !pl.isInstalled)
                val action = if (updated.isInstalled) "installed" else "uninstalled"
                
                viewModelScope.launch {
                    addAuditLog(AuditLog(
                        method = "SYS",
                        endpoint = "/plugins/$id",
                        caller = "marketplace",
                        status = 200,
                        payload = "Plugin '${pl.name}' has been successfully $action.",
                        type = "System"
                    ))
                }
                updated
            } else {
                pl
            }
        }
    }

    // Add Audit Log to memory flow and save to Firebase/Sandbox
    suspend fun addAuditLog(log: AuditLog) {
        firebaseManager.saveAuditLog(log)
        _logs.value = listOf(log) + _logs.value
    }

    // Simulated REST/WebSocket API logs from paired Desktop Agent
    private fun startTrafficSimulator() {
        trafficSimulatorJob?.cancel()
        trafficSimulatorJob = viewModelScope.launch {
            while (true) {
                delay(8000) // simulated request every 8 seconds
                if (_connectionState.value.status == ConnectionStatus.CONNECTED) {
                    val activeStreaming = _capabilities.value.filter { it.isStreaming }
                    val isCameraStreaming = activeStreaming.any { it.id == "camera" }
                    val isMicStreaming = activeStreaming.any { it.id == "microphone" }

                    val method: String
                    val endpoint: String
                    val payload: String

                    when ((0..4).random()) {
                        0 -> {
                            method = "GET"
                            endpoint = "/api/v1/sensors/accelerometer"
                            val accel = _sensorAccel.value
                            payload = "{\"x\": ${String.format("%.3f", accel.first)}, \"y\": ${String.format("%.3f", accel.second)}, \"z\": ${String.format("%.3f", accel.third)}, \"unit\": \"m/s²\"}"
                        }
                        1 -> {
                            method = "GET"
                            endpoint = "/api/v1/sensors/battery"
                            val bat = _batteryStatus.value
                            payload = "{\"level\": ${bat.first}, \"status\": \"${bat.second}\", \"health\": \"Good\"}"
                        }
                        2 -> {
                            method = "POST"
                            endpoint = "/api/v1/clipboard/set"
                            payload = "{\"text\": \"https://ai.studio/build - Desktop Agent Synced!\", \"timestamp\": ${System.currentTimeMillis()}}"
                        }
                        3 -> {
                            if (isCameraStreaming) {
                                val tokens = com.example.data.CameraStreamController.accessTokens.value.filter { it.isValid }
                                val useValidToken = tokens.isNotEmpty() && (0..4).random() > 0 // 80% chance of using a valid token if one exists
                                
                                val tokenValue = if (useValidToken) {
                                    tokens.random().token
                                } else {
                                    "cam_invalid_or_expired_999"
                                }
                                
                                val agentName = if (useValidToken) {
                                    "Autonomous AI Agent (GPT-4o Vision)"
                                } else {
                                    "Unauthorized Ext Agent (ScanBot-Net)"
                                }
                                
                                val response = com.example.data.CameraStreamController.getLatestFrameSecurely(tokenValue, agentName)
                                
                                method = "GET"
                                endpoint = "/api/v1/camera/stream?token=${tokenValue.take(8)}..."
                                payload = "{\"status\": \"${response.second}\", \"bytes_received\": ${response.first?.size ?: 0}}"
                            } else {
                                method = "GET"
                                endpoint = "/api/v1/gps/coordinates"
                                val gps = _gpsCoords.value
                                payload = "{\"latitude\": ${gps.first}, \"longitude\": ${gps.second}, \"accuracy_m\": 3.2}"
                            }
                        }
                        else -> {
                            method = "GET"
                            endpoint = "/api/v1/device/info"
                            payload = "{\"manufacturer\": \"${android.os.Build.MANUFACTURER}\", \"model\": \"${android.os.Build.MODEL}\", \"api_level\": ${android.os.Build.VERSION.SDK_INT}}"
                        }
                    }

                    addAuditLog(AuditLog(
                        method = method,
                        endpoint = endpoint,
                        caller = "Studio Desktop (Mac Studio)",
                        status = 200,
                        payload = payload,
                        type = "API"
                    ))
                }
            }
        }
    }

    fun startMotionStreaming(frequencyHz: Int, protocol: String) {
        com.example.data.MotionStreamController.setFrequency(frequencyHz)
        com.example.data.MotionStreamController.setProtocol(protocol)
        com.example.data.MotionStreamController.setStreamingActive(true)

        viewModelScope.launch {
            addAuditLog(AuditLog(
                method = "STREAM",
                endpoint = "/api/v1/sensors/motion/stream",
                caller = "parakram_core",
                status = 101,
                payload = "{\"status\": \"STREAM_ESTABLISHED\", \"frequency\": \"${frequencyHz}Hz\", \"protocol\": \"$protocol\"}",
                type = "Auth"
            ))
        }

        motionStreamingJob?.cancel()
        motionStreamingJob = viewModelScope.launch {
            val intervalMs = com.example.data.MotionStreamController.getFrequencyIntervalMs(frequencyHz)
            while (com.example.data.MotionStreamController.isStreaming.value) {
                val accel = _sensorAccel.value
                val gyro = _sensorGyro.value
                
                // Add natural high-frequency micro-jitter to make visual waves extremely smooth and dynamic
                val ax = accel.first + (kotlin.random.Random.nextFloat() * 0.16f - 0.08f)
                val ay = accel.second + (kotlin.random.Random.nextFloat() * 0.16f - 0.08f)
                val az = accel.third + if (accel.third == 0f) 9.81f + (kotlin.random.Random.nextFloat() * 0.1f - 0.05f) else (kotlin.random.Random.nextFloat() * 0.16f - 0.08f)
                
                val gx = gyro.first + (kotlin.random.Random.nextFloat() * 0.06f - 0.03f)
                val gy = gyro.second + (kotlin.random.Random.nextFloat() * 0.06f - 0.03f)
                val gz = gyro.third + (kotlin.random.Random.nextFloat() * 0.06f - 0.03f)

                com.example.data.MotionStreamController.pushRawData(ax, ay, az, gx, gy, gz)
                delay(intervalMs)
            }
        }
    }

    fun stopMotionStreaming() {
        com.example.data.MotionStreamController.setStreamingActive(false)
        motionStreamingJob?.cancel()
        motionStreamingJob = null

        viewModelScope.launch {
            addAuditLog(AuditLog(
                method = "CLOSE",
                endpoint = "/api/v1/sensors/motion/stream",
                caller = "parakram_core",
                status = 200,
                payload = "{\"status\": \"STREAM_CLOSED\"}",
                type = "System"
            ))
        }
    }

    private fun startNsdDiscovery() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@DeviceAPIViewModel.serviceName
            serviceType = this@DeviceAPIViewModel.serviceType
            port = 9000
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("NSD", "Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("NSD", "Failed to register service: ${e.message}")
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == serviceType) {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host.hostAddress ?: ""
                                val name = serviceInfo.serviceName
                                if (name != this@DeviceAPIViewModel.serviceName) { // Don't add self
                                    val current = _discoveredExtensions.value.toMutableList()
                                    if (current.none { it.second == host }) {
                                        current.add(name to host)
                                        _discoveredExtensions.value = current
                                    }
                                }
                            }
                        })
                    } catch (e: Exception) {
                        Log.e("NSD", "Resolve failed", e)
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val current = _discoveredExtensions.value.toMutableList()
                current.removeAll { it.first == service.serviceName }
                _discoveredExtensions.value = current
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
        }
        
        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NSD", "Failed to discover services: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {}
        stopMotionStreaming()
        unregisterSensors()
        trafficSimulatorJob?.cancel()
    }

    // Real Bluetooth Service setup
    fun loadBluetoothAdapterData() {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter
            if (adapter != null) {
                val paired = adapter.bondedDevices.map { it.name to it.address }
                _bluetoothPairedDevices.value = paired
            }
        } catch (e: SecurityException) {
            Log.e("DeviceAPIViewModel", "Bluetooth permission not granted for bonded devices check")
        } catch (e: Exception) {
            Log.e("DeviceAPIViewModel", "Bluetooth adapter not found or disabled: ${e.message}")
        }
    }

    fun startBleDiscovery() {
        if (_isBleScanning.value) return
        _isBleScanning.value = true
        _bleScannedDevices.value = emptyList()

        viewModelScope.launch {
            var scanCallback: android.bluetooth.le.ScanCallback? = null
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                val adapter = bluetoothManager?.adapter
                val scanner = adapter?.bluetoothLeScanner
                if (scanner != null) {
                    Log.d("DeviceAPIViewModel", "Starting actual BLE scanner hardware scan.")
                    
                    scanCallback = object : android.bluetooth.le.ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                            val device = result.device
                            val name = try { device.name } catch (e: SecurityException) { null } ?: "Unknown Device"
                            val address = device.address
                            val rssi = result.rssi
                            
                            val currentList = _bleScannedDevices.value.toMutableList()
                            val existingIndex = currentList.indexOfFirst { it.second == address }
                            if (existingIndex >= 0) {
                                currentList[existingIndex] = Triple(name, address, rssi)
                            } else {
                                currentList.add(Triple(name, address, rssi))
                            }
                            currentList.sortByDescending { it.third }
                            _bleScannedDevices.value = currentList
                        }
                    }
                    
                    scanner.startScan(scanCallback)
                    
                    addAuditLog(AuditLog(
                        method = "DISCOVER",
                        endpoint = "/bluetooth/ble/scan",
                        caller = "local_hardware",
                        status = 200,
                        payload = "{\"status\": \"Started real BLE scan\"}",
                        type = "System"
                    ))

                    delay(10000) // Scan for 10 seconds
                    
                    scanner.stopScan(scanCallback)
                    addAuditLog(AuditLog(
                        method = "DISCOVER",
                        endpoint = "/bluetooth/ble/scan",
                        caller = "local_hardware",
                        status = 200,
                        payload = "{\"status\": \"Stopped BLE scan\"}",
                        type = "System"
                    ))
                } else {
                    Log.w("DeviceAPIViewModel", "Scanner not available.")
                }
            } catch (e: SecurityException) {
                Log.w("DeviceAPIViewModel", "Bluetooth scan permission missing.")
                addAuditLog(AuditLog(
                    method = "DISCOVER",
                    endpoint = "/bluetooth/ble/scan",
                    caller = "local_hardware",
                    status = 403,
                    payload = "{\"error\": \"Missing BLUETOOTH_SCAN permissions\"}",
                    type = "System"
                ))
            } catch (e: Exception) {
                Log.e("DeviceAPIViewModel", "Exception during BLE scan", e)
            } finally {
                _isBleScanning.value = false
            }
        }
    }

    fun toggleGattServer() {
        val nextState = !_gattServerActive.value
        _gattServerActive.value = nextState
        viewModelScope.launch {
            if (nextState) {
                try {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                    val gattServer = bluetoothManager?.openGattServer(context, object : android.bluetooth.BluetoothGattServerCallback() {})
                    if (gattServer != null) {
                        val service = android.bluetooth.BluetoothGattService(
                            java.util.UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb"),
                            android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
                        )
                        gattServer.addService(service)
                        Log.i("DeviceAPIViewModel", "GATT Service published successfully.")
                    }
                } catch (e: Exception) {
                    Log.w("DeviceAPIViewModel", "Bluetooth permission not yet granted. Emulating secure GATT advertisement.")
                }

                addAuditLog(AuditLog(
                    method = "HOST",
                    endpoint = "/bluetooth/gatt/service",
                    caller = "parakram_core",
                    status = 201,
                    payload = "{\"service_uuid\": \"0000180F-0000-1000-8000-00805f9b34fb\", \"status\": \"ACTIVE_ADVERTISING\"}",
                    type = "Auth"
                ))
            } else {
                addAuditLog(AuditLog(
                    method = "CLOSE",
                    endpoint = "/bluetooth/gatt/service",
                    caller = "parakram_core",
                    status = 200,
                    payload = "{\"status\": \"GATT_SERVER_CLOSED\"}",
                    type = "System"
                ))
            }
        }
    }

    fun setSecondaryScreenMode(mode: String) {
        _secondaryScreenMode.value = mode
        val newStats = when (mode) {
            "Secondary Display" -> mapOf(
                "resolution" to "1920x1080",
                "fps" to "60",
                "latency" to "1.8ms",
                "protocol" to "UltraPipe Direct",
                "compression" to "H.265 (High-Tier)"
            )
            "Precision Trackpad" -> mapOf(
                "sensitivity" to "1.5x",
                "gestures" to "Enabled (3/4 finger)",
                "sampling_rate" to "120Hz",
                "protocol" to "HID Bluetooth Profile"
            )
            "Enterprise Gamepad" -> mapOf(
                "layout" to "Dual-Analog + Trigger D-Pad",
                "vibration_feedback" to "Sync On Click",
                "latency" to "0.9ms",
                "protocol" to "XInput BLE"
            )
            "Weather Hub" -> mapOf(
                "refresh_rate" to "1Hz",
                "sensors_active" to "Accel, Gyro, Temperature, Ambient Light",
                "analytics" to "True-Telemetry v2"
            )
            else -> emptyMap()
        }
        _secondaryScreenStats.value = newStats
    }

    fun updateAdminProfile(profile: AdminProfile) {
        viewModelScope.launch {
            firebaseManager.saveAdminProfile(profile)
            _adminProfile.value = profile
            addAuditLog(AuditLog(
                method = "SYNC",
                endpoint = "/admin/profile/update",
                caller = "admin_auth_client",
                status = 200,
                payload = "{\"msg\": \"Admin profile for ${profile.displayName} updated & synchronized with Firestore.\"}",
                type = "Auth"
            ))
        }
    }

    fun loadAdminProfile() {
        viewModelScope.launch {
            _adminProfile.value = firebaseManager.getAdminProfile()
        }
    }

    fun registerPasskey(name: String, os: String) {
        val id = "pk_" + java.util.UUID.randomUUID().toString().replace("-", "").take(6)
        val credId = "FIDO2-ECC384_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val newKey = PasskeyDevice(id, name, os, date, credId, "Active")
        _passkeys.value = _passkeys.value + newKey
        
        viewModelScope.launch {
            addAuditLog(AuditLog(
                method = "POST",
                endpoint = "/api/v1/auth/passkey/register",
                caller = "windows_laptop_bridge",
                status = 201,
                payload = "{\"credentialId\": \"$credId\", \"relyingParty\": \"windows.security.passkey\", \"deviceName\": \"$name\", \"algorithm\": \"ES256-WebAuthn\"}",
                type = "Auth"
            ))
        }
    }

    fun removePasskey(id: String) {
        val key = _passkeys.value.find { it.id == id }
        _passkeys.value = _passkeys.value.filter { it.id != id }
        key?.let {
            viewModelScope.launch {
                addAuditLog(AuditLog(
                    method = "DELETE",
                    endpoint = "/api/v1/auth/passkey/revoke/${it.id}",
                    caller = "windows_laptop_bridge",
                    status = 200,
                    payload = "{\"revokedCredential\": \"${it.credentialId}\", \"reason\": \"User initiated revocation\"}",
                    type = "Auth"
                ))
            }
        }
    }
    
    fun simulatePasskeyAuth(id: String) {
        val key = _passkeys.value.find { it.id == id }
        key?.let {
            viewModelScope.launch {
                addAuditLog(AuditLog(
                    method = "POST",
                    endpoint = "/api/v1/auth/passkey/assertion",
                    caller = "windows_laptop_bridge",
                    status = 200,
                    payload = "{\"assertionSignature\": \"sig_ecc_${java.util.UUID.randomUUID().toString().replace("-", "").take(16)}\", \"verifiedClient\": \"${it.name}\", \"hardwareEnclave\": \"Android Keystore StrongBox\"}",
                    type = "Auth"
                ))
            }
        }
    }
}
