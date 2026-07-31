package com.mtd.megawallet.ui.compose.components

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext

private const val CLIP_LABEL = "MegaWallet"

/**
 * TASK-52 — the single way to copy text to the clipboard from Compose.
 *
 * The app had grown two different approaches (`LocalClipboard.nativeClipboard.text` in
 * Receive/Send, the deprecated `LocalClipboardManager` in `SecretRevealOverlay`); this exists so a
 * third one doesn't appear. Uses `setPrimaryClip` rather than the deprecated `ClipboardManager.text`
 * setter.
 *
 * Confirmation: Android 13+ draws its own copy confirmation, so a second one would just stack on top
 * of the system's — the toast is therefore only shown below API 33. A styled in-app success snackbar
 * would be nicer, but the only snackbar the app has today is error-styled (red, `colorError`);
 * building a success variant belongs to TASK-57, not here.
 *
 * Blank input is ignored so a call site never silently wipes the user's clipboard with an empty
 * value (e.g. an address that hasn't loaded yet).
 */
@Composable
fun rememberClipboardCopier(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { text: String ->
            if (text.isNotBlank()) {
                clipboard.nativeClipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
