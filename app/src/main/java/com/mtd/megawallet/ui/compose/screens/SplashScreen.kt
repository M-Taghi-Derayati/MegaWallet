package com.mtd.megawallet.ui.compose.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
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
        delay(2900) // نگه داشتن برای یک ثانیه
        onAnimationEnd()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LottieRefinedLogo()
    }
}


@Composable
fun LottieRefinedLogo() {
    val durationMillis = 2900
    val animatableFrame = remember { Animatable(0f) }
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        animatableFrame.animateTo(
            targetValue = 290f,
            animationSpec = tween(durationMillis, easing = LinearEasing)
        )
    }

    val currentFrame = animatableFrame.value

    val pathsData = remember {
        mapOf(
            "right" to "M453.023 213.922V456.537C462.023 456.537 479.523 457.356 490.523 455.889C501.523 454.422 518.023 448.922 526.523 443.922C530.257 441.726 536.113 438.546 542.523 432.922C550.707 425.743 559.794 415.361 566.523 406.389C578.239 390.768 581.523 376.889 582.023 369.389C582.523 361.889 583.751 345.889 582.023 336.389C581.523 333.634 576.523 317.889 575.023 314.389C573.523 310.889 558.523 285.389 558.523 285.389L520.023 217.422C513.523 204.922 498.363 179.902 494.523 175.423C488.523 168.423 473.523 155.423 466.523 151.423C459.523 147.423 446.523 141.889 434.523 139.889C422.523 137.889 391.523 139.889 377.023 139.889L390.523 142.922C398.357 144.589 410.152 149.6 424.023 160.389C433.023 167.389 443.023 183.289 446.023 189.889C448.523 195.389 452.523 204.922 453.023 213.922Z",
            "left" to "M247.023 456.922C247.023 377.06 247.523 251.922 247.523 251.922C247.523 251.922 242.757 248.112 239.523 245.922C235.392 243.124 233.108 241.391 228.523 239.422C224.949 237.887 222.83 237.228 219.023 236.422C213.669 235.289 210.513 235.338 205.047 235.422L205.023 235.422C200.516 235.492 197.949 235.562 193.523 236.422C188.068 237.483 185.018 238.485 180.023 240.922C174.962 243.392 172.348 245.314 168.023 248.922C163.464 252.727 161.378 255.404 157.523 259.922C151.023 267.542 142.56 283.633 134.023 299.422C129.122 308.487 124.774 315.422 122.023 322.922C119.447 329.949 118.148 334.023 117.023 341.422C115.527 351.268 115.797 357.04 117.023 366.922C117.759 372.851 118.294 376.204 120.023 381.922C121.797 387.788 123.443 390.864 126.023 396.422C131.734 408.725 136.771 414.66 143.523 426.422C149.444 436.736 152.655 442.58 158.523 452.922C169.615 472.47 175.687 483.515 187.023 502.922C191.483 510.556 193.836 514.926 198.523 522.422C202.486 528.759 204.072 532.824 209.023 538.422C213.794 543.817 222.5 551 222.5 551C222.5 551 231.315 557.708 237.523 560.922C243.227 563.876 246.857 564.627 253.023 566.422L253.198 566.473C259.988 568.45 262.969 569.318 270.023 570.422C278.023 571.675 298.454 570.792 316.023 570.422C337.13 569.979 370.523 568.922C370.523 568.922C370.523 568.922 362.301 568.737 354.523 567.922C344.466 566.869 335.362 566.378 325.523 563.422C316.732 560.782 311.929 558.588 304.023 553.922C295.277 548.761 290.541 545.25 283.023 538.422C276.584 532.573 273.082 529 268.023 521.922C263.048 514.959 261.029 510.641 257.523 502.922C254.254 495.723 252.531 491.571 250.523 483.922C248.812 477.402 247.968 473.649 247.523 466.922C247.253 462.831 247.023 461.023 247.023 456.922Z",
            "top" to "M453.023 252.922C449.523 256.922 350.523 319 350.523 319C350.523 319 272 269 263 262C254 255 249.5 252 243.5 247.5C237.5 243 233.677 241.119 228.023 238.922C222.308 236.702 219.161 236.005 213.023 235.422C207.023 234.852 201.207 235.002 195.023 235.922C190.523 236.592 185.853 238.211 181.023 240.422C175.508 242.948 172.908 244.887 168.523 248.422C163.365 252.583 157.023 260.422 157.023 260.422C157.023 260.422 180.999 214.59 199.023 187.922C203.028 181.998 204.364 179.056 208.523 173.922C212.609 168.879 215.21 166.277 220.023 161.922C224.456 157.913 227.434 155.556 232.523 152.422C239.533 148.107 243.845 146.844 251.523 144.422C259.704 141.842 265.523 140.922 271.523 140.922C325.523 140.922 377.523 139.922 377.523 139.922C377.523 139.922 388.719 141.565 395.523 143.922C398.916 145.097 400.775 145.894 404.023 147.422C409.962 150.218 413.212 152.066 418.523 155.922C423.117 159.257 425.58 161.34 429.523 165.422C434.95 171.04 437.491 174.734 441.523 181.422C444.68 186.657 446.34 189.713 448.523 195.422C451.044 202.013 453.023 209.922 453.023 212.922C453.023 215.922 453.771 252.068 453.023 252.922Z",
            "bottom" to "M248.332 456C251.832 452 350.832 386.5 350.832 386.5C350.832 386.5 428.168 439 439.5 446C450.832 453 455.224 456 468.832 456C479.509 456 489.457 455.568 500 454C504.5 453.331 510.671 450.712 515.5 448.5C521.015 445.975 541.017 432.983 545 429C550 424 572 398 572 398C572 398 520.356 494.333 502.332 521C498.328 526.924 496.991 529.866 492.832 535C488.746 540.043 486.146 542.646 481.332 547C476.899 551.01 473.922 553.366 468.832 556.5C461.823 560.815 457.51 562.079 449.832 564.5C441.651 567.08 435.832 568 429.832 568C375.832 568 323.832 569 323.832 569C323.832 569 312.636 567.357 305.832 565C302.44 563.825 300.58 563.029 297.332 561.5C291.393 558.705 288.144 556.856 282.832 553C278.239 549.666 275.776 547.583 271.832 543.5C266.406 537.882 263.865 534.189 259.832 527.5C256.676 522.265 255.016 519.209 252.832 513.5C250.312 506.909 248.332 499 248.332 496C248.332 493 247.585 456.854 248.332 456Z"
        )
    }

    val composePaths = remember {
        pathsData.mapValues { PathParser().parsePathString(it.value).toPath() }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(300.dp)) {
            val scaleX = size.width / 697f
            val scaleY = size.height / 708f
            val matrix = android.graphics.Matrix().apply { setScale(scaleX, scaleY) }

            // تعریف رنگ‌ها برای تم لایت (آبی برای عمودی‌ها، بنفش برای افقی‌ها/مورب‌ها)
            val lightBlue = Color(0xFF007AFF)
            val lightPurple = Color(0xFF8B5CF6)

            val paintBrushes = if (isDark) {
                // تم دارک (سفید شفاف)
                mapOf(
                    "right" to Brush.linearGradient(0f to Color.White, 1f to Color.Transparent, start = Offset(size.width * 0.7f, size.height * 0.2f), end = Offset(size.width * 0.7f, size.height * 0.65f)),
                    "left" to Brush.linearGradient(0f to Color.Transparent, 1f to Color.White, start = Offset(size.width * 0.35f, size.height * 0.33f), end = Offset(size.width * 0.35f, size.height * 0.8f)),
                    "top" to Brush.linearGradient(0f to Color.White, 1f to Color.Transparent, start = Offset(size.width * 0.3f, size.height * 0.25f), end = Offset(size.width * 0.65f, size.height * 0.25f)),
                    "bottom" to Brush.linearGradient(0f to Color.White, 1f to Color.Transparent, start = Offset(size.width * 0.7f, size.height * 0.75f), end = Offset(size.width * 0.37f, size.height * 0.75f))
                )
            } else {
                // تم لایت (استفاده از منطق دارک اما با رنگ‌های انتخابی شما)
                mapOf(
                    // راست و چپ بنفش
                    "right" to Brush.linearGradient(0f to lightBlue, 1f to Color.Transparent, start = Offset(size.width * 0.7f, size.height * 0.2f), end = Offset(size.width * 0.7f, size.height * 0.65f)),
                    "left" to Brush.linearGradient(0f to Color.Transparent, 1f to lightPurple, start = Offset(size.width * 0.35f, size.height * 0.33f), end = Offset(size.width * 0.35f, size.height * 0.8f)),
                    // بالا و پایین آبی
                    "top" to Brush.linearGradient(0f to lightBlue, 1f to Color.Transparent, start = Offset(size.width * 0.3f, size.height * 0.25f), end = Offset(size.width * 0.65f, size.height * 0.25f)),
                    "bottom" to Brush.linearGradient(0f to lightBlue, 1f to Color.Transparent, start = Offset(size.width * 0.7f, size.height * 0.75f), end = Offset(size.width * 0.37f, size.height * 0.75f))
                )
            }

            composePaths.forEach { (name, path) ->
                val transformedPath = Path().apply {
                    addPath(path)
                    asAndroidPath().transform(matrix)
                }

                val currentBrush = paintBrushes[name] ?: SolidColor(Color.White)

                // انیمیشن رسم
                val trimProgress = when(name) {
                    "top" -> (currentFrame / 150f).coerceIn(0f, 1f)
                    "bottom" -> (currentFrame / 220f).coerceIn(0f, 1f)
                    "left" -> (currentFrame / 200f).coerceIn(0f, 1f)
                    "right" -> ((currentFrame - 80) / 40f).coerceIn(0f, 1f)
                    else -> 1f
                }

                val opacity = when(name) {
                    "top" -> if (currentFrame < 100) 0f else ((currentFrame - 100) / 100f).coerceIn(0f, 1f)
                    "bottom" -> if (currentFrame < 130) 0f else ((currentFrame - 130) / 100f).coerceIn(0f, 1f)
                    "left" -> if (currentFrame < 140) 0f else ((currentFrame - 140) / 100f).coerceIn(0f, 1f)
                    "right" -> if (currentFrame < 120) 0f else ((currentFrame - 120) / 100f).coerceIn(0f, 1f)
                    else -> 1f
                }

                // رسم خطوط در حین انیمیشن (Stroke)
                if (trimProgress > 0f && opacity < 1f) {
                    val pathMeasure = android.graphics.PathMeasure(transformedPath.asAndroidPath(), false)
                    val segmentPath = android.graphics.Path()
                    pathMeasure.getSegment(0f, pathMeasure.length * trimProgress, segmentPath, true)

                    drawPath(
                        path = segmentPath.asComposePath(),
                        brush = currentBrush,
                        alpha = (1f - opacity) * 0.4f,
                        style = Stroke(width = 2.5f)
                    )
                }

                // رسم اصلی لوگو (Fill)
                if (opacity > 0f) {
                    drawPath(
                        path = transformedPath,
                        brush = currentBrush,
                        alpha = opacity
                    )
                }
            }
        }
    }
}
