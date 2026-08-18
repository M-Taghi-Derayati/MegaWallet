package com.mtd.domain.model.ui

import com.mtd.domain.model.error.ErrorReason

sealed class UiEvent {
    /**
     * Error-styled top snackbar.
     *
     * The pill shows [shortMessage] alone. [reasons] and [detailedMessage] (the PII-scrubbed
     * technical text) are what it unfolds into when tapped; when both are empty there is nothing
     * to unfold and the pill is a plain, self-dismissing message.
     */
    data class ShowErrorSnackbar(
        val shortMessage: String,
        val detailedMessage: String="",
        val errorTitle: String = "خطا",
        val reasons: List<ErrorReason> = emptyList()
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
