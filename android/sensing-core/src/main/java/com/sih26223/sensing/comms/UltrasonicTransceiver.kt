package com.sih26223.sensing.comms

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Ultrasonic Data Transmission (Data-over-Sound) - Epic 2
 * A fallback mechanism for extreme communication blackouts where BLE Mesh is obstructed.
 * Broadcasts high-frequency, inaudible acoustic "chirps" containing compressed distress payloads.
 */
class UltrasonicTransceiver {
    
    private val TAG = "UltrasonicTransceiver"
    private val SAMPLE_RATE = 44100
    private val CARRIER_FREQUENCY = 19000.0 // 19 kHz (inaudible to most adults)

    /**
     * Modulates a tiny, encrypted payload into an inaudible audio wave.
     * Uses simple Frequency Shift Keying (FSK) as a mock implementation.
     */
    fun transmitDistressPayload(payloadHex: String) {
        Log.d(TAG, "Broadcasting distress payload via Ultrasonic Audio: $payloadHex")
        
        // Generate a 1-second sine wave at 19kHz
        val duration = 1.0 // seconds
        val numSamples = (duration * SAMPLE_RATE).toInt()
        val sample = DoubleArray(numSamples)
        val generatedSnd = ByteArray(2 * numSamples)

        for (i in 0 until numSamples) {
            // Generate a simple carrier wave for the mock
            sample[i] = Math.sin(2 * Math.PI * i / (SAMPLE_RATE / CARRIER_FREQUENCY))
        }

        // convert to 16 bit pcm sound array
        var idx = 0
        for (dVal in sample) {
            val val16 = (dVal * 32767).toInt().toShort()
            generatedSnd[idx++] = (val16.toInt() and 0x00ff).toByte()
            generatedSnd[idx++] = ((val16.toInt() and 0xff00) ushr 8).toByte()
        }

        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            generatedSnd.size,
            AudioTrack.MODE_STATIC
        )

        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.play()
        
        Log.d(TAG, "Ultrasonic transmission complete.")
    }

    /**
     * Listens for 19kHz-20kHz spikes to decode incoming chirps.
     * (Would share the AudioRecord instance with AcousticFingerprint in production)
     */
    fun startListeningForChirps() {
        Log.d(TAG, "Listening for incoming Ultrasonic distress chirps...")
        // Requires FFT to isolate the 19kHz band and decode the FSK.
    }
}
