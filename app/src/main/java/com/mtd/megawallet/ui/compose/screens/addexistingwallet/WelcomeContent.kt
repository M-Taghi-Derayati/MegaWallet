package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtd.common_ui.R
import com.mtd.megawallet.event.DriveBackupState
import com.mtd.megawallet.ui.compose.components.WalletOptionItem
import com.mtd.megawallet.ui.compose.theme.Green

/**
 * Welcome content for adding existing wallet screen.
 * Shows options for importing wallet.
 */
@Composable
fun WelcomeContent(
    onGetStarted: () -> Unit,
    driveBackupState: DriveBackupState,
    onCloudBackupClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    // تعیین آیکون، رنگ و متن بر اساس state
    val (cloudIcon, cloudColor, cloudTitle) = when (driveBackupState) {
        is DriveBackupState.Checking -> Triple(
            null,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "در حال بررسی"
        )
        is DriveBackupState.BackupFound -> Triple(
            Icons.Default.CloudQueue,
            Color(0xFFFFA726),
            "فایل پشتیبان یافت شد"
        )
        is DriveBackupState.NoBackup -> Triple(
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "فایل پشتیبان یافت نشد"
        )
        is DriveBackupState.NotConnected -> Triple(
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "اتصال برقرار نیست"
        )
        is DriveBackupState.Error -> Triple(
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.error,
            driveBackupState.message
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "افزودن کیف پول موجود",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontFamily = FontFamily(Font(R.font.vazirmatn_bold, FontWeight.Bold))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "به استفاده از کیف پولی که از قبل دارید ادامه دهید یا هر آدرسی را ردیابی کنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light))
        )
        Spacer(modifier = Modifier.height(32.dp))
        Column {
            WalletOptionItem(
                icon = Icons.Default.Person,
                iconColor = Color(0xFF42A5F5),
                title = "ورود به حساب کاربری",
                subtitle = "افزودن کیف پول با ورود به حساب خانوادگی موجود",
                onClick = {},
                enabled = false
            )
            Spacer(modifier = Modifier.height(16.dp))
            WalletOptionItem(
                icon = Icons.Default.ArrowDownward,
                iconColor = Green,
                title = "وارد کردن",
                subtitle = "افزودن کیف پول موجود با عبارت بازیابی یا کلید خصوصی",
                onClick = onGetStarted
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Backup با آیکون و رنگ داینامیک
            if (driveBackupState is DriveBackupState.Checking) {
                WalletOptionItemWithLoading(
                    title = cloudTitle,
                    subtitle = "بازیابی کیف پول موجود از پشتیبان گوگل درایو",
                    onClick = onCloudBackupClicked,
                    enabled = false
                )
            } else {
                WalletOptionItem(
                    icon = cloudIcon!!,
                    iconColor = cloudColor,
                    title = cloudTitle,
                    subtitle = "بازیابی کیف پول موجود از پشتیبان گوگل درایو",
                    onClick = onCloudBackupClicked,
                    enabled = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            WalletOptionItem(
                icon = Icons.Default.Visibility,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "مشاهده",
                subtitle = "پیگیری کیف پول با آدرس یا نام ENS",
                onClick = {},
                enabled = false
            )
        }
    }
}

