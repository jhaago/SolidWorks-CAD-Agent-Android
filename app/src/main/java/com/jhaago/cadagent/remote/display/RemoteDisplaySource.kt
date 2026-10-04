package com.jhaago.cadagent.remote.display

import kotlinx.coroutines.flow.StateFlow

/** Demo metadata. A live source will supply an authenticated video render surface separately. */
data class RemoteDisplayFrame(val width: Int, val height: Int, val title: String) {
    init { require(width > 0 && height > 0) }
}
interface RemoteDisplaySource { val frame: StateFlow<RemoteDisplayFrame?> }
