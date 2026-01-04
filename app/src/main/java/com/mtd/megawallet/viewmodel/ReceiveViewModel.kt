package com.mtd.megawallet.viewmodel


import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.ReceiveUiState
import com.mtd.megawallet.event.ReceiveUiState.AddressGroup
import com.mtd.megawallet.event.ReceiveUiState.AddressItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val activeWalletManager: com.mtd.domain.wallet.ActiveWalletManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    private val _uiState = MutableStateFlow<ReceiveUiState>(ReceiveUiState.Loading)
    val uiState = _uiState.asStateFlow()

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
        _uiState.value = ReceiveUiState.Loading
        launchSafe {

            val addressGroups = mutableListOf<AddressGroup>()

            // گروه ۱: آدرس‌های سازگار با EVM
            val evmKey = activeWallet.keys.find { it.networkType == NetworkType.EVM }
            if (evmKey != null) {
                val supportedEvmNetworks = blockchainRegistry.getAllNetworks()
                    .filter { it.networkType == NetworkType.EVM }
                    .joinToString(", ") { it.name.name.replaceFirstChar { char -> char.titlecase() } }

                addressGroups.add(AddressGroup(
                    title = "آدرس اتریوم",
                    subtitle = "شامل شبکه‌های: $supportedEvmNetworks",
                    items = listOf(AddressItem(
                        id = "EVM",
                        networkName = "آدرس مشترک EVM",
                        address = evmKey.address,
                        iconUrl = blockchainRegistry.getNetworkByName(NetworkName.SEPOLIA)?.iconUrl
                    ))
                ))
            }

            // گروه ۲: آدرس‌های دیگر (کریپتو)
            val otherKeys = activeWallet.keys.filter { it.networkType != NetworkType.EVM }
            if (otherKeys.isNotEmpty()) {
                addressGroups.add(AddressGroup(
                    title = "کریپتو",
                    subtitle = "",
                    items = otherKeys.map { key ->
                        val networkInfo = blockchainRegistry.getNetworkByName(key.networkName)
                        AddressItem(
                            id = networkInfo?.id ?: key.networkName.name,
                            networkName = networkInfo?.name?.name?.replaceFirstChar { it.titlecase() } ?: "Unknown",
                            address = key.address,
                            iconUrl = networkInfo?.iconUrl
                        )
                    }
                ))
            }

            _uiState.value = ReceiveUiState.Success(addressGroups)
        }
    }
}

