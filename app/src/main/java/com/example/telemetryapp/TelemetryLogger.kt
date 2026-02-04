package com.example.telemetryapp

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelemetryLogger(private val context: Context) {
    private var file: File? = null
    private var lastCompletedFile: File? = null

    fun startLogging(): File {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(dir, "telemetry_$timestamp.csv")
        logFile.writeText("ts,raw,filt,env,strength,depth,cls,conf\n")
        file = logFile
        return logFile
    }

    fun append(packet: TelemetryPacket) {
        val current = file ?: return
        val line = "${packet.ts},${packet.raw},${packet.filt},${packet.env},${packet.strength},${packet.depth},${packet.cls},${packet.conf}\n"
        current.appendText(line)
    }

    fun stopLogging(): File? {
        val current = file
        if (current != null) {
            lastCompletedFile = current
        }
        file = null
        return current
    }

    fun currentFile(): File? = file ?: lastCompletedFile
}
