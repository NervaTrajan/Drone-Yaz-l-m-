package com.example.telemetryapp

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.SystemClock

class AlertManager(private val context: Context) {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    private var lastAlertTime = 0L
    private var wasAboveThreshold = false

    fun handleStrength(strength: Int, threshold: Int, enabled: Boolean) {
        if (!enabled) {
            wasAboveThreshold = false
            return
        }
        val isAbove = strength >= threshold
        val now = SystemClock.elapsedRealtime()
        val canAlert = now - lastAlertTime > 1000
        if (isAbove && (!wasAboveThreshold || canAlert)) {
            triggerAlert()
            lastAlertTime = now
        }
        wasAboveThreshold = isAbove
    }

    private fun triggerAlert() {
        val vibratorManager = context.getSystemService(VibratorManager::class.java)
        vibratorManager?.defaultVibrator?.vibrate(
            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }
}
