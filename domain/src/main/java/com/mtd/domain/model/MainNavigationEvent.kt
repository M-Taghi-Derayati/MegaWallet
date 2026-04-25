package com.mtd.domain.model

sealed class MainNavigationEvent {
    object Loading : MainNavigationEvent() // وضعیت اولیه
    object NavigateToHome : MainNavigationEvent()
    object NavigateToOnboarding : MainNavigationEvent()
}