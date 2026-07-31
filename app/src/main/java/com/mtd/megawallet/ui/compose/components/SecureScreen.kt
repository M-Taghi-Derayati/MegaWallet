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
