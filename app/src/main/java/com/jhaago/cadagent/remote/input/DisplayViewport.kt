package com.jhaago.cadagent.remote.input

import kotlin.math.min

data class NormalizedPoint(val x: Float, val y: Float)

/** The input mapping uses the exact same aspect-fit rectangle as the display surface. */
data class DisplayViewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun normalize(x: Float, y: Float): NormalizedPoint? {
        if (!x.isFinite() || !y.isFinite() || width <= 0f || height <= 0f ||
            x < left || x > left + width || y < top || y > top + height) return null
        return NormalizedPoint(((x - left) / width).coerceIn(0f, 1f), ((y - top) / height).coerceIn(0f, 1f))
    }

    companion object {
        fun fit(viewWidth: Float, viewHeight: Float, frameWidth: Int, frameHeight: Int): DisplayViewport {
            if (!viewWidth.isFinite() || !viewHeight.isFinite() || viewWidth <= 0 || viewHeight <= 0 || frameWidth <= 0 || frameHeight <= 0)
                return DisplayViewport(0f, 0f, 0f, 0f)
            val scale = min(viewWidth / frameWidth, viewHeight / frameHeight)
            val width = frameWidth * scale
            val height = frameHeight * scale
            return DisplayViewport((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
        }
    }
}
