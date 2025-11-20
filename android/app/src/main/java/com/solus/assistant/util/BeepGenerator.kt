package com.solus.assistant.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * Generates and plays beep sounds for wake word detection and request sending
 */
object BeepGenerator {

    private const val SAMPLE_RATE = 44100

    /**
     * Play wake word detected beep (higher pitch, short)
     */
    fun playWakeWordBeep() {
        playBeep(800.0, 150) // 800 Hz, 150ms
    }

    /**
     * Play request sent beep (lower pitch, short)
     */
    fun playRequestSentBeep() {
        playBeep(600.0, 100) // 600 Hz, 100ms
    }

    /**
     * Generate and play a beep sound
     * @param frequency Frequency in Hz
     * @param durationMs Duration in milliseconds
     */
    private fun playBeep(frequency: Double, durationMs: Int) {
        Thread {
            try {
                val numSamples = (durationMs * SAMPLE_RATE) / 1000
                val samples = DoubleArray(numSamples)
                val buffer = ShortArray(numSamples)

                // Generate sine wave
                for (i in samples.indices) {
                    samples[i] = sin(2.0 * Math.PI * i.toDouble() / (SAMPLE_RATE / frequency))
                    buffer[i] = (samples[i] * Short.MAX_VALUE).toInt().toShort()
                }

                // Apply envelope to avoid clicks (fade in/out)
                val fadeLength = numSamples / 10
                for (i in 0 until fadeLength) {
                    val factor = i.toFloat() / fadeLength
                    buffer[i] = (buffer[i] * factor).toInt().toShort()
                    buffer[numSamples - 1 - i] = (buffer[numSamples - 1 - i] * factor).toInt().toShort()
                }

                // Play the sound
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Wait for playback to complete
                Thread.sleep(durationMs.toLong())
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
