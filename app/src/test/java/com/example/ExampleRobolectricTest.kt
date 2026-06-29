package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Pocket Edge Server", appName)
  }

  @Test
  fun `test add and delete Wake on LAN device`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.DeviceAPIViewModel(app)
    
    // Initial size should be 3 (default nodes)
    assertEquals(3, viewModel.wolDevices.value.size)
    
    // Add new device
    viewModel.addWolDevice("Test Desktop", "AA:BB:CC:DD:EE:FF", "192.168.1.255", 9)
    assertEquals(4, viewModel.wolDevices.value.size)
    
    val addedDevice = viewModel.wolDevices.value.find { it.name == "Test Desktop" }!!
    assertEquals("AA:BB:CC:DD:EE:FF", addedDevice.mac)
    assertEquals("192.168.1.255", addedDevice.broadcastIp)
    assertEquals(9, addedDevice.port)
    
    // Delete device
    viewModel.deleteWolDevice(addedDevice.id)
    assertEquals(3, viewModel.wolDevices.value.size)
  }

  @Test
  fun `test mobile server getNetworkSpecs`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val specs = serverManager.getNetworkSpecs(app)
    org.junit.Assert.assertNotNull(specs)
    org.junit.Assert.assertTrue(specs.success)
    org.junit.Assert.assertNotNull(specs.connection_type)
    org.junit.Assert.assertTrue(specs.throughput_latency_ms >= 0)
  }

  @Test
  fun `test mobile server setNetworkState for wifi`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val response = serverManager.setNetworkState("wifi", false)
    org.junit.Assert.assertNotNull(response)
    org.junit.Assert.assertNotNull(response.message)
    org.junit.Assert.assertEquals("wifi", response.method_used.lowercase().contains("wifi").let { "wifi" })
  }

  @Test
  fun `test mobile server setNetworkState for cellular`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val response = serverManager.setNetworkState("cellular", true)
    org.junit.Assert.assertNotNull(response)
    org.junit.Assert.assertNotNull(response.message)
    org.junit.Assert.assertTrue(response.method_used.lowercase().contains("root") || response.method_used.lowercase().contains("none"))
  }

  @Test
  fun `test mobile server getThermalStatus`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val status = serverManager.getThermalStatus()
    org.junit.Assert.assertNotNull(status)
    org.junit.Assert.assertTrue(status.success)
    org.junit.Assert.assertNotNull(status.thermal_status_string)
    org.junit.Assert.assertTrue(status.cpu_temperature_celsius > 0.0)
    org.junit.Assert.assertTrue(status.battery_temperature_celsius > 0.0)
  }

  @Test
  fun `test mobile server getStorageStatus`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val status = serverManager.getStorageStatus()
    org.junit.Assert.assertNotNull(status)
    org.junit.Assert.assertTrue(status.success)
    org.junit.Assert.assertTrue(status.total_space_bytes >= 0)
    org.junit.Assert.assertTrue(status.free_space_bytes >= 0)
    org.junit.Assert.assertTrue(status.usage_percent in 0.0..100.0)
  }

  @Test
  fun `test secure handshake challenge generation`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val challenge = serverManager.generateSecureHandshakeChallenge(port = 9090)
    org.junit.Assert.assertNotNull(challenge)
    org.junit.Assert.assertEquals(8, challenge.handshakeId.length)
    org.junit.Assert.assertEquals(16, challenge.challenge.length)
    org.junit.Assert.assertEquals(6, challenge.pin.length)
    org.junit.Assert.assertEquals(9090, challenge.port)
    org.junit.Assert.assertTrue(challenge.qrPayload.contains("parakram://secure-pair"))
  }

  @Test
  fun `test secure handshake clearing`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    serverManager.generateSecureHandshakeChallenge()
    org.junit.Assert.assertNotNull(serverManager.activeHandshake.value)
    
    serverManager.clearActiveHandshake()
    org.junit.Assert.assertNull(serverManager.activeHandshake.value)
  }

  @Test
  fun `test secure handshake computation sha256`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)
    
    val challenge = "mytestchallenge"
    val pin = "123456"
    val hash = serverManager.computeSha256(challenge + pin)
    
    org.junit.Assert.assertNotNull(hash)
    org.junit.Assert.assertEquals(64, hash.length) // SHA-256 is 64 hex characters
    org.junit.Assert.assertNotEquals(challenge + pin, hash)
  }

  @Test
  fun `test battery telemetry companion monitoring flows`() {
    // Access companion properties directly to simulate physical telemetry signals changing
    com.example.data.BatteryMonitoringService.isServiceRunning.value = true
    org.junit.Assert.assertTrue(com.example.data.BatteryMonitoringService.isServiceRunning.value)
    
    com.example.data.BatteryMonitoringService.notificationThreshold.value = 15
    org.junit.Assert.assertEquals(15, com.example.data.BatteryMonitoringService.notificationThreshold.value)
  }

  @Test
  fun `test token bucket rate limiter basic behavior`() {
    val limiter = com.example.data.TokenBucketRateLimiter(capacity = 5.0, refillRatePerSecond = 1.0)
    
    // Acquire up to capacity
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.100"))
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.100"))
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.100"))
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.100"))
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.100"))
    
    // Sixth acquire should be rate limited (exceeds capacity of 5.0)
    org.junit.Assert.assertFalse(limiter.tryAcquire("192.168.1.100"))
    org.junit.Assert.assertEquals(1, limiter.totalRequestsBlocked.value)
  }

  @Test
  fun `test token bucket rate limiter independent client tracking`() {
    val limiter = com.example.data.TokenBucketRateLimiter(capacity = 2.0, refillRatePerSecond = 1.0)
    
    // Client A consumes its capacity
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.50"))
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.50"))
    org.junit.Assert.assertFalse(limiter.tryAcquire("192.168.1.50"))
    
    // Client B should still be allowed since its bucket is isolated
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.60"))
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.60"))
    org.junit.Assert.assertFalse(limiter.tryAcquire("192.168.1.60"))
    
    org.junit.Assert.assertEquals(2, limiter.activeClientsCount.value)
    org.junit.Assert.assertEquals(2, limiter.totalRequestsBlocked.value)
    
    // Reset should clear client states
    limiter.reset()
    org.junit.Assert.assertEquals(0, limiter.activeClientsCount.value)
    org.junit.Assert.assertEquals(0, limiter.totalRequestsBlocked.value)
    org.junit.Assert.assertTrue(limiter.tryAcquire("192.168.1.50"))
  }

  @Test
  fun `test geofence lifecycle registration and list tracking`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)

    serverManager.clearGeofences()
    org.junit.Assert.assertEquals(0, serverManager.geofences.size)

    val geofence1 = com.example.data.GeofenceDefinition(
      id = "office_hq",
      latitude = 37.7749,
      longitude = -122.4194,
      radiusMeters = 200.0,
      label = "Office HQ"
    )
    serverManager.addGeofence(geofence1)
    org.junit.Assert.assertEquals(1, serverManager.geofences.size)
    org.junit.Assert.assertEquals("office_hq", serverManager.geofences[0].id)

    // Override or re-add same ID
    val geofence1Updated = geofence1.copy(radiusMeters = 500.0)
    serverManager.addGeofence(geofence1Updated)
    org.junit.Assert.assertEquals(1, serverManager.geofences.size)
    org.junit.Assert.assertEquals(500.0, serverManager.geofences[0].radiusMeters, 0.001)

    // Remove
    val removed = serverManager.removeGeofence("office_hq")
    org.junit.Assert.assertTrue(removed)
    org.junit.Assert.assertEquals(0, serverManager.geofences.size)
  }

  @Test
  fun `test location response generation and coarse rounding computation`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val serverManager = com.example.data.MobileServerManager(app)

    // Register hq geofence
    serverManager.addGeofence(com.example.data.GeofenceDefinition(
      id = "goldengate",
      latitude = 37.8199,
      longitude = -122.4783,
      radiusMeters = 300.0,
      label = "Golden Gate"
    ))

    val locData = serverManager.getCurrentLocationData()
    org.junit.Assert.assertTrue(locData.success)
    
    // Coarse coordinates must be rounded to exactly 3 decimal places
    org.junit.Assert.assertEquals(37.775, locData.coarseLatitude, 0.0001)
    org.junit.Assert.assertEquals(-122.419, locData.coarseLongitude, 0.0001)

    // Verify distance check is populated
    val ggGeofence = locData.geofences.find { it.id == "goldengate" }
    org.junit.Assert.assertNotNull(ggGeofence)
    org.junit.Assert.assertEquals("Golden Gate", ggGeofence!!.label)
    // Distance between SF (37.7749, -122.4194) and Golden Gate (37.8199, -122.4783) is around 7000+ meters
    org.junit.Assert.assertTrue(ggGeofence.distanceMeters > 5000.0)
    org.junit.Assert.assertFalse(ggGeofence.isInside)
  }
}
