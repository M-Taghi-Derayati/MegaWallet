package com.mtd.megawallet.ui.compose.components.floatingbackground

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter


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

