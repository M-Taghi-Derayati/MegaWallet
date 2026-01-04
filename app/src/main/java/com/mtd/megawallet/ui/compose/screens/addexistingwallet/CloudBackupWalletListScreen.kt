package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.mtd.common_ui.R
import com.mtd.megawallet.event.CloudWalletItem
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.TopHeader


@Composable
fun CloudBackupWalletListScreen(
    wallets: SnapshotStateList<CloudWalletItem>,
    onBack: () -> Unit,
    onImportSelected: (List<String>) -> Unit,
    isCalculatingBalances: Boolean = false
) {
    var selectedWallets by remember { mutableStateOf(setOf<String>()) }
    val hasSelection = selectedWallets.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // هدر با استایل جدید
            TopHeader(
                title = "بازیابی کیف پول‌ها",
                subtitle = "کیف پول‌هایی که می‌خواهید به این دستگاه منتقل شوند را انتخاب کنید."
            )

            Spacer(modifier = Modifier.height(32.dp))

            // لیست کیف پول‌ها با انیمیشن ورود
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = wallets,
                        key = { wallet -> wallet.id }
                    ) { wallet ->
                        WalletListItem(
                            wallet = wallet,
                            isSelected = selectedWallets.contains(wallet.id),
                            onToggleSelection = {
                                selectedWallets = if (selectedWallets.contains(wallet.id)) {
                                    selectedWallets - wallet.id
                                } else {
                                    selectedWallets + wallet.id
                                }
                            },
                            isBalanceLoading = isCalculatingBalances && wallet.balanceUsdt.isEmpty()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // دکمه عملیاتی
            PrimaryButton(
                text = if (hasSelection) {
                    "بازیابی ${selectedWallets.size} کیف پول"
                } else {
                    "یک یا چند مورد را انتخاب کنید"
                },
                onClick = { onImportSelected(selectedWallets.toList()) },
                enabled = hasSelection
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WalletListItem(
    wallet: CloudWalletItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    isBalanceLoading: Boolean = false
) {
    val walletColor = try {
        Color(wallet.colorHex.toColorInt())
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) walletColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "BgColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) walletColor.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(300),
        label = "BorderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onToggleSelection),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ۱. چک‌باکس با انیمیشن
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) walletColor else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) walletColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // ۲. اطلاعات کیف پول
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = wallet.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily(Font(R.font.vazirmatn_bold, FontWeight.ExtraBold)),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // ایندیکیتور رنگی کوچک
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(walletColor)
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBalanceLoading || wallet.balanceUsdt.isEmpty()) {
                        // Progress indicator کوچک به جای "0.00 USDT"
                       CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = walletColor.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = "${wallet.balanceUsdt} تتر ",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = FontFamily(Font(R.font.vazirmatn_bold, FontWeight.Bold)),
                            color = walletColor,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}
