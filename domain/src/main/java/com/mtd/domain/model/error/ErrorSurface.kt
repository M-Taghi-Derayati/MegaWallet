package com.mtd.domain.model.error

/**
 * TASK-57 — the app-wide severity policy for a failure. Every error branch must pick one
 * deliberately; "do nothing" is only allowed as [SILENT], which still logs.
 *
 * How to choose:
 *
 * - [SILENT] — the user did not ask for this and cannot act on it. Background/periodic work:
 *   price and balance refresh, fee polling, sponsor-preview enrichment, address-book warm-up,
 *   cached-value lookups. A failed background refresh must never nag; the stale value stays on
 *   screen and the next tick retries.
 * - [SNACKBAR] — the user started something that failed but nothing was lost and they can retry:
 *   loading a list, switching wallets, connecting cloud backup, validating input. Shows the top
 *   snackbar with curated Persian copy; the likely [ErrorReason]s and the technical text sit one
 *   tap away, inside the card it unfolds into.
 * - [BLOCKING] — money, keys, or data are at stake and the user must acknowledge before moving on:
 *   a failed send/broadcast, a failed gasless submit, wallet deletion, backup delete, seed reveal.
 *   Shows a modal dialog.
 */
enum class ErrorSurface {
    SILENT,
    SNACKBAR,
    BLOCKING
}

/**
 * A failure already turned into something safe to render: curated Persian [shortMessage], a
 * PII-scrubbed [technicalDetail], the likely [reasons] behind it, and the [surface] that decides
 * how loudly to show it. Produced by [ErrorMapper.present]; never build one from a raw exception.
 *
 * [reasons] and [technicalDetail] are what the snackbar shows once the user taps it open; when
 * both are empty the message stays a plain pill and simply times out.
 */
data class ErrorPresentation(
    val title: String,
    val shortMessage: String,
    val technicalDetail: String,
    val surface: ErrorSurface,
    val reasons: List<ErrorReason> = emptyList()
)
