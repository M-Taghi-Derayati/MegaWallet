package com.mtd.megawallet.viewmodel.news


import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import com.mtd.core.manager.ErrorManager
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
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private val _selectedAssetId = MutableStateFlow<String?>(null)
    val selectedAssetId: StateFlow<String?> = _selectedAssetId.asStateFlow()

    private val _selectedTab = MutableStateFlow(MainTab.WALLET)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _hasPendingHistoryActivity = MutableStateFlow(false)
    val hasPendingHistoryActivity: StateFlow<Boolean> = _hasPendingHistoryActivity.asStateFlow()

    var lastSelectedId: String? = null
        private set

    val assetBounds = mutableStateMapOf<String, Rect>()

    init {
        observeHistoryActivity()
    }

    fun onAssetClicked(assetId: String, bounds: Rect) {
        assetBounds[assetId] = bounds
        lastSelectedId = assetId
        _selectedAssetId.value = assetId
    }

    fun onNavigateBack() {
        _selectedAssetId.value = null
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
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
}
