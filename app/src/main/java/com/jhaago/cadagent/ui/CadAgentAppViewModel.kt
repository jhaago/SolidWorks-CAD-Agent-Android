package com.jhaago.cadagent.ui

import androidx.lifecycle.ViewModel
import com.jhaago.cadagent.di.AppContainer

/** Keep the same job/session repositories across Activity orientation changes. */
class CadAgentAppViewModel : ViewModel() {
    val container = AppContainer()
    override fun onCleared() {
        container.remoteInput.releaseAll()
        container.remoteSession.disconnect()
    }
}
