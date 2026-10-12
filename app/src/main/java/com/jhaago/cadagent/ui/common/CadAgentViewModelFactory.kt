package com.jhaago.cadagent.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CadAgentViewModelFactory<VM : ViewModel>(
    private val createViewModel: () -> VM,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel() as T
}
