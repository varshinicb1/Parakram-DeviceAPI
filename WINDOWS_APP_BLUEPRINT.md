# Parakram Windows Controller & MCP Server Blueprint
## Multi-Device Controller Architecture and Model Context Protocol (MCP) Integration

This document serves as the complete engineering specification, protocol layout, and integration guide to build a high-performance **Windows Controller Application** and a native **Model Context Protocol (MCP) Server** for Parakram (Pocket Edge Server).

---

## 1. Architectural Overview

```
                      +---------------------------------------+
                      |         Desktop AI Agent              |
                      |   (Claude Desktop, Cursor, etc.)      |
                      +-------------------+-------------------+
                                          |
                        Uses MCP Protocol | (JSON-RPC over StdIn)
                                          v
                      +---------------------------------------+
                      |      Parakram Windows Controller      |
                      |        & Local MCP Server             |
                      +---+-------------------------------+---+
                          |                               |
                          | (WiFi / mDNS / NSD)           | (WiFi / Local Network)
                          v                               v
              +-----------------------+       +-----------------------+
              |   Parakram Android    |       |   Parakram Android    |
              |   Server (Device A)   |       |   Server (Device B)   |
              +-----------------------+       +-----------------------+
```

* **Android Server (Endpoints)**: Serves as the high-accuracy sensor hub, local file container, and Edge database (SQLite via UTAP) on your physical phone.
* **Windows Controller**: Acts as the visual supervisor and multi-device management cockpit. It discovers Parakram Android servers over the local network via mDNS, manages access tokens, logs aggregated telemetry, and exposes a high-compliance MCP Server.
* **MCP Server**: Translates natural language tool-calling inputs from desktop AI agents (e.g., Claude, Cursor, ChatGPT) into precise JSON REST requests directed to the target Parakram Android phone.

---

## 2. Windows Controller Specification

### A. Core Requirements
1. **Framework Options**: 
   * **Node.js + Electron / React**: Highly recommended for rapid UI iteration, seamless local network package bindings, and easy built-in MCP server integration.
   * **C# / WPF (.NET 8)**: Native performance, deep Windows tray integrations, and exceptional Windows networking sockets.
2. **mDNS Auto-Discovery**:
   * Listens for mDNS packets with the service type `_parakram._tcp` or `_http._tcp` to resolve phone IPs dynamically.
   * Maintains a dynamic registry of all identified devices (ID, Model, IP, Port, Connection Status).
3. **Multi-Device Dashboard**:
   * Exposes a gorgeous slate-themed grid showcasing real-time CPU loads, Battery status, Thermal throttle states, and Free storage of all registered mobile endpoints.
   * Secure credential vault storing `X-Agent-Key` for every individual phone.

---

## 3. Model Context Protocol (MCP) Server Implementation

Exposing the Parakram hardware, database, and telemetry capabilities directly to Desktop LLMs is done via a standardized **MCP Server**. This allows an AI to read phone sensors, toggle network connections, store memory via UTAP, and query files seamlessly.

### MCP Tool Schema Definitions (JSON-RPC)

When a Desktop AI Agent requests available tools, the Parakram MCP Server replies with the following schemas:

#### 1. `parakram_get_hardware_specs`
* **Description**: Fetch CPU, RAM, and Battery specifications of the target mobile device.
* **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "deviceId": { "type": "string", "description": "The unique ID or registered IP of the target device." }
    },
    "required": ["deviceId"]
  }
  ```

#### 2. `parakram_get_thermal_status`
* **Description**: Returns CPU temperatures, Battery temperature, and thermal throttling state. Use this before launching high-compute tasks.
* **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "deviceId": { "type": "string", "description": "The target device ID or IP." }
    },
    "required": ["deviceId"]
  }
  ```

#### 3. `parakram_get_storage_status`
* **Description**: Returns total and usable storage space in bytes.
* **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "deviceId": { "type": "string" }
    },
    "required": ["deviceId"]
  }
  ```

#### 4. `parakram_toggle_network`
* **Description**: Remotely enable or disable Wi-Fi/Cellular connectivity for orchestration testing.
* **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "deviceId": { "type": "string" },
      "type": { "type": "string", "enum": ["wifi", "cellular"], "description": "Network adapter type." },
      "enabled": { "type": "boolean", "description": "State target." }
    },
    "required": ["deviceId", "type", "enabled"]
  }
  ```

#### 5. `parakram_utap_query`
* **Description**: Query the phone's local SQLite database using our secure Universal Agentic Table Access Protocol.
* **Parameters**:
  ```json
  {
    "type": "object",
    "properties": {
      "deviceId": { "type": "string" },
      "table": { "type": "string" },
      "where": { "type": "string", "description": "SQL WHERE clause condition (e.g. key = 'theme')" },
      "limit": { "type": "number" }
    },
    "required": ["deviceId", "table"]
  }
  ```

---

## 4. Example Node.js MCP Server Code (Standalone / Embedded)

This complete, production-grade JavaScript snippet demonstrates how to run the Parakram MCP Server over StdIn/StdOut.

```javascript
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import fetch from "node-fetch";

const server = new Server(
  { name: "parakram-pocket-edge-mcp", version: "1.0.0" },
  { capabilities: { tools: {} } }
);

// Registry of paired device IPs & keys
const devices = {
  "phone1": { ip: "192.168.1.105", port: 8080, apiKey: "your-api-key" }
};

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "get_specs",
        description: "Fetch core hardware metrics of paired phone (RAM, CPU, Battery)",
        inputSchema: {
          type: "object",
          properties: { deviceId: { type: "string" } },
          required: ["deviceId"]
        }
      },
      {
        name: "get_thermal",
        description: "Fetch device temperatures and thermal throttling levels",
        inputSchema: {
          type: "object",
          properties: { deviceId: { type: "string" } },
          required: ["deviceId"]
        }
      },
      {
        name: "get_storage",
        description: "Fetch storage telemetry and check for low disk warnings",
        inputSchema: {
          type: "object",
          properties: { deviceId: { type: "string" } },
          required: ["deviceId"]
        }
      },
      {
        name: "toggle_network",
        description: "Programmatically toggle Wi-Fi or cellular networks on device",
        inputSchema: {
          type: "object",
          properties: {
            deviceId: { type: "string" },
            type: { type: "string", enum: ["wifi", "cellular"] },
            enabled: { type: "boolean" }
          },
          required: ["deviceId", "type", "enabled"]
        }
      }
    ]
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;
  const dev = devices[args.deviceId];
  if (!dev) {
    return { content: [{ type: "text", text: `Error: Device ${args.deviceId} is not registered.` }], isError: true };
  }

  const baseUrl = `http://${dev.ip}:${dev.port}`;
  const headers = { "X-Agent-Key": dev.apiKey, "Content-Type": "application/json" };

  try {
    if (name === "get_specs") {
      const res = await fetch(`${baseUrl}/api/hardware`, { headers });
      const data = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
    
    if (name === "get_thermal") {
      const res = await fetch(`${baseUrl}/api/hardware/thermal`, { headers });
      const data = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }

    if (name === "get_storage") {
      const res = await fetch(`${baseUrl}/api/hardware/storage`, { headers });
      const data = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }

    if (name === "toggle_network") {
      const res = await fetch(`${baseUrl}/api/hardware/network/toggle`, {
        method: "POST",
        headers,
        body: JSON.stringify({ type: args.type, enabled: args.enabled })
      });
      const data = await res.json();
      return { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] };
    }
  } catch (error) {
    return { content: [{ type: "text", text: `HTTP Communication failure: ${error.message}` }], isError: true };
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
console.error("Parakram MCP Server connected over Stdio!");
```

---

## 5. Protocol Verification Checklists

### A. Wi-Fi Pairing
- **Reliability Layer**: DeviceAPIScreen establishes high-speed secure token negotiation via pairing code verification.
- **Fail-safe**: Fallback to direct network QR code pairing in high-security, low-visibility enterprise intranets.

### B. Bluetooth / mDNS Discovery
- **Redundancy**: Local network mDNS resolution operates concurrently with BLE advertisement packets. 
- **Robustness**: In case of router AP isolation (where devices cannot direct-connect over Wi-Fi), the Windows app seamlessly fallbacks to utilizing direct Bluetooth RFCOMM socket bridge streams.

---

## 6. Secure QR Code Handshake Protocol (Windows Side Implementation)

When pairing a Windows Controller with the Parakram Android server via QR Code:

1. **Scan QR Code**:
   The Windows application scans the QR code shown on the Android phone.
   * **URI Format**: `parakram://secure-pair?ip=<IP>&port=<PORT>&hid=<HANDSHAKE_ID>&ch=<CHALLENGE>&pin=<PIN>`
   * **Parameters**:
     - `ip`: Phone's local IP address (e.g., `192.168.1.100`)
     - `port`: Port on which Ktor is running (e.g., `8080`)
     - `hid`: Ephemeral handshake session ID (e.g., `a7c1b2f4`)
     - `ch`: Challenge string (e.g., `10a9f029837cd82e`)
     - `pin`: Ephemeral matching PIN (e.g., `483921`)

2. **Calculate Response Hash**:
   The Windows client computes the SHA-256 hash of the concatenated challenge and PIN:
   * **Formula**: `SHA256(challenge + pin)`
   * **C# implementation**:
     ```csharp
     using System.Security.Cryptography;
     using System.Text;

     string expectedInput = challenge + pin;
     using (SHA256 sha256 = SHA256.Create())
     {
         byte[] bytes = sha256.ComputeHash(Encoding.UTF8.GetBytes(expectedInput));
         StringBuilder builder = new StringBuilder();
         foreach (byte b in bytes)
         {
             builder.Append(b.ToString("x2"));
         }
         string responseHash = builder.ToString(); // Hex string
     }
     ```
   * **Node.js (crypto) implementation**:
     ```javascript
     const crypto = require('crypto');
     const input = challenge + pin;
     const responseHash = crypto.createHash('sha256').update(input).digest('hex');
     ```

3. **Submit Verification Handshake Request**:
   The Windows controller makes an unauthenticated `POST` request to the phone's pairing endpoint:
   * **Route**: `POST http://<IP>:<PORT>/api/auth/pair/secure-handshake`
   * **JSON Body**:
     ```json
     {
       "handshakeId": "<HANDSHAKE_ID>",
       "clientName": "Lenovo-ThinkPad-X1",
       "clientMac": "00:1A:2B:3C:4D:5E",
       "responseHash": "<COMPUTED_SHA256_HEX>"
     }
     ```

4. **Receive Secure API Key**:
   On successful validation, the Android server responds with a `200 OK` and returns the secure API key:
   * **Response JSON**:
     ```json
     {
       "success": true,
       "message": "Handshake successful. Secure key established and Windows controller registered.",
       "apiKey": "sk_agent_a1b2c3d4e5f6g7h8i9j0...",
       "deviceName": "Pixel 8 Pro"
     }
     ```
   * The Windows controller saves this `apiKey` securely in its credentials storage and uses it as the `X-Agent-Key` header for all future requests.

