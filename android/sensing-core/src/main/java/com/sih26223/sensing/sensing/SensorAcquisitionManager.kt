package com.sih26223.sensing.sensing

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.util.MathUtils
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

data class SensorSample(
    val kind: SensorKind,
    val timestampNs: Long,
    val magnitude: Float,
    val raw: FloatArray
)

/**
 * Ring-buffered, thread-safe sample collector for all hardware sensors.
 * Missing sensors are simply not registered — downstream code uses null-safe reads.
 */
class SensorSampleBuffer(private val maxPerSensor: Int = 512) {

    private val buffers = SensorKind.entries.associateWith { ConcurrentLinkedQueue<SensorSample>() }

    fun push(sample: SensorSample) {
        val q = buffers.getValue(sample.kind)
        q.add(sample)
        while (q.size > maxPerSensor) q.poll()
    }

    fun drain(kind: SensorKind, max: Int = maxPerSensor): List<SensorSample> {
        val q = buffers.getValue(kind)
        val out = mutableListOf<SensorSample>()
        while (out.size < max) {
            val s = q.poll() ?: break
            out.add(s)
        }
        return out
    }

    fun peekMagnitudes(kind: SensorKind, count: Int): FloatArray {
        val q = buffers.getValue(kind)
        return q.toList().takeLast(count).map { it.magnitude }.toFloatArray()
    }

    fun latest(kind: SensorKind): SensorSample? = buffers[kind]?.peekLast()
}

class SensorAcquisitionManager(
    private val sensorManager: SensorManager,
    private val buffer: SensorSampleBuffer,
    private val availableSensors: Set<SensorKind>
) {
    private val listeners = mutableMapOf<SensorKind, SensorEventListener>()

    fun start(profile: com.sih26223.sensing.model.SamplingProfile) {
        stop()
        registerIfAvailable(SensorKind.ACCELEROMETER, profile.accelHz, Sensor.TYPE_ACCELEROMETER)
        registerIfAvailable(SensorKind.GYROSCOPE, profile.gyroHz, Sensor.TYPE_GYROSCOPE)
        registerIfAvailable(SensorKind.BAROMETER, profile.baroHz, Sensor.TYPE_PRESSURE)
        registerIfAvailable(SensorKind.MAGNETOMETER, profile.magHz, Sensor.TYPE_MAGNETIC_FIELD)
    }

    fun stop() {
        listeners.values.forEach { sensorManager.unregisterListener(it) }
        listeners.clear()
    }

    private fun registerIfAvailable(kind: SensorKind, rateHz: Float, androidType: Int) {
        if (rateHz <= 0f || kind !in availableSensors) return
        val sensor = sensorManager.getDefaultSensor(androidType) ?: return
        val delayUs = max(1, (1_000_000f / rateHz).toInt())

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val magnitude = when (kind) {
                    SensorKind.BAROMETER -> event.values[0] // absolute hPa — orientation independent
                    else -> MathUtils.magnitude(event.values)
                }
                buffer.push(
                    SensorSample(
                        kind = kind,
                        timestampNs = event.timestamp,
                        magnitude = magnitude,
                        raw = event.values.copyOf()
                    )
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, delayUs, delayUs)
        listeners[kind] = listener
    }
}

private fun <T> ConcurrentLinkedQueue<T>.peekLast(): T? {
    var last: T? = null
    for (item in this) last = item
    return last
}
