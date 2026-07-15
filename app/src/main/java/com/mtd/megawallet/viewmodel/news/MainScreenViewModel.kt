package com.mtd.megawallet.viewmodel.news


import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.SavedStateHandle
import com.mtd.core.manager.ErrorManager
import com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.usecase.history.ObservePendingHistoryActivityUseCase
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.ui.compose.screens.main.MainTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(
    private val observePendingHistoryActivityUseCase: ObservePendingHistoryActivityUseCase,
    private val connectionModeProvider: DefaultBlockchainConnectionModeProvider,
    private val savedStateHandle: SavedStateHandle,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    // TASK-15 — the current tab + open asset are navigation state; mirror them into SavedStateHandle so
    // a low-memory process kill restores the same screen. The MutableStateFlow stays the reactive source
    // of truth (seeded from the handle on recreate); each setter writes the value back through.
    private val _selectedAssetId = MutableStateFlow(savedStateHandle.get<String?>(KEY_SELECTED_ASSET_ID))
    val selectedAssetId: StateFlow<String?> = _selectedAssetId.asStateFlow()

    /**
     * KAN-9 / KAN-19 — current DIRECT/PROXY transport, surfaced so the header search icon can both
     * toggle it and reflect it. Seeded from the persisted preference via the shared singleton
     * provider (the same instance [com.mtd.data.datasource.ChainDataSourceFactory] reads).
     */
    private val _connectionMode = MutableStateFlow(connectionModeProvider.currentMode())
    val connectionMode: StateFlow<BlockchainConnectionMode> = _connectionMode.asStateFlow()

    private val _selectedTab = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SELECTED_TAB)
            ?.let { runCatching { MainTab.valueOf(it) }.getOrNull() }
            ?: MainTab.WALLET
    )
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _hasPendingHistoryActivity = MutableStateFlow(false)
    val hasPendingHistoryActivity: StateFlow<Boolean> = _hasPendingHistoryActivity.asStateFlow()

    var lastSelectedId: String? = null
        private set

    val assetBounds = mutableStateMapOf<String, Rect>()

    init {
        observeHistoryActivity()
        // TASK-05/TD-19 — hydrate the persisted transport mode off the main thread (the field seed
        // above is now a non-blocking cache-or-default read), then reflect the real value.
        launchSafe(checkNetwork = false) {
            connectionModeProvider.prime()
            _connectionMode.value = connectionModeProvider.currentMode()
        }
    }

    fun onAssetClicked(assetId: String, bounds: Rect) {
        assetBounds[assetId] = bounds
        lastSelectedId = assetId
        _selectedAssetId.value = assetId
        savedStateHandle[KEY_SELECTED_ASSET_ID] = assetId
    }

    fun onNavigateBack() {
        _selectedAssetId.value = null
        savedStateHandle[KEY_SELECTED_ASSET_ID] = null
    }

    /**
     * Flips the blockchain transport between PROXY and DIRECT. [DefaultBlockchainConnectionModeProvider.setMode]
     * persists the choice (survives process death) and refreshes the in-memory cache the data-source
     * factory reads, so the next network request already uses the new mode.
     */
    fun toggleConnectionMode() {
        val next = when (_connectionMode.value) {
            BlockchainConnectionMode.DIRECT -> BlockchainConnectionMode.PROXY
            BlockchainConnectionMode.PROXY -> BlockchainConnectionMode.DIRECT
        }
        // Optimistically reflect in the UI; persist + refresh the provider cache off the main thread.
        _connectionMode.value = next
        launchSafe(checkNetwork = false) {
            connectionModeProvider.setMode(next)
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
        savedStateHandle[KEY_SELECTED_TAB] = tab.name
        if (tab == MainTab.HISTORY) {
            _hasPendingHistoryActivity.value = false
        }
    }

    private fun observeHistoryActivity() {
        launchSafe(checkNetwork = false) {
            observePendingHistoryActivityUseCase().collect { hasPendingActivity ->
                _hasPendingHistoryActivity.value = hasPendingActivity
            }
        }
    }

    private companion object {
        const val KEY_SELECTED_TAB = "main.selectedTab"
        const val KEY_SELECTED_ASSET_ID = "main.selectedAssetId"
    }
}
