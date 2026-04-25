package com.mtd.megawallet.viewmodel.news


import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import com.mtd.core.manager.ErrorManager
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainScreenViewModel @Inject constructor(errorManager: ErrorManager): BaseViewModel(errorManager) {

    private val _selectedAssetId = MutableStateFlow<String?>(null)
    val selectedAssetId: StateFlow<String?> = _selectedAssetId.asStateFlow()

    private val _selectedTab = MutableStateFlow(com.mtd.megawallet.ui.compose.screens.main.MainTab.WALLET)
    val selectedTab: StateFlow<com.mtd.megawallet.ui.compose.screens.main.MainTab> = _selectedTab.asStateFlow()

    var lastSelectedId: String? = null
        private set

    val assetBounds = mutableStateMapOf<String, Rect>()

    fun onAssetClicked(assetId: String, bounds: Rect) {
        assetBounds[assetId] = bounds
        lastSelectedId = assetId
        _selectedAssetId.value = assetId
    }

    fun onNavigateBack() {
        _selectedAssetId.value = null
    }

    fun selectTab(tab: com.mtd.megawallet.ui.compose.screens.main.MainTab) {
        _selectedTab.value = tab
    }
}