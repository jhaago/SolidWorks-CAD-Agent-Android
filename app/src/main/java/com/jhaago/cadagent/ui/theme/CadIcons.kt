package com.jhaago.cadagent.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Consistent vectors accompany labels, with no new icon dependency. */
object CadIcons {
    val Chat = ImageVector.Builder("Chat", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 16f); lineTo(9f, 16f); lineTo(4f, 20f); close()
            moveTo(8f, 8f); lineTo(16f, 8f); moveTo(8f, 12f); lineTo(13f, 12f)
        }
    }.build()
    val Desktop = ImageVector.Builder("Desktop", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 16f); lineTo(3f, 16f); close()
            moveTo(12f, 16f); lineTo(12f, 20f); moveTo(8f, 20f); lineTo(16f, 20f)
        }
    }.build()
    val Settings = ImageVector.Builder("Settings", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 7f); lineTo(8f, 7f); moveTo(12f, 7f); lineTo(20f, 7f)
            moveTo(4f, 17f); lineTo(12f, 17f); moveTo(16f, 17f); lineTo(20f, 17f)
            moveTo(8f, 4f); lineTo(12f, 4f); lineTo(12f, 10f); lineTo(8f, 10f); close()
            moveTo(12f, 14f); lineTo(16f, 14f); lineTo(16f, 20f); lineTo(12f, 20f); close()
        }
    }.build()
    val Model = ImageVector.Builder("Model", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 2f); lineTo(21f, 7f); lineTo(21f, 17f); lineTo(12f, 22f); lineTo(3f, 17f); lineTo(3f, 7f); close()
            moveTo(3f, 7f); lineTo(12f, 12f); lineTo(21f, 7f); moveTo(12f, 12f); lineTo(12f, 22f)
        }
    }.build()
}
