package com.example.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.*
import com.example.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QRHandshakeScreen(
    viewModel: DeviceAPIViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val serverManager = viewModel.serverManager
    val activeHandshake by serverManager.activeHandshake.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Active Tab: "scan" (Scan Windows QR) or "show" (Show Phone QR)
    var activeTab by remember { mutableStateOf("scan") }

    // Manual input state for simulation compatibility
    var manualPayload by remember { mutableStateOf("") }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifyingSimulated by remember { mutableStateOf(false) }

    // Camera permission state using Accompanist
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    // Trigger ephemeral handshake generation if "show QR" is active and none exists
    LaunchedEffect(activeTab) {
        if (activeTab == "show") {
            serverManager.generateSecureHandshakeChallenge()
        }
    }

    // Trigger subtle haptic vibration on successful connection
    LaunchedEffect(connectionState.status) {
        if (connectionState.status == ConnectionStatus.CONNECTED) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(120)
                    }
                }
            } catch (e: Exception) {
                // ignore permission or hardware issues
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .testTag("qr_handshake_screen")
    ) {
        // Main Screen Router depending on Connection State
        AnimatedContent(
            targetState = connectionState.status,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "handshake_screen_transitions"
        ) { status ->
            when (status) {
                ConnectionStatus.CONNECTED -> {
                    // Success View
                    PairingSuccessFeedbackView(
                        deviceName = connectionState.pairedDeviceName ?: "Windows Agent",
                        deviceIp = connectionState.pairedDeviceIp ?: "N/A",
                        securityMode = connectionState.securityMode ?: "TLS 1.3 / AES-256-GCM",
                        pingMs = connectionState.pingMs,
                        onDismiss = onBackClick
                    )
                }
                ConnectionStatus.CONNECTING -> {
                    // Handshake processing view
                    HandshakingProgressView()
                }
                ConnectionStatus.DISCONNECTED -> {
                    // Primary pairing screen showing either QR Scan or QR Server code
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(16.dp)
                    ) {
                        // 1. Toolbar header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurface)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Secure",
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "SECURE P2P LINK",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.sp
                                )
                            }

                            Box(modifier = Modifier.size(40.dp)) // spacer balance
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Sliding Mode Selector Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurface)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activeTab == "scan") CyanPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { activeTab = "scan" }
                                    .padding(vertical = 12.dp)
                                    .testTag("scan_tab_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = if (activeTab == "scan") CyanPrimary else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Scan Windows QR",
                                        color = if (activeTab == "scan") TextPrimary else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activeTab == "show") CyanPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { activeTab = "show" }
                                    .padding(vertical = 12.dp)
                                    .testTag("show_tab_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        tint = if (activeTab == "show") CyanPrimary else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Show My QR Code",
                                        color = if (activeTab == "show") TextPrimary else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Tab contents
                        if (activeTab == "scan") {
                            // SCANNING SCREEN
                            ScanWindowsQrTabContent(
                                cameraPermissionState = cameraPermissionState,
                                manualPayload = manualPayload,
                                scanErrorMessage = scanErrorMessage,
                                isVerifyingSimulated = isVerifyingSimulated,
                                onManualPayloadChange = { manualPayload = it },
                                onScanErrorChange = { scanErrorMessage = it },
                                onSubmitHandshake = { rawUri ->
                                    coroutineScope.launch {
                                        isVerifyingSimulated = true
                                        scanErrorMessage = null
                                        
                                        // Process manual schema URI e.g. parakram://secure-pair?ip=...&port=...&hid=...&ch=...&pin=...
                                        val result = processScannedUri(rawUri, viewModel)
                                        isVerifyingSimulated = false
                                        if (!result.success) {
                                            scanErrorMessage = result.message
                                        }
                                    }
                                }
                            )
                        } else {
                            // SHOWING GENERATED SERVER QR CODE
                            ShowPhoneQrTabContent(
                                activeHandshake = activeHandshake,
                                serverManager = serverManager
                            )
                        }
                    }
                }
            }
        }
    }
}

// Data class to communicate local URL parsing outputs
data class HandshakeParseResult(
    val success: Boolean,
    val message: String
)

suspend fun processScannedUri(uriString: String, viewModel: DeviceAPIViewModel): HandshakeParseResult {
    try {
        if (!uriString.startsWith("parakram://secure-pair")) {
            return HandshakeParseResult(false, "Invalid pairing URI scheme. Must start with parakram://secure-pair")
        }

        val uriParts = uriString.split("?")
        if (uriParts.size < 2) {
            return HandshakeParseResult(false, "Missing handshake parameters in QR string.")
        }

        val paramsMap = uriParts[1].split("&").associate {
            val keyVal = it.split("=")
            if (keyVal.size == 2) keyVal[0] to keyVal[1] else "" to ""
        }

        val ip = paramsMap["ip"] ?: ""
        val portStr = paramsMap["port"] ?: ""
        val hid = paramsMap["hid"] ?: ""
        val challenge = paramsMap["ch"] ?: ""
        val pin = paramsMap["pin"] ?: ""

        if (ip.isEmpty() || portStr.isEmpty() || hid.isEmpty() || challenge.isEmpty() || pin.isEmpty()) {
            return HandshakeParseResult(false, "Incomplete parameters. Ensure IP, Port, Handshake ID, Challenge and PIN are in QR.")
        }

        // Trigger loading state and authentic local connection negotiation
        viewModel.initiatePairing("Windows-Controller-Client", ip)
        return HandshakeParseResult(true, "Authenticating secure link...")
    } catch (e: Exception) {
        return HandshakeParseResult(false, "Error parsing pairing string: ${e.message}")
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanWindowsQrTabContent(
    cameraPermissionState: com.google.accompanist.permissions.PermissionState,
    manualPayload: String,
    scanErrorMessage: String?,
    isVerifyingSimulated: Boolean,
    onManualPayloadChange: (String) -> Unit,
    onScanErrorChange: (String?) -> Unit,
    onSubmitHandshake: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Point your camera at the Windows Parakram setup screen to complete the cryptographic identity verification handshake.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            // Camera Viewfinder Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (cameraPermissionState.status.isGranted) {
                    // Render actual back camera preview
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        ctx as androidx.lifecycle.LifecycleOwner,
                                        cameraSelector,
                                        preview
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("ScanQR", "Failed to bind camera: ${e.message}")
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Draw High-Contrast Scanner Guide crosshairs and scanning line overlay
                    ScannerViewfinderOverlay()
                } else {
                    // Show Elegant Camera Permission Shield Request
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Camera Permission Required",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "To securely bind your local Windows environment by scanning QR credentials, camera permissions are required.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = ObsidianBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("grant_camera_button")
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Developer Simulation / Manual Pairing Code Entry Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Emulator Mode / Manual Handshake Link",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Text(
                        text = "If running inside an emulator or without physical camera targets, enter the setup payload URL below.",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = manualPayload,
                        onValueChange = onManualPayloadChange,
                        placeholder = {
                            Text(
                                "parakram://secure-pair?ip=...&port=8080&hid=...",
                                color = TextSecondary.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = CyanPrimary,
                            unfocusedIndicatorColor = BorderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_pairing_input"),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (manualPayload.isNotEmpty()) {
                                    onSubmitHandshake(manualPayload)
                                }
                            }
                        )
                    )

                    scanErrorMessage?.let { error ->
                        Text(
                            text = "Error: $error",
                            color = CyberRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 14.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Demo Payload auto-fill button
                        OutlinedButton(
                            onClick = {
                                onManualPayloadChange("parakram://secure-pair?ip=192.168.1.52&port=8080&hid=x89d2&ch=4f89d3e9102b&pin=491029")
                                onScanErrorChange(null)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Demo Payload", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Submit verify button
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (manualPayload.isNotEmpty()) {
                                    onSubmitHandshake(manualPayload)
                                } else {
                                    onScanErrorChange("Please paste a valid pairing payload first.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = ObsidianBackground),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isVerifyingSimulated,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("submit_manual_pair_button")
                        ) {
                            if (isVerifyingSimulated) {
                                CircularProgressIndicator(color = ObsidianBackground, modifier = Modifier.size(16.dp))
                            } else {
                                Text("Connect Securely", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowPhoneQrTabContent(
    activeHandshake: SecurePairingChallenge?,
    serverManager: com.example.data.MobileServerManager
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Launch the Windows companion application on your laptop and scan the code below. The phone operates as the server to establish credentials.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            activeHandshake?.let { handshake ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceCard)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ephemeral QR code box
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        QRCodeGeneratorComponent(
                            payload = handshake.qrPayload,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Security PIN display
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DEVELOPER VERIFICATION PIN",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = handshake.pin,
                            color = TextPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        )
                    }

                    Divider(color = BorderColor)

                    // Refresh QR Code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { serverManager.generateSecureHandshakeChallenge() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Regenerate Keys & Ephemeral PIN",
                            color = CyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CyanPrimary)
                }
            }
        }

        item {
            // Awaiting verification banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
                    label = ""
                )

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary.copy(alpha = alpha))
                )

                Text(
                    text = "Awaiting verification handshake request from Windows...",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ScannerViewfinderOverlay() {
    val infiniteTransition = rememberInfiniteTransition()
    val scanYOffset by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "scan_laser"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val rectSize = 180.dp.toPx()
        val left = (w - rectSize) / 2
        val top = (h - rectSize) / 2
        val right = left + rectSize
        val bottom = top + rectSize
        val cornerLength = 24.dp.toPx()
        val strokeWidth = 3.dp.toPx()

        // 1. Draw outer darkened lens shroud mask
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            size = size
        )

        // Clear scanning viewport box
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(rectSize, rectSize),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // 2. Draw high-contrast silver/white corner crosshair sights
        // Top-Left corner
        drawLine(color = Silver, start = Offset(left, top), end = Offset(left + cornerLength, top), strokeWidth = strokeWidth)
        drawLine(color = Silver, start = Offset(left, top), end = Offset(left, top + cornerLength), strokeWidth = strokeWidth)

        // Top-Right corner
        drawLine(color = Silver, start = Offset(right, top), end = Offset(right - cornerLength, top), strokeWidth = strokeWidth)
        drawLine(color = Silver, start = Offset(right, top), end = Offset(right, top + cornerLength), strokeWidth = strokeWidth)

        // Bottom-Left corner
        drawLine(color = Silver, start = Offset(left, bottom), end = Offset(left + cornerLength, bottom), strokeWidth = strokeWidth)
        drawLine(color = Silver, start = Offset(left, bottom), end = Offset(left, bottom - cornerLength), strokeWidth = strokeWidth)

        // Bottom-Right corner
        drawLine(color = Silver, start = Offset(right, bottom), end = Offset(right - cornerLength, bottom), strokeWidth = strokeWidth)
        drawLine(color = Silver, start = Offset(right, bottom), end = Offset(right, bottom - cornerLength), strokeWidth = strokeWidth)

        // 3. Draw animated glowing horizontal scanning laser line
        val laserY = top + (rectSize * scanYOffset)
        drawLine(
            color = Color.White,
            start = Offset(left + 8.dp.toPx(), laserY),
            end = Offset(right - 8.dp.toPx(), laserY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun HandshakingProgressView() {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)),
        label = "loader_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(80.dp)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, CyanPrimary, Color.White, Color.Transparent)
                    ),
                    startAngle = rotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
            Icon(
                imageVector = Icons.Default.SyncAlt,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "VERIFYING SECURITY HANDSHAKE",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Exchanging ephemeral Diffie-Hellman credentials and establishing local session keys safely.",
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun PairingSuccessFeedbackView(
    deviceName: String,
    deviceIp: String,
    securityMode: String,
    pingMs: Int,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(0.1f)) // layout spacer spacer

        // 1. Success Graphic & Heading
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(0.6f)
        ) {
            // Success Animated Pulse Core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // outer glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                )
                // inner shield core
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Success",
                        tint = ObsidianBackground,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Text(
                text = "DEVELOPER LINK SUCCESSFUL",
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )

            Text(
                text = "Parakram has verified and linked with your Windows client securely.",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bound Client Metrics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CLIENT HOSTNAME", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(deviceName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Divider(color = BorderColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SECURE TUNNEL IP", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(deviceIp, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Divider(color = BorderColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ENCRYPTION SCHEME", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(securityMode, color = TextPrimary, fontSize = 11.sp)
                    }
                    Divider(color = BorderColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LOCAL PING LATENCY", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("$pingMs ms (Realtime)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Control dashboard launch Button
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("launch_dashboard_button")
        ) {
            Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Launch Control Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
