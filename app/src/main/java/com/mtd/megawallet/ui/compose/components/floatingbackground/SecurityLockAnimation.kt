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
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Component for drawing security lock with ripple effect.
 * Uses remember for caching color to improve performance.
 */
@Composable
fun SecurityLockAnimation(
    position: Offset,
    size: Float,
    rippleRadius: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    // Cache color to avoid recreating on each recomposition
    val lockColor = remember { Color(0xFF22C55E) }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawSecurityLock(position, size, rippleRadius, rotation, lockColor)
    }
}

/**
 * Helper function to draw security lock with ripple effect.
 */
private fun DrawScope.drawSecurityLock(
    center: Offset,
    size: Float,
    rippleRadius: Float,
    rotation: Float,
    lockColor: Color
) {
    // Ripple waves (امواج)
    if (rippleRadius > 0) {
        drawCircle(
            color = lockColor.copy(alpha = 0.4f),
            radius = rippleRadius,
            center = center,
            style = Stroke(width = 3f)
        )
        
        drawCircle(
            color = lockColor.copy(alpha = 0.1f),
            radius = rippleRadius * 5.9f,
            center = center,
            style = Stroke(width = 2f)
        )
    }
    
    // قفل با چرخش ملایم
    rotate(rotation, center) {
        // بدنه قفل
        drawRoundRect(
            color = lockColor,
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
        
        drawPath(path, lockColor, style = Stroke(width = size * 0.12f))
        
        // سوراخ کلید
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = size * 0.12f,
            center = Offset(center.x, center.y + size * 0.25f)
        )
    }
}

