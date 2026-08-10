package com.mtd.megawallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val connectionModeProvider: DefaultBlockchainConnectionModeProvider
) : ViewModel() {
    private val _connectionMode = MutableStateFlow(connectionModeProvider.currentMode())
    val connectionMode: StateFlow<BlockchainConnectionMode> = _connectionMode.asStateFlow()

    fun setConnectionMode(mode: BlockchainConnectionMode) {
        if (_connectionMode.value == mode) return
        _connectionMode.value = mode
        viewModelScope.launch { connectionModeProvider.setMode(mode) }
    }
}
