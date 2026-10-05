package com.jhaago.cadagent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jhaago.cadagent.di.AppContainer
import com.jhaago.cadagent.remote.live.*

/** Keep the same job/session repositories across Activity orientation changes. */
class CadAgentAppViewModel(application: Application) : AndroidViewModel(application) {
    val container = AppContainer().apply {
        val preferences = application.getSharedPreferences("remote-workstation", 0)
        configureRemoteSettings(viewModelScope, HttpsRemoteTransport(), KeystoreRemoteCredentialStore(application),
            preferences.getString("origin", "").orEmpty()) { preferences.edit().putString("origin", it).apply() }
    }
    override fun onCleared() {
        container.close()
    }
}
