package com.mtd.megawallet.ui.compose.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * TASK-02 / TD-36 — screenshot & screen-recording protection for sensitive screens.
 *
 * While [active] is true, the host Activity window carries `FLAG_SECURE`, which blocks screenshots,
 * screen recording, and blanks the app-switcher (recents) thumbnail. The flag is removed when the
 * screen leaves composition or [active] becomes false.
 *
 * Reference-counted (single-window app assumption) so overlapping secure screens — e.g. an unlock
 * sheet over onboarding — do not clear the flag prematurely. Use this uniformly (rather than an
 * Activity-level `window.setFlags`) so a child's `onDispose` can never strip a parent's protection.
 *
 * Apply to any composable that renders a recovery phrase, private key, or passcode entry. Onboarding
 * covers all its screens by placing [SecureFlagEffect] at the top of its content.
 */
private val secureRefCount = AtomicInteger(0)

@Composable
fun SecureFlagEffect(active: Boolean = true) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivityWindow() }
    DisposableEffect(active, window) {
        if (active && window != null) {
            secureRefCount.incrementAndGet()
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                if (secureRefCount.decrementAndGet() <= 0) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        } else {
            onDispose { }
        }
    }
}

private tailrec fun Context.findActivityWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}
