package com.mtd.megawallet.ui.compose.components.floatingbackground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Component for drawing simple lock icon.
 * Uses remember for caching color to improve performance.
 */
@Composable
fun SimpleLock(
    position: Offset,
    color: Color,
    size: Float,
    modifier: Modifier = Modifier
) {
    // Cache color to avoid recreating on each recomposition
    val cachedColor = remember { color }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawLock(position, cachedColor, size)
    }
}

/**
 * Helper function to draw simple lock.
 */
private fun DrawScope.drawLock(
    center: Offset,
    color: Color,
    size: Float
) {
    // بدنه قفل
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - size * 0.4f, center.y),
        size = Size(size * 0.8f, size * 0.6f),
        cornerRadius = CornerRadius(size * 0.1f)
    )

    // دسته قفل
    val path = Path().apply {
        moveTo(center.x - size * 0.3f, center.y)
        lineTo(center.x - size * 0.3f, center.y - size * 0.3f)
        cubicTo(
            center.x - size * 0.3f, center.y - size * 0.6f,
            center.x + size * 0.3f, center.y - size * 0.6f,
            center.x + size * 0.3f, center.y - size * 0.3f
        )
        lineTo(center.x + size * 0.3f, center.y)
    }

    drawPath(path, color, style = Stroke(width = size * 0.12f))

    // سوراخ کلید
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = size * 0.12f,
        center = Offset(center.x, center.y + size * 0.25f)
    )
}

