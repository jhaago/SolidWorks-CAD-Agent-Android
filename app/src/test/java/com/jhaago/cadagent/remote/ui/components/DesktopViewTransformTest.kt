package com.jhaago.cadagent.remote.ui.components

import org.junit.Assert.*
import org.junit.Test

class DesktopViewTransformTest {
    @Test fun portraitFitRejectsLetterbox() {
        val view = DesktopViewTransform.fit(400f, 800f, 1920, 1080)
        assertNull(view.normalize(200f, 100f))
        assertEquals(.5f, view.normalize(200f, 400f)!!.x, .0001f)
        assertEquals(.5f, view.normalize(200f, 400f)!!.y, .0001f)
    }
    @Test fun zoomKeepsGestureFocusAndMappingAligned() {
        val view = DesktopViewTransform.fit(800f, 400f, 1600, 800)
        val zoomed = view.gesture(200f, 100f, 2f, 30f, 20f)
        assertEquals(.25f, zoomed.normalize(230f, 120f)!!.x, .0001f)
        assertEquals(.25f, zoomed.normalize(230f, 120f)!!.y, .0001f)
    }
    @Test fun zoomIsBoundedAndInvalidCoordinatesRejected() {
        val view = DesktopViewTransform.fit(800f, 400f, 1600, 800)
        assertEquals(5f, view.gesture(400f, 200f, 100f, 0f, 0f).zoom, .0001f)
        assertNull(view.normalize(Float.NaN, 20f))
        assertEquals(1f, view.gesture(400f, 200f, .1f, 0f, 0f).zoom, .0001f)
    }
}
