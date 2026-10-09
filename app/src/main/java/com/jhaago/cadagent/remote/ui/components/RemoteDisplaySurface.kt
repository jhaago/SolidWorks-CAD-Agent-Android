package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.input.*

@Composable
fun RemoteDisplaySurface(frame: RemoteDisplayFrame, onPointer: (RemotePointerEvent) -> Boolean,
    inputEnabled: Boolean = true, onCancelled: () -> Unit = {}, modifier: Modifier = Modifier, fullScreen: Boolean = false, viewResetKey: Int = 0) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var transform by remember(frame.width, frame.height, frame.displayGeneration, viewSize, viewResetKey) { mutableStateOf(DesktopViewTransform.fit(viewSize.width.toFloat(), viewSize.height.toFloat(), frame.width, frame.height)) }
    val send by rememberUpdatedState(onPointer)
    val cancel by rememberUpdatedState(onCancelled)
    // Compose can synthesize an Up when removing a touched node, rather than
    // throw into its gesture loop. Disposal must still invalidate authority.
    DisposableEffect(Unit) { onDispose { cancel() } }
    val image by produceState<ImageBitmap?>(null, frame) {
        value = frame.jpegBytes?.let { bytes -> withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } }
    }
    Column(modifier.then(if (frame.jpegBytes != null && image != null) Modifier.testTag("live-frame") else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!fullScreen) Text("${frame.title} · ${if (frame.jpegBytes == null) "image unavailable" else "live desktop"}", style = MaterialTheme.typography.labelLarge)
        Canvas((if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat() / frame.height.coerceAtLeast(1))).onSizeChanged { viewSize = it }.background(Color(0xFF0C1720)).testTag("remote-display")
            .pointerInput(frame.width, frame.height, frame.displayGeneration, inputEnabled, image != null, viewSize, viewResetKey) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!inputEnabled) {
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val focus = event.calculateCentroid(useCurrent = false)
                                val pan = event.calculatePan()
                                transform = transform.gesture(focus.x, focus.y, event.calculateZoom(), pan.x, pan.y)
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                    if (frame.jpegBytes != null && image == null) return@awaitEachGesture
                    val viewport = transform.viewport
                    val start = viewport.normalize(down.position.x, down.position.y) ?: return@awaitEachGesture
                    if (!send(RemotePointerEvent(PointerAction.Down, start.x, start.y))) return@awaitEachGesture
                    var last = start
                    var released = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            viewport.normalize(change.position.x, change.position.y)?.let { point ->
                                if (point != last) send(RemotePointerEvent(PointerAction.Move, point.x, point.y))
                                last = point
                            }
                            change.consume()
                            released = !change.pressed
                        } while (change.pressed)
                    } finally {
                        send(RemotePointerEvent(PointerAction.Up, last.x, last.y))
                        if (!released) cancel()
                    }
                }
            }) {
            val viewport = transform.viewport
            val origin = Offset(viewport.left, viewport.top)
            val w = viewport.width
            val h = viewport.height
            if (frame.jpegBytes != null) {
                image?.let { bitmap ->
                    clipRect {
                        withTransform({ translate(origin.x, origin.y); scale(w / bitmap.width, h / bitmap.height, Offset.Zero) }) {
                            drawImage(bitmap)
                        }
                    }
                }
                val cursor = origin + Offset(w * frame.cursorX, h * frame.cursorY)
                drawCircle(Color.Black, 5f, cursor)
                drawCircle(Color.White, 3f, cursor)
                return@Canvas
            }
            drawRect(Color(0xFF0C1720), origin, Size(w, h))
        }
    }
}
