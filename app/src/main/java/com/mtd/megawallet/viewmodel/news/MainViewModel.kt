package com.mtd.megawallet.viewmodel.news

import com.mtd.core.manager.ErrorManager
import com.mtd.data.repository.IWalletRepository
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.MainNavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    // استفاده از StateFlow برای نگهداری وضعیت ناوبری اولیه
    private val _navigationState = MutableStateFlow<MainNavigationEvent>(MainNavigationEvent.Loading)
    val navigationState = _navigationState.asStateFlow()


    init {
        // به محض ساخته شدن ViewModel، وضعیت کیف پول را بررسی کن
        checkWalletStatus()
    }

    private fun checkWalletStatus() {
        launchSafe {
            if (walletRepository.hasWallet()) {
                _navigationState.value = MainNavigationEvent.NavigateToHome
            } else {
                _navigationState.value = MainNavigationEvent.NavigateToOnboarding
            }
        }
    }


}

