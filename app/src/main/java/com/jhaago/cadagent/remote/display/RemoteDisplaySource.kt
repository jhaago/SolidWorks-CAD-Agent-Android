package com.jhaago.cadagent.remote.display

import kotlinx.coroutines.flow.StateFlow

/** Live JPEG bytes stay in memory. A null image denotes the explicitly labelled demo. */
data class RemoteDisplayFrame(
    val width: Int, val height: Int, val title: String,
    val jpegBytes: ByteArray? = null, val displayGeneration: Long = 0, val frameId: Long = 0,
    val cursorX: Float = .5f, val cursorY: Float = .5f,
) {
    init { require(width > 0 && height > 0) }
    override fun toString() = "Remote display (${width}x$height, frame $frameId)"
}
interface RemoteDisplaySource { val frame: StateFlow<RemoteDisplayFrame?> }
