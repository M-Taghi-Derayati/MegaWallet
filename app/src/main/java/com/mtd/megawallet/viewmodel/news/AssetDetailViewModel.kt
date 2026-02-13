package com.mtd.megawallet.viewmodel.news

import androidx.lifecycle.SavedStateHandle
import com.mtd.core.registry.AssetRegistry
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.domain.wallet.ActiveWalletManager
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.HomeUiState.DisplayCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject


@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val marketDataRepository: IMarketDataRepository,
    private val assetRegistry: AssetRegistry,
    private val activeWalletManager: ActiveWalletManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val _asset = MutableStateFlow<AssetItem?>(null)
    val asset = _asset.asStateFlow()

    private val _chartData = MutableStateFlow<List<Pair<Long, Double>>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _isLoadingChart = MutableStateFlow(false)
    val isLoadingChart = _isLoadingChart.asStateFlow()

    private val _selectedTimeFrame = MutableStateFlow("1") // "1", "7", "30", "365"
    val selectedTimeFrame = _selectedTimeFrame.asStateFlow()

    // فیلد جهت نمایش مبالغ (تومان یا تتر)
    private val _displayCurrency = MutableStateFlow(DisplayCurrency.USDT)
    val displayCurrency = _displayCurrency.asStateFlow()

    init {
        // دریافت اطلاعات اولیه از SavedStateHandle (اگر پاس داده شده باشد)
        // یا لود کردن بر اساس ID
        val assetId: String? = savedStateHandle["assetId"]
        assetId?.let { loadAsset(it) }
    }

    internal fun loadAsset(assetId: String) {
        val isGroup = assetId.startsWith("GROUP_")
        val resolvedId = if (isGroup) {
            // For grouped assets, we try to find the first asset with that symbol in the registry
            val symbol = assetId.removePrefix("GROUP_")
            assetRegistry.getAllAssets().find { it.symbol.equals(symbol, true) }?.id ?: assetId
        } else {
            assetId
        }

        val config = assetRegistry.getAssetById(resolvedId)

        launchSafe {
            _asset.value = AssetItem(
                id = assetId,
                name = config?.name ?: (if (isGroup) assetId.removePrefix("GROUP_") else assetId),
                faName = config?.faName,
                symbol = config?.symbol ?: (if (isGroup) assetId.removePrefix("GROUP_") else ""),
                networkName = if (isGroup) "" else (config?.networkId ?: ""),
                networkId = if (isGroup) "GROUP" else (config?.networkId ?: ""),
                iconUrl = config?.iconUrl,
                balance = "...",
                balanceUsdt = "...",
                isGroupHeader = isGroup
            )

            // لود کردن چارت
            val coinGeckoId = config?.symbol
                ?: (if (isGroup) assetId.removePrefix("GROUP_").lowercase() else assetId.lowercase())
            loadChartData(coinGeckoId)
        }
    }

    fun setAsset(assetItem: AssetItem) {
        _asset.value = assetItem
        assetItem.id.let {
            val config = assetRegistry.getAssetById(it)
            loadChartData(config?.symbol ?: assetItem.symbol.lowercase())
        }
    }

    fun onTimeFrameSelected(days: String) {
        if (_selectedTimeFrame.value == days) return
        _selectedTimeFrame.value = days
        val coinGeckoId = assetRegistry.getAssetById(_asset.value?.id ?: "")?.symbol
            ?: _asset.value?.symbol?.lowercase() ?: return

        loadChartData(coinGeckoId, days)
    }

    private fun loadChartData(coinId: String, days: String = _selectedTimeFrame.value) {
        launchSafe {
            _isLoadingChart.value = true
            when (val result = marketDataRepository.getHistoricalPrices(coinId, days)) {
                is ResultResponse.Success -> {
                    _chartData.value = result.data ?: emptyList()
                }
                is ResultResponse.Error -> {
                    // هندل کردن خطا
                }
            }
            _isLoadingChart.value = false
        }
    }

    fun setDisplayCurrency(currency: DisplayCurrency) {
        _displayCurrency.value = currency
    }
}
