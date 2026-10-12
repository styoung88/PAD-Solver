package com.example.phoneapp.capture

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import com.example.phoneapp.util.dpToPx

/** Small native toolbar that does not intercept touches outside its own window. */
internal class OverlayControlsView(
    context: Context,
    onCapture: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    initiallyLocked: Boolean,
    onLockChanged: (Boolean) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragFinished: () -> Unit,
) : LinearLayout(context) {
    private var positionLocked = initiallyLocked
    private val accent = 0xFF66DFCA.toInt()
    private val status: TextView
    private val capture: View
    private val settings: TextView
    private val panelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80111827.toInt()
        style = android.graphics.Paint.Style.FILL
    }

    init {
        orientation = VERTICAL
        setPadding(resources.dpToPx(12), resources.dpToPx(8), resources.dpToPx(12), resources.dpToPx(12))
        background = rounded(Color.TRANSPARENT, 18, 0x80354258.toInt())
        alpha = 1f
        elevation = resources.dpToPx(8).toFloat()
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "⋮⋮"
            gravity = Gravity.CENTER_VERTICAL
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent)
            contentDescription = "Toolbar drag handle"
            var startX = 0f
            var startY = 0f
            var previousX = 0f
            var previousY = 0f
            var dragging = false
            val slop = ViewConfiguration.get(context).scaledTouchSlop
            setOnTouchListener { view, event ->
                if (positionLocked) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        previousX = startX
                        previousY = startY
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.hypot(event.rawX - startX, event.rawY - startY) > slop) dragging = true
                        if (dragging) {
                            onDrag(event.rawX - previousX, event.rawY - previousY)
                            previousX = event.rawX
                            previousY = event.rawY
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragging) onDragFinished() else if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                        dragging = false
                    }
                }
                true
            }
        }, LayoutParams(0, resources.dpToPx(40), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        header.addView(LockIconView(context).apply {
            locked = positionLocked
            background = RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), rounded(0xFF273246.toInt(), 9), null)
            isFocusable = true
            setOnClickListener {
                positionLocked = !positionLocked
                locked = positionLocked
                onLockChanged(positionLocked)
            }
        }, LayoutParams(resources.dpToPx(22), resources.dpToPx(22)).apply { marginEnd = resources.dpToPx(6) })
        header.addView(button("×", 0xFF273246.toInt(), Color.WHITE, onClose).apply {
            textSize = 12f
            setPadding(0, 0, 0, 0)
            background = RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), rounded(0xFF273246.toInt(), 9), null)
            contentDescription = "Close overlay"
        }, LayoutParams(resources.dpToPx(22), resources.dpToPx(22)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        capture = CaptureIconView(context).apply {
            background = RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), rounded(accent, 12), null)
            contentDescription = "Capture board"
            isFocusable = true
            setOnClickListener { onCapture() }
        }
        settings = button("⚙", 0xFF2D394D.toInt(), Color.WHITE, onSettings).apply {
            textSize = 24f
            contentDescription = "Open solve settings"
        }
        actions.addView(capture, LayoutParams(0, resources.dpToPx(44), 1f).apply { marginEnd = resources.dpToPx(8) })
        actions.addView(settings, LayoutParams(0, resources.dpToPx(44), 1f))
        addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = resources.dpToPx(6) })
        status = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFF1F5F9.toInt())
            setShadowLayer(resources.dpToPx(1).toFloat(), 0f, resources.dpToPx(1).toFloat(), 0xCC000000.toInt())
            maxLines = 5
            accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = resources.dpToPx(8) })
        status.visibility = GONE
        layoutParams = LayoutParams(resources.dpToPx(176), LayoutParams.WRAP_CONTENT)
        minimumWidth = resources.dpToPx(176)
    }

    fun setStatus(message: String, isError: Boolean = false, busy: Boolean = false) {
        status.text = message
        status.visibility = if (message.isBlank()) GONE else VISIBLE
        status.setTextColor(if (isError) 0xFFFF8585.toInt() else 0xFFF1F5F9.toInt())
        capture.isEnabled = !busy
        capture.alpha = if (busy) 0.45f else 1f
        settings.isEnabled = true
        settings.alpha = 1f
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        // Paint the panel backing once at 50% opacity, retaining fully visible
        // buttons and labels instead of fading the whole window.
        val inset = resources.dpToPx(1).toFloat()
        val radius = resources.dpToPx(17).toFloat()
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, panelPaint)
        super.dispatchDraw(canvas)
    }

    private fun button(label: String, fill: Int, foreground: Int, action: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(foreground)
        setPadding(resources.dpToPx(10), 0, resources.dpToPx(10), 0)
        background = RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), rounded(fill, 12), null)
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = resources.dpToPx(radius).toFloat()
        stroke?.let { setStroke(resources.dpToPx(1), it) }
    }

    private class CaptureIconView(context: Context) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF102822.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 1.8f
            strokeCap = android.graphics.Paint.Cap.ROUND
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val unit = resources.displayMetrics.density
            canvas.save()
            canvas.translate(width / 2f - 12 * unit, height / 2f - 12 * unit)
            canvas.scale(unit, unit)
            paint.strokeWidth = 1.8f
            canvas.drawRoundRect(2f, 6f, 22f, 20f, 2f, 2f, paint)
            canvas.drawLine(7f, 6f, 9f, 3f, paint)
            canvas.drawLine(9f, 3f, 15f, 3f, paint)
            canvas.drawLine(15f, 3f, 17f, 6f, paint)
            canvas.drawCircle(12f, 13f, 4f, paint)
            canvas.restore()
        }
    }

    private class LockIconView(context: Context) : View(context) {
        var locked: Boolean = false
            set(value) {
                field = value
                contentDescription = if (value) "Unlock toolbar position" else "Lock toolbar position"
                invalidate()
            }
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            canvas.save()
            canvas.translate((width - 12 * density) / 2, (height - 12 * density) / 2)
            canvas.scale(density, density)
            paint.color = if (locked) 0xFF66DFCA.toInt() else Color.WHITE
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 0.9f
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            canvas.drawRoundRect(2.5f, 5f, 9.5f, 10.5f, 1f, 1f, paint)
            val left = if (locked) 4f else 6f
            canvas.drawArc(android.graphics.RectF(left, 1.5f, left + 4f, 6.5f), 180f, 180f, false, paint)
            canvas.drawLine(left, 4f, left, 5f, paint)
            if (locked) canvas.drawLine(8f, 4f, 8f, 5f, paint)
            canvas.drawLine(6f, 7f, 6f, 8.5f, paint)
            canvas.restore()
        }
    }
}
