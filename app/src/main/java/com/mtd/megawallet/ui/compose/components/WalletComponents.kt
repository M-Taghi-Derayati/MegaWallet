package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.theme.Green

/**
 * کارت‌های انیمیشنی که در بالای صفحه "افزودن کیف پول موجود" به صورت پشته‌ای نمایش داده می‌شوند.
 * این کارت‌ها با انیمیشن از پایین به بالا حرکت می‌کنند (Slide Up).
 *
 * @param color رنگ پس‌زمینه کارت (شامل alpha)
 * @param scale اندازه کارت (۱.۰ برای کارت جلویی، کمتر برای عقبی‌ها)
 * @param yOffset موقعیت نهایی کارت در محور Y (نسبت به مرکز)
 * @param startOffsetY موقعیت شروع کارت در محور Y (برای شروع انیمیشن از پایین)
 * @param animValue مقدار انیمیشن بین ۰ تا ۱ (۰ = شروع، ۱ = پایان)
 * @param modifier Modifier اضافی (مثلاً برای zIndex)
 */


@Composable
fun WalletStackCard(
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    height: Dp = 120.dp,
    isRevealed: Boolean = false,
    frontContent: @Composable () -> Unit,
    backContent: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
    ) {
        Box(
            modifier = modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(color)
        ) {
            Crossfade(
                targetState = isRevealed,
                animationSpec = tween(durationMillis = 400),
                label = "CardContentFade"
            ) { revealed ->
                if (revealed) {
                    backContent()
                } else {
                    frontContent()
                }
            }
        }
    }
}

@Composable
fun WalletStackCardFront() {
    // دایره تزیینی در گوشه بالا سمت چپ
    Box(modifier = Modifier.fillMaxSize()) {

        // مستطیل تزیینی کنار دایره
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 24.dp)
                .offset(x = 44.dp, y = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.25f))
        )

        // دایره تزیینی در گوشه بالا سمت چپ
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset(x = 12.dp, y = 12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f))
        )



    }
}


@Composable
fun WalletStackCardBackWordKeys(
    phrases: List<String>,
    onPasteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAnimationStable: Boolean = false
) {
    // لیستی از placeholder ها برای نمایش در حالت اولیه
    val placeholderPhrases = remember { List(12) { "••••••" } }

    
    // بهینه‌سازی: delay کردن render SeedPhraseGrid تا انیمیشن کارت ثابت شود
    // SeedPhraseGrid فقط بعد از ثبات انیمیشن کارت render می‌شود تا lag نداشته باشیم
    var shouldShowGrid by remember { mutableStateOf(false) }
    
    LaunchedEffect(isAnimationStable) {
        if (isAnimationStable) {
            // Delay کوچک اضافی برای اطمینان از ثبات کامل انیمیشن
            kotlinx.coroutines.delay(130)
            shouldShowGrid = true
        } else {
            // وقتی انیمیشن تمام نشده، grid را مخفی می‌کنیم
            shouldShowGrid = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        // --- لایه ۱: گرید کلمات ---
        if (shouldShowGrid) {

            SeedPhraseGrid(
                words = if (phrases.isEmpty()) placeholderPhrases else phrases,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            )
        }
        }

        // --- لایه ۲: حاله سبز و دکمه ---
        AnimatedVisibility(
            visible = phrases.isEmpty(),
            exit = fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // حاله سبز کم‌رنگ
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Green.copy(alpha = 0.8f))
                )

                // دکمه شما
                Button(
                    onClick = onPasteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPasteGo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "جایگذاری کلید ها",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light))
                    )
                }
            }
        }
    }



@Composable
fun WalletStackCardBackPrivateKey(
    privateKey: String,
    onPasteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PrivateKeyWallet(privateKey)

        // --- لایه ۲: حاله سبز و دکمه ---
        AnimatedVisibility(
            visible = privateKey.isEmpty(),
            exit = fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // حاله سبز کم‌رنگ
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Green.copy(alpha = 0.8f))
                )

                // دکمه شما
                Button(
                    onClick = onPasteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .height(38.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPasteGo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "جایگذاری",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light))
                    )
                }
            }
        }
    }
}


@Composable
fun PrivateKeyWallet(
    secret: String
) {
    // --- لایه ۱: نمایش کلید خصوصی ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "کلید خصوصی شما",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Light))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (secret.isNotEmpty()) secret else "••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            lineHeight = 25.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SeedPhraseGrid(
    words: List<String>,
    modifier: Modifier = Modifier,
    paddingVerticalItem: Dp=6.dp,
    paddingHorizontalItem: Dp=1.dp
) {
    // بهینه‌سازی: استفاده از remember برای cache کردن مقادیر ثابت
    val spacing = remember { 8.dp }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            // بهینه‌سازی: استفاده از contentPadding برای padding داخلی
            contentPadding = PaddingValues(0.dp),
            modifier = modifier
        ) {
            items(
                count = words.size,
                // بهینه‌سازی: استفاده از stable key (index برای لیست ثابت کافی است)
                key = { index -> index }
            ) { index ->
                SecretPhraseItem(
                    index = index,
                    word = words[index],
                    paddingVertical = paddingVerticalItem,
                    paddingHorizontal = paddingHorizontalItem
                )
            }
        }
    }
}

@Composable
fun SecretPhraseItem(
    index: Int,
    word: String,
    modifier: Modifier = Modifier,
    paddingVertical: Dp=8.dp,
    paddingHorizontal: Dp=8.dp,
    color:Color= Color.White.copy(alpha = 0.12f)
) {
    // بهینه‌سازی: استفاده از remember برای cache کردن مقادیر ثابت
    val cornerRadius = remember { 8.dp }
    val borderWidth = remember { 0.5.dp }
    val borderColor = remember { Color.White.copy(alpha = 0.2f) }
    val textColor = remember { Color.White }
    val fontSize = remember { 11.sp }
    
    // بهینه‌سازی: ساخت متن یکبار
    val displayText = remember(index, word) {
        "${index + 1}. $word"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(color)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .padding(vertical = paddingVertical, horizontal = paddingHorizontal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            fontSize = fontSize,
            overflow = TextOverflow.Ellipsis
        )
    }

}


/**
 * آیتم‌های لیست گزینه‌ها در صفحات (مثل دکمه Import، Login و ...).
 * شامل آیکون، عنوان، زیرعنوان و وضعیت غیرفعال بودن.
 *
 * @param icon آیکون وکتوری
 * @param iconColor رنگ آیکون (زمانی که فعال است)
 * @param title عنوان اصلی
 * @param subtitle توضیحات زیر عنوان
 * @param onClick اکشن کلیک
 * @param enabled وضعیت فعال/غیرفعال بودن دکمه
 */
@Composable
fun WalletOptionItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) MaterialTheme.colorScheme.surface else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (enabled) Color.Transparent else MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.3f
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {

        // آیکون (سمت چپ)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (enabled) iconColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }



        Spacer(modifier = Modifier.width(16.dp))

        // متن‌ها (سمت راست)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start // RTL: راست‌چین
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onTertiary,
                fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Bold)),
                textAlign = TextAlign.Start // RTL
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiary,
                maxLines = 2,
                lineHeight = 16.sp,
                fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Medium)),
                textAlign = TextAlign.Start // RTL
            )
        }
    }
}


@Preview
@Composable
fun PreviewWalletOption() {
    MaterialTheme {
        WalletOptionItem(
            icon = Icons.Default.TextFields,
            iconColor = Color(0xFF22C55E),
            title = "عبارت بازیابی",
            subtitle = "وارد کردن کیف پول با عبارت ۱۲/۲۴ کلمه‌ای بازیابی",
            onClick = {}
        )
    }
}
