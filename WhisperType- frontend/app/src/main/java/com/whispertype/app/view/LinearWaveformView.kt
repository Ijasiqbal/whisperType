package com.whispertype.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * LinearWaveformView - A horizontal bar-style audio waveform visualizer
 * 
 * Draws vertical bars in a row that animate based on audio amplitude.
 * Designed for the compact pill overlay layout.
 * Uses Liquid Glass-style gradient coloring.
 */
class LinearWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 7 // Number of bars in the waveform
        private const val MIN_BAR_HEIGHT_RATIO = 0.15f // Minimum bar height as ratio of view height
        private const val MAX_BAR_HEIGHT_RATIO = 0.85f // Maximum bar height as ratio of view height
        private const val BAR_WIDTH_DP = 3f // Width of each bar
        private const val BAR_GAP_DP = 2f // Gap between bars
        private const val BAR_CORNER_DP = 2f // Corner radius for rounded bars
        private const val MAX_AMPLITUDE = 15000f // Maximum expected amplitude for normalization
        private const val SMOOTHING_FACTOR = 0.3f // Lerp smoothing (0-1, higher = faster response)
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val density = resources.displayMetrics.density
    private val barWidth = BAR_WIDTH_DP * density
    private val barGap = BAR_GAP_DP * density
    private val barCorner = BAR_CORNER_DP * density

    // Array to store the current height of each bar (normalized 0-1)
    private val barHeights = FloatArray(BAR_COUNT) { MIN_BAR_HEIGHT_RATIO }
    
    // Target heights for smooth animation
    private val targetHeights = FloatArray(BAR_COUNT) { MIN_BAR_HEIGHT_RATIO }
    
    private val barRect = RectF()
    
    // Gradient colors (violet theme)
    private val gradientColorStart = 0xFF8B5CF6.toInt() // Violet
    private val gradientColorEnd = 0xFFA78BFA.toInt() // Light violet

    /**
     * Update the waveform based on current amplitude
     * @param amplitude Audio amplitude value (0-32767)
     */
    fun updateAmplitude(amplitude: Int) {
        // Normalize amplitude to 0-1 range (avoid division by zero, clamp result)
        val normalizedAmplitude = (amplitude.coerceAtLeast(0).toFloat() / MAX_AMPLITUDE).coerceIn(0f, 1f)
        
        // Height range is constant - calculate once
        val range = MAX_BAR_HEIGHT_RATIO - MIN_BAR_HEIGHT_RATIO
        val centerIndex = BAR_COUNT / 2f
        
        // Update each bar with randomized height based on amplitude
        for (i in 0 until BAR_COUNT) {
            // Add randomness for organic look - center bars tend to be taller
            val centerWeight = 1f - kotlin.math.abs(i - centerIndex) / centerIndex * 0.3f
            val randomFactor = 0.6f + Random.nextFloat() * 0.8f
            
            // Calculate target height
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO + (normalizedAmplitude * range * randomFactor * centerWeight)
        }
        
        // Smooth animation towards target (simple lerp with smoothing factor)
        for (i in 0 until BAR_COUNT) {
            barHeights[i] += (targetHeights[i] - barHeights[i]) * SMOOTHING_FACTOR
        }
        
        // Trigger redraw
        invalidate()
    }

    /**
     * Reset all bars to minimum height (flat/silent state)
     */
    fun reset() {
        for (i in 0 until BAR_COUNT) {
            barHeights[i] = MIN_BAR_HEIGHT_RATIO
            targetHeights[i] = MIN_BAR_HEIGHT_RATIO
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update gradient based on new size
        barPaint.shader = LinearGradient(
            0f, h.toFloat(),
            0f, 0f,
            gradientColorStart,
            gradientColorEnd,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val viewHeight = height.toFloat()
        val viewWidth = width.toFloat()
        
        // Calculate total width needed for all bars
        val totalBarsWidth = (BAR_COUNT * barWidth) + ((BAR_COUNT - 1) * barGap)
        
        // Start X position to center the bars
        var startX = (viewWidth - totalBarsWidth) / 2f
        
        // Center Y position
        val centerY = viewHeight / 2f
        
        for (i in 0 until BAR_COUNT) {
            val barHeight = viewHeight * barHeights[i]
            val halfHeight = barHeight / 2f
            
            // Bar rect centered vertically
            barRect.set(
                startX,
                centerY - halfHeight,
                startX + barWidth,
                centerY + halfHeight
            )
            
            // Draw rounded bar
            canvas.drawRoundRect(barRect, barCorner, barCorner, barPaint)
            
            // Move to next bar position
            startX += barWidth + barGap
        }
    }
}
