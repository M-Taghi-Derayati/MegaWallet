package com.mtd.megawallet.ui.compose.components.floatingbackground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.mtd.megawallet.R

/**
 * Component for drawing wallet icon.
 * Uses remember for caching painter to improve performance.
 */
@Composable
fun WalletAnimation(
    position: Offset,
    size: Float,
    modifier: Modifier = Modifier
) {
    // Painter - Compose automatically optimizes this
    val walletIcon = painterResource(R.drawable.ic_wallet_color)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawWallet(position, size, walletIcon)
    }
}

/**
 * Helper function to draw wallet icon.
 */
private fun DrawScope.drawWallet(
    center: Offset,
    size: Float,
    walletPainter: Painter
) {
    drawIntoCanvas { canvas ->
        canvas.save()
        
        // انتقال به مرکز
        canvas.translate(center.x, center.y)
        
        // محاسبه scale بر اساس intrinsic size
        val intrinsicWidth = walletPainter.intrinsicSize.width
        val intrinsicHeight = walletPainter.intrinsicSize.height
        
        val scale = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            (size * 3f) / maxOf(intrinsicWidth, intrinsicHeight)
        } else {
            (size * 3f) / 100f
        }
        
        canvas.scale(scale, scale)
        canvas.translate(-intrinsicWidth / 2, -intrinsicHeight / 2)
        
        with(walletPainter) {
            draw(
                size = Size(intrinsicWidth, intrinsicHeight),
                alpha = 0.9f
            )
        }
        
        canvas.restore()
    }
}

