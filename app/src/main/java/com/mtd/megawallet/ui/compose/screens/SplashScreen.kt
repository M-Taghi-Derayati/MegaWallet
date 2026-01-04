package com.mtd.megawallet.ui.compose.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * صفحه اسپلش (Splash Screen) که هنگام شروع برنامه نمایش داده می‌شود.
 * شامل یک انیمیشن بزرگ‌نمایی (Scale) با افکت فنری (Spring) برای لوگوی مگاوالت است.
 * بعد از اتمام انیمیشن و یک مکث کوتاه، کالبک پایان فراخوانی می‌شود.
 *
 * @param onAnimationEnd کالبک برای زمانی که انیمیشن اسپلش تمام شده و باید به صفحه بعد رفت.
 */
@Composable
fun SplashScreen(onAnimationEnd: () -> Unit) {
    // حفظ انیمیشن scale برای ورود
    val scaleAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // انیمیشن Scale با فیزیک Spring (Overshoot)
        scaleAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(1000) // نگه داشتن برای یک ثانیه
        onAnimationEnd()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "MegaWallet",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
            }
        )
    }
}
