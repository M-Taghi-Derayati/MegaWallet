package com.mtd.megawallet.ui.compose.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtd.megawallet.ui.compose.components.FloatingShapesBackground
import com.mtd.common_ui.R as commonui

/**
 * صفحه خوش‌آمدگویی (Onboarding Screen) که اولین صفحه بعد از Splash است.
 * شامل عنوان، توضیحات و دو دکمه اصلی:
 * 1. "ساخت کیف پول جدید" (دکمه اصلی سبز)
 * 2. "من کیف پول دارم" (دکمه ثانویه خاکستری)
 *
 * طبق Technical Spec: 
 * - شامل پس‌زمینه Animated Blobs با Blur Effect (API 31+)
 * - هنگام باز شدن Modal، صفحه با Parallax Effect (Scale + Dim) کوچک و مات می‌شود
 *
 * @param viewModel ViewModel برای مدیریت وضعیت Modal
 * @param onConnectWallet اکشن برای رفتن به صفحه "افزودن کیف پول موجود"
 * @param onCreateWallet اکشن برای رفتن به صفحه "ساخت کیف پول جدید"
 */
@Composable
fun OnboardingScreen(
    onConnectWallet: () -> Unit,
    onCreateWallet: () -> Unit
) {

    
    // انیمیشن Scale (کوچک شدن به 0.95)
    val scale by animateFloatAsState(
        targetValue =  1f,
        animationSpec = tween(durationMillis = 400),
        label = "onboarding_scale"
    )
    
    // انیمیشن Alpha (مات شدن به 0.6)
    val alpha by animateFloatAsState(
        targetValue =  1f,
        animationSpec = tween(durationMillis = 400),
        label = "onboarding_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer{
                scaleX=scale
                scaleY=scale
                this.alpha=alpha
            }
    ) {
        // پس‌زمینه متحرک با اشکال مختلف (لایه پشتی)
        FloatingShapesBackground()
        
        // محتوای اصلی (لایه جلویی)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // قسمت هدر / لوگو در مرکز بالا
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "رمز ارز ها\n تحت کنترل تو",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = FontFamily(Font(commonui.font.iransansmobile_fa_regular, FontWeight.Medium))
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "یک کیف پول جدید بسازید یا کیف پول موجود خود را اضافه کنید",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily(Font(commonui.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
            }

            // دکمه‌ها
            Button(
                onClick = onCreateWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape =CircleShape
            ) {
                Text(
                    "ساخت کیف پول جدید",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,//TODO اینجا مشکل هاور داره روی متن دکمه
                    fontFamily = FontFamily(Font(commonui.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onConnectWallet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape =CircleShape
            ) {
                Text(
                    "من کیف پول دارم",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily(Font(commonui.font.iransansmobile_fa_regular, FontWeight.Medium))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
