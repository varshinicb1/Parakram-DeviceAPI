# Parakram - Pocket Edge Server & Developer Hub

<p align="center">
  <img src="github_banner.jpg" alt="Parakram Banner" width="100%">
</p>

---

## 🚀 The Billion-Dollar Pocket Edge Server Architecture

**Parakram** is a high-performance, localized edge-computing server running natively on Android. It transforms your mobile phone into an asynchronous hardware extension and secure telemetry proxy for desktop AI agents, developers, and remote orchestration workflows. 

By exposing a secure local REST API, Parakram enables desktop agents to interact with on-device hardware sensors, local filesystems, real-time terminals, and mobile battery states without sacrificing privacy or relying on heavy cloud infrastructures.

---

## 🛠️ Key Architectural Pillars & Innovations

### 1. The UTAP Protocol (Universal agentic Table Access Protocol)
To eliminate the need for error-prone raw SQL queries transmitted over networks by AI agents, Parakram introduces **UTAP**. 
UTAP is a declarative, secure CRUD interface exposing unified REST endpoints to safely create tables, insert logs, and fetch database states using parameterized statements.

#### 💡 Dynamic Schema Declaration
* **Route**: `POST /api/db/utap/schema`
* **Headers**: `X-Agent-Key: <your_key>`
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

#### ✍️ Secure Parameterized Insert
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

#### 🔍 Dynamic Sanitized Query
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

---

### 2. Battery Sentinel & Multi-Sensor Telemetry
* **High-Precision Logs**: Continuously logs charging state, current voltage, temperature, capacity, and system health.
* **Low-Latency Streams**: Exposes state variables via reactive shared flows and Ktor endpoints so your automation agents can adjust workloads based on thermal bounds or battery reserves.

---

### 3. Play Store Compliance (Android 14+ API 34/35)
Parakram is engineered for public Play Store compliance, strictly adhering to foreground service permissions:
* **`dataSync`**: Used by the main `ServerForegroundService` to handle asynchronous file exchange and automation streams.
* **`specialUse` with Property Subtype**: Used by the `BatteryMonitoringService` for live hardware telemetry logging, including the mandatory Play-Store-compliant property tag.
  ```xml
  <service android:name=".data.BatteryMonitoringService" android:foregroundServiceType="specialUse">
      <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" 
                android:value="Telemetry monitoring of battery state and developer sensors to expose to desktop agents over dynamic endpoint." />
  </service>
  ```

---

## 📈 Scalability, Analytics & Monetization Paths

1. **Firebase Analytics & Play Console Logging**:
   * Critical stability events (e.g., `stability_report_logged`) are registered automatically on database sync, server lifecycle changes, and developer issue logs.
   * On-device support issues are filed inside the **Support & Report Center** UI, bundling real-time device profiling data.
2. **Google Mobile Ads SDK (AdMob Ready)**:
   * Optimized for localized developer promotion banners and professional service sponsorships without interrupting core edge server cycles.

---

## 🛠️ Build & Installation Guide

1. **Check System Compilation**:
   ```bash
   # Run the unified build sequence
   gradle assembleDebug
   ```
2. **Verify JVM & Screenshot Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. **Run on Device / Emulator**:
   * Install the generated APK found in `/app/build/outputs/apk/debug/`.
   * Enable desired server modules (Files, Database, Terminal, Media Stream).
   * Authorize necessary permissions via the streamlined **"GRANT ALL PERMISSIONS"** single-line layout.

---

## 📝 Continuous AI Development

For instructions on how to extend, deploy, or automate Parakram with next-generation coding agents, see the companion [AGENTS.md](./AGENTS.md) file.
