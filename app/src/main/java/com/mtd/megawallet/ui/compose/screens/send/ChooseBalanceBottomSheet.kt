package com.mtd.megawallet.ui.compose.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.domain.model.AssetItem
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import java.math.BigDecimal
import com.mtd.common_ui.theme.InterRegular
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme

/**
 * شیت پایینی انتخاب موجودی برای دارایی‌های گروهیِ چند-شبکه‌ای.
 * با `asset != null` نمایان می‌شود و لیست زیرمجموعه‌ها را ارائه می‌دهد.
 */
@Composable
fun ChooseBalanceBottomSheet(
    asset: AssetItem?,
    onDismiss: () -> Unit,
    onNetworkSelected: (AssetItem) -> Unit
) {
    val visible = asset != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9999f)
    ) {
        // Scrim background
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        // Bottom Sheet Content
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300)
                    ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val safeAsset = asset ?: return@AnimatedVisibility
            val listToDisplay = remember(safeAsset) {
                val withBalance = safeAsset.groupAssets.filter { it.balanceRaw > BigDecimal.ZERO }
                withBalance.ifEmpty { safeAsset.groupAssets.ifEmpty { listOf(safeAsset) } }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED))
                    .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                    .clickable(enabled = false) {} // برای جلوگیری از کلیک روی لایه پشت
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "انتخاب موجودی",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = IranSansBold,
                        fontSize = 20.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Asset List
                listToDisplay.forEachIndexed { index, subAsset ->
                    ChooseBalanceRow(
                        asset = subAsset,
                        onClick = { onNetworkSelected(subAsset) }
                    )
                    if (index < listToDisplay.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseBalanceRow(
    asset: AssetItem,
    onClick: () -> Unit
) {
    val networkIcon = getNetworkIconResId(asset.networkId)
    val tokenIcon = getLocalIconResId(asset.symbol)

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Network Icon + Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = networkIcon),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = asset.networkFaName ?: asset.networkName.removePrefix("on ").replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansRegular,
                    fontSize = 17.sp
                )
            }

            // Right: Token Icon (diamond/symbol) + Balance
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = asset.balance,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = InterRegular,
                    fontSize = 17.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ============================================
// Previews
// ============================================

@Preview(name = "ChooseBalanceRow - Light")
@Composable
private fun ChooseBalanceRowLightPreview() {
    MegaWalletTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                ChooseBalanceRow(asset = sampleConfirmAsset, onClick = {})
            }
        }
    }
}

@Preview(name = "ChooseBalanceRow - Dark")
@Composable
private fun ChooseBalanceRowDarkPreview() {
    MegaWalletTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                ChooseBalanceRow(asset = sampleConfirmAsset, onClick = {})
            }
        }
    }
}
