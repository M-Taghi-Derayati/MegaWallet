package com.mtd.megawallet.ui.compose.screens.explore

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.InterBold
import com.mtd.common_ui.theme.IranSansRegular

@Composable
fun ExploreScreen() {
    val durationMillis = 4000

    val infiniteTransition = rememberInfiniteTransition(label = "MagneticSpringTransition")

    // ۱. شبیه‌سازی دقیق جابجایی افقی فیزیک فنر
    val translateX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = durationMillis
                0f at 0 using CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
                -66f at 1200 using CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
                -58f at 1520 using CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
                -62f at 1800 using CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
                -60f at 2000 using CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
                0f at 2800 using CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
                0f at 4000
            }
        ), label = "TranslationX"
    )

    // ۲. شبیه‌سازی تغییر اندازه کشسانی کارت در لحظه برخورد
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = durationMillis
                1f at 0 with FastOutSlowInEasing
                0.95f at 1200 with FastOutSlowInEasing
                1.05f at 1520 with FastOutSlowInEasing
                0.98f at 1800 with FastOutSlowInEasing
                1f at 2000 with FastOutSlowInEasing
                1f at 2800
                1f at 4000
            }
        ), label = "CardScale"
    )

    // ۳. تغییر آلفای بوردر کارت ثابت هنگام چفت شدن
    val walletBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                this.durationMillis = durationMillis
                0.08f at 0
                0.08f at 1120
                0.30f at 1200
                0.15f at 2000
                0.08f at 2800
                0.08f at 4000
            }
        ), label = "WalletBorderAlpha"
    )

    // کانتینر اصلی صفحه (پشتیبانی کامپلت از دگرگونی تم دارک/لایت)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // DarkBackground یا LightBackground
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // ایزوله کردن هندسه انیمیشن از جهت سیستم‌عامل
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {

            Box(
                modifier = Modifier
                    .width(288.dp)
                    .height(176.dp)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {

                // خط راهنمای اتصال مویی (لاین میانی منعطف با رنگ متن پس‌زمینه)
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(1.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0f)
                                )
                            )
                        )
                )

                // گره سمت چپ: Wallet (کارت ثابت - هماهنگ با لایه‌های سطحی سیستم)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 48.dp)
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface, // DarkSurface یا LightSurface
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = walletBorderAlpha),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // دات مرکزی ولت
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(50)
                            )
                    )
                }

                // گره سمت راست: dApp (کارت متحرک - های‌کنتراست در هر دو حالت)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 48.dp)
                        .size(56.dp)
                        .graphicsLayer {
                            translationX = translateX.dp.toPx()
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(
                            color = MaterialTheme.colorScheme.onBackground, // دارک: سفید | لایت: مشکی خالص
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // دات مرکزی dApp
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.background, // دارک: مشکی | لایت: سفید خالص
                                shape = RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
        // بخش متن و تایپوگرافی کامپکت
        Text(
            text = "dApp Browser",
            color = MaterialTheme.colorScheme.tertiary,
            fontSize = 15.sp,
            fontFamily = InterBold,
            letterSpacing = (-0.02).sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "این بخش در دست توسعه است",
            color = MaterialTheme.colorScheme.onTertiary,
            fontSize = 12.sp,
            fontFamily = IranSansRegular,
            textAlign = TextAlign.Center
        )
    }
}