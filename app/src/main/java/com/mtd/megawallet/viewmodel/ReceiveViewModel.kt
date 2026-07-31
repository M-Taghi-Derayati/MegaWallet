package com.mtd.megawallet.viewmodel

import com.mtd.domain.model.ReceiveUiState
import com.mtd.domain.usecase.receive.BuildReceiveAddressGroupsUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val buildReceiveAddressGroupsUseCase: BuildReceiveAddressGroupsUseCase,
    private val observeActiveWalletUseCase: ObserveActiveWalletUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
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
            observeActiveWalletUseCase().collect { wallet ->
                if (wallet != null) {
                    loadReceiveAddresses()
                } else {
                    _uiState.value = ReceiveUiState.Error("کیف پول پیدا نشد.")
                }
            }
        }
    }

    private fun loadReceiveAddresses() {
        launchSafe {
            val activeWallet = getActiveWalletUseCase()
            if (activeWallet == null) {
                _uiState.value = ReceiveUiState.Error("کیف پول پیدا نشد.")
                return@launchSafe
            }

            _uiState.value = ReceiveUiState.Loading
            val lastId = _selectedAddress.value?.id
            val addressGroups = buildReceiveAddressGroupsUseCase(activeWallet)
            val allItems = addressGroups.flatMap { it.items }

            _selectedAddress.value = allItems.find { it.id == lastId } ?: allItems.firstOrNull()
            _uiState.value = ReceiveUiState.Success(addressGroups)
        }
    }

    fun selectAddress(item: ReceiveUiState.AddressItem) {
        _selectedAddress.value = item
    }
}
