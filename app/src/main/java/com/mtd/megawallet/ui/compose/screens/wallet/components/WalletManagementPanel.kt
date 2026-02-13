package com.mtd.megawallet.ui.compose.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.screens.createwallet.WALLET_COLORS

@Composable
fun WalletManagementMenuContent(
    isBackedUp: Boolean = false,
    onBackupClick: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        BackupStatusItem(isBackedUp = isBackedUp, onBackupClick = onBackupClick)
        Spacer(modifier = Modifier.height(24.dp))

        DetailMenuItem(icon = Icons.Default.GridView, title = "اتصالات", onClick = {})
        DetailDivider()
        DetailMenuItem(icon = Icons.Default.VerifiedUser, title = "تاییدیه ها", onClick = {})
        DetailDivider()
        DetailMenuItem(
            icon = Icons.Default.Notifications,
            title = "اعلان ها",
            rightIcon = Icons.Default.MoreHoriz,
            onClick = {}
        )
        DetailDivider()
        DetailMenuItem(icon = Icons.Default.Delete, title = "سطل زباله", onClick = onDelete)
        DetailDivider()
        DetailMenuItem(
            icon = Icons.Default.Menu,
            title = "گزینه ها",
            rightIcon = Icons.Default.MoreHoriz,
            onClick = onSettings
        )
    }
}

@Composable
fun WalletPersonalizationContent(
    selectedColor: Color,
    onColorSelect: (Color) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(WALLET_COLORS) { color ->
                ColorItem(
                    color = color,
                    isSelected = selectedColor.toArgb() == color.toArgb(),
                    onClick = { onColorSelect(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "ذخیره تغییرات",
            containerColor = selectedColor,
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun ColorItem(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun WalletRecoveryMethodsContent(
    isManualBackedUp: Boolean,
    isCloudBackedUp: Boolean,
    onMethodClick: (String) -> Unit
) {
    val completedCount = (if (isManualBackedUp) 1 else 0) + (if (isCloudBackedUp) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "روش های پشتیبان",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
            )
            Spacer(modifier = Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$completedCount از 3 ",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
                )
                Spacer(modifier = Modifier.width(8.dp))
                RecoveryProgressCircle(completedCount = completedCount)
            }
        }

        RecoveryMethodItem(
            title = "حساب کاربری",
            subtitle = "این ویژگی در به روز رسانی های آتی در دسترس خواهد بود",
            badge = "به زودی",
            icon = Icons.Default.Person,
            iconColor = Color(0xFF90A4AE),
            isCompleted = false,
            isAvailable = false,
            onClick = { onMethodClick("account") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RecoveryMethodItem(
            title = "پشتیبان‌گیری ابری",
            subtitle = "عبارت بازیابی خود را با یک رمز عبور در گوگل درایو ذخیره کنید",
            icon = Icons.Default.Cloud,
            iconColor = Color(AnimationConstants.CLOUD_BACKUP_COLOR),
            isCompleted = isCloudBackedUp,
            onClick = { onMethodClick("cloud") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        RecoveryMethodItem(
            title = "پشتیبان‌گیری دستی",
            subtitle = "عبارت بازیابی خود را در مکانی امن یادداشت و نگهداری کنید",
            badge = "پیشرفته",
            icon = Icons.Default.Edit,
            iconColor = Color.Gray,
            isCompleted = isManualBackedUp,
            onClick = { onMethodClick("manual") }
        )
    }
}

@Composable
private fun BackupStatusItem(
    isBackedUp: Boolean,
    onBackupClick: () -> Unit
) {
    if (isBackedUp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onBackupClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = " پشتیبان تهیه شده",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "کپی پشتیبان تهیه نشده",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 14.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFFEF5350))
                    .clickable { onBackupClick() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "همین حالا",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
                )
            }
        }
    }
}

@Composable
private fun DetailDivider() {
    Divider(
        modifier = Modifier.padding(start = 60.dp),
        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.05f),
        thickness = 1.dp
    )
}

@Composable
private fun DetailMenuItem(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onTertiary,
    rightIcon: ImageVector = Icons.Default.KeyboardArrowLeft,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            rightIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(if (rightIcon == Icons.Default.MoreHoriz) 20.dp else 18.dp)
        )
    }
}

@Composable
private fun RecoveryProgressCircle(completedCount: Int) {
    val sweepAngle = when (completedCount) {
        0 -> 0f
        1 -> 120f
        else -> 360f
    }

    val color = when (completedCount) {
        0 -> MaterialTheme.colorScheme.onTertiary
        1 -> Color(0xFFFFB74D)
        else -> Color(0xFF4CAF50)
    }
    val ringColor = MaterialTheme.colorScheme.surface

    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        drawCircle(
            color = ringColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

@Composable
private fun RecoveryMethodItem(
    title: String,
    subtitle: String,
    badge: String? = null,
    icon: ImageVector,
    iconColor: Color,
    isCompleted: Boolean = false,
    isAvailable: Boolean = true,
    onClick: () -> Unit = {}
) {
    val alpha = if (isAvailable) 1f else 0.5f
    val finalIconColor = if (isAvailable) iconColor else Color(0xFF424242)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isAvailable) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                }
            )
            .clickable(enabled = isAvailable) { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AnimationConstants.BACKUP_OPTION_ITEM_ICON_SIZE)
                .background(finalIconColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (isAvailable) 1f else 0.4f),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onTertiary.copy(alpha = if (isAvailable) 0.1f else 0.05f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = alpha),
                        fontSize = 10.sp,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = alpha),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isAvailable) {
            if (isCompleted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f),
                            CircleShape
                        )
                )
            }
        }
    }
}
