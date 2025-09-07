package com.mtd.megawallet.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.megawallet.event.ReceiveUiState
import com.mtd.megawallet.event.ReceiveUiState.AddressGroup
import com.mtd.megawallet.event.ReceiveUiState.AddressItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val blockchainRegistry: BlockchainRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceiveUiState>(ReceiveUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadReceiveAddresses()
    }

    private fun loadReceiveAddresses() {
        _uiState.value = ReceiveUiState.Loading
        viewModelScope.launch {
            val walletResult = walletRepository.loadExistingWallet()
            if (walletResult !is ResultResponse.Success || walletResult.data == null) {
                _uiState.value = ReceiveUiState.Error("کیف پول پیدا نشد.")
                return@launch
            }
            val activeWallet = walletResult.data!!

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

