package com.mtd.core.ui

sealed class UiEvent {
//    data class ShowSnackbar(val message: String) : UiEvent()
    data class ShowErrorSnackbar(
        val shortMessage: String,
        val detailedMessage: String="",
        val errorTitle: String = "خطا"
    ) : UiEvent()
    data class ShowDialog(
        val title: String,
        val message: String,
        val positiveButton: String = "تایید",
        val negativeButton: String? = null,
        val onPositive: () -> Unit = {},
        val onNegative: () -> Unit = {}
    ) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    data object DismissLoading : UiEvent()
}
