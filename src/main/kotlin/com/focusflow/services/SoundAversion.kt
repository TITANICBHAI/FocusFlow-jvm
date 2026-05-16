package com.focusflow.services

import kotlinx.coroutines.*
import javax.sound.sampled.*
import kotlin.math.*

/**
 * SoundAversion — three-layer audio feedback engine
 *
 * Layer 1 — Richer synthesis:
 *   All tones use additive synthesis: a fundamental frequency plus harmonic
 *   overtones (2nd at ½ amplitude, 3rd at ¼ amplitude). This produces a
 *   warmer, more natural sound compared to a bare sine wave.
 *   A full ADSR envelope (attack / decay / sustain / release) shapes each tone
 *   so notes don't start or end with a click.
 *
 * Layer 2 — Expanded event library:
 *   - playBlockAlert()      harsh 880 Hz buzz cluster — aversive block feedback
 *   - playSessionStart()    ascending three-tone chime — positive reinforcement
 *   - playSessionEnd()      two-tone descend — session completion cue
 *   - playBreakReminder()   gentle two-tone nudge — break time notification
 *   - playMilestone()       four-tone ascending fanfare — 25/50/75% session mark
 *   - playNuclearAlert()    three-pulse low-high burst — nuclear mode activated
 *
 * Layer 3 — User control:
 *   [volumeMultiplier] (0.0–1.0) scales all playback volume uniformly.
 *   [isEnabled] gates all sound globally.
 *   Every play call falls back to [Toolkit.beep()] if the audio mixer is busy
 *   or unavailable, so there is always some audible feedback.
 */
object SoundAversion {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile var isEnabled: Boolean = true

    /**
     * Global volume scalar (0.0 = mute, 1.0 = full).
     * Applied on top of each sound's own base volume.
     */
    @Volatile var volumeMultiplier: Float = 1.0f
        set(v) { field = v.coerceIn(0f, 1f) }

    // ── Layer 2: Sound event library ─────────────────────────────────────────

    /** Harsh 880 Hz buzz — immediate aversive feedback when an app is blocked. */
    fun playBlockAlert() {
        if (!isEnabled) return
        scope.launch {
            tryPlay {
                playNote(880.0, 0.10, volume = 0.90f)
                delay(30)
                playNote(1100.0, 0.08, volume = 0.85f)
                delay(30)
                playNote(880.0, 0.14, volume = 0.90f)
            }
        }
    }

    /** Ascending three-tone chime — positive reinforcement at session start. */
    fun playSessionStart() {
        if (!isEnabled) return
        scope.launch {
            tryPlay {
                playNote(440.0, 0.12, volume = 0.55f)
                delay(25)
                playNote(550.0, 0.12, volume = 0.55f)
                delay(25)
                playNote(660.0, 0.22, volume = 0.60f)
            }
        }
    }

    /** Two-tone descend — session completion cue. */
    fun playSessionEnd() {
        if (!isEnabled) return
        scope.launch {
            tryPlay {
                playNote(660.0, 0.15, volume = 0.55f)
                delay(35)
                playNote(440.0, 0.28, volume = 0.50f)
            }
        }
    }

    /**
     * Gentle two-tone nudge — break time notification.
     * Deliberately softer than [playBlockAlert] so it doesn't startle.
     */
    fun playBreakReminder() {
        if (!isEnabled) return
        scope.launch {
            tryPlay {
                playNote(523.0, 0.12, volume = 0.40f)   // C5
                delay(60)
                playNote(659.0, 0.20, volume = 0.40f)   // E5
            }
        }
    }

    /**
     * Four-tone ascending fanfare — plays at 25%, 50%, and 75% session milestones.
     * Rewarding enough to feel like an achievement without breaking flow.
     */
    fun playMilestone() {
        if (!isEnabled) return
        scope.launch {
            tryPlay {
                playNote(440.0, 0.10, volume = 0.45f)
                delay(20)
                playNote(523.0, 0.10, volume = 0.50f)
                delay(20)
                playNote(659.0, 0.10, volume = 0.55f)
                delay(20)
                playNote(880.0, 0.22, volume = 0.60f)
            }
        }
    }

    /**
     * Three-pulse low-high burst — fired when Nuclear Mode activates.
     * Distinct enough that the user immediately understands something serious
     * has changed, without being painfully loud.
     */
    fun playNuclearAlert() {
        if (!isEnabled) return
        scope.launch {
            tryPlay {
                repeat(3) {
                    playNote(220.0, 0.07, volume = 0.75f)
                    delay(15)
                    playNote(880.0, 0.07, volume = 0.80f)
                    delay(50)
                }
            }
        }
    }

    // ── Layer 1: Richer synthesis ─────────────────────────────────────────────

    /**
     * Synthesise and play a single note.
     *
     * Additive synthesis — sums three harmonics:
     *   • Fundamental  at [frequencyHz]        — amplitude 1.0
     *   • 2nd harmonic at [frequencyHz] × 2    — amplitude 0.50
     *   • 3rd harmonic at [frequencyHz] × 3    — amplitude 0.25
     *
     * ADSR envelope:
     *   • Attack  — first 5% of samples, linear ramp 0→1
     *   • Decay   — next 10%, linear ramp 1→sustainLevel
     *   • Sustain — middle 70% at sustainLevel (0.80)
     *   • Release — final 15%, linear ramp sustainLevel→0
     */
    private fun playNote(
        frequencyHz: Double,
        durationSec: Double,
        volume: Float = 0.60f
    ) {
        val effective   = volume * volumeMultiplier
        val sampleRate  = 44_100f
        val numSamples  = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val data        = ByteArray(numSamples * 2)

        val sustainLevel = 0.80
        val attackEnd    = (numSamples * 0.05).toInt()
        val decayEnd     = (numSamples * 0.15).toInt()
        val releaseStart = (numSamples * 0.85).toInt()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate

            // ADSR envelope
            val env = when {
                i < attackEnd    -> i.toDouble() / attackEnd.coerceAtLeast(1)
                i < decayEnd     -> 1.0 - (1.0 - sustainLevel) * (i - attackEnd) /
                                        (decayEnd - attackEnd).coerceAtLeast(1)
                i < releaseStart -> sustainLevel
                else             -> sustainLevel * (numSamples - i) /
                                        (numSamples - releaseStart).coerceAtLeast(1)
            }

            // Additive synthesis: fundamental + 2nd + 3rd harmonic
            val sample = sin(2.0 * PI * frequencyHz       * t) * 1.00 +
                         sin(2.0 * PI * frequencyHz * 2.0 * t) * 0.50 +
                         sin(2.0 * PI * frequencyHz * 3.0 * t) * 0.25

            // Normalise (peak of sum = 1.75), apply envelope and volume
            val value = (Short.MAX_VALUE * effective * env * (sample / 1.75)).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

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

    // ── Layer 3: Graceful fallback ────────────────────────────────────────────

    /**
     * Wraps a sound-producing block with a two-level fallback:
     *   1. Try the full synthesised playback via [playNote].
     *   2. On any [LineUnavailableException] or other audio error, fall back to
     *      [java.awt.Toolkit.getDefaultToolkit().beep()] — always available,
     *      always produces some feedback.
     */
    private suspend fun tryPlay(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: LineUnavailableException) {
            withContext(Dispatchers.Main) {
                runCatching { java.awt.Toolkit.getDefaultToolkit().beep() }
            }
        } catch (_: Exception) {
            runCatching { java.awt.Toolkit.getDefaultToolkit().beep() }
        }
    }
}
