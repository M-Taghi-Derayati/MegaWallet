package com.mtd.domain.model.ui

sealed class UiEvent {
    /**
     * Error-styled top snackbar. [detailedMessage] is the PII-scrubbed technical text; when it is
     * non-empty the snackbar grows a "جزئیات" affordance that opens `ErrorDetailDialog`.
     */
    data class ShowErrorSnackbar(
        val shortMessage: String,
        val detailedMessage: String="",
        val errorTitle: String = "خطا"
    ) : UiEvent()

    /**
     * TASK-57 — success-styled top snackbar. Confirmation only: it auto-dismisses and never
     * carries technical detail. Used for copy confirmation (TASK-52) and wallet deletion (TASK-58).
     */
    data class ShowSuccessSnackbar(val message: String) : UiEvent()

    /** Blocking modal — the user must acknowledge before continuing. See `ErrorSurface.BLOCKING`. */
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
