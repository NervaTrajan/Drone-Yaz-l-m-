package com.example.telemetryapp

object TelemetryParser {
    fun parse(line: String): TelemetryPacket? {
        if (!line.startsWith("S;")) return null
        val parts = line.split(";").drop(1)
        val map = parts.mapNotNull { segment ->
            val idx = segment.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = segment.substring(0, idx)
            val value = segment.substring(idx + 1)
            key to value
        }.toMap()
        return TelemetryPacket(
            ts = map["ts"]?.toLongOrNull() ?: 0L,
            raw = map["raw"]?.toIntOrNull() ?: 0,
            filt = map["filt"]?.toIntOrNull() ?: 0,
            env = map["env"]?.toIntOrNull() ?: 0,
            strength = map["strength"]?.toIntOrNull() ?: 0,
            depth = map["depth"]?.toFloatOrNull() ?: 0f,
            cls = map["cls"] ?: "unknown",
            conf = map["conf"]?.toIntOrNull() ?: 0
        )
    }
}
