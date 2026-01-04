package com.mtd.megawallet.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R


@Composable
fun InputManualSection(modifier: Modifier = Modifier, text:String="", icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        )
        // دکمه شما
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, indication = null, interactionSource = null)
                .background(Color.Transparent)
                .padding(horizontal = 30.dp) // کمی فاصله از لبه‌ها
                .height(35.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            GradientLine(isRevert = false)
            Spacer(modifier = Modifier.width(10.dp))
            Icon(modifier = Modifier
                .size(18.dp),
                imageVector =icon ,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color =  MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light)),
                modifier=Modifier.clickable(onClick = onClick, indication = null, interactionSource = null)
            )
            Spacer(modifier = Modifier.width(10.dp))
            GradientLine(isRevert = true)
        }
    }
}


@Composable
fun GradientLine(
    modifier: Modifier = Modifier,
    startColor: Color = Color.Transparent,
    endColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    strokeWidth: Dp = 1.dp,
    isRevert: Boolean
) {
    Canvas(modifier = modifier.width(50.dp).height(0.2.dp)) {
        // 1. تعریف قلم‌موی گرادینت خطی
        // این قلم‌مو از چپ (startX = 0.0) به راست (endX = size.width) رنگ را تغییر می‌دهد
        val brush = if (isRevert) {
             Brush.linearGradient(
                colors = listOf(endColor, startColor),
                start = Offset(x = 0f, y = center.y),
                end = Offset(x = size.width, y = center.y)
            )
        }else{
            Brush.linearGradient(
                colors = listOf(startColor, endColor),
                start = Offset(x = 0f, y = center.y),
                end = Offset(x = size.width, y = center.y)
            )
        }

        // 2. رسم خط با استفاده از قلم‌موی گرادینت
        drawLine(
            brush = brush,
            start = Offset(x = 0f, y = center.y), // نقطه شروع خط در چپ
            end = Offset(x = size.width, y = center.y),   // نقطه پایان خط در راست
            strokeWidth = strokeWidth.toPx(),
            cap = StrokeCap.Round // برای داشتن لبه‌های گرد و نرم
        )
    }
}

fun Modifier.threeSidedDashedGradientBorder(
    strokeWidth: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 3.dp,
    gapLength: Dp = 3.dp
): Modifier = this.drawBehind {

    // 1. استایل خط‌چین را تعریف می‌کنیم (برای همه مسیرها مشترک است)
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
            phase = 0f
        )
    )
    val cornerRadiusPx = cornerRadius.toPx()

    // 2. گرادینت عمودی را برای خطوط چپ و راست تعریف می‌کنیم
    val verticalGradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, color),
        startY = size.height, // گرادینت از پایین شروع می‌شود
        endY = 0f           // و در بالا تمام می‌شود
    )

    // --- رسم مسیرها ---

    // 3. مسیر خط چپ (با گرادینت)
    val pathLeft = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, cornerRadiusPx)
    }
    drawPath(path = pathLeft, brush = verticalGradientBrush, style = stroke)

    // 4. مسیر خط راست (با گرادینت)
    val pathRight = Path().apply {
        moveTo(size.width, size.height)
        lineTo(size.width, cornerRadiusPx)
    }
    drawPath(path = pathRight, brush = verticalGradientBrush, style = stroke)

    // 5. مسیر خط بالا (شامل گوشه‌های گرد، با رنگ ثابت)
    val pathTop = Path().apply {
        moveTo(0f, cornerRadiusPx) // شروع از انتهای خط چپ
        quadraticBezierTo(x1 = 0f, y1 = 0f, x2 = cornerRadiusPx, y2 = 0f) // گوشه بالا-چپ
        lineTo(size.width - cornerRadiusPx, 0f) // خط صاف بالا
        quadraticBezierTo(
            x1 = size.width,
            y1 = 0f,
            x2 = size.width,
            y2 = cornerRadiusPx
        ) // گوشه بالا-راست
    }
    drawPath(path = pathTop, color = color, style = stroke)
}



