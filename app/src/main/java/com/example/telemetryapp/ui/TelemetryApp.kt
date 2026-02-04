package com.example.telemetryapp.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telemetryapp.ChartPoint
import com.example.telemetryapp.ConnectionStatus
import com.example.telemetryapp.PlotSignal
import com.example.telemetryapp.R
import com.example.telemetryapp.TelemetryPacket
import com.example.telemetryapp.TelemetryViewModel
import com.example.telemetryapp.UiState
import android.widget.Toast
import kotlin.math.max

private enum class ViewMode(val label: String) {
    Scope("Scope"),
    Panel("Panel"),
    Combo("Combo")
}

@Composable
fun TelemetryApp(viewModel: TelemetryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(ViewMode.Scope) }
    val context = LocalContext.current
    var lastStatus by remember { mutableStateOf(uiState.connectionStatus) }

    LaunchedEffect(uiState.connectionStatus) {
        if (lastStatus == ConnectionStatus.Connected && uiState.connectionStatus == ConnectionStatus.Disconnected && !uiState.settings.demoMode) {
            Toast.makeText(context, "Connection lost. Tap Reconnect to try again.", Toast.LENGTH_SHORT).show()
        }
        lastStatus = uiState.connectionStatus
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Taştan Olsun",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        TabRow(selectedTabIndex = mode.ordinal) {
            ViewMode.values().forEachIndexed { index, viewMode ->
                Tab(
                    selected = mode.ordinal == index,
                    onClick = { mode = viewMode },
                    text = { Text(viewMode.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ConnectionSection(
            uiState = uiState,
            onConnect = { viewModel.connect() },
            onDisconnect = { viewModel.disconnect() },
            onReconnect = { viewModel.reconnect() },
            onSelectDevice = viewModel::selectDevice,
            onRefresh = viewModel::refreshBondedDevices
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsSection(
            uiState = uiState,
            onDemoToggle = viewModel::toggleDemoMode,
            onAlertsToggle = viewModel::updateAlertsEnabled,
            onThresholdChange = viewModel::updateStrengthThreshold,
            onPlotSignalChange = viewModel::updatePlotSignal,
            onRecordToggle = viewModel::toggleRecording,
            onExport = {
                val intent: Intent? = viewModel.shareLatestLog()
                if (intent != null) {
                    context.startActivity(Intent.createChooser(intent, "Share telemetry"))
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (mode) {
            ViewMode.Scope -> ScopeScreen(uiState)
            ViewMode.Panel -> PanelScreen(uiState)
            ViewMode.Combo -> ComboScreen(uiState)
        }
    }
}

@Composable
private fun ConnectionSection(
    uiState: UiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onSelectDevice: (android.bluetooth.BluetoothDevice) -> Unit,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Connection", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            DeviceSelector(uiState = uiState, onSelectDevice = onSelectDevice, onRefresh = onRefresh)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ${uiState.connectionStatus}")
                Spacer(modifier = Modifier.width(8.dp))
                when (uiState.connectionStatus) {
                    ConnectionStatus.Disconnected -> {
                        Button(onClick = onConnect, enabled = uiState.selectedDevice != null) { Text("Connect") }
                    }
                    ConnectionStatus.Connecting -> {
                        OutlinedButton(onClick = onDisconnect) { Text("Cancel") }
                    }
                    ConnectionStatus.Connected -> {
                        Button(onClick = onDisconnect, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Disconnect")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onReconnect) { Text("Reconnect") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSelector(
    uiState: UiState,
    onSelectDevice: (android.bluetooth.BluetoothDevice) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = uiState.selectedDevice?.name ?: uiState.selectedDevice?.address ?: "Select paired device"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { expanded = true }) { Text(selectedLabel) }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        uiState.bondedDevices.forEach { device ->
            DropdownMenuItem(
                text = { Text("${device.name ?: "Unknown"} (${device.address})") },
                onClick = {
                    onSelectDevice(device)
                    expanded = false
                }
            )
        }
        if (uiState.bondedDevices.isEmpty()) {
            DropdownMenuItem(text = { Text("No paired devices") }, onClick = { expanded = false })
        }
    }
}

@Composable
private fun SettingsSection(
    uiState: UiState,
    onDemoToggle: (Boolean) -> Unit,
    onAlertsToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onPlotSignalChange: (PlotSignal) -> Unit,
    onRecordToggle: () -> Unit,
    onExport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Settings", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Demo Mode")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = uiState.settings.demoMode, onCheckedChange = onDemoToggle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alerts")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = uiState.settings.alertsEnabled, onCheckedChange = onAlertsToggle)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Strength threshold: ${uiState.settings.strengthThreshold}")
            Slider(
                value = uiState.settings.strengthThreshold.toFloat(),
                onValueChange = { onThresholdChange(it.toInt()) },
                valueRange = 0f..100f
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Plot signal")
                Spacer(modifier = Modifier.width(8.dp))
                PlotSignalDropdown(uiState.settings.plotSignal, onPlotSignalChange)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onRecordToggle) {
                    Text(if (uiState.isRecording) "Stop Recording" else "Start Recording")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onExport, enabled = uiState.isRecording.not()) {
                    Text("Export CSV")
                }
            }
        }
    }
}

@Composable
private fun PlotSignalDropdown(selected: PlotSignal, onSelect: (PlotSignal) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text(selected.name) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        PlotSignal.values().forEach { signal ->
            DropdownMenuItem(text = { Text(signal.name) }, onClick = {
                onSelect(signal)
                expanded = false
            })
        }
    }
}

@Composable
private fun ScopeScreen(uiState: UiState) {
    Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Scope", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            ScopeChart(points = uiState.chartPoints)
        }
    }
}

@Composable
private fun PanelScreen(uiState: UiState) {
    PanelContent(packet = uiState.latestPacket)
}

@Composable
private fun ComboScreen(uiState: UiState) {
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Scope", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                ScopeChart(points = uiState.chartPoints, compact = true)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PanelContent(packet = uiState.latestPacket)
    }
}

@Composable
private fun PanelContent(packet: TelemetryPacket) {
    Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status Panel", fontWeight = FontWeight.Bold)
            Text("Strength: ${packet.strength}", fontSize = 18.sp)
            LinearProgressIndicator(progress = packet.strength / 100f, modifier = Modifier.fillMaxWidth())
            Text("Depth: ${"%.2f".format(packet.depth)} m")
            Text("Range: ${rangeLabel(packet.strength)}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = targetIcon(packet.cls)),
                    contentDescription = "Target class",
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Class: ${packet.cls}")
            }
            Text("Confidence: ${packet.conf}")
            Text("Device time: ${packet.ts} ms")
        }
    }
}

private fun rangeLabel(strength: Int): String {
    return when {
        strength >= 75 -> "Near"
        strength >= 45 -> "Med"
        else -> "Far"
    }
}

private fun targetIcon(cls: String): Int {
    return when (cls) {
        "gold_like" -> R.drawable.ic_target_gold
        "ferrous" -> R.drawable.ic_target_ferrous
        else -> R.drawable.ic_target_unknown
    }
}

@Composable
private fun ScopeChart(points: List<ChartPoint>, compact: Boolean = false) {
    val height = if (compact) 140.dp else 220.dp
    Box(modifier = Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (points.isEmpty()) return@Canvas
            val latestTime = points.last().timeMs
            val windowStart = max(0L, latestTime - 5000)
            val windowPoints = points.filter { it.timeMs >= windowStart }
            if (windowPoints.size < 2) return@Canvas
            val minValue = windowPoints.minOf { it.value }
            val maxValue = windowPoints.maxOf { it.value }
            val valueRange = (maxValue - minValue).coerceAtLeast(1)

            val path = Path()
            windowPoints.forEachIndexed { index, point ->
                val x = (point.timeMs - windowStart).toFloat() / 5000f * size.width
                val y = size.height - ((point.value - minValue).toFloat() / valueRange) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(0xFF4CAF50),
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
