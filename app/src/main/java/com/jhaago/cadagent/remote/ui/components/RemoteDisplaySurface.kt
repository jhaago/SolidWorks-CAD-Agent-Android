package com.jhaago.cadagent.remote.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jhaago.cadagent.remote.display.RemoteDisplayFrame
import com.jhaago.cadagent.remote.input.*

@Composable
fun RemoteDisplaySurface(frame: RemoteDisplayFrame, onPointer: (RemotePointerEvent) -> Boolean) {
    val send by rememberUpdatedState(onPointer)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${frame.title} · simulated frame", style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF0C1720)).testTag("remote-display")
            .pointerInput(frame.width, frame.height) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val viewport = DisplayViewport.fit(size.width.toFloat(), size.height.toFloat(), frame.width, frame.height)
                    val start = viewport.normalize(down.position.x, down.position.y) ?: return@awaitEachGesture
                    send(RemotePointerEvent(PointerAction.Down, start.x, start.y))
                    var last = start
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            viewport.normalize(change.position.x, change.position.y)?.let { point ->
                                if (point != last) send(RemotePointerEvent(PointerAction.Move, point.x, point.y))
                                last = point
                            }
                            change.consume()
                        } while (change.pressed)
                    } finally {
                        send(RemotePointerEvent(PointerAction.Up, last.x, last.y))
                    }
                }
            }) {
            val viewport = DisplayViewport.fit(size.width, size.height, frame.width, frame.height)
            val origin = Offset(viewport.left, viewport.top)
            val w = viewport.width
            val h = viewport.height
            drawRect(Color(0xFF243541), origin, Size(w, h * .08f))
            drawRect(Color(0xFF1A2934), origin + Offset(0f, h * .08f), Size(w * .22f, h * .92f))
            for (i in 1..5) drawLine(Color(0xFF526572), origin + Offset(w * .03f, h * (.18f + i * .1f)), origin + Offset(w * .18f, h * (.18f + i * .1f)), 2f)
            val plate = Path().apply {
                moveTo(origin.x + w * .39f, origin.y + h * .41f)
                lineTo(origin.x + w * .7f, origin.y + h * .3f)
                lineTo(origin.x + w * .9f, origin.y + h * .58f)
                lineTo(origin.x + w * .58f, origin.y + h * .7f)
                close()
            }
            drawPath(plate, Color(0xFF98B7C9))
            drawPath(plate, Color(0xFFD5E7F1), style = Stroke(2f))
            drawOval(Color(0xFF243541), origin + Offset(w * .60f, h * .43f), Size(w * .10f, h * .13f))
            drawLine(Color(0xFFCE725F), origin + Offset(w * .3f, h * .85f), origin + Offset(w * .4f, h * .85f), 2f)
            drawLine(Color(0xFF82B88C), origin + Offset(w * .3f, h * .85f), origin + Offset(w * .3f, h * .72f), 2f)
        }
    }
}
