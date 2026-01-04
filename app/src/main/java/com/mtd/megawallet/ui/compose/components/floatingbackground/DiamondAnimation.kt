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
 * Component for drawing animated diamond.
 * Uses remember for caching painter to improve performance.
 */
@Composable
fun DiamondAnimation(
    position: Offset,
    size: Float,
    rotation: Float,
    modifier: Modifier = Modifier
) {
    // Painter - Compose automatically optimizes this
    val diamondIcon = painterResource(R.drawable.ic_diamond)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawDiamond(position, size, rotation, diamondIcon)
    }
}

/**
 * Helper function to draw diamond with rotation.
 */
private fun DrawScope.drawDiamond(
    center: Offset,
    size: Float,
    rotation: Float,
    diamondPainter: Painter
) {
    drawIntoCanvas { canvas ->
        canvas.save()
        
        // انتقال به مرکز
        canvas.translate(center.x, center.y)
        
        // اعمال چرخش
        canvas.rotate(rotation)
        
        // استفاده از سایز ثابت به جای intrinsic برای جلوگیری از bitmap بزرگ
        val drawSize = size * 2f // سایز رسم
        
        canvas.translate(-drawSize / 2, -drawSize / 2)
        
        with(diamondPainter) {
            draw(
                size = Size(drawSize, drawSize),
                alpha = 0.9f
            )
        }
        
        canvas.restore()
    }
}

