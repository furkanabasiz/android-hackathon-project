package com.hackathon.smilehairclinic.ui.customview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.hackathon.smilehairclinic.R

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#01223d") // Semi-transparent black (70% opacity)
        style = Paint.Style.FILL
    }

    private val transparentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f // The thickness of the progress border
        color = ContextCompat.getColor(context, R.color.card) // Background color of the progress
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND // Makes the ends of the arc rounded
        color = ContextCompat.getColor(context, R.color.green) // Progress color
    }

    private val ovalRect = RectF()
    private var currentDrawProgress: Float = 0f // The progress value used for drawing
    private var progressAnimator: ValueAnimator? = null

    /**
     * Sets the progress with an animation based on the number of completed steps.
     * @param steps The number of photos already taken (0 to 5).
     */
    fun setProgress(steps: Int) {
        val newProgress = steps / 5.0f

        // Cancel any ongoing animation
        progressAnimator?.cancel()

        // Animate from the current drawing progress to the new progress
        progressAnimator = ValueAnimator.ofFloat(currentDrawProgress, newProgress).apply {
            duration = 1000 // Animation duration in milliseconds
            interpolator = DecelerateInterpolator() // Makes the animation start fast and slow down

            addUpdateListener { animator ->
                currentDrawProgress = animator.animatedValue as Float
                invalidate() // Redraw the view on each animation frame
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Define the oval's boundaries
        val horizontalMargin = width * 0.07f
        val verticalMargin = height * 0.20f
        ovalRect.set(
            horizontalMargin,
            verticalMargin,
            width - horizontalMargin,
            height - verticalMargin
        )

        // Use a bitmap for proper PorterDuff mode operation
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmap)

        // 1. Draw the semi-transparent overlay
        tempCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // 2. Punch a hole in the overlay
        tempCanvas.drawOval(ovalRect, transparentPaint)

        // 3. Draw the background track for the progress border
        tempCanvas.drawOval(ovalRect, borderPaint)

        // 4. Draw the progress arc on top
        if (currentDrawProgress > 0) {
            tempCanvas.drawArc(ovalRect, -90f, currentDrawProgress * 360f, false, progressPaint)
        }

        // Draw the result to the main canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }
}