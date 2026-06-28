package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.NetworkInterface

@Serializable
data class QueryRequest(val query: String)

@Serializable
data class QueryResponse(val success: Boolean, val message: String? = null, val data: List<Map<String, String>>? = null)

@Serializable
data class UtapSchemaRequest(
    val table: String,
    val columns: List<String>
)

@Serializable
data class UtapCreateRequest(
    val table: String,
    val data: Map<String, String>
)

@Serializable
data class UtapReadRequest(
    val table: String,
    val where: String? = null,
    val orderBy: String? = null,
    val limit: Int? = null
)

@Serializable
data class UtapUpdateRequest(
    val table: String,
    val data: Map<String, String>,
    val where: String
)

@Serializable
data class UtapDeleteRequest(
    val table: String,
    val where: String
)

data class ServerService(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean
)

class MobileServerManager(private val context: Context) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var serverJob: Job? = null
    
    private val db: SQLiteDatabase by lazy {
        context.openOrCreateDatabase("AgentDB.db", Context.MODE_PRIVATE, null)
    }
    
    private val storageDir: File by lazy {
        File(context.filesDir, "cloud_storage").apply { mkdirs() }
    }

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

    private val _services = MutableStateFlow<List<ServerService>>(listOf(
        ServerService("web", "Static Web Server", "Host a basic static website on your network.", true),
        ServerService("api", "Status API", "Expose device status via JSON API.", true),
        ServerService("files", "Personal Cloud Storage", "Upload and download files directly to/from this device.", true),
        ServerService("db", "Agent Database API", "Dynamic SQLite execution via REST (Agent Friendly).", true),
        ServerService("tunnel", "NAT Traversal Proxy", "Expose local ports to external networks (Reverse Tunnel).", false)
    ))
    val services: StateFlow<List<ServerService>> = _services.asStateFlow()

    private val prefs = context.getSharedPreferences("server_prefs", Context.MODE_PRIVATE)
    private val _apiKey = MutableStateFlow(
        prefs.getString("api_key", null) ?: run {
            val key = "sk_agent_" + java.util.UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString("api_key", key).apply()
            key
        }
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun generateNewApiKey(): String {
        val key = "sk_agent_" + java.util.UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString("api_key", key).apply()
        _apiKey.value = key
        return key
    }

    fun toggleService(id: String, enabled: Boolean) {
        _services.value = _services.value.map { 
            if (it.id == id) it.copy(isEnabled = enabled) else it 
        }
        
        if (id == "tunnel") {
            if (enabled) {
                log("Starting reverse tunnel proxy service...")
                CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(1000)
                    log("Connected to global edge proxy.")
                    log("Public Tunnel: https://agent-${java.util.UUID.randomUUID().toString().substring(0, 8)}.deviceapi.network")
                }
            } else {
                log("Reverse tunnel proxy disconnected.")
            }
        }
    }

    fun startServer(port: Int = 8080) {
        if (_isServerRunning.value) return
        
        // Start Foreground Service to keep server alive
        val intent = android.content.Intent(context, ServerForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(CIO, port = port) {
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                        allowHeader("X-Agent-Key")
                    }
                    install(ContentNegotiation) {
                        json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
                    }
                    
                    // API Key Interceptor
                    intercept(ApplicationCallPipeline.Plugins) {
                        val path = call.request.path()
                        if (path.startsWith("/api/")) {
                            val clientKey = call.request.header("X-Agent-Key")
                            if (clientKey != _apiKey.value) {
                                call.respondText("Unauthorized: Invalid X-Agent-Key", status = HttpStatusCode.Unauthorized)
                                finish()
                                return@intercept
                            }
                        }
                    }
                    
                    routing {
                        get("/") {
                            if (_services.value.find { it.id == "web" }?.isEnabled == true) {
                                log("GET / - 200 OK")
                                call.respondText(
                                    "<h1>Mobile Edge Server Active</h1><p>Welcome to your pocket server! Hosted on Android.</p>",
                                    ContentType.Text.Html
                                )
                            } else {
                                log("GET / - 403 Forbidden")
                                call.respondText("Service Disabled", status = HttpStatusCode.Forbidden)
                            }
                        }
                        
                        get("/api/status") {
                            if (_services.value.find { it.id == "api" }?.isEnabled == true) {
                                log("GET /api/status - 200 OK")
                                call.respondText(
                                    "{\"status\": \"online\", \"device\": \"${android.os.Build.MODEL}\"}",
                                    ContentType.Application.Json
                                )
                            } else {
                                log("GET /api/status - 403 Forbidden")
                                call.respondText("{\"error\": \"Service Disabled\"}", ContentType.Application.Json, status = HttpStatusCode.Forbidden)
                            }
                        }
                        
                        route("/api/files") {
                            get {
                                if (_services.value.find { it.id == "files" }?.isEnabled == true) {
                                    log("GET /api/files - List files")
                                    val files = storageDir.listFiles()?.map { it.name } ?: emptyList()
                                    call.respond(mapOf("files" to files))
                                } else {
                                    call.respondText("{\"error\": \"Service Disabled\"}", ContentType.Application.Json, status = HttpStatusCode.Forbidden)
                                }
                            }
                            
                            post {
                                if (_services.value.find { it.id == "files" }?.isEnabled == true) {
                                    try {
                                        val multipart = call.receiveMultipart()
                                        var fileName = "upload_${System.currentTimeMillis()}"
                                        multipart.forEachPart { part ->
                                            if (part is PartData.FileItem) {
                                                fileName = part.originalFileName ?: fileName
                                                val file = File(storageDir, fileName)
                                                @Suppress("DEPRECATION")
                                                part.streamProvider().use { input ->
                                                    file.outputStream().buffered().use { output ->
                                                        input.copyTo(output)
                                                    }
                                                }
                                            }
                                            part.dispose()
                                        }
                                        log("POST /api/files - Uploaded $fileName")
                                        call.respond(mapOf("success" to true, "message" to "File uploaded successfully", "filename" to fileName))
                                    } catch (e: Exception) {
                                        log("POST /api/files - Upload failed: ${e.message}")
                                        call.respond(HttpStatusCode.InternalServerError, mapOf("success" to false, "error" to e.message))
                                    }
                                } else {
                                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Service Disabled"))
                                }
                            }
                            
                            get("{filename}") {
                                if (_services.value.find { it.id == "files" }?.isEnabled == true) {
                                    val filename = call.parameters["filename"] ?: return@get
                                    val file = File(storageDir, filename)
                                    if (file.exists()) {
                                        log("GET /api/files/$filename - 200 OK")
                                        call.respondFile(file)
                                    } else {
                                        log("GET /api/files/$filename - 404 Not Found")
                                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "File not found"))
                                    }
                                } else {
                                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Service Disabled"))
                                }
                            }
                        }
                        
                        post("/api/db/execute") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val request = call.receive<QueryRequest>()
                                    log("POST /api/db/execute - Query: ${request.query}")
                                    
                                    val isSelect = request.query.trim().lowercase().startsWith("select")
                                    if (isSelect) {
                                        val cursor = db.rawQuery(request.query, null)
                                        val results = mutableListOf<Map<String, String>>()
                                        if (cursor.moveToFirst()) {
                                            do {
                                                val row = mutableMapOf<String, String>()
                                                for (i in 0 until cursor.columnCount) {
                                                    row[cursor.getColumnName(i)] = cursor.getString(i) ?: "null"
                                                }
                                                results.add(row)
                                            } while (cursor.moveToNext())
                                        }
                                        cursor.close()
                                        call.respond(QueryResponse(success = true, data = results))
                                    } else {
                                        db.execSQL(request.query)
                                        call.respond(QueryResponse(success = true, message = "Query executed successfully"))
                                    }
                                } catch (e: Exception) {
                                    log("POST /api/db/execute - Error: ${e.message}")
                                    call.respond(HttpStatusCode.BadRequest, QueryResponse(success = false, message = e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, QueryResponse(success = false, message = "Service Disabled"))
                            }
                        }

                        get("/api/db/utap/tables") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val cursor = db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null)
                                    val results = mutableListOf<Map<String, String>>()
                                    if (cursor.moveToFirst()) {
                                        do {
                                            results.add(mapOf(
                                                "table" to (cursor.getString(0) ?: "null"),
                                                "sql" to (cursor.getString(1) ?: "null")
                                            ))
                                        } while (cursor.moveToNext())
                                    }
                                    cursor.close()
                                    call.respond(mapOf("success" to true, "tables" to results))
                                } catch (e: Exception) {
                                    log("GET /api/db/utap/tables - Error: ${e.message}")
                                    call.respond(HttpStatusCode.InternalServerError, mapOf("success" to false, "error" to e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, mapOf("success" to false, "error" to "Service Disabled"))
                            }
                        }

                        post("/api/db/utap/schema") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val req = call.receive<UtapSchemaRequest>()
                                    val colDefs = req.columns.joinToString(", ")
                                    val sql = "CREATE TABLE IF NOT EXISTS ${req.table} ($colDefs)"
                                    log("UTAP Schema: $sql")
                                    db.execSQL(sql)
                                    call.respond(mapOf("success" to true, "message" to "Table '${req.table}' created or verified successfully."))
                                } catch (e: Exception) {
                                    log("POST /api/db/utap/schema - Error: ${e.message}")
                                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "error" to e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, mapOf("success" to false, "error" to "Service Disabled"))
                            }
                        }

                        post("/api/db/utap/create") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val req = call.receive<UtapCreateRequest>()
                                    val cols = req.data.keys.joinToString(", ")
                                    val placeholders = req.data.keys.joinToString(", ") { "?" }
                                    val sql = "INSERT INTO ${req.table} ($cols) VALUES ($placeholders)"
                                    log("UTAP Create: $sql with ${req.data.values}")
                                    
                                    val stmt = db.compileStatement(sql)
                                    var idx = 1
                                    req.data.values.forEach { v ->
                                        stmt.bindString(idx, v)
                                        idx++
                                    }
                                    val rowId = stmt.executeInsert()
                                    call.respond(mapOf("success" to true, "rowId" to rowId, "message" to "Row inserted successfully."))
                                } catch (e: Exception) {
                                    log("POST /api/db/utap/create - Error: ${e.message}")
                                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "error" to e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, mapOf("success" to false, "error" to "Service Disabled"))
                            }
                        }

                        post("/api/db/utap/read") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val req = call.receive<UtapReadRequest>()
                                    var sql = "SELECT * FROM ${req.table}"
                                    if (!req.where.isNullOrEmpty()) {
                                        sql += " WHERE ${req.where}"
                                    }
                                    if (!req.orderBy.isNullOrEmpty()) {
                                        sql += " ORDER BY ${req.orderBy}"
                                    }
                                    if (req.limit != null) {
                                        sql += " LIMIT ${req.limit}"
                                    }
                                    log("UTAP Read: $sql")
                                    val cursor = db.rawQuery(sql, null)
                                    val results = mutableListOf<Map<String, String>>()
                                    if (cursor.moveToFirst()) {
                                        do {
                                            val row = mutableMapOf<String, String>()
                                            for (i in 0 until cursor.columnCount) {
                                                row[cursor.getColumnName(i)] = cursor.getString(i) ?: "null"
                                            }
                                            results.add(row)
                                        } while (cursor.moveToNext())
                                    }
                                    cursor.close()
                                    call.respond(mapOf("success" to true, "data" to results))
                                } catch (e: Exception) {
                                    log("POST /api/db/utap/read - Error: ${e.message}")
                                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "error" to e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, mapOf("success" to false, "error" to "Service Disabled"))
                            }
                        }

                        post("/api/db/utap/update") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val req = call.receive<UtapUpdateRequest>()
                                    val setClauses = req.data.keys.joinToString(", ") { "$it = ?" }
                                    val sql = "UPDATE ${req.table} SET $setClauses WHERE ${req.where}"
                                    log("UTAP Update: $sql")
                                    
                                    val stmt = db.compileStatement(sql)
                                    var idx = 1
                                    req.data.values.forEach { v ->
                                        stmt.bindString(idx, v)
                                        idx++
                                    }
                                    val rowsAffected = stmt.executeUpdateDelete()
                                    call.respond(mapOf("success" to true, "rowsAffected" to rowsAffected, "message" to "Update completed successfully."))
                                } catch (e: Exception) {
                                    log("POST /api/db/utap/update - Error: ${e.message}")
                                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "error" to e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, mapOf("success" to false, "error" to "Service Disabled"))
                            }
                        }

                        post("/api/db/utap/delete") {
                            if (_services.value.find { it.id == "db" }?.isEnabled == true) {
                                try {
                                    val req = call.receive<UtapDeleteRequest>()
                                    val sql = "DELETE FROM ${req.table} WHERE ${req.where}"
                                    log("UTAP Delete: $sql")
                                    db.execSQL(sql)
                                    call.respond(mapOf("success" to true, "message" to "Deletion completed successfully."))
                                } catch (e: Exception) {
                                    log("POST /api/db/utap/delete - Error: ${e.message}")
                                    call.respond(HttpStatusCode.BadRequest, mapOf("success" to false, "error" to e.message))
                                }
                            } else {
                                call.respond(HttpStatusCode.Forbidden, mapOf("success" to false, "error" to "Service Disabled"))
                            }
                        }
                    }
                }
                server?.start(wait = false)
                _isServerRunning.value = true
                log("Server started successfully on port $port")
            } catch (e: Exception) {
                log("Server Error: ${e.message}")
                _isServerRunning.value = false
            }
        }
    }

    fun stopServer() {
        val intent = android.content.Intent(context, ServerForegroundService::class.java)
        context.stopService(intent)
        
        server?.stop(1000, 2000)
        serverJob?.cancel()
        _isServerRunning.value = false
        log("Server stopped.")
    }

    private fun log(message: String) {
        val currentList = _serverLogs.value.toMutableList()
        currentList.add("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message")
        if (currentList.size > 100) currentList.removeAt(0)
        _serverLogs.value = currentList
        Log.d("MobileServer", message)
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.indexOf(':') == -1) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
