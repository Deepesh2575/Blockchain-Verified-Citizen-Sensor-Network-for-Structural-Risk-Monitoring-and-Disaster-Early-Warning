package com.sih26223.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    private val path = Path()
    private val dataPoints = FloatArray(150)
    private var head = 0
    private var isFilled = false

    fun addDataPoint(value: Float) {
        dataPoints[head] = value
        head = (head + 1) % dataPoints.size
        if (head == 0) isFilled = true
        postInvalidate()
    }
    
    fun setLineColor(hexColor: String) {
        paint.color = Color.parseColor(hexColor)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val midY = height / 2f
        
        path.reset()
        
        val size = if (isFilled) dataPoints.size else head
        if (size == 0) return
        
        val stepX = width / (dataPoints.size - 1)
        
        for (i in 0 until size) {
            val index = if (isFilled) (head + i) % dataPoints.size else i
            val value = dataPoints[index]
            
            // Assuming max value is around 10.0 for scaling
            val scaledY = midY - (value * (height / 20f))
            
            val x = i * stepX
            if (i == 0) {
                path.moveTo(x, scaledY)
            } else {
                path.lineTo(x, scaledY)
            }
        }
        
        canvas.drawPath(path, paint)
    }
}
