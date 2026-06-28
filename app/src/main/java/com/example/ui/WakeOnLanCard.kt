package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WolDevice
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WakeOnLanCard(viewModel: DeviceAPIViewModel) {
    val wolDevices by viewModel.wolDevices.collectAsState()
    var isAddingDevice by remember { mutableStateOf(false) }
    
    // Form States
    var nameInput by remember { mutableStateOf("") }
    var macInput by remember { mutableStateOf("") }
    var ipInput by remember { mutableStateOf("255.255.255.255") }
    var portInput by remember { mutableStateOf("9") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Helper MAC address validator
    fun validateMac(mac: String): Boolean {
        val clean = mac.replace("-", ":").trim()
        val regex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return regex.matches(clean)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ChocolateBorder, RoundedCornerShape(16.dp))
            .testTag("wake_on_lan_card"),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ChromeYellow.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Wake on LAN Icon",
                            tint = ChromeYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "WAKE-ON-LAN REMOTE",
                            color = ChromeYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Hardware Power Controller",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Add button
                IconButton(
                    onClick = {
                        isAddingDevice = !isAddingDevice
                        errorMessage = null
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isAddingDevice) CyanPrimary.copy(alpha = 0.15f) else ChocolateBorder.copy(alpha = 0.5f))
                        .testTag("wol_toggle_add_btn")
                ) {
                    Icon(
                        imageVector = if (isAddingDevice) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Toggle Add Device",
                        tint = if (isAddingDevice) CyanPrimary else ChromeYellow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Add Device Form
            AnimatedVisibility(
                visible = isAddingDevice,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ChocolateSurface),
                    border = BorderStroke(0.5.dp, ChocolateBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ADD NEW TARGET MACHINE",
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        // Name input
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Device Name (e.g. Workstation PC)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ChromeYellow,
                                unfocusedBorderColor = ChocolateBorder,
                                focusedLabelColor = ChromeYellow,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wol_add_name_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // MAC address input
                        OutlinedTextField(
                            value = macInput,
                            onValueChange = { input ->
                                // Auto upper-case and filter invalid characters
                                val uppercase = input.uppercase().filter { it.isLetterOrDigit() || it == ':' || it == '-' }
                                macInput = uppercase
                            },
                            label = { Text("MAC Address (e.g. AB:CD:EF:12:34:56)", fontSize = 11.sp) },
                            placeholder = { Text("00:11:22:33:44:55", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.5f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ChromeYellow,
                                unfocusedBorderColor = ChocolateBorder,
                                focusedLabelColor = ChromeYellow,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wol_add_mac_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // Subrow for IP & Port
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = ipInput,
                                onValueChange = { ipInput = it },
                                label = { Text("Broadcast IP", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ChromeYellow,
                                    unfocusedBorderColor = ChocolateBorder,
                                    focusedLabelColor = ChromeYellow,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .testTag("wol_add_ip_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                            )

                            OutlinedTextField(
                                value = portInput,
                                onValueChange = { portInput = it.filter { char -> char.isDigit() } },
                                label = { Text("Port", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ChromeYellow,
                                    unfocusedBorderColor = ChocolateBorder,
                                    focusedLabelColor = ChromeYellow,
                                    unfocusedLabelColor = TextSecondary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wol_add_port_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                            )
                        }

                        // Error message
                        errorMessage?.let { error ->
                            Text(
                                text = error,
                                color = CyberRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                if (nameInput.trim().isEmpty()) {
                                    errorMessage = "Please enter a device name"
                                } else if (!validateMac(macInput)) {
                                    errorMessage = "Invalid MAC Address format. Use XX:XX:XX:XX:XX:XX"
                                } else {
                                    val port = portInput.toIntOrNull() ?: 9
                                    viewModel.addWolDevice(
                                        name = nameInput.trim(),
                                        mac = macInput.trim().replace("-", ":"),
                                        broadcastIp = ipInput.trim().ifEmpty { "255.255.255.255" },
                                        port = port
                                    )
                                    // Reset and collapse
                                    nameInput = ""
                                    macInput = ""
                                    ipInput = "255.255.255.255"
                                    portInput = "9"
                                    errorMessage = null
                                    isAddingDevice = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ChromeYellow, contentColor = ObsidianBackground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wol_add_submit_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Target Node", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Divider
            HorizontalDivider(color = ChocolateBorder, thickness = 0.5.dp)

            // Devices list
            if (wolDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No Wake-on-LAN target devices configured.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    wolDevices.forEach { device ->
                        WolDeviceRow(device = device, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun WolDeviceRow(device: WolDevice, viewModel: DeviceAPIViewModel) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val isRecentlyWoken = remember(device.lastWoken) {
        val last = device.lastWoken
        last != null && (System.currentTimeMillis() - last < 12000)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wol_device_item_${device.id}"),
        colors = CardDefaults.cardColors(containerColor = ChocolateSurface),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            0.5.dp, 
            if (isRecentlyWoken) ElectricGreen.copy(alpha = 0.6f) 
            else if (device.isWaking) CyanPrimary.copy(alpha = 0.6f) 
            else ChocolateBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Device Icon with animated glow
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOutBack),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecentlyWoken) ElectricGreen.copy(alpha = 0.15f)
                            else if (device.isWaking) CyanPrimary.copy(alpha = 0.15f)
                            else TextSecondary.copy(alpha = 0.08f)
                        )
                        .border(
                            1.dp,
                            if (isRecentlyWoken) ElectricGreen.copy(alpha = 0.4f)
                            else if (device.isWaking) CyanPrimary.copy(alpha = 0.4f)
                            else Color.Transparent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "Desktop Machine",
                        tint = if (isRecentlyWoken) ElectricGreen
                               else if (device.isWaking) CyanPrimary
                               else TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .then(
                                if (device.isWaking) Modifier.background(Color.Transparent) // animate if waking?
                                else Modifier
                            )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = device.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "MAC: ${device.mac}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BROADCAST: ${device.broadcastIp}:${device.port}",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    // Connection / Last Woken badge
                    if (device.isWaking) {
                        Text(
                            text = "⚡ BROADCASTING MAGIC PACKET...",
                            color = CyanPrimary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (isRecentlyWoken) {
                        Text(
                            text = "✓ MAGIC PACKET DISPATCHED SUCCESSFUL",
                            color = ElectricGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (device.lastWoken != null) {
                        Text(
                            text = "Last woke at ${formatter.format(Date(device.lastWoken))}",
                            color = TextSecondary,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Wake Power Button
                IconButton(
                    onClick = { viewModel.sendWolPacket(device.id) },
                    enabled = !device.isWaking,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecentlyWoken) ElectricGreen.copy(alpha = 0.15f)
                            else if (device.isWaking) Color.Black
                            else ChromeYellow.copy(alpha = 0.12f)
                        )
                        .testTag("wol_wake_btn_${device.id}")
                ) {
                    if (device.isWaking) {
                        CircularProgressIndicator(
                            color = CyanPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isRecentlyWoken) Icons.Default.Check else Icons.Default.FlashOn,
                            contentDescription = "Send Magic Packet",
                            tint = if (isRecentlyWoken) ElectricGreen else ChromeYellow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Delete Button (Only if it's not a preconfigured default device)
                if (device.id != "wol_mac_studio" && device.id != "wol_gaming_pc" && device.id != "wol_nas_server") {
                    IconButton(
                        onClick = { viewModel.deleteWolDevice(device.id) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("wol_delete_btn_${device.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Device",
                            tint = CyberRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
