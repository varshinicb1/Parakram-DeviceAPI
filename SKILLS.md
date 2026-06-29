# Parakram Pocket Edge Server - Agent Tool Skills

This file provides the official **MCP Tool and Agentic Skill declarations** for Parakram Pocket Edge Server. AI agents can use this file as a semantic prompt registry to interact with the Android hardware layer, manage SQLite dynamic schemas, and control network topologies.

---

## 💾 Storage & Filesystem Skills

### 1. `get_storage_status`
* **Description**: Retrieve disk storage allocations, usage percentages, and low storage warnings.
* **Endpoint**: `GET /api/hardware/storage`
* **Headers**: `X-Agent-Key: <key>`
* **Response Payload**:
  ```json
  {
    "success": true,
    "total_space_bytes": 128000000000,
    "free_space_bytes": 45000000000,
    "usable_space_bytes": 45000000000,
    "used_space_bytes": 83000000000,
    "usage_percent": 64.84,
    "storage_path": "/data/user/0/com.aistudio.deviceapi.vtxqza/files",
    "low_storage_state": false
  }
  ```

### 2. `list_files`
* **Description**: List files stored inside the secure Parakram edge container sandbox.
* **Endpoint**: `GET /api/files`
* **Headers**: `X-Agent-Key: <key>`
* **Response Payload**:
  ```json
  {
    "files": ["database_dump.sqlite", "system_log_2026.txt"]
  }
  ```

---

## 🌡️ Thermal & Load Management Skills

### 1. `get_thermal_status`
* **Description**: Query system thermal throttling states, CPU temperature, and battery temperature. Use this to determine if background processes need to be throttled to prevent overheating.
* **Endpoint**: `GET /api/hardware/thermal`
* **Headers**: `X-Agent-Key: <key>`
* **Response Payload**:
  ```json
  {
    "success": true,
    "thermal_status_code": 0,
    "thermal_status_string": "NONE",
    "cpu_temperature_celsius": 38.5,
    "battery_temperature_celsius": 32.2,
    "is_overheating": false,
    "recommended_action": "OPTIMAL: DEVICE IS OPERATING WELL WITHIN SAFE THERMAL BOUNDS"
  }
  ```

---

## 🔌 Connection & Topology Skills

### 1. `toggle_network_state`
* **Description**: Programmatically toggle Wi-Fi or cellular networks on the target device. Excellent for modeling packet loss, off-grid scenarios, or testing automation fallbacks.
* **Endpoint**: `POST /api/hardware/network/toggle`
* **Headers**: `X-Agent-Key: <key>`
* **Payload**:
  ```json
  {
    "type": "wifi", // "wifi" or "cellular"
    "enabled": false
  }
  ```
* **Response Payload**:
  ```json
  {
    "success": true,
    "message": "Wi-Fi toggled successfully via system API.",
    "method_used": "WifiManager API",
    "current_state": false
  }
  ```

---

## 🗄️ Database Skills (UTAP Protocol)

AI Agents can create, query, insert, and delete records dynamically using the **Universal agentic Table Access Protocol** without writing raw SQL.

### 1. `utap_define_schema`
* **Description**: Declare a table structure.
* **Endpoint**: `POST /api/db/utap/schema`
* **Payload**:
  ```json
  {
    "table": "telemetry_logs",
    "columns": [
      "id INTEGER PRIMARY KEY AUTOINCREMENT",
      "device_id TEXT",
      "cpu_load REAL",
      "temp REAL",
      "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
    ]
  }
  ```

### 2. `utap_create_record`
* **Description**: Insert structured parameters.
* **Endpoint**: `POST /api/db/utap/create`
* **Payload**:
  ```json
  {
    "table": "telemetry_logs",
    "data": {
      "device_id": "nexus_9_edge",
      "cpu_load": 14.2,
      "temp": 38.5
    }
  }
  ```

### 3. `utap_read_records`
* **Description**: Read matched parameters safely.
* **Endpoint**: `POST /api/db/utap/read`
* **Payload**:
  ```json
  {
    "table": "telemetry_logs",
    "where": "cpu_load > 10.0",
    "orderBy": "timestamp DESC",
    "limit": 5
  }
  ```
