package com.example.telemetryapp

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class DemoTelemetryGenerator {
    fun generate(): Flow<String> = flow {
        var time = 0L
        while (true) {
            val raw = (1500 + 300 * sin(time / 200.0)).toInt() + Random.nextInt(-20, 20)
            val env = 600 + Random.nextInt(-10, 10)
            val filt = (raw + env) / 2
            val strength = (abs(raw - env) / 10).coerceIn(0, 100)
            val depth = (1.0 + strength / 100.0).toFloat()
            val cls = when {
                strength > 80 -> "gold_like"
                strength > 45 -> "ferrous"
                else -> "unknown"
            }
            val conf = (60 + strength / 2).coerceIn(0, 100)
            val line = "S;ts=$time;raw=$raw;filt=$filt;env=$env;strength=$strength;depth=$depth;cls=$cls;conf=$conf"
            emit(line)
            time += 50
            delay(50)
        }
    }
}
