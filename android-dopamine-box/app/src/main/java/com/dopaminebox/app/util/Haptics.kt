package com.dopaminebox.app.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun tap() {
        runHaptic(
            oneShotDuration = 18L,
            oneShotAmplitude = VibrationEffect.DEFAULT_AMPLITUDE,
        )
    }

    fun win() {
        runHaptic(
            timings = longArrayOf(0, 40, 60, 40, 80, 30),
            amplitudes = intArrayOf(0, 200, 0, 180, 0, 220),
        )
    }

    fun lose() {
        runHaptic(
            timings = longArrayOf(0, 80, 100, 80),
            amplitudes = intArrayOf(220, 0, 200, 0),
        )
    }

    fun woohoo() {
        runHaptic(
            timings = longArrayOf(0, 30, 30, 30, 30, 30, 60, 80),
            amplitudes = intArrayOf(200, 0, 200, 0, 200, 0, 255, 0),
        )
    }

    fun scrollFastTick() {
        runHaptic(oneShotDuration = 8L, oneShotAmplitude = 80)
    }

    private fun runHaptic(
        oneShotDuration: Long? = null,
        oneShotAmplitude: Int? = null,
        timings: LongArray? = null,
        amplitudes: IntArray? = null,
    ) {
        if (!vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when {
                    oneShotDuration != null && oneShotAmplitude != null -> {
                        VibrationEffect.createOneShot(oneShotDuration, oneShotAmplitude)
                    }

                    timings != null && amplitudes != null -> {
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    }

                    else -> return
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(oneShotDuration ?: timings?.sum() ?: 20L)
            }
        } catch (_: Throwable) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(oneShotDuration ?: timings?.sum() ?: 20L)
        }
    }
}