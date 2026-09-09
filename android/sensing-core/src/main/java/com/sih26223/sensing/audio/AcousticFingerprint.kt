package com.sih26223.sensing.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlin.math.sqrt

/**
 * Acoustic Resonance Fingerprinting (Epic 2)
 * Listens to the "ambient acoustic hum" of a structure and runs FFT to detect
 * microscopic resonance shifts indicative of metal fatigue or concrete micro-fractures.
 */
class AcousticFingerprint(private val context: Context) {
    
    private val TAG = "AcousticFingerprint"
    private val SAMPLE_RATE = 44100
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    @android.annotation.SuppressLint("MissingPermission")
    fun startListening() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize
        )

        audioRecord?.startRecording()
        isRecording = true
        Log.d(TAG, "Started acoustic resonance listening...")

        Thread {
            val buffer = ShortArray(minBufferSize)
            while (isRecording) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readResult > 0) {
                    analyzeResonance(buffer)
                }
            }
        }.start()
    }

    private fun analyzeResonance(buffer: ShortArray) {
        // Mock FFT (Fast Fourier Transform) logic to find the dominant frequency
        // In a full implementation, we'd use a library like JTransforms here.
        var energy = 0.0
        for (sample in buffer) {
            energy += (sample * sample).toDouble()
        }
        val rms = sqrt(energy / buffer.size)
        
        // Simulating the detection of a high-frequency shift (concrete cracking)
        if (rms > 500.0) { // Arbitrary threshold for demo
            Log.w(TAG, "⚠️ Structural Acoustic Shift Detected! Potential micro-fracture or fatigue.")
        }
    }

    fun stopListening() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Stopped acoustic listening.")
    }
}
