package com.jhaago.cadagent.remote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decode via a bounded sample and normalize to JPEG so gallery metadata is never uploaded. */
internal suspend fun loadCadPhoto(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val input = resolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Could not open the selected picture.")
    input.use { BitmapFactory.decodeStream(it, null, bounds) }
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0 || width.toLong() * height > 100_000_000L)
        throw IllegalArgumentException("The picture could not be decoded safely.")

    var sample = 1
    while (maxOf(width, height) / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("Could not decode the selected picture.")
    try {
        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val oriented = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        try {
            val scaled = if (maxOf(oriented.width, oriented.height) > 2048) {
                val ratio = 2048f / maxOf(oriented.width, oriented.height)
                Bitmap.createScaledBitmap(oriented, (oriented.width * ratio).toInt().coerceAtLeast(1),
                    (oriented.height * ratio).toInt().coerceAtLeast(1), true)
            } else oriented
            try {
                val flattened = if (scaled.hasAlpha()) Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
                } else scaled
                try {
                    for (quality in intArrayOf(85, 70, 55)) {
                        val buffer = ByteArrayOutputStream()
                        if (!flattened.compress(Bitmap.CompressFormat.JPEG, quality, buffer)) continue
                        if (buffer.size() in 1..4 * 1024 * 1024) return@withContext buffer.toByteArray()
                    }
                    throw IllegalArgumentException("The picture is too large to attach.")
                } finally { if (flattened !== scaled) flattened.recycle() }
            } finally { if (scaled !== oriented) scaled.recycle() }
        } finally { if (oriented !== decoded) oriented.recycle() }
    } finally { decoded.recycle() }
}
