package com.focusflow.services

import kotlinx.coroutines.*
import javax.sound.sampled.*
import kotlin.math.*

/**
 * SoundAversion
 *
 * Plays synthesised tones on focus events using javax.sound.sampled (standard JVM, no deps).
 * Three sounds:
 *   - playBlockAlert()    — harsh 880 Hz buzz, immediate aversive feedback
 *   - playSessionStart()  — ascending three-tone chime, positive reinforcement
 *   - playSessionEnd()    — two-tone descend, session completion cue
 */
object SoundAversion {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile var isEnabled: Boolean = true

    fun playBlockAlert() {
        if (!isEnabled) return
        scope.launch {
            try {
                val tones = listOf(880.0 to 0.12, 1100.0 to 0.10, 880.0 to 0.15)
                tones.forEach { (freq, dur) -> playTone(freq, dur, volume = 0.85f) }
            } catch (_: Exception) {
                java.awt.Toolkit.getDefaultToolkit().beep()
            }
        }
    }

    fun playSessionStart() {
        if (!isEnabled) return
        scope.launch {
            try {
                listOf(440.0 to 0.12, 550.0 to 0.12, 660.0 to 0.20).forEach { (f, d) ->
                    playTone(f, d, volume = 0.5f)
                    delay(20)
                }
            } catch (_: Exception) { }
        }
    }

    fun playSessionEnd() {
        if (!isEnabled) return
        scope.launch {
            try {
                listOf(660.0 to 0.15, 440.0 to 0.25).forEach { (f, d) ->
                    playTone(f, d, volume = 0.5f)
                    delay(30)
                }
            } catch (_: Exception) { }
        }
    }

    private fun playTone(frequencyHz: Double, durationSec: Double, volume: Float = 0.6f) {
        val sampleRate = 44100f
        val numSamples = (sampleRate * durationSec).toInt()
        val data = ByteArray(numSamples * 2)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val fade = when {
                i < numSamples * 0.05 -> i / (numSamples * 0.05)
                i > numSamples * 0.85 -> (numSamples - i) / (numSamples * 0.15)
                else -> 1.0
            }
            val value = (Short.MAX_VALUE * volume * fade * sin(2.0 * PI * frequencyHz * t)).toInt().toShort()
            data[i * 2]     = (value.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = (value.toInt() shr 8).toByte()
        }

        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val info   = DataLine.Info(SourceDataLine::class.java, format)
        val line   = AudioSystem.getLine(info) as SourceDataLine
        line.open(format)
        line.start()
        line.write(data, 0, data.size)
        line.drain()
        line.close()
    }
}
