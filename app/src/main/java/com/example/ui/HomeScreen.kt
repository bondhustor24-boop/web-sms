package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CommandLog
import com.example.ui.theme.PolishBackground
import com.example.ui.theme.PolishBorder
import com.example.ui.theme.PolishDanger
import com.example.ui.theme.PolishDarkLogBg
import com.example.ui.theme.PolishDarkLogText
import com.example.ui.theme.PolishGreen
import com.example.ui.theme.PolishGreenBg
import com.example.ui.theme.PolishPrimary
import com.example.ui.theme.PolishPrimaryContainer
import com.example.ui.theme.PolishSecondaryContainer
import com.example.ui.theme.PolishSurface
import com.example.ui.theme.PolishSurfaceVariant
import com.example.ui.theme.PolishTextMain
import com.example.ui.theme.PolishTextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val tokenRemaining by viewModel.tokenSecondsRemaining.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("All") }
    var showTestSmsSheet by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    // Permission launcher for SMS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updateSmsPermission(isGranted)
        if (isGranted) {
            Toast.makeText(context, "SMS permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "SMS permission denied. Bridge cannot send SMS.", Toast.LENGTH_LONG).show()
        }
    }

    // Check permission on startup
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updateSmsPermission(granted)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PolishBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = PolishPrimary
                        )
                        Text(
                            text = "SMS Bridge",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            color = PolishTextMain,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                actions = {
                    // Professional Polish Online badge
                    Surface(
                        shape = CircleShape,
                        color = PolishSecondaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (uiState.isOnline) PolishGreen else PolishDanger,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (uiState.isOnline) "ONLINE" else "OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D192B),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PolishBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("home_screen_content"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Device Info Card (Polish style)
            item {
                DeviceInfoCard(
                    deviceId = uiState.deviceId,
                    deviceName = uiState.deviceName,
                    isPaired = uiState.isPaired,
                    onCopyId = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Device ID", uiState.deviceId)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Device ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 2. Main Pairing Section (QR code or Paired Status)
            item {
                if (uiState.isPaired) {
                    PairedStatusCard(
                        pairedClient = uiState.pairedClientInfo ?: "Web Dashboard",
                        onDisconnectClick = { showDisconnectConfirm = true },
                        onTestSmsClick = { showTestSmsSheet = true }
                    )
                } else {
                    QrPairingCard(
                        qrBitmap = qrBitmap,
                        pairingToken = uiState.pairingToken,
                        secondsRemaining = tokenRemaining,
                        onRegenerate = { viewModel.regenerateToken() },
                        onSimulateScan = { viewModel.simulateWebPairing() }
                    )
                }
            }

            // 3. Status Badges Grid (SMS & Network)
            item {
                StatusGridRow(
                    hasSmsPermission = uiState.hasSmsPermission,
                    isOnline = uiState.isOnline,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                )
            }

            // 4. Activity Logs Header & Filters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity & Command Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextMain
                    )
                    if (logs.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearAllLogs() },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Logs",
                                tint = PolishTextMuted
                            )
                        }
                    }
                }

                // Filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Sent", "Failed", "Pending").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PolishPrimaryContainer,
                                selectedLabelColor = PolishPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 5. Activity Log List
            val filteredLogs = logs.filter { log ->
                when (selectedFilter) {
                    "Sent" -> log.status.equals("sent", ignoreCase = true)
                    "Failed" -> log.status.equals("failed", ignoreCase = true)
                    "Pending" -> log.status.equals("pending", ignoreCase = true)
                    else -> true
                }
            }

            if (filteredLogs.isEmpty()) {
                item {
                    EmptyLogCard(selectedFilter = selectedFilter)
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    CommandLogItem(log = log)
                }
            }

            // 6. Terminal Console Preview Box
            item {
                TerminalLogPreview(logs = logs)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Confirmation Dialog for incoming Pairing Request from Web
    uiState.pendingPairingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.declinePairing() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = PolishPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Do you want to connect this device?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A Web Dashboard is requesting authorization to bridge SMS commands with this phone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = PolishSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Client: ${req.clientInfo}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Token: ${req.token}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = PolishPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptPairing() },
                    colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("allow_pairing_button")
                ) {
                    Text("Allow Connection")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.declinePairing() },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("decline_pairing_button")
                ) {
                    Text("Decline")
                }
            }
        )
    }

    // Confirmation for Disconnect
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = PolishDanger
                )
            },
            title = { Text("Disconnect Web Dashboard?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will immediately revoke the pairing token and terminate active SMS bridging sessions.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectConfirm = false
                        Toast.makeText(context, "Device disconnected", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PolishDanger),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("confirm_disconnect_button")
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manual Test SMS Sheet Dialog
    if (showTestSmsSheet) {
        TestSmsDialog(
            onDismiss = { showTestSmsSheet = false },
            onSend = { recipient, message ->
                viewModel.sendTestSms(recipient, message)
                showTestSmsSheet = false
                Toast.makeText(context, "Test SMS triggered", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun DeviceInfoCard(
    deviceId: String,
    deviceName: String,
    isPaired: Boolean,
    onCopyId: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_info_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PolishBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DEVICE ID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishPrimary,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = deviceId,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = PolishTextMain
                        )
                        IconButton(
                            onClick = onCopyId,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy ID",
                                modifier = Modifier.size(14.dp),
                                tint = PolishPrimary
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "VERSION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "v2.4.0 • $deviceName",
                        fontSize = 13.sp,
                        color = PolishTextMain
                    )
                }
            }

            HorizontalDivider(color = PolishBorder, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CONNECTED WEBSITE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isPaired) "dashboard.smsbridge.io (Linked)" else "Waiting for QR Pairing",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPaired) PolishGreen else PolishPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPaired) PolishGreenBg else PolishSecondaryContainer
                ) {
                    Text(
                        text = if (isPaired) "PAIRED" else "UNPAIRED",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaired) PolishGreen else PolishPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun QrPairingCard(
    qrBitmap: Bitmap?,
    pairingToken: String,
    secondsRemaining: Int,
    onRegenerate: () -> Unit,
    onSimulateScan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qr_pairing_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, PolishPrimary.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = PolishPrimary
                    )
                    Text(
                        text = "Pair with Web Dashboard",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = PolishTextMain
                    )
                }
                IconButton(
                    onClick = onRegenerate,
                    modifier = Modifier.size(32.dp).testTag("regenerate_token_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate Token",
                        tint = PolishPrimary
                    )
                }
            }

            // QR Frame with Corner Highlights
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, PolishBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Pairing QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = PolishTextMuted
                    )
                }
            }

            val minutes = secondsRemaining / 60
            val secs = secondsRemaining % 60
            val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
            val progress = (secondsRemaining.toFloat() / 300f).coerceIn(0f, 1f)

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Scan to pair device",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PolishTextMain
                )
                Text(
                    text = "Token ($pairingToken) expires in $timeFormatted",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = PolishTextMuted
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (secondsRemaining < 60) PolishDanger else PolishPrimary,
                    trackColor = PolishBorder
                )
            }

            // Simulated web scan trigger for fast testing
            OutlinedButton(
                onClick = onSimulateScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulate_scan_button"),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PolishPrimary)
            ) {
                Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = PolishPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simulate Web Dashboard Pairing", color = PolishPrimary)
            }
        }
    }
}

@Composable
fun StatusGridRow(
    hasSmsPermission: Boolean,
    isOnline: Boolean,
    onRequestPermission: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SMS Status Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PolishSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .testTag("permission_status_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (hasSmsPermission) PolishPrimary else Color(0xFFF57F17),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasSmsPermission) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (hasSmsPermission) "Granted" else "Needed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PolishTextMain
                    )
                }
                if (!hasSmsPermission) {
                    IconButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.size(28.dp).testTag("grant_permission_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Grant",
                            tint = PolishPrimary
                        )
                    }
                }
            }
        }

        // Network Status Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PolishSurfaceVariant,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isOnline) PolishPrimary else PolishDanger,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "NETWORK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextMuted,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isOnline) "Linked" else "Offline",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PolishTextMain
                    )
                }
            }
        }
    }
}

@Composable
fun PairedStatusCard(
    pairedClient: String,
    onDisconnectClick: () -> Unit,
    onTestSmsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("paired_status_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, PolishGreen.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PolishGreenBg,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PolishGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected & Active",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = PolishTextMain
                    )
                    Text(
                        text = "Linked with: $pairedClient",
                        style = MaterialTheme.typography.bodySmall,
                        color = PolishGreen
                    )
                }
            }

            Text(
                text = "This device is actively listening for authorized SMS dispatch commands from your Web Dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = PolishTextMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onTestSmsClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_sms_button"),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test SMS")
                }
            }

            // High-Impact Disconnect Button matching theme spec
            Button(
                onClick = onDisconnectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("disconnect_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PolishDanger)
            ) {
                Icon(imageVector = Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("DISCONNECT DEVICE", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
fun TerminalLogPreview(logs: List<CommandLog>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PolishDarkLogBg,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .border(
                width = 2.dp,
                color = PolishPrimary,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val recentLogs = logs.take(3)
            if (recentLogs.isEmpty()) {
                Text(
                    text = "[SYSTEM] Socket listener active. Awaiting pairing commands...",
                    color = Color(0xFFD0BCFF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "[READY] AES-token generator initialized.",
                    color = PolishDarkLogText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                recentLogs.forEach { log ->
                    val color = if (log.status.equals("sent", ignoreCase = true)) Color(0xFFB6EEA6) else Color(0xFFD0BCFF)
                    Text(
                        text = "[${log.status.uppercase()}] To: ${log.recipient} - ${log.message.take(30)}",
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CommandLogItem(log: CommandLog) {
    val statusColor = when (log.status.lowercase()) {
        "sent" -> PolishGreen
        "failed" -> PolishDanger
        else -> Color(0xFFEF6C00)
    }

    val statusBg = when (log.status.lowercase()) {
        "sent" -> PolishGreenBg
        "failed" -> Color(0xFFFFEBEE)
        else -> Color(0xFFFFF3E0)
    }

    val timeStr = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_${log.commandId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PolishSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PolishBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusBg,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (log.status.lowercase()) {
                                    "sent" -> Icons.Default.CheckCircle
                                    "failed" -> Icons.Default.Error
                                    else -> Icons.Default.HourglassTop
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "To: ${log.recipient}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PolishTextMain
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = log.status.uppercase(),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = PolishTextMain,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!log.errorMessage.isNullOrBlank()) {
                Text(
                    text = "Error: ${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PolishDanger,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${log.commandId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PolishTextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = PolishTextMuted
                )
            }
        }
    }
}

@Composable
fun EmptyLogCard(selectedFilter: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_logs_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PolishSurfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                tint = PolishTextMuted,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No $selectedFilter activity logs yet",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PolishTextMain
            )
            Text(
                text = "SMS commands sent from the paired Web Dashboard will be tracked here in real-time.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = PolishTextMuted
            )
        }
    }
}

@Composable
fun TestSmsDialog(
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var recipient by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Test SMS via Bridge", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Phone Number") },
                    placeholder = { Text("+1234567890") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("test_sms_recipient_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("SMS Content") },
                    placeholder = { Text("Test SMS message from Bridge") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("test_sms_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipient.isNotBlank() && message.isNotBlank()) {
                        onSend(recipient, message)
                    }
                },
                enabled = recipient.isNotBlank() && message.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("confirm_send_test_sms_button")
            ) {
                Text("Send Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

