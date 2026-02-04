package com.example.telemetryapp

import android.bluetooth.BluetoothDevice

enum class ConnectionStatus {
    Disconnected,
    Connecting,
    Connected
}

data class TelemetryPacket(
    val ts: Long = 0L,
    val raw: Int = 0,
    val filt: Int = 0,
    val env: Int = 0,
    val strength: Int = 0,
    val depth: Float = 0f,
    val cls: String = "unknown",
    val conf: Int = 0
)

data class ChartPoint(val timeMs: Long, val value: Int)

data class SettingsState(
    val strengthThreshold: Int = 70,
    val alertsEnabled: Boolean = true,
    val plotSignal: PlotSignal = PlotSignal.Raw,
    val demoMode: Boolean = false,
    val lastDeviceAddress: String? = null
)

enum class PlotSignal { Raw, Env }

data class UiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val latestPacket: TelemetryPacket = TelemetryPacket(),
    val chartPoints: List<ChartPoint> = emptyList(),
    val logLines: List<String> = emptyList(),
    val settings: SettingsState = SettingsState(),
    val isRecording: Boolean = false,
    val connectionError: String? = null
)
