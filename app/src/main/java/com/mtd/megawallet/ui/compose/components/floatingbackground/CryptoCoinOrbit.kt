package com.mtd.megawallet.ui.compose.components.floatingbackground

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.mtd.megawallet.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Component for drawing crypto coins in orbit around center.
 * Uses remember for caching painters to improve performance.
 */
@Composable
fun CryptoCoinOrbit(
    rotateFast: Float,
    rotateMedium: Float,
    rotateSlow: Float,
    center: Offset,
    minDim: Float,
    modifier: Modifier = Modifier
) {
    // Painters - Compose automatically optimizes these
    val btcIcon = painterResource(R.drawable.ic_btc_light)
    val ethIcon = painterResource(R.drawable.ic_eth_light)
    val usdtIcon = painterResource(R.drawable.ic_usdt_light)
    
    // Colors - using remember for simple Color objects (not composable)
    val btcColor = remember { Color(0xFFF7931A) }
    val ethColor = remember { Color(0xFF627EEA) }
    val usdtColor = remember { Color(0xFF009393) }

    // بهینه‌سازی: محاسبه مدارها با remember برای cache کردن
    val orbit1 = remember(minDim) { minDim * 0.22f }
    val orbit2 = remember(minDim) { minDim * 0.32f }
    val orbit3 = remember(minDim) { minDim * 0.44f }
    
    // بهینه‌سازی: تبدیل درجه به رادیان (PI/180) یکبار محاسبه می‌شود
    val degreesToRadians = remember { PI / 180.0 }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // زاویه‌ها - استفاده از PI/180 به جای Math.toRadians برای بهینه‌سازی
        val btcAngle = rotateFast * degreesToRadians
        val ethAngle = (rotateMedium + 90) * degreesToRadians
        val usdtAngle = (rotateSlow + 200) * degreesToRadians

        // موقعیت‌ها - محاسبات sin/cos بهینه شده
        val btcPos = center + Offset(cos(btcAngle).toFloat(), sin(btcAngle).toFloat()) * orbit1
        val ethPos = center + Offset(cos(ethAngle).toFloat(), sin(ethAngle).toFloat()) * orbit2
        val usdtPos = center + Offset(cos(usdtAngle).toFloat(), sin(usdtAngle).toFloat()) * orbit3

        // سایه
        drawCircle(Color.Black.copy(alpha = 0.8f), minDim * 0.02f, btcPos + Offset(4f, 6f))
        drawCircle(Color.Black.copy(alpha = 0.07f), minDim * 0.018f, ethPos + Offset(4f, 6f))
        drawCircle(Color.Black.copy(alpha = 0.06f), minDim * 0.02f, usdtPos + Offset(4f, 6f))

        // رسم سکه‌ها
        drawCoinSimple(btcPos, minDim * 0.06f, btcColor, btcIcon)
        drawCoinSimple(ethPos, minDim * 0.058f, ethColor, ethIcon)
        drawCoinSimple(usdtPos, minDim * 0.06f, usdtColor, usdtIcon)
    }
}

/**
 * Helper function to draw a coin with icon.
 */
private fun DrawScope.drawCoinSimple(
    center: Offset,
    size: Float,
    color: Color,
    symbolPainter: Painter
) {
    // outer metallic ring
    drawCircle(color.copy(alpha = 0.95f), size, center)

    // highlight
    drawOval(
        Color.White.copy(alpha = 0.15f),
        topLeft = Offset(center.x - size * 0.45f, center.y - size * 0.75f),
        size = Size(size * 0.6f, size * 0.35f)
    )

    // side rim
    drawCircle(
        Color.Black.copy(alpha = 0.22f),
        radius = size * 1.02f,
        center = center,
        style = Stroke(size * 0.08f)
    )

    // icon - رسم Painter با حفظ aspect ratio
    val iconSize = size * 1.2f
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(center.x, center.y)
        
        // محاسبه scale بر اساس intrinsic size
        val intrinsicWidth = symbolPainter.intrinsicSize.width
        val intrinsicHeight = symbolPainter.intrinsicSize.height
        
        val scale = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            iconSize / maxOf(intrinsicWidth, intrinsicHeight)
        } else {
            iconSize / 100f // fallback
        }
        
        canvas.scale(scale, scale)
        canvas.translate(-intrinsicWidth / 2, -intrinsicHeight / 2)
        
        with(symbolPainter) {
            draw(
                size = Size(intrinsicWidth, intrinsicHeight),
                alpha = 1f
            )
        }
        canvas.restore()
    }
}

