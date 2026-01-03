package com.whispertype.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * CircularWaveformView - A circular audio waveform visualizer
 * 
 * Draws bars radiating outward from the center in a circle,
 * with heights that animate based on audio amplitude.
 * Creates the classic circular audio visualizer effect.
 */
class CircularWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 32 // Number of bars around the circle
        private const val MIN_BAR_HEIGHT_DP = 0f // Minimum bar height when silent
        private const val MAX_BAR_HEIGHT_DP = 16f // Maximum bar height at full amplitude
        private const val BAR_WIDTH_DP = 3f // Width of each bar
        private const val BAR_GAP_DEGREES = 2f // Gap between bars in degrees
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBB6366F1.toInt() // Purple color matching the theme
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val density = resources.displayMetrics.density
    private val minBarHeight = MIN_BAR_HEIGHT_DP * density
    private val maxBarHeight = MAX_BAR_HEIGHT_DP * density
    private val barWidth = BAR_WIDTH_DP * density

    // Array to store the current height of each bar
    private val barHeights = FloatArray(BAR_COUNT) { minBarHeight }
    
    // The inner radius where bars start (will be set based on mic button size)
    private var innerRadius = 0f
    
    private val barRect = RectF()

    /**
     * Update the waveform based on current amplitude
     * @param amplitude Audio amplitude value (0-32767)
     */
    fun updateAmplitude(amplitude: Int) {
        // Normalize amplitude to 0-1 range
        val normalizedAmplitude = (amplitude.toFloat() / 15000f).coerceIn(0f, 1f)
        
        // Update each bar with randomized height based on amplitude
        for (i in 0 until BAR_COUNT) {
            // Add randomness for organic look
            val randomFactor = 0.5f + (Math.random().toFloat() * 1f)
            
            // Calculate target height
            val amplitudeContribution = normalizedAmplitude * (maxBarHeight - minBarHeight) * randomFactor
            barHeights[i] = minBarHeight + amplitudeContribution
        }
        
        // Trigger redraw
        invalidate()
    }

    /**
     * Reset all bars to minimum height (flat/silent state)
     */
    fun reset() {
        for (i in 0 until BAR_COUNT) {
            barHeights[i] = minBarHeight
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate inner radius based on view size
        // The bars should start just outside the mic button
        val minDimension = minOf(w, h).toFloat()
        innerRadius = minDimension / 2f - maxBarHeight - (4 * density) // 4dp padding
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Draw each bar radiating from center
        val anglePerBar = 360f / BAR_COUNT
        
        for (i in 0 until BAR_COUNT) {
            val angle = i * anglePerBar - 90f // Start from top (-90 degrees)
            val angleRad = Math.toRadians(angle.toDouble())
            
            val barHeight = barHeights[i]
            
            // Calculate bar start position (on inner circle)
            val startX = centerX + (innerRadius * cos(angleRad)).toFloat()
            val startY = centerY + (innerRadius * sin(angleRad)).toFloat()
            
            // Calculate bar end position (outer)
            val endX = centerX + ((innerRadius + barHeight) * cos(angleRad)).toFloat()
            val endY = centerY + ((innerRadius + barHeight) * sin(angleRad)).toFloat()
            
            // Draw the bar as a thick line
            barPaint.strokeWidth = barWidth
            canvas.drawLine(startX, startY, endX, endY, barPaint)
        }
    }
}
