package com.mtd.megawallet.ui.compose.screens.security

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtd.domain.security.AppLockManager

/**
 * صفحه‌کلیدِ عددی و نقطه‌های رمز — یک پیاده‌سازی برای هر جایی که رمزِ برنامه گرفته می‌شود.
 *
 * پیش از این فقط صفحهٔ بازکردنِ قفل صفحه‌کلید داشت و صفحهٔ **تنظیمِ** رمز دو `OutlinedTextField`
 * بود؛ یعنی کاربر رمزی را با صفحه‌کلیدِ سیستم می‌ساخت که بعداً باید با صفحه‌کلیدِ خودِ برنامه
 * وارد می‌کرد. حالا هر دو از این‌جا می‌آیند و نمی‌توانند از هم جدا بیفتند.
 */

/** اندازهٔ کلید. ثابتِ مشترک است چون چیدمانِ هر دو صفحه به آن گره خورده. */
internal val KEYPAD_KEY_SIZE = 88.dp

/**
 * نقطه‌های پیشرفتِ رمز.
 *
 * نقطهٔ پُرشده علاوه بر رنگ کمی بزرگ‌تر هم می‌شود؛ در نسخهٔ قبلی فقط رنگ عوض می‌شد و روی صفحهٔ
 * تیره تفاوتش به‌سختی دیده می‌شد.
 */
@Composable
internal fun PasscodeDots(
    filledCount: Int,
    modifier: Modifier = Modifier,
    total: Int = AppLockManager.PASSCODE_LENGTH,
    errorColor: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val filled = index < filledCount

            val color by animateColorAsState(
                targetValue = when {
                    filled && errorColor -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                label = "passcode_dot_color"
            )

            val size: Dp by animateDpAsState(
                targetValue = if (filled) 15.dp else 12.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
                label = "passcode_dot_size"
            )

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * صفحه‌کلیدِ سه‌ستونه با کلیدِ حذف در ردیفِ آخر.
 *
 * جایِ سمتِ دیگرِ ردیفِ آخر عمداً با یک [Spacer]ِ هم‌اندازه پُر می‌شود تا «۰» دقیقاً وسط بماند.
 */
@Composable
internal fun PasscodeKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canBackspace: Boolean = true,
    /**
     * صفحهٔ قفل تمام‌صفحه است و جا دارد؛ شیتِ تنظیمِ رمز عنوان و توضیح و کلید هم دارد و با
     * کلیدِ ۸۸ روی گوشی‌های کوتاه از صفحه بیرون می‌زد. پیش‌فرض همان مقدارِ صفحهٔ قفل است.
     */
    keySize: Dp = KEYPAD_KEY_SIZE
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KeypadRow(3, 2, 1, enabled, keySize, onDigit)
        Spacer(modifier = Modifier.height(12.dp))
        KeypadRow(6, 5, 4, enabled, keySize, onDigit)
        Spacer(modifier = Modifier.height(12.dp))
        KeypadRow(9, 8, 7, enabled, keySize, onDigit)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(keySize)
                    .clickable(
                        enabled = enabled && canBackspace,
                        indication = null,
                        interactionSource = null
                    ) { onBackspace() },
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "حذف رقم",
                        tint = if (enabled && canBackspace) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
                        }
                    )
                }
            }
            KeypadDigit(label = "0", enabled = enabled, size = keySize) { onDigit("0") }
            // جای خالیِ هم‌اندازه تا «۰» دقیقاً وسط بماند.
            Spacer(modifier = Modifier.size(keySize))
        }
    }
}

@Composable
private fun KeypadRow(
    first: Int,
    second: Int,
    third: Int,
    enabled: Boolean,
    size: Dp,
    onDigit: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeypadDigit(label = first.toString(), enabled = enabled, size = size) { onDigit(first.toString()) }
        KeypadDigit(label = second.toString(), enabled = enabled, size = size) { onDigit(second.toString()) }
        KeypadDigit(label = third.toString(), enabled = enabled, size = size) { onDigit(third.toString()) }
    }
}

@Composable
private fun KeypadDigit(
    label: String,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(size)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                indication = null,
                interactionSource = null
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
