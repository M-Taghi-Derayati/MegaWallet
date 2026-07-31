package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mtd.domain.model.ui.UiEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** How long a success confirmation stays up before it dismisses itself. */
private const val SUCCESS_SNACKBAR_DURATION_MS = 2_200L

/**
 * TASK-57 — the single place app-wide messages are drawn.
 *
 * Mount exactly one of these at each Activity root and feed it `ErrorManager.uiMessages`. It
 * renders the three surfaces of the severity policy:
 *
 * - `ShowErrorSnackbar` → red top snackbar, sticky; tapping opens [ErrorDetailDialog] when there
 *   is technical detail, otherwise dismisses.
 * - `ShowSuccessSnackbar` → green top snackbar, auto-dismissing.
 * - `ShowDialog` → blocking modal the user must acknowledge.
 *
 * `SILENT` failures never reach here by construction — `ErrorManager` logs and stops.
 */
@Composable
fun AppMessageHost(
    uiMessages: Flow<UiEvent>,
    modifier: Modifier = Modifier
) {
    var snackbar by remember { mutableStateOf<SnackbarMessage?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var blockingDialog by remember { mutableStateOf<UiEvent.ShowDialog?>(null) }
    // Distinguishes two identical consecutive messages so the auto-dismiss timer restarts.
    val nextId = remember { mutableLongStateOf(0L) }

    LaunchedEffect(uiMessages) {
        uiMessages.collect { event ->
            when (event) {
                is UiEvent.ShowErrorSnackbar -> {
                    showDetailDialog = false
                    snackbar = SnackbarMessage(
                        id = nextId.longValue++,
                        message = event.shortMessage,
                        detail = event.detailedMessage,
                        title = event.errorTitle,
                        style = TopSnackbarStyle.ERROR
                    )
                }

                is UiEvent.ShowSuccessSnackbar -> {
                    showDetailDialog = false
                    snackbar = SnackbarMessage(
                        id = nextId.longValue++,
                        message = event.message,
                        detail = "",
                        title = "",
                        style = TopSnackbarStyle.SUCCESS
                    )
                }

                is UiEvent.ShowDialog -> blockingDialog = event

                is UiEvent.Navigate,
                UiEvent.DismissLoading -> Unit
            }
        }
    }

    val current = snackbar

    // Success confirmations clear themselves; errors stay until the user acknowledges them.
    LaunchedEffect(current?.id) {
        val active = current ?: return@LaunchedEffect
        if (active.style != TopSnackbarStyle.SUCCESS) return@LaunchedEffect
        delay(SUCCESS_SNACKBAR_DURATION_MS)
        if (snackbar?.id == active.id) snackbar = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (current != null) {
            CustomTopSnackbar(
                message = current.message,
                isVisible = true,
                style = current.style,
                showDetailsAffordance = current.detail.isNotEmpty(),
                onClick = {
                    if (current.detail.isNotEmpty()) {
                        showDetailDialog = true
                    } else {
                        snackbar = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp)
                    .zIndex(100f)
            )
        }
    }

    if (showDetailDialog && current != null && current.detail.isNotEmpty()) {
        ErrorDetailDialog(
            title = current.title.ifBlank { "خطا" },
            detailedMessage = current.detail,
            onDismiss = {
                showDetailDialog = false
                snackbar = null
            }
        )
    }

    blockingDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { /* blocking — acknowledgement is required */ },
            title = { Text(text = dialog.title, style = MaterialTheme.typography.titleMedium) },
            text = { Text(text = dialog.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    blockingDialog = null
                    dialog.onPositive()
                }) {
                    Text(text = dialog.positiveButton)
                }
            },
            dismissButton = dialog.negativeButton?.let { negative ->
                {
                    TextButton(onClick = {
                        blockingDialog = null
                        dialog.onNegative()
                    }) {
                        Text(text = negative)
                    }
                }
            }
        )
    }
}

private data class SnackbarMessage(
    val id: Long,
    val message: String,
    val detail: String,
    val title: String,
    val style: TopSnackbarStyle
)
