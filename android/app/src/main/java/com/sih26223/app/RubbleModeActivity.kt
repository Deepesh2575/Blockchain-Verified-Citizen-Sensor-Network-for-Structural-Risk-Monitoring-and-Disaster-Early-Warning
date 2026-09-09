package com.sih26223.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RubbleModeActivity : AppCompatActivity() {

    private lateinit var tvDecodedText: TextView
    private lateinit var btnExitRubble: Button
    private lateinit var vibrator: Vibrator

    private var tapCount = 0
    private var tapResetJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rubble_mode)

        tvDecodedText = findViewById(R.id.tvDecodedText)
        btnExitRubble = findViewById(R.id.btnExitRubble)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        btnExitRubble.setOnClickListener {
            finish()
        }

        // Simulate incoming haptic message from Responders
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            triggerHapticPulse(3) // 3 pulses = "Are you there?"
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            tapCount++
            triggerHapticPulse(1, 50L) // Tactile feedback
            
            tapResetJob?.cancel()
            tapResetJob = CoroutineScope(Dispatchers.Main).launch {
                delay(1500) // Wait 1.5s for user to finish tapping
                decodeTapsAndSend()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun decodeTapsAndSend() {
        val message = when (tapCount) {
            1 -> "YES"
            2 -> "NO"
            3 -> "HELP"
            else -> "UNKNOWN ($tapCount taps)"
        }
        
        tvDecodedText.text = "Sent via Mesh: $message"
        tapCount = 0
        
        // Confirm sent
        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            triggerHapticPulse(1, 300L) // Long pulse confirming sent
        }
    }

    private fun triggerHapticPulse(count: Int, duration: Long = 200L) {
        CoroutineScope(Dispatchers.Default).launch {
            for (i in 0 until count) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
                delay(duration + 200L)
            }
        }
    }
}
