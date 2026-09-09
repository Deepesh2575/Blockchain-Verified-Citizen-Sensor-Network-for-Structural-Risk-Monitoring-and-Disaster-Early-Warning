package com.sih26223.app

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sih26223.app.databinding.ActivityMainBinding
import com.sih26223.sensing.network.GatewaySyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.withContext
import java.util.UUID
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Intent
import android.view.View
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sih26223.sensing.comms.BleMeshRouter

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private lateinit var gatewaySyncManager: GatewaySyncManager
    private lateinit var bleMeshRouter: BleMeshRouter
    private val deviceId = UUID.randomUUID().toString()
    
    private var isSensing = false
    private val sensorBuffer = FloatArray(450) // For the AI Model (X, Y, Z * 150)
    private val magnitudeBuffer = FloatArray(150) // For UI and simple thresholding
    private var bufferIndex = 0
    private var lastUiUpdateTime = 0L

    private var currentLat: Double = 28.6139
    private var currentLon: Double = 77.2090
    
    // Acoustic Sensing (Audio AI)
    private var audioRecord: AudioRecord? = null
    private var isAudioListening = false
    private var currentAudioRisk = 0.0f
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    
    // Tokenomics
    private var currentTokens = 12.50f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gatewaySyncManager = GatewaySyncManager(this)
        bleMeshRouter = BleMeshRouter(this)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupUI()
        
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        } else {
            fetchLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    log("📍 GPS Locked: $currentLat, $currentLon")
                } else {
                    log("📍 GPS Warning: Cannot find location, using default.")
                }
            }
        } catch (e: SecurityException) {
            log("GPS Permission Denied")
        }
    }

    private fun setupUI() {
        binding.btnToggleSensing.setOnClickListener { toggleSensing() }
        
        binding.btnArEscape.setOnClickListener {
            startActivity(Intent(this, ArEscapeRouteActivity::class.java))
        }
        
        binding.btnRubbleMode.setOnClickListener {
            startActivity(Intent(this, RubbleModeActivity::class.java))
        }
        
        binding.btnTriggerFL.setOnClickListener {
            log("Triggering Nightly Federated Learning Job...")
            val flWorkRequest = OneTimeWorkRequestBuilder<FederatedLearningWorker>().build()
            WorkManager.getInstance(this).enqueue(flWorkRequest)
        }
    }

    private fun toggleSensing() {
        if (isSensing) {
            stopSensing()
        } else {
            startSensing()
        }
    }

    private fun startSensing() {
        if (accelerometer == null) {
            log("ERROR: No accelerometer hardware found on this device!")
            return
        }
        
        isSensing = true
        bufferIndex = 0
        
        // Update Button
        binding.btnToggleSensing.text = "STOP SENSING"
        binding.btnToggleSensing.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btnStop))
        
        // Update Status indicator
        binding.tvStatus.text = "LISTENING (50Hz)"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.statusListening))
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.statusListening))
        startPulseAnimation()
        
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        log("Started physical hardware accelerometer at 50Hz.")
        
        startAudioSensing()
    }

    private fun stopSensing() {
        isSensing = false
        
        binding.btnToggleSensing.text = "START SENSING"
        binding.btnToggleSensing.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.btnStart))
        
        binding.tvStatus.text = "OFFLINE"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.textSecondary))
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.statusOffline))
        
        // Reset values
        binding.tvValX.text = "0.00"
        binding.tvValY.text = "0.00"
        binding.tvValZ.text = "0.00"
        binding.tvValMag.text = "0.00 G"
        
        sensorManager.unregisterListener(this)
        log("Stopped physical hardware accelerometer.")
        
        stopAudioSensing()
    }

    private fun startPulseAnimation() {
        if (!isSensing) return
        val anim = ObjectAnimator.ofFloat(binding.statusDot, "alpha", 1f, 0.2f, 1f)
        anim.duration = 1000
        anim.repeatCount = ValueAnimator.INFINITE
        anim.start()
    }
    
    private fun startAudioSensing() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            audioRecord?.startRecording()
            isAudioListening = true
            
            CoroutineScope(Dispatchers.IO).launch {
                val audioBuffer = ShortArray(bufferSize)
                while (isAudioListening) {
                    val read = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        // Look for sharp transients (cracking concrete, breaking glass)
                        var maxAudioAmp = 0
                        for (i in 0 until read) {
                            val amp = Math.abs(audioBuffer[i].toInt())
                            if (amp > maxAudioAmp) maxAudioAmp = amp
                        }
                        
                        // Extremely loud sharp sounds will spike maxAudioAmp > 15000
                        if (maxAudioAmp > 15000) {
                            currentAudioRisk = 1.0f 
                        } else if (maxAudioAmp > 5000) {
                            currentAudioRisk = 0.5f
                        } else {
                            currentAudioRisk = currentAudioRisk * 0.9f // Fast decay
                        }
                    }
                }
            }
            log("Started acoustic microphone monitoring.")
        } catch (e: Exception) {
            log("Acoustic monitoring failed to start: \${e.message}")
        }
    }
    
    private fun stopAudioSensing() {
        isAudioListening = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isSensing || event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - 9.81f
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUiUpdateTime > 100) {
            lastUiUpdateTime = currentTime
            
            // DePIN Tokenomics Increment
            currentTokens += 0.0001f
            
            runOnUiThread {
                binding.tvValX.text = String.format("%.2f", x)
                binding.tvValY.text = String.format("%.2f", y)
                binding.tvValZ.text = String.format("%.2f", z)
                binding.tvValMag.text = String.format("%.2f G", magnitude)
                binding.tvWalletBalance.text = String.format("%.4f SCT", currentTokens)
            }
        }
        
        sensorBuffer[bufferIndex * 3] = x
        sensorBuffer[bufferIndex * 3 + 1] = y
        sensorBuffer[bufferIndex * 3 + 2] = z
        magnitudeBuffer[bufferIndex] = magnitude
        bufferIndex++

        if (bufferIndex >= 150) {
            val bufferCopy = sensorBuffer.copyOf()
            val magCopy = magnitudeBuffer.copyOf()
            bufferIndex = 0
            
            if (Math.random() < 0.1) {
                 log("Buffer filled. Running TFLite inference...")
            }

            CoroutineScope(Dispatchers.IO).launch {
                val maxVib = magCopy.maxOrNull() ?: 0f
                val estimatedFreq = calculateDominantFrequency(magCopy)
                gatewaySyncManager.processSensorStream(deviceId, bufferCopy, maxVib, estimatedFreq, currentLat, currentLon, currentAudioRisk)
                
                if (maxVib > 5.0f) {
                    withContext(Dispatchers.Main) {
                        log("⚠️ CRITICAL VIBRATION DETECTED! Freq: ${String.format("%.1f", estimatedFreq)}Hz")
                        binding.tvLogConsole.setTextColor(Color.RED)
                        binding.btnArEscape.visibility = View.VISIBLE
                        bleMeshRouter.startMeshAdvertising("SOS|CRITICAL_VIB|$deviceId|$currentLat|$currentLon")
                        triggerRedFlash()
                    }
                }
            }
        }
    }
    
    private fun calculateDominantFrequency(magBuffer: FloatArray): Float {
        // Calculate dynamic frequency by counting mean-crossings
        var crossings = 0
        val mean = magBuffer.average().toFloat()
        
        for (i in 1 until magBuffer.size) {
            val prev = magBuffer[i - 1] - mean
            val curr = magBuffer[i] - mean
            if (prev * curr < 0) {
                crossings++
            }
        }
        
        // 150 samples at 50Hz = 3 seconds of data
        // Frequency = (crossings / 2) / seconds
        return (crossings / 2.0f) / 3.0f
    }
    
    private fun triggerRedFlash() {
        val originalColor = ContextCompat.getColor(this, R.color.backgroundDark)
        val flashColor = ContextCompat.getColor(this, R.color.alertBackground)
        
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), originalColor, flashColor, originalColor)
        colorAnimation.duration = 600
        colorAnimation.addUpdateListener { animator ->
            binding.rootLayout.setBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimation.start()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun log(message: String) {
        runOnUiThread {
            val currentText = binding.tvLogConsole.text.toString()
            val newText = if (currentText.length > 2000) {
                currentText.substring(currentText.length - 1000)
            } else {
                currentText
            }
            binding.tvLogConsole.text = "$newText\n> $message"
            binding.tvLogConsole.setTextColor(ContextCompat.getColor(this, R.color.neonGreen))
            
            // Auto scroll to bottom
            binding.logScrollView.post {
                binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isSensing) {
            stopSensing()
        }
        bleMeshRouter.stopMesh()
    }
}
