package com.mtd.megawallet.event

sealed interface ValidationResult {
    /**
     * ورودی معتبر است.
     */
    data object Valid : ValidationResult

    data object Loading : ValidationResult

    /**
     * ورودی نامعتبر است.
     * @property reason دلیل نامعتبر بودن (برای نمایش به کاربر).
     */
    data class Invalid(val reason: String) : ValidationResult
}