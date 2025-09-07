package com.mtd.megawallet.event

// ایونت‌های ناوبری
sealed class OnboardingNavigationEvent {
    data class NavigateToShowMnemonic(val mnemonic: List<String>) : OnboardingNavigationEvent()
    object NavigateToHome : OnboardingNavigationEvent()
    object NavigateToImportOptions : OnboardingNavigationEvent()
    data class NavigateToSelectWallets(val mnemonic: String?=null,val privateKey: String?=null) : OnboardingNavigationEvent()
}