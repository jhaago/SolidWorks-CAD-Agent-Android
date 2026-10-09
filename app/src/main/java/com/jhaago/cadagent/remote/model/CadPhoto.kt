package com.jhaago.cadagent.remote.model

/** One image attached to the next normal CAD job. The server keeps its own durable copy. */
class CadPhoto(val jpegBytes: ByteArray) {
    init { require(jpegBytes.isNotEmpty() && jpegBytes.size <= 4 * 1024 * 1024) }
}
