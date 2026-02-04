package com.example.telemetryapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.UUID

class BluetoothTelemetryManager(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var readThread: Thread? = null

    private val _lineFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val lineFlow: Flow<String> = _lineFlow

    fun bondedDevices(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.toList().orEmpty()
    }

    fun connect(device: BluetoothDevice): Flow<ConnectionStatus> = callbackFlow {
        trySend(ConnectionStatus.Connecting)
        val sppUuid = UUID.fromString(SPP_UUID)
        val clientSocket = device.createRfcommSocketToServiceRecord(sppUuid)
        adapter?.cancelDiscovery()
        socket = clientSocket
        try {
            clientSocket.connect()
            trySend(ConnectionStatus.Connected)
            startReadLoop(clientSocket.inputStream) {
                trySend(ConnectionStatus.Disconnected)
            }
        } catch (e: Exception) {
            Log.e("BluetoothTelemetry", "Connection failed", e)
            trySend(ConnectionStatus.Disconnected)
            close(e)
        }
        awaitClose {
            disconnect()
        }
    }.flowOn(ioDispatcher)

    private fun startReadLoop(inputStream: InputStream, onDisconnected: () -> Unit) {
        readThread?.interrupt()
        readThread = Thread {
            val buffered = BufferedInputStream(inputStream)
            val buffer = ByteArray(256)
            val lineBuffer = StringBuilder()
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val read = buffered.read(buffer)
                    if (read == -1) {
                        break
                    }
                    val chunk = String(buffer, 0, read)
                    for (char in chunk) {
                        if (char == '\n') {
                            val line = lineBuffer.toString().trim()
                            lineBuffer.clear()
                            if (line.isNotEmpty()) {
                                _lineFlow.tryEmit(line)
                            }
                        } else {
                            lineBuffer.append(char)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothTelemetry", "Read loop error", e)
            } finally {
                onDisconnected()
            }
        }
        readThread?.start()
    }

    fun disconnect() {
        try {
            readThread?.interrupt()
            socket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothTelemetry", "Disconnect failed", e)
        } finally {
            socket = null
        }
    }

    companion object {
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }
}
