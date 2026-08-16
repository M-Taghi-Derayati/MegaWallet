package com.mtd.megawallet.ui.compose.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.security.SecuritySnapshot
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.PrimaryButton

/**
 * امنیتِ برنامه — قفل، رمز عبور، اثر انگشت و زمانِ قفلِ خودکار.
 *
 * روی [AnimatedBottomSheetCard] می‌نشیند، همان شیتی که بقیهٔ برنامه استفاده می‌کند: نسخهٔ قبلی
 * یک `Box` + `Surface`ِ دست‌ساز بود که نه انیمیشن داشت، نه دکمهٔ برگشتِ دستگاه را می‌گرفت.
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "قفل برنامه با رمز عبور و اثر انگشت",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            val enabled = snapshot?.appLockEnabled == true
            SecurityToggleRow(
                title = "قفل برنامه",
                subtitle = if (enabled) "فعال" else "غیرفعال",
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) onEnableAppLock() else onDisableAppLock()
                }
            )

            // بقیهٔ گزینه‌ها فقط وقتی معنی دارند که قفل روشن باشد؛ نشان‌دادنشان به‌صورتِ غیرفعال
            // فقط شیت را بلندتر می‌کرد.
            if (enabled && snapshot != null) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "تغییر رمز عبور",
                    onClick = onChangePasscode
                )

                Spacer(Modifier.height(16.dp))
                SecurityToggleRow(
                    title = "ورود با اثر انگشت",
                    subtitle = if (biometricAvailable) {
                        "در دستگاه پشتیبانی می‌شود"
                    } else {
                        "این دستگاه پشتیبانی نمی‌کند"
                    },
                    checked = snapshot.biometricEnabled,
                    enabled = biometricAvailable,
                    onCheckedChange = onBiometricToggle
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "زمان قفل خودکار",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = IranSansBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeoutChip(
                        label = "فوری",
                        selected = snapshot.lockTimeoutSeconds == 0,
                        onClick = { onTimeoutSelect(0) },
                        modifier = Modifier.weight(1f)
                    )
                    TimeoutChip(
                        label = "۳۰ ثانیه",
                        selected = snapshot.lockTimeoutSeconds == 30,
                        onClick = { onTimeoutSelect(30) },
                        modifier = Modifier.weight(1f)
                    )
                    TimeoutChip(
                        label = "۶۰ ثانیه",
                        selected = snapshot.lockTimeoutSeconds == 60,
                        onClick = { onTimeoutSelect(60) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "برای امنیت بیشتر، رمز عبور را با کسی به اشتراک نگذارید.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** عنوان + یک خط وضعیت در ابتدا، کلید در انتها. */
@Composable
private fun SecurityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun TimeoutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontFamily = IranSansRegular,
                fontSize = 13.sp
            )
        }
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
