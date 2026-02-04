package com.example.telemetryapp

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TelemetryViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val bluetoothManager = BluetoothTelemetryManager()
    private val demoGenerator = DemoTelemetryGenerator()
    private val logger = TelemetryLogger(application)
    private val alertManager = AlertManager(application)

    private val chartBuffer = ChartBuffer(1500)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var connectionJob: Job? = null
    private var telemetryJob: Job? = null

    init {
        refreshBondedDevices()
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
                val lastAddress = settings.lastDeviceAddress
                val device = _uiState.value.bondedDevices.firstOrNull { it.address == lastAddress }
                _uiState.value = _uiState.value.copy(selectedDevice = device)
                if (settings.demoMode && _uiState.value.connectionStatus != ConnectionStatus.Connected) {
                    startDemo()
                }
            }
        }
    }

    fun onPermissionResult(results: Map<String, Boolean>) {
        val granted = results.values.all { it }
        if (granted) {
            refreshBondedDevices()
        }
    }

    fun refreshBondedDevices() {
        val devices = bluetoothManager.bondedDevices()
        _uiState.value = _uiState.value.copy(bondedDevices = devices)
    }

    fun selectDevice(device: BluetoothDevice) {
        _uiState.value = _uiState.value.copy(selectedDevice = device)
        viewModelScope.launch {
            settingsDataStore.updateLastDevice(device.address)
        }
    }

    fun connect() {
        val device = _uiState.value.selectedDevice ?: return
        connectionJob?.cancel()
        telemetryJob?.cancel()
        connectionJob = viewModelScope.launch {
            bluetoothManager.connect(device).collectLatest { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
            }
        }
        telemetryJob = viewModelScope.launch {
            bluetoothManager.lineFlow.collectLatest { line ->
                processLine(line)
            }
        }
    }

    fun disconnect() {
        bluetoothManager.disconnect()
        connectionJob?.cancel()
        telemetryJob?.cancel()
        _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Disconnected)
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    fun toggleDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateDemoMode(enabled)
        }
        if (enabled) {
            disconnect()
            startDemo()
        } else {
            stopDemo()
        }
    }

    private fun startDemo() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            demoGenerator.generate().collectLatest { line ->
                processLine(line)
            }
        }
        _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Connected)
    }

    private fun stopDemo() {
        telemetryJob?.cancel()
        _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.Disconnected)
    }

    private fun processLine(line: String) {
        val packet = TelemetryParser.parse(line) ?: return
        val settings = _uiState.value.settings
        val plotValue = if (settings.plotSignal == PlotSignal.Raw) packet.raw else packet.env
        chartBuffer.add(ChartPoint(packet.ts, plotValue))
        val trimmedLog = (_uiState.value.logLines + line).takeLast(50)
        _uiState.value = _uiState.value.copy(
            latestPacket = packet,
            chartPoints = chartBuffer.toList(),
            logLines = trimmedLog
        )
        alertManager.handleStrength(packet.strength, settings.strengthThreshold, settings.alertsEnabled)
        if (_uiState.value.isRecording) {
            logger.append(packet)
        }
    }

    fun updateStrengthThreshold(value: Int) {
        viewModelScope.launch { settingsDataStore.updateThreshold(value) }
    }

    fun updateAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.updateAlertsEnabled(enabled) }
    }

    fun updatePlotSignal(signal: PlotSignal) {
        viewModelScope.launch { settingsDataStore.updatePlotSignal(signal) }
        chartBuffer.clear()
        _uiState.value = _uiState.value.copy(chartPoints = emptyList())
    }

    fun toggleRecording() {
        val isRecording = _uiState.value.isRecording
        if (isRecording) {
            logger.stopLogging()
        } else {
            logger.startLogging()
        }
        _uiState.value = _uiState.value.copy(isRecording = !isRecording)
    }

    fun shareLatestLog(): Intent? {
        val file = logger.currentFile() ?: return null
        val uri: Uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

class TelemetryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TelemetryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TelemetryViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
