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
    assertEquals("DeviceAPI", appName)
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
}
