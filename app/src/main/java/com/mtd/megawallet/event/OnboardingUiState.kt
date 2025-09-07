package com.mtd.megawallet.event


sealed interface OnboardingUiState {
    /**
     * وضعیت اولیه یا بیکار.
     */
    data object Idle : OnboardingUiState

    /**
     * در حال انجام یک عملیات زمان‌بر (مثل ساخت یا وارد کردن کیف پول).
     * @property message یک پیام اختیاری برای نمایش به کاربر (مثلا "در حال ساخت کیف پول...").
     */
    data class Loading(val message: String? = null) : OnboardingUiState

    /**
     * وضعیت صفحه وارد کردن عبارت بازیابی.
     * @property enteredWords لیست کلماتی که کاربر تا الان وارد کرده.
     * @property validationResult نتیجه اعتبارسنجی لحظه‌ای ورودی.
     */
    data class EnteringSeed(
        val enteredWords: List<String> = emptyList(),
        val currentWord: String = "",
        val suggestion: String? = null,
        val isPrivateKey: Boolean = false,
        val validationResult: ValidationResult = ValidationResult.Loading
    ) : OnboardingUiState

    data class WalletsToImport(
        val isLoading: Boolean = true,
        val accounts: List<AccountInfo> = emptyList(),
        val selectedAccountIds: Set<String> = emptySet()
    ) : OnboardingUiState

    /**
     * یک خطای قابل نمایش به کاربر رخ داده است.
     * @property message پیام خطا.
     */
    data class Error(val message: String) : OnboardingUiState
}





