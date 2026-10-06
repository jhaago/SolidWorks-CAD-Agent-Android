package com.jhaago.cadagent.remote.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRemoteDisplaySource : RemoteDisplaySource {
    override val frame = MutableStateFlow<RemoteDisplayFrame?>(RemoteDisplayFrame(1600, 900, "Demo CAD desktop")).asStateFlow()
}
