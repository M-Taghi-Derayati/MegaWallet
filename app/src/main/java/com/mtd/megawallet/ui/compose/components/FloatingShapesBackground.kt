package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import com.mtd.megawallet.ui.compose.components.floatingbackground.CryptoCoinOrbit
import com.mtd.megawallet.ui.compose.components.floatingbackground.DiamondAnimation
import com.mtd.megawallet.ui.compose.components.floatingbackground.SecurityLockAnimation
import com.mtd.megawallet.ui.compose.components.floatingbackground.SimpleLock
import com.mtd.megawallet.ui.compose.components.floatingbackground.WalletAnimation

/**
 * پس‌زمینه متحرک با المان‌های کریپتو و بلاکچین
 * شامل: Bitcoin، Ethereum، کیف پول، الماس، سکه، زنجیره بلاک، گره شبکه و...
 *
 * هر المان دارای انیمیشن مستقل حرکت، چرخش و Float است.
 * 
 * Performance improvements:
 * - استفاده از remember برای caching painters و colors
 * - جدا کردن animations به components کوچکتر
 * - استفاده از onSizeChanged برای محاسبه positions
 */
@Composable
fun FloatingShapesBackground(modifier: Modifier = Modifier) {
    // استفاده از رنگ‌های تم برای سازگاری با Dark/Light Mode
    // MaterialTheme.colorScheme is automatically optimized by Compose
    val centerColor = MaterialTheme.colorScheme.primary
    
    // کنترل lifecycle: انیمیشن‌های بی‌نهایت به طور خودکار وقتی component dispose می‌شود متوقف می‌شوند
    // DisposableEffect برای documentation و cleanup در صورت نیاز به منابع اضافی
    DisposableEffect(Unit) {
        // انیمیشن‌ها شروع می‌شوند
        onDispose {
            // وقتی component dispose می‌شود، انیمیشن‌های بی‌نهایت به طور خودکار متوقف می‌شوند
            // این باعث کاهش مصرف باتری و CPU می‌شود
        }
    }
    
    val infinite = rememberInfiniteTransition(label = "orbit_coins")
    val rotateFast by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotateFast"
    )
    val rotateMedium by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotateMedium"
    )
    val rotateSlow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotateSlow"
    )
    
    // انیمیشن چرخش الماس
    val diamondRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diamondRotation"
    )
    
    // انیمیشن Ripple برای قفل امنیتی
    val rippleRadius by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleRadius"
    )
    
    // انیمیشن چرخش قفل
    val lockRotation by infinite.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockRotation"
    )

    // Calculate positions using remember for performance
    var sizeState by remember { mutableStateOf(Size.Zero) }
    
    val center = remember(sizeState) {
        if (sizeState != Size.Zero) {
            Offset(sizeState.width / 2f, sizeState.height / 2f)
        } else {
            Offset.Zero
        }
    }
    val minDim = remember(sizeState) {
        if (sizeState != Size.Zero) {
            sizeState.minDimension
        } else {
            0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { sizeState = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        // Crypto coins in orbit
        if (sizeState != Size.Zero && minDim > 0f) {
            CryptoCoinOrbit(
                rotateFast = rotateFast,
                rotateMedium = rotateMedium,
                rotateSlow = rotateSlow,
                center = center,
                minDim = minDim,
                modifier = Modifier.fillMaxSize()
            )

            // Diamond animation (top right)
            DiamondAnimation(
                position = Offset(sizeState.width * 0.8f, sizeState.height * 0.2f),
                size = minDim * 0.055f,
                rotation = diamondRotation,
                modifier = Modifier.fillMaxSize()
            )

            // Security lock with ripple (bottom left)
            SecurityLockAnimation(
                position = Offset(sizeState.width * 0.25f, sizeState.height * 0.75f),
                size = minDim * 0.08f,
                rippleRadius = rippleRadius,
                rotation = lockRotation,
                modifier = Modifier.fillMaxSize()
            )

            // Simple lock (top center)
            SimpleLock(
                position = Offset(sizeState.width * 0.4f, sizeState.height * 0.15f),
                color = centerColor,
                size = minDim * 0.1f,
                modifier = Modifier.fillMaxSize()
            )

            // Wallet (bottom right)
            WalletAnimation(
                position = Offset(sizeState.width * 0.85f, sizeState.height * 0.75f),
                size = minDim * 0.055f,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
