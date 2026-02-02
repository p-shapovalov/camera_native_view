package io.flutter.plugins.camerapreview

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import kotlin.math.max
import kotlin.math.min

/**
 * A SurfaceView that maintains aspect ratio based on preview size.
 * Scales to cover the available space (center-crop behavior).
 */
class AspectRatioSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "AspectRatioSurfaceView"
    }

    private var previewSize: Size? = null

    fun setPreviewSize(size: Size) {
        if (previewSize == size) return

        previewSize = size
        holder.setFixedSize(size.width, size.height)

        Log.d(TAG, "Preview size set to: ${size.width}x${size.height}")
        post { requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = previewSize
        if (size == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val viewHeight = MeasureSpec.getSize(heightMeasureSpec)
        val previewRatio = max(size.width, size.height).toFloat() / min(size.width, size.height)

        val (measuredWidth, measuredHeight) = calculateDimensions(viewWidth, viewHeight, previewRatio)

        Log.d(TAG, "view=${viewWidth}x$viewHeight | preview=${size.width}x${size.height} | result=${measuredWidth}x$measuredHeight")
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    /**
     * Calculates dimensions that maintain aspect ratio while covering the view.
     * Uses the smaller dimension as base and scales up if needed to cover.
     */
    private fun calculateDimensions(viewWidth: Int, viewHeight: Int, ratio: Float): Pair<Int, Int> {
        val isPortrait = viewHeight > viewWidth

        // Use the smaller dimension as base, calculate the other using ratio
        val (baseSize, calculatedSize, availableSize) = if (isPortrait) {
            Triple(viewWidth, (viewWidth * ratio).toInt(), viewHeight)
        } else {
            Triple(viewHeight, (viewHeight * ratio).toInt(), viewWidth)
        }

        // If calculated size doesn't cover, scale up proportionally
        return if (calculatedSize < availableSize) {
            val scale = availableSize.toFloat() / calculatedSize
            val scaledBase = (baseSize * scale).toInt()
            if (isPortrait) Pair(scaledBase, availableSize) else Pair(availableSize, scaledBase)
        } else {
            if (isPortrait) Pair(baseSize, calculatedSize) else Pair(calculatedSize, baseSize)
        }
    }
}
