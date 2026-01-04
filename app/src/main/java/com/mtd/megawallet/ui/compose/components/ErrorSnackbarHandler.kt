package com.mtd.megawallet.ui.compose.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mtd.core.ui.UiEvent
import kotlinx.coroutines.flow.Flow

/**
 * Handler برای مدیریت Error Snackbar کاستوم
 * این composable باید در root layout صفحه قرار بگیرد
 */
@Composable
fun ErrorSnackbarHandler(
    uiEvents: Flow<UiEvent>,
    modifier: Modifier = Modifier
) {
    var errorSnackbarState by remember { mutableStateOf<ErrorSnackbarState?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    // جمع‌آوری events
    LaunchedEffect(Unit) {
        uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowErrorSnackbar -> {
                    errorSnackbarState = ErrorSnackbarState(
                        shortMessage = event.shortMessage,
                        detailedMessage = event.detailedMessage,
                        errorTitle = event.errorTitle
                    )
                }
                else -> {}
            }
        }
    }

    // نمایش Snackbar
    errorSnackbarState?.let { state ->
        CustomTopSnackbar(
            message = state.shortMessage,
            isVisible = true,
            onClick = {
                // اگر detailedMessage وجود داشت، Dialog را نمایش بده
                if (state.detailedMessage.isNotEmpty()) {
                    showErrorDialog = true
                } else {
                    // اگر detailedMessage خالی بود، فقط Snackbar را ببند
                    errorSnackbarState = null
                }
            },
            modifier = modifier
        )
    }

    // نمایش Dialog (فقط اگر detailedMessage وجود داشته باشد)
    if (showErrorDialog && errorSnackbarState != null && errorSnackbarState!!.detailedMessage.isNotEmpty()) {
        ErrorDetailDialog(
            title = errorSnackbarState!!.errorTitle,
            detailedMessage = errorSnackbarState!!.detailedMessage,
            onDismiss = {
                showErrorDialog = false
                errorSnackbarState = null
            }
        )
    }
}

/**
 * State برای نگهداری اطلاعات Error Snackbar
 */
private data class ErrorSnackbarState(
    val shortMessage: String,
    val detailedMessage: String,
    val errorTitle: String
)

