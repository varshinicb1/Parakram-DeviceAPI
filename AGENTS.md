# Parakram - Pocket Edge Server & Developer Hub
## Dynamic AI Agent Architecture & Protocol Documentation

This document serves as a complete blueprint, architectural reference, and instruction set for any AI agent or systems integrator continuing the development of Parakram (Pocket Edge Server). 

---

## 1. Core Architectural Pillars

Parakram is a localized mobile edge server running a micro-architecture designed to bridge remote desktop automation, desktop AI agents, and local mobile hardware/telemetry.

### A. Core Engine Components
1. **Ktor CIO Mobile Web Server (`MobileServerManager.kt`)**: 
   - A lightweight asynchronous Kotlin web server running on CIO (Coroutine-based I/O).
   - Manages state, handles authenticated API routes, dynamic databases, real-time files, and sensor feeds.
2. **Foreground Keeping Service (`ServerForegroundService.kt`)**:
   - Keeps the server alive in the background under strict Android 14+ (API 34) and Android 15+ (API 35) power constraints.
3. **Telemetry Sentinel (`BatteryMonitoringService.kt`)**:
   - Periodically samples battery parameters, voltage, temperatures, and charging states, broadcasting via shared Kotlin `StateFlow`s.

---

## 2. Our Invented Protocol: UTAP (Universal agentic Table Access Protocol)

To eliminate the need for error-prone raw SQL queries transmitted from external AI agents, Parakram invents and implements the **UTAP** protocol. It exposes standard REST endpoints that allow AI agents to dynamically declare schemas and execute secure CRUD operations on a local SQLite database on the phone.

### UTAP API Endpoints

All UTAP endpoints are authenticated via the `X-Agent-Key` header.

#### 1. Define Table Schema
* **Route**: `POST /api/db/utap/schema`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "columns": [
      "id INTEGER PRIMARY KEY AUTOINCREMENT",
      "key TEXT UNIQUE",
      "value TEXT",
      "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP"
    ]
  }
  ```
* **Behavior**: Dynamically executes `CREATE TABLE IF NOT EXISTS` sanitizing definitions.

#### 2. Insert Record (Create)
* **Route**: `POST /api/db/utap/create`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "data": {
      "key": "user_preference_theme",
      "value": "obsidian_dark"
    }
  }
  ```
* **Behavior**: Uses parameterized SQLite statements under the hood to safely insert data and returns `rowId`.

#### 3. Query Records (Read)
* **Route**: `POST /api/db/utap/read`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "where": "key = 'user_preference_theme'",
    "orderBy": "timestamp DESC",
    "limit": 1
  }
  ```
* **Behavior**: Queries the database dynamically and returns key-value mapping rows in JSON.

#### 4. Update Records (Update)
* **Route**: `POST /api/db/utap/update`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "data": {
      "value": "nebula_cyan"
    },
    "where": "key = 'user_preference_theme'"
  }
  ```
* **Behavior**: Safely executes updates, parameterized to prevent any SQL injection.

#### 5. Delete Records (Delete)
* **Route**: `POST /api/db/utap/delete`
* **Payload**:
  ```json
  {
    "table": "agent_memory",
    "where": "id = 1"
  }
  ```

#### 6. List Dynamic Tables
* **Route**: `GET /api/db/utap/tables`
* **Response**: Returns a list of all custom developer tables and their associated SQL schemas.

---

## 3. Play Store Compliance & Android 14+ (API 34/35) Security Mandates

Android 14+ introduces tight regulations regarding foreground services to protect battery and prevent malicious apps.

### A. Declaring Proper Service Types
In `/app/src/main/AndroidManifest.xml`, both services specify strict, Play-Store-compliant foreground types:
- `ServerForegroundService` uses `dataSync` to enable real-time local file exchange and terminal command streams.
- `BatteryMonitoringService` uses `specialUse` to track ongoing sensor logs.

### B. Special Use Subtype Property (CRITICAL)
For services using `specialUse`, Google Play and the Android OS *require* a `<property>` tag to avoid instant runtime verification failure:
```xml
<service
    android:name=".data.BatteryMonitoringService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Telemetry monitoring of battery state and developer sensors to expose to desktop agents over dynamic endpoint." />
</service>
```

### C. Run-time Code Invocation
Always call `startForeground()` passing the correct service type bitmask on API Q+:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
```

---

## 4. Play Store Scaling & Monetization Guide

To transition Parakram into a high-revenue, multi-million download app, execute the following steps:

### A. Ad Revenue Integration (Google Mobile Ads SDK)
1. Add the play-services-ads library to `libs.versions.toml`:
   ```toml
   play-services-ads = { group = "com.google.android.gms", name = "play-services-ads-lite", version = "23.0.0" }
   ```
2. Initialize AdMob in `MainActivity.kt` on startup:
   ```kotlin
   MobileAds.initialize(this) {}
   ```
3. Use specialized Jetpack Compose Banner or Interstitial wrappers to display developer and pro ads to the user.

### B. Firebase Analytics & Play Console Stability Tracking
- **User Engagement**: We have integrated `firebase-analytics`. Custom events are fired on login, sever state toggles, pairing events, and issue submissions.
- **Support Flow**: Users can submit diagnostic telemetry directly through the "Support & Report Center" inside the Settings/Secure tab. Submitted tickets trigger a custom Firebase analytical event `stability_report_logged` with complete device profiles.

---

## 5. Development Guidelines for Future Agents
- **M3 Touch Targets**: Every button must be exactly `48.dp` in height (or use `Modifier.height(48.dp)`) to provide top-tier touch precision on hardware screens.
- **State Management**: Keep ViewModels free of raw database connections; always route through Ktor or local repositories.
- **Compile and Build**: Verify all changes by running `compile_applet` to confirm compilation is green.
