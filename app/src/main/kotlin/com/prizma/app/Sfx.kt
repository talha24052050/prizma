package com.prizma.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sound effects synthesised at start-up, so the app ships no audio files.
 * Every call is a no-op when sound is off or the device refuses an audio track.
 */
class Sfx(private val enabled: () -> Boolean) {
    private class Note(val freq: Double, val start: Double, val length: Double, val triangle: Boolean = false)

    private val rate = 22050
    private val tracks = HashMap<String, AudioTrack?>()

    private fun render(volume: Double, vararg notes: Note): ShortArray {
        val total = notes.maxOf { it.start + it.length }
        val out = DoubleArray((total * rate).toInt() + 1)
        for (n in notes) {
            val first = (n.start * rate).toInt()
            val count = (n.length * rate).toInt()
            for (k in 0 until count) {
                val t = k.toDouble() / rate
                val phase = t * n.freq
                val wave = if (n.triangle) 4 * abs(phase - Math.floor(phase + 0.5)) - 1 else sin(2 * PI * phase)
                // quick attack, exponential decay
                val env = minOf(1.0, t / 0.004) * exp(-5.0 * t / n.length)
                out[first + k] += wave * env * volume
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun track(name: String, build: () -> ShortArray): AudioTrack? = tracks.getOrPut(name) {
        try {
            val pcm = build()
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .also { it.write(pcm, 0, pcm.size) }
        } catch (e: Exception) {
            null
        }
    }

    private fun play(name: String, build: () -> ShortArray) {
        if (!enabled()) return
        try {
            val t = track(name, build) ?: return
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
            // no sound is better than a crash
        }
    }

    /** A piece turned; [state] picks one of a few pitches so turns do not all sound alike. */
    fun turn(state: Int) = play("turn$state") { render(0.35, Note(400.0 + 70 * state, 0.0, 0.07, triangle = true)) }

    /** A target lit; [index] raises the pitch for each further target. */
    fun lit(index: Int) = play("lit${index % 4}") { render(0.3, Note(660.0 + 110 * (index % 4), 0.0, 0.3)) }

    fun hint() = play("hint") { render(0.25, Note(300.0, 0.0, 0.16)) }

    fun undo() = play("undo") { render(0.3, Note(330.0, 0.0, 0.06, triangle = true)) }

    fun win() = play("win") {
        render(
            0.3,
            Note(523.25, 0.15, 0.4, true), Note(659.25, 0.27, 0.4, true),
            Note(783.99, 0.39, 0.4, true), Note(1046.5, 0.51, 0.6, true),
        )
    }

    fun release() {
        tracks.values.forEach { runCatching { it?.release() } }
        tracks.clear()
    }
}
