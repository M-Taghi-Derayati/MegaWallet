package com.mtd.megawallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.data.repository.IWalletRepository
import com.mtd.megawallet.event.MainNavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val walletRepository: IWalletRepository
) : ViewModel() {

    // استفاده از StateFlow برای نگهداری وضعیت ناوبری اولیه
    private val _navigationState = MutableStateFlow<MainNavigationEvent>(MainNavigationEvent.Loading)
    val navigationState = _navigationState.asStateFlow()


    init {
        // به محض ساخته شدن ViewModel، وضعیت کیف پول را بررسی کن
        checkWalletStatus()
    }

    private fun checkWalletStatus() {
        viewModelScope.launch {
            if (walletRepository.hasWallet()) {
                _navigationState.value = MainNavigationEvent.NavigateToHome
            } else {
                _navigationState.value = MainNavigationEvent.NavigateToOnboarding
            }
        }
    }


}


