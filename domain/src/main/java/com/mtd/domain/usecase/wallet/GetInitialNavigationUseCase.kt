package com.mtd.domain.usecase.wallet

import com.mtd.domain.model.MainNavigationEvent
import javax.inject.Inject

class GetInitialNavigationUseCase @Inject constructor(
    private val hasWalletUseCase: HasWalletUseCase
) {
    suspend operator fun invoke(): MainNavigationEvent {
        return if (hasWalletUseCase()) {
            MainNavigationEvent.NavigateToHome
        } else {
            MainNavigationEvent.NavigateToOnboarding
        }
    }
}
