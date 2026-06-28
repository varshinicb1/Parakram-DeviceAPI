const fs = require('fs');

let code = fs.readFileSync('app/src/main/java/com/example/ui/DeviceAPIScreen.kt', 'utf8');

const s1 = code.lastIndexOf('@Composable', code.indexOf('fun WorkflowsTab('));
const e2 = code.lastIndexOf('@Composable', code.indexOf('fun ProfileTab('));

const serverTabCode = `@Composable
fun ServerTab(viewModel: DeviceAPIViewModel) {
    val isServerRunning by viewModel.serverManager.isServerRunning.collectAsState()
    val serverLogs by viewModel.serverManager.serverLogs.collectAsState()
    val localIp = remember { viewModel.serverManager.getLocalIpAddress() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mobile Edge Server", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Turn your device into a local server. Host websites, APIs, and access local storage securely on your network.", color = TextSecondary, fontSize = 14.sp)
        }

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isServerRunning) Color(0xFF4CAF50) else Color(0xFFF44336))
                        )
                        Text(if (isServerRunning) "Online" else "Offline", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = { if (it) viewModel.serverManager.startServer() else viewModel.serverManager.stopServer() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50), checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f))
                    )
                }

                if (isServerRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBackground)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Server Address", color = TextSecondary, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
                            Text("http://$localIp:8080", color = CyanPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                        }
                        Text("Connect to this address from any device on the same Wi-Fi network.", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Live Traffic Console
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = ChromeYellow, modifier = Modifier.size(16.dp))
                    Text("Server Logs", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBackground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(serverLogs) { log ->
                        Text(log, color = NeonGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    if (serverLogs.isEmpty()) {
                        item {
                            Text("Waiting for requests...", color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
`;

if (s1 !== -1 && e2 !== -1) {
    code = code.substring(0, s1) + serverTabCode + '\\n\\n' + code.substring(e2);
    fs.writeFileSync('app/src/main/java/com/example/ui/DeviceAPIScreen.kt', code, 'utf8');
    console.log("Success");
} else {
    console.log("Failed to find boundaries");
}
