package com.dopaminebox.app.util

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class SoundManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleRate = 44_100
    private val maxVolume = AudioTrack.getMaxVolume().coerceAtLeast(1f)

    fun win() {
        playSweep(
            startHz = 220.0,
            endHz = 440.0,
            durationSec = 0.30,
            volume = 0.35f,
        )
    }

    fun lose() {
        playSweep(
            startHz = 300.0,
            endHz = 150.0,
            durationSec = 0.40,
            volume = 0.30f,
        )
    }

    fun woohoo() {
        scope.launch {
            val sequence = listOf(523.25, 659.25, 783.99)
            val pcm = buildList<Short> {
                sequence.forEach { hz ->
                    addAll(generateSineTone(hz, 0.12, 0.40f).asList())
                }
            }.toShortArray()
            playPcm(pcm)
        }
    }

    fun coin() {
        playSweep(
            startHz = 800.0,
            endHz = 800.0,
            durationSec = 0.05,
            volume = 0.25f,
        )
    }

    private fun playSweep(startHz: Double, endHz: Double, durationSec: Double, volume: Float) {
        scope.launch {
            val sampleCount = (sampleRate * durationSec).toInt().coerceAtLeast(1)
            val vol = volume.coerceIn(0f, 0.4f)
            val pcm = ShortArray(sampleCount)
            for (i in 0 until sampleCount) {
                val t = i.toDouble() / sampleCount.toDouble()
                val hz = startHz + (endHz - startHz) * t
                val angle = 2.0 * PI * hz * (i.toDouble() / sampleRate.toDouble())
                val envelope = envelopeForIndex(i, sampleCount)
                pcm[i] = (sin(angle) * Short.MAX_VALUE * vol * envelope).toInt().toShort()
            }
            playPcm(pcm)
        }
    }

    private fun generateSineTone(hz: Double, durationSec: Double, volume: Float): ShortArray {
        val sampleCount = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val vol = volume.coerceIn(0f, 0.4f)
        return ShortArray(sampleCount) { i ->
            val angle = 2.0 * PI * hz * (i.toDouble() / sampleRate.toDouble())
            val envelope = envelopeForIndex(i, sampleCount)
            (sin(angle) * Short.MAX_VALUE * vol * envelope).toInt().toShort()
        }
    }

    private fun envelopeForIndex(index: Int, total: Int): Float {
        val attack = (total * 0.1f).toInt().coerceAtLeast(1)
        val releaseStart = (total * 0.8f).toInt().coerceAtLeast(1)
        return when {
            index < attack -> index / attack.toFloat()
            index > releaseStart -> ((total - index) / (total - releaseStart).toFloat()).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    private fun playPcm(pcm: ShortArray) {
        val minSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val byteData = ByteArray(pcm.size * 2)
        pcm.forEachIndexed { idx, sample ->
            byteData[idx * 2] = (sample.toInt() and 0xFF).toByte()
            byteData[idx * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        var track: AudioTrack? = null
        try {
            track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minSize, byteData.size),
                AudioTrack.MODE_STATIC,
            )
            val trackVolume = (0.4f * maxVolume).coerceIn(0f, 0.4f * maxVolume)
            @Suppress("DEPRECATION")
            track.setStereoVolume(trackVolume, trackVolume)
            track.write(byteData, 0, byteData.size)
            track.play()
            val durationMs = (pcm.size * 1000L / sampleRate).coerceAtLeast(40L)
            Thread.sleep(durationMs)
        } catch (_: Throwable) {
            // Audio failures should never block gameplay.
        } finally {
            try {
                track?.stop()
            } catch (_: Throwable) {
            }
            track?.release()
        }
    }
}