package com.mtd.megawallet.viewmodel.assetdetail

import androidx.lifecycle.SavedStateHandle
import com.mtd.core.manager.ErrorManager
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.HomeUiState
import com.mtd.domain.usecase.asset.BuildAssetDetailItemUseCase
import com.mtd.domain.usecase.asset.LoadAssetChartPointsUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val buildAssetDetailItemUseCase: BuildAssetDetailItemUseCase,
    private val loadAssetChartPointsUseCase: LoadAssetChartPointsUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private val _asset = MutableStateFlow<AssetItem?>(null)
    val asset = _asset.asStateFlow()

    private val _chartData = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _isLoadingChart = MutableStateFlow(false)
    val isLoadingChart = _isLoadingChart.asStateFlow()

    private val _selectedTimeFrame = MutableStateFlow("1")
    val selectedTimeFrame = _selectedTimeFrame.asStateFlow()

    private val _displayCurrency = MutableStateFlow(HomeUiState.DisplayCurrency.USDT)
    val displayCurrency = _displayCurrency.asStateFlow()

    init {
        val assetId: String? = savedStateHandle["assetId"]
        assetId?.let { loadAsset(it) }
    }

    internal fun loadAsset(assetId: String) {
        launchSafe {
            val detail = buildAssetDetailItemUseCase(assetId)
            _asset.value = detail.item
            loadChartData(detail.chartSymbol)
        }
    }

    fun setAsset(assetItem: AssetItem) {
        _asset.value = assetItem
        loadChartData(assetItem.name.lowercase())
    }

    fun onTimeFrameSelected(days: String) {
        if (_selectedTimeFrame.value == days) return
        _selectedTimeFrame.value = days
        val baseSymbol = _asset.value?.name?.lowercase() ?: return

        loadChartData(baseSymbol, days)
    }

    private fun loadChartData(coinName: String, days: String = _selectedTimeFrame.value) {
        launchSafe {
            _isLoadingChart.value = true
            _chartData.value = loadAssetChartPointsUseCase(coinName, days)
            _isLoadingChart.value = false
        }
    }

    fun setDisplayCurrency(currency: HomeUiState.DisplayCurrency) {
        _displayCurrency.value = currency
    }
}