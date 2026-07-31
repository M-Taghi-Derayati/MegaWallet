package com.mtd.megawallet.viewmodel

import com.mtd.core.manager.ErrorManager
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.domain.model.MainNavigationEvent
import com.mtd.domain.usecase.wallet.GetInitialNavigationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getInitialNavigationUseCase: GetInitialNavigationUseCase,
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
            _navigationState.value = getInitialNavigationUseCase()
        }
    }


}

