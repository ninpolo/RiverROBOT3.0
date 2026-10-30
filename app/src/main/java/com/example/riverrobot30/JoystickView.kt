package com.example.riverrobot30

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onCommandChanged: ((Char) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF475569.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F766E.toInt() }
    private val knobHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5EEAD4.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCBD5E1.toInt()
        textAlign = Paint.Align.CENTER
        textSize = dp(14f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private var centerX = 0f
    private var centerY = 0f
    private var knobX = 0f
    private var knobY = 0f
    private var trackRadius = 0f
    private var travelRadius = 0f
    private var activeCommand = 'S'

    init {
        isClickable = true
        contentDescription = "Movement joystick"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(248f).toInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        centerX = width / 2f
        centerY = height / 2f
        trackRadius = min(width, height) * 0.44f
        travelRadius = trackRadius * 0.62f
        knobX = centerX
        knobY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(centerX, centerY, trackRadius, trackPaint)
        canvas.drawCircle(centerX, centerY, trackRadius, ringPaint)
        canvas.drawCircle(centerX, centerY, travelRadius, guidePaint)
        canvas.drawLine(centerX - travelRadius, centerY, centerX + travelRadius, centerY, guidePaint)
        canvas.drawLine(centerX, centerY - travelRadius, centerX, centerY + travelRadius, guidePaint)

        val labelOffset = trackRadius * 0.82f
        canvas.drawText("↑", centerX, centerY - labelOffset + dp(5f), labelPaint)
        canvas.drawText("↓", centerX, centerY + labelOffset + dp(5f), labelPaint)
        canvas.drawText("←", centerX - labelOffset, centerY + dp(5f), labelPaint)
        canvas.drawText("→", centerX + labelOffset, centerY + dp(5f), labelPaint)

        val diagonalOffset = trackRadius * 0.55f
        canvas.drawText("↖", centerX - diagonalOffset, centerY - diagonalOffset + dp(5f), labelPaint)
        canvas.drawText("↗", centerX + diagonalOffset, centerY - diagonalOffset + dp(5f), labelPaint)
        canvas.drawText("↙", centerX - diagonalOffset, centerY + diagonalOffset + dp(5f), labelPaint)
        canvas.drawText("↘", centerX + diagonalOffset, centerY + diagonalOffset + dp(5f), labelPaint)

        val knobRadius = trackRadius * 0.25f
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint)
        canvas.drawCircle(knobX, knobY, knobRadius, knobHighlightPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateStick(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = centerX
                knobY = centerY
                updateCommand('S')
                invalidate()
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun resetStick() {
        knobX = centerX
        knobY = centerY
        updateCommand('S')
        invalidate()
    }

    private fun updateStick(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = hypot(dx, dy)
        val scale = if (distance > travelRadius) travelRadius / distance else 1f
        knobX = centerX + dx * scale
        knobY = centerY + dy * scale

        val command = when {
            distance < travelRadius * 0.22f -> 'S'
            abs(dx) < abs(dy) * 0.45f -> if (dy < 0) 'F' else 'B'
            abs(dy) < abs(dx) * 0.45f -> if (dx < 0) 'L' else 'R'
            dy < 0 && dx < 0 -> 'G' // forward-left
            dy < 0 -> 'I' // forward-right
            dx < 0 -> 'J' // backward-left
            else -> 'N' // backward-right
        }
        updateCommand(command)
        invalidate()
    }

    private fun updateCommand(command: Char) {
        if (activeCommand == command) return
        activeCommand = command
        onCommandChanged?.invoke(command)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
