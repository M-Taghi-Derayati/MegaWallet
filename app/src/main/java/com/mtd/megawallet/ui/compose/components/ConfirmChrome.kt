package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular

/**
 * زبانِ بصریِ صفحهٔ تأیید — یک‌جا تعریف می‌شود و دو مصرف‌کننده دارد: ارسال و تبدیل.
 *
 * تا پیش از این هر فلو کارت و ردیفِ خودش را داشت و دو پالتِ متفاوت را صدا می‌زد
 * (`tertiary`/`onTertiary` در ارسال، `onBackground`/`onSurfaceVariant` در تبدیل)، پس دو صفحه‌ای که
 * کارِ یکسانی می‌کنند شبیهِ دو اپ به نظر می‌رسیدند. این‌جا فقط **قاب** مشترک است؛ محتوای ردیف‌ها
 * همچنان مالِ خودِ فلوست و تبدیل ردیف‌هایی دارد که ارسال ندارد.
 */

/**
 * یک ردیف «برچسب ← مقدار» در کارتِ جزئیاتِ تأیید.
 *
 * [valueLeft] برای مقداری است که فقط متن نیست — آیکونِ ارز کنارِ عدد، یا دو سطرِ فیات زیرِ هم.
 */
@Composable
fun ConfirmDetailRow(label: String, value: String? = null, valueLeft: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onTertiary,
            fontFamily = IranSansRegular,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (valueLeft != null) valueLeft()
        else if (value != null) {
            Text(text = value, color = MaterialTheme.colorScheme.tertiary, fontFamily = IranSansBold, fontSize = 15.sp)
        }
    }
}

/**
 * کارتِ جزئیاتِ تأیید: همان سطحِ گِردی که ردیف‌های [ConfirmDetailRow] داخلش می‌نشینند.
 * فاصلهٔ بینِ ردیف‌ها بخشی از خودِ کارت است تا هیچ صدازننده‌ای مجبور نباشد آن را تکرار کند.
 */
@Composable
fun ConfirmDetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * نشانگرِ عمودیِ سطحِ کارمزد — همان ستونِ نقطه‌ها که کنارِ بلوکِ کارمزد می‌نشیند.
 *
 * رنگ به **جایگاه** بسته است نه به نامِ سطح: زنجیره‌های مختلف برچسب‌های متفاوتی برمی‌گردانند و
 * ترتیب تنها چیزی است که همه‌جا یکسان می‌ماند.
 */
@Composable
fun FeeLevelIndicator(selectedIndex: Int, totalOptions: Int, isLoading: Boolean = false) {
    val dotsCount = maxOf(3, totalOptions)
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in (dotsCount - 1) downTo 0) {
                val isSelected = (i == selectedIndex) || (selectedIndex > 2 && i == 2)

                val size by animateDpAsState(if (isSelected) 18.dp else 5.dp, label = "dotSize")
                val color = when (i) {
                    2 -> Color(0xFFFF7043)
                    1 -> Color(0xFFFFCA28)
                    0 -> Color(0xFF29B6F6)
                    else -> Color.Gray
                }

                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected || isLoading,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) // just keep dot colored without icon
                        } else {
                            Icon(
                                imageVector = when (i) {
                                    2 -> Icons.Default.Bolt
                                    1 -> Icons.Default.Check
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
