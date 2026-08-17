package com.mtd.megawallet.ui.compose.screens.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.security.SecuritySnapshot
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.screens.settings.PickerOptionRow
import com.mtd.megawallet.ui.compose.screens.settings.PickerSheetBody
import com.mtd.megawallet.ui.compose.screens.settings.SwitchCaption
import com.mtd.megawallet.ui.compose.screens.settings.SwitchRow

/**
 * گزینه‌های زمانِ قفلِ خودکار.
 *
 * ثانیه‌ها این‌جا تعریف می‌شوند و نه در دلِ چیدمان، چون هم برچسب و هم توضیح و هم مقایسه با
 * `snapshot.lockTimeoutSeconds` باید از یک جا بیایند؛ قبلاً هر سه در سه جای جدا نوشته شده بودند.
 */
private val LOCK_TIMEOUTS: List<Triple<Int, String, String>> = listOf(
    Triple(0, "فوری", "به‌محضِ بیرون‌رفتن از برنامه قفل می‌شود."),
    Triple(30, "۳۰ ثانیه", "تا نیم دقیقه بعد از بیرون‌رفتن، بازگشت بدونِ رمز است."),
    Triple(60, "۶۰ ثانیه", "تا یک دقیقه فرصت دارید بدونِ رمز برگردید.")
)

/**
 * همان فنرِ همیشگیِ برنامه؛ باز و بسته‌شدنِ این بخش باید مثلِ بقیهٔ حرکت‌ها حس شود.
 *
 * `IntSize` است و نه `Int`: `expandVertically`/`shrinkVertically` روی اندازه کار می‌کنند.
 * `visibilityThreshold` هم لازم است، وگرنه فنر تا کسرهای زیرِ یک پیکسل ادامه می‌دهد.
 */
private val PremiumSpringSize = spring(
    dampingRatio = 0.82f,
    stiffness = 380f,
    visibilityThreshold = IntSize.VisibilityThreshold
)
private val PremiumSpringFloat = spring<Float>(dampingRatio = 0.82f, stiffness = 380f)

/**
 * امنیتِ برنامه — قفل، رمز عبور، اثر انگشت و زمانِ قفلِ خودکار.
 *
 * ### چرا بازنویسی شد
 * این شیت هر چهار عنصرش را جدا از بقیهٔ تنظیمات ساخته بود: کلیدها `Row`ِ لخت بودند و نه قابِ
 * خاکستریِ [SwitchRow]، انتخابِ زمان یک ردیفِ سه‌تایی «چیپ» بود که هیچ‌جای دیگرِ برنامه وجود
 * ندارد، «تغییر رمز عبور» یک `PrimaryButton`ِ تمام‌عرض وسطِ فهرست بود (در حالی که آن دکمه در کلِ
 * برنامه یعنی «کنشِ اصلیِ این صفحه»)، و باز و بسته‌شدنِ گزینه‌ها هیچ انیمیشنی نداشت.
 *
 * حالا از همان واژگانی استفاده می‌کند که انتخابگرهای پوسته و اعلان و واحدِ پول استفاده می‌کنند.
 */
@Composable
fun SecuritySettingsSheet(
    visible: Boolean,
    snapshot: SecuritySnapshot?,
    biometricAvailable: Boolean,
    onClose: () -> Unit,
    onEnableAppLock: () -> Unit,
    onDisableAppLock: () -> Unit,
    onChangePasscode: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onTimeoutSelect: (Int) -> Unit
) {
    AnimatedBottomSheetCard(
        visible = visible,
        title = "امنیت برنامه",
        onDismiss = onClose
    ) {
        PickerSheetBody {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )

            Spacer(Modifier.height(16.dp))

            val enabled = snapshot?.appLockEnabled == true
            SwitchRow(
                label = "قفل برنامه",
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) onEnableAppLock() else onDisableAppLock()
                }
            )

            Spacer(Modifier.height(8.dp))
            SwitchCaption(
                "با روشن‌کردن، هر بار که برنامه را باز می‌کنید رمز عبور خواسته می‌شود. کلیدهای خصوصی همیشه روی همین دستگاه می‌مانند؛ این قفل جلوی دسترسیِ کسی را می‌گیرد که گوشی دستش باشد."
            )

            // بقیهٔ گزینه‌ها فقط وقتی معنی دارند که قفل روشن باشد. قبلاً با یک `if` ناگهان
            // ظاهر و ناپدید می‌شدند و ارتفاعِ شیت می‌پرید.
            AnimatedVisibility(
                visible = enabled && snapshot != null,
                enter = expandVertically(animationSpec = PremiumSpringSize) +
                    fadeIn(animationSpec = PremiumSpringFloat),
                exit = shrinkVertically(animationSpec = PremiumSpringSize) +
                    fadeOut(animationSpec = PremiumSpringFloat)
            ) {
                // ⚠️ `snapshot` این‌جا هنوز می‌تواند `null` باشد: در حینِ انیمیشنِ خروج، محتوا یک
                // فریمِ دیگر هم ترکیب می‌شود. `?:` جلوی کرش را می‌گیرد بدونِ اینکه چیزی بپرد.
                val current = snapshot ?: return@AnimatedVisibility

                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(16.dp))

                    SecurityActionRow(
                        label = "تغییر رمز عبور",
                        onClick = onChangePasscode
                    )

                    Spacer(Modifier.height(16.dp))

                    SwitchRow(
                        label = "ورود با اثر انگشت",
                        checked = current.biometricEnabled,
                        enabled = biometricAvailable,
                        onCheckedChange = onBiometricToggle
                    )

                    if (!biometricAvailable) {
                        Spacer(Modifier.height(8.dp))
                        SwitchCaption("این دستگاه اثر انگشت یا تشخیص چهره ندارد.")
                    }

                    Spacer(Modifier.height(22.dp))

                    Text(
                        text = "زمان قفل خودکار",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = IranSansBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(Modifier.height(4.dp))

                    // همان انتخابگری که واحدِ پول و حالتِ اتصال استفاده می‌کنند: عنوان، یک خط
                    // توضیح، و تیکِ گزینهٔ فعلی. «چیپ»های قبلی نه توضیح جا می‌دادند و نه شبیهِ
                    // هیچ انتخابِ دیگری در برنامه بودند.
                    LOCK_TIMEOUTS.forEachIndexed { index, (seconds, label, description) ->
                        PickerOptionRow(
                            title = label,
                            description = description,
                            selected = current.lockTimeoutSeconds == seconds,
                            showDivider = index < LOCK_TIMEOUTS.lastIndex,
                            onClick = { onTimeoutSelect(seconds) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SwitchCaption("رمز عبور را با کسی به اشتراک نگذارید. هیچ‌کس از تیم پشتیبانی آن را از شما نمی‌پرسد.")

            Spacer(Modifier.height(22.dp))
            PrimaryButton(text = "تمام", onClick = onClose)
        }
    }
}

/**
 * ردیفی که جای دیگری می‌برد — همان قابِ خاکستریِ [SwitchRow]، ولی با شِوران به‌جای کلید.
 *
 * `PrimaryButton` نیست چون آن دکمه در کلِ برنامه یعنی «کنشِ اصلیِ این صفحه»؛ یک دکمهٔ پُرِ
 * تمام‌عرض وسطِ فهرست، «تغییر رمز عبور» را مهم‌تر از خودِ روشن‌بودنِ قفل نشان می‌داد.
 */
@Composable
private fun SecurityActionRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview
@Composable
private fun SecuritySettingsSheetPreview() {
    MegaWalletTheme {
        // شیت خودش را به اندازهٔ کلِ صفحه می‌کشد، پس پیش‌نمایش باید ابعادِ یک صفحه به آن بدهد.
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(720.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            SecuritySettingsSheet(
                visible = true,
                snapshot = SecuritySnapshot(true, true, true, 30, true),
                biometricAvailable = true,
                onClose = {},
                onEnableAppLock = {},
                onDisableAppLock = {},
                onChangePasscode = {},
                onBiometricToggle = {},
                onTimeoutSelect = {}
            )
        }
    }
}
