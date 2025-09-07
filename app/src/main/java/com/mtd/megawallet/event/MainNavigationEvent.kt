package com.mtd.megawallet.event

sealed class MainNavigationEvent {
    object Loading : MainNavigationEvent() // وضعیت اولیه
    object NavigateToHome : MainNavigationEvent()
    object NavigateToOnboarding : MainNavigationEvent()
}