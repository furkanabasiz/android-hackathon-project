package com.hackathon.smilehairclinic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#99000000") // Yarı saydam siyah
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    var isAligned = false
        set(value) {
            field = value
            borderPaint.color = if (value) Color.GREEN else Color.WHITE
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2.5f
        val ovalWidth = width * 0.6f
        val ovalHeight = height * 0.45f

        // Tam ekranı karart
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Oval alan temizle (şeffaf yap)
        val oval = RectF(
            centerX - ovalWidth / 2,
            centerY - ovalHeight / 2,
            centerX + ovalWidth / 2,
            centerY + ovalHeight / 2
        )
        canvas.drawOval(oval, clearPaint)

        // Oval kenarlık çiz
        canvas.drawOval(oval, borderPaint)
    }

    fun getTargetOvalBounds(): RectF {
        val centerX = width / 2f
        val centerY = height / 2.5f
        val ovalWidth = width * 0.6f
        val ovalHeight = height * 0.45f

        return RectF(
            centerX - ovalWidth / 2,
            centerY - ovalHeight / 2,
            centerX + ovalWidth / 2,
            centerY + ovalHeight / 2
        )
    }
}