package com.example.data

enum class ConnectionStatus {
    CONNECTED, CONNECTING, DISCONNECTED
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val pairedDeviceName: String? = null,
    val pairedDeviceIp: String? = null,
    val securityMode: String? = null,
    val pingMs: Int = 0
)

data class Capability(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val status: String, // "Exposed", "Streaming", "Unauthorized", "Disabled"
    val category: String, // "Media", "Sensors", "Security", "Utility"
    val isStreaming: Boolean = false
)

data class AuditLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String, // "GET", "POST", "STREAM", "SYS"
    val endpoint: String,
    val caller: String,
    val status: Int, // e.g. 200, 403, 101
    val payload: String,
    val type: String // "API", "System", "Auth"
)

data class AutomationWorkflow(
    val id: String,
    val title: String,
    val description: String,
    val trigger: String,
    val action: String,
    val isActive: Boolean = false
)

data class Plugin(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val rating: Float,
    val price: String = "Free",
    val isInstalled: Boolean = false
)

data class PasskeyDevice(
    val id: String,
    val name: String,
    val os: String,
    val createdDate: String,
    val credentialId: String,
    val status: String = "Active"
)

data class WolDevice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val mac: String,
    val broadcastIp: String = "255.255.255.255",
    val port: Int = 9,
    val isWaking: Boolean = false,
    val lastWoken: Long? = null
)

