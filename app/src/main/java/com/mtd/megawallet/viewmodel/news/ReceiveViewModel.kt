package com.mtd.megawallet.viewmodel.news

import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.domain.wallet.ActiveWalletManager
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.ReceiveUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val activeWalletManager: ActiveWalletManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val _uiState = MutableStateFlow<ReceiveUiState>(ReceiveUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedAddress = MutableStateFlow<ReceiveUiState.AddressItem?>(null)
    val selectedAddress = _selectedAddress.asStateFlow()

    init {
        observeActiveWallet()
    }

    private fun observeActiveWallet() {
        launchSafe {
            activeWalletManager.activeWallet.collect { wallet ->
                if (wallet != null) {
                    loadReceiveAddresses(wallet)
                } else {
                    _uiState.value = ReceiveUiState.Error("کیف پول پیدا نشد.")
                }
            }
        }
    }

    private fun loadReceiveAddresses(activeWallet: com.mtd.domain.model.Wallet) {
        launchSafe {
            _uiState.value = ReceiveUiState.Loading
            
            // ذخیره ID انتخاب شده فعلی برای حفظ انتخاب در صورت امکان
            val lastId = _selectedAddress.value?.id
            val addressGroups = mutableListOf<ReceiveUiState.AddressGroup>()

            // گروه EVM
            val evmKey = activeWallet.keys.find { it.networkType == NetworkType.EVM }
            if (evmKey != null) {
                val evmNetworks = blockchainRegistry.getAllNetworks()
                    .filter { it.networkType == NetworkType.EVM }
                
                val supportedIcons = evmNetworks.mapNotNull { it.iconUrl }
                val supportedIds = evmNetworks.map { it.id }

                val item = ReceiveUiState.AddressItem(
                    id = "EVM",
                    symbol = "ETH",
                    networkName = "EVM Networks",
                    networkFaName = "شبکه‌های اتریومی",
                    address = evmKey.address,
                    iconUrl = blockchainRegistry.getNetworkByName(NetworkName.SEPOLIA)?.iconUrl,
                    supportedNetworkIcons = supportedIcons,
                    supportedNetworkIds = supportedIds
                )
                
                addressGroups.add(ReceiveUiState.AddressGroup(
                    title = "آدرس اتریوم",
                    subtitle = "Supported Networks",
                    items = listOf(item)
                ))
            }

            // سایر شبکه‌ها
            val otherKeys = activeWallet.keys.filter { it.networkType != NetworkType.EVM }
            if (otherKeys.isNotEmpty()) {
                val items = otherKeys.map { key ->
                    val networkInfo = blockchainRegistry.getNetworkByName(key.networkName)
                    ReceiveUiState.AddressItem(
                        id = networkInfo?.id ?: key.networkName.name,
                        symbol = networkInfo?.currencySymbol ?: key.networkName.name,
                        networkName = networkInfo?.name?.name?.replaceFirstChar { it.titlecase() } ?: "Unknown",
                        networkFaName = networkInfo?.faName,
                        address = key.address,
                        iconUrl = networkInfo?.iconUrl
                    )
                }
                addressGroups.add(ReceiveUiState.AddressGroup(
                    title = "سایر شبکه‌ها",
                    subtitle = "",
                    items = items
                ))
            }

            // آپدیت آدرس انتخاب شده:
            // اگر قبلاً چیزی انتخاب شده بود و در لیست جدید هم هست، همان را آپدیت کن
            // در غیر این صورت، اولین مورد را انتخاب کن
            val allItems = addressGroups.flatMap { it.items }
            _selectedAddress.value = allItems.find { it.id == lastId } ?: allItems.firstOrNull()

            _uiState.value = ReceiveUiState.Success(addressGroups)
        }
    }

    fun selectAddress(item: ReceiveUiState.AddressItem) {
        _selectedAddress.value = item
    }
}
