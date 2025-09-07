package com.mtd.megawallet.event

data class ImportUiState(
    val isLoading: Boolean = false,
    val inputText: String = "",
    val isValid: Boolean = false,
    val validationError: String? = null
)