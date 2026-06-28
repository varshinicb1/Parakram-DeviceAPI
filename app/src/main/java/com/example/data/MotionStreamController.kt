package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class MotionPacket(
    val id: Long,
    val timestamp: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float,
    val gy: Float,
    val gz: Float,
    val protocol: String,
    val frequencyHz: Int,
    val latencyMs: Float
)

object MotionStreamController {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _selectedFrequency = MutableStateFlow(50) // default 50Hz (Game rate)
    val selectedFrequency: StateFlow<Int> = _selectedFrequency.asStateFlow()

    private val _selectedProtocol = MutableStateFlow("BLE L2CAP Channel")
    val selectedProtocol: StateFlow<String> = _selectedProtocol.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _totalPackets = MutableStateFlow(0L)
    val totalPackets: StateFlow<Long> = _totalPackets.asStateFlow()

    private val _currentLatencyMs = MutableStateFlow(2.4f)
    val currentLatencyMs: StateFlow<Float> = _currentLatencyMs.asStateFlow()

    // Rolling history for live real-time graph rendering
    private val _accelHistoryX = MutableStateFlow<List<Float>>(List(40) { 0f })
    val accelHistoryX: StateFlow<List<Float>> = _accelHistoryX.asStateFlow()

    private val _accelHistoryY = MutableStateFlow<List<Float>>(List(40) { 0f })
    val accelHistoryY: StateFlow<List<Float>> = _accelHistoryY.asStateFlow()

    private val _accelHistoryZ = MutableStateFlow<List<Float>>(List(40) { 0f })
    val accelHistoryZ: StateFlow<List<Float>> = _accelHistoryZ.asStateFlow()

    private val _gyroHistoryX = MutableStateFlow<List<Float>>(List(40) { 0f })
    val gyroHistoryX: StateFlow<List<Float>> = _gyroHistoryX.asStateFlow()

    private val _gyroHistoryY = MutableStateFlow<List<Float>>(List(40) { 0f })
    val gyroHistoryY: StateFlow<List<Float>> = _gyroHistoryY.asStateFlow()

    private val _gyroHistoryZ = MutableStateFlow<List<Float>>(List(40) { 0f })
    val gyroHistoryZ: StateFlow<List<Float>> = _gyroHistoryZ.asStateFlow()

    private var packetCounter = 0L

    // UUIDs for low-latency BLE transmission service
    val SERVICE_UUID = UUID.fromString("0000ffa0-0000-1000-8000-00805f9b34fb")
    val ACCEL_CHARACTERISTIC_UUID = UUID.fromString("0000ffa1-0000-1000-8000-00805f9b34fb")
    val GYRO_CHARACTERISTIC_UUID = UUID.fromString("0000ffa2-0000-1000-8000-00805f9b34fb")

    // Protocol options
    val protocols = listOf(
        "BLE L2CAP Channel",       // Raw, ultra low latency
        "BLE GATT Notification",    // Event-driven characteristic notify
        "Bluetooth RFCOMM Socket",  // SPP classical streams
        "Secure Mock Airwave"      // Encrypted local emulation
    )

    // Callback to alert ViewModel / Audit logs
    var onPacketDispatched: ((MotionPacket) -> Unit)? = null

    fun setStreamingActive(active: Boolean) {
        _isStreaming.value = active
        if (!active) {
            packetCounter = 0
            _totalPackets.value = 0
            _totalBytes.value = 0
        }
    }

    fun setFrequency(hz: Int) {
        _selectedFrequency.value = hz
    }

    fun setProtocol(protocol: String) {
        if (protocols.contains(protocol)) {
            _selectedProtocol.value = protocol
        }
    }

    fun pushRawData(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        if (!_isStreaming.value) return

        packetCounter++
        _totalPackets.value = packetCounter

        // Each packet carries: timestamp(8B), accel(12B), gyro(12B), header(4B) = ~36 bytes raw
        val packetSize = if (_selectedProtocol.value.contains("L2CAP")) 32 else 48
        _totalBytes.value += packetSize

        // Fluctuate latency slightly based on selected protocol
        val baseLatency = when (_selectedProtocol.value) {
            "BLE L2CAP Channel" -> 1.5f + kotlin.random.Random.nextFloat() * 0.5f
            "BLE GATT Notification" -> 3.2f + kotlin.random.Random.nextFloat() * 0.9f
            "Bluetooth RFCOMM Socket" -> 4.8f + kotlin.random.Random.nextFloat() * 1.5f
            else -> 0.8f + kotlin.random.Random.nextFloat() * 0.2f
        }
        _currentLatencyMs.value = baseLatency

        // Update rolling histories (maintain last 40 samples)
        _accelHistoryX.value = (_accelHistoryX.value.drop(1) + ax)
        _accelHistoryY.value = (_accelHistoryY.value.drop(1) + ay)
        _accelHistoryZ.value = (_accelHistoryZ.value.drop(1) + az)

        _gyroHistoryX.value = (_gyroHistoryX.value.drop(1) + gx)
        _gyroHistoryY.value = (_gyroHistoryY.value.drop(1) + gy)
        _gyroHistoryZ.value = (_gyroHistoryZ.value.drop(1) + gz)

        // Occasionally dispatch packet callback for console / audits (e.g. every 10 packets to avoid drowning the UI log thread)
        if (packetCounter % 10L == 0L) {
            val packet = MotionPacket(
                id = packetCounter,
                timestamp = System.currentTimeMillis(),
                ax = ax,
                ay = ay,
                az = az,
                gx = gx,
                gy = gy,
                gz = gz,
                protocol = _selectedProtocol.value,
                frequencyHz = _selectedFrequency.value,
                latencyMs = baseLatency
            )
            onPacketDispatched?.invoke(packet)
        }
    }

    // Helper to generate a range of coordinates for clean background grids or reference lines
    fun getFrequencyIntervalMs(hz: Int): Long {
        return (1000L / hz).coerceAtLeast(10L)
    }
}
