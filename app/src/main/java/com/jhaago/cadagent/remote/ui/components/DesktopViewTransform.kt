package com.jhaago.cadagent.remote.ui.components

import com.jhaago.cadagent.remote.input.DisplayViewport
import com.jhaago.cadagent.remote.input.NormalizedPoint

/** One transform is shared by painting and inverse touch mapping. */
data class DesktopViewTransform(val base: DisplayViewport, val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {
    val viewport: DisplayViewport get() = DisplayViewport(base.left + panX, base.top + panY, base.width * zoom, base.height * zoom)
    fun normalize(x: Float, y: Float): NormalizedPoint? = viewport.normalize(x, y)
    fun gesture(focusX: Float, focusY: Float, scale: Float, deltaX: Float, deltaY: Float): DesktopViewTransform {
        if (!listOf(focusX, focusY, scale, deltaX, deltaY).all { it.isFinite() } || scale <= 0f) return this
        val nextZoom = (zoom * scale).coerceIn(1f, 5f)
        val ratio = nextZoom / zoom
        val current = viewport
        return copy(zoom = nextZoom,
            panX = focusX - (focusX - current.left) * ratio + deltaX - base.left,
            panY = focusY - (focusY - current.top) * ratio + deltaY - base.top)
    }
    companion object {
        fun fit(width: Float, height: Float, frameWidth: Int, frameHeight: Int) =
            DesktopViewTransform(DisplayViewport.fit(width, height, frameWidth, frameHeight))
    }
}
