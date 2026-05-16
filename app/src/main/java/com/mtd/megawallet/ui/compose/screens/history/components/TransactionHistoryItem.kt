package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.megawallet.ui.compose.theme.Green
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@Composable
fun TransactionHistoryItem(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { context.imageLoader }

    val iconUrl = remember(transaction) { getLocalIconResId(viewModel.getHistoryAssetIconUrl(transaction)?:"") }
    val localIconNetworkResId = remember(transaction) {
        getNetworkIconResId(viewModel.networkId(transaction))
    }

    val assetTitle = remember(transaction) { viewModel.getHistoryAssetTitle(transaction) }
    val primaryLabel = remember(transaction) { viewModel.getHistoryPrimaryLabel(transaction) }
    val counterpartyLabel = remember(transaction) { viewModel.getHistoryCounterpartyLabel(transaction) }
    val amountText = remember(transaction) { viewModel.formatListAmount(transaction) }
    val fiatText = remember(transaction) { viewModel.formatTransactionFiat(transaction) }

    TransactionHistoryItemContent(
        isOutgoing = transaction.isOutgoing,
        status = transaction.status,
        iconUrl = iconUrl,
        iconNetworkUrl = localIconNetworkResId,
        assetTitle = assetTitle,
        primaryLabel = primaryLabel,
        counterpartyLabel = counterpartyLabel,
        amountText = amountText,
        fiatText = fiatText,
        imageLoader = imageLoader,
        onClick = onClick
    )
}

@Composable
fun TransactionHistoryItemContent(
    isOutgoing: Boolean,
    status: TransactionStatus,
    iconUrl: Int,
    iconNetworkUrl: Int,
    assetTitle: String,
    primaryLabel: String,
    counterpartyLabel: String,
    amountText: String,
    fiatText: String?,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit
) {
    val isPending = status == TransactionStatus.PENDING
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isPending) 14.dp else 0.dp,
                vertical = if (isPending) 5.dp else 0.dp
            ),
        shape = if (isPending) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp),
        color = if (isPending) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
        shadowElevation = if (isPending) 10.dp else 0.dp,
        tonalElevation = if (isPending) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (isPending) 14.dp else 16.dp,
                    vertical = if (isPending) 14.dp else 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box(
            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)
        ) {

            AssetAvatar(
                iconUrl = iconUrl,
                fallbackLabel = assetTitle,
                imageLoader = imageLoader,
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_SIZE)
                    .align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(
                        if (isOutgoing)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.background,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (status == TransactionStatus.PENDING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        painter = if (isOutgoing)
                            painterResource(id = R.drawable.ic_send)
                        else
                            painterResource(id = R.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp).padding(2.dp),
                        tint = Color.White
                    )
                }

            }


            Box(
                modifier = Modifier
                    .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_SMALE)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pls),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = if (isSystemInDarkTheme()) Color.Black else Color.White
                )

                Image(
                    painter = painterResource(id = iconNetworkUrl),
                    contentDescription = "network icon",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING),
                    contentScale = ContentScale.Fit,
                    colorFilter = null
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Center Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = primaryLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_light)),
                    color = Color(0xFF8F8F96)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = counterpartyLabel,
                    fontSize = 13.sp,
                    fontFamily = FontFamily(Font(R.font.inter_medium)),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Text(
                text = assetTitle,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Bold)),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Content (Amount)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.inter_medium)),
                color = if (isOutgoing) Color(0xFF8F8F96) else Green,
                textAlign = TextAlign.End
            )
            fiatText?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    fontFamily = FontFamily(Font(R.font.inter_regular)),
                    color = Color(0xFFB0B0B8),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun AssetAvatar(
    iconUrl: Int,
    fallbackLabel: String,
    imageLoader: coil.ImageLoader,
    modifier: Modifier = Modifier
) {
    if (iconUrl!=0) {
        Image(
            painter = painterResource(id = iconUrl),
            contentDescription = "$iconUrl icon",
            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
            contentScale = ContentScale.Fit,
            colorFilter = null
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Color(0xFFF1F1F3)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackLabel.take(1).uppercase(),
                fontSize = 18.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                color = Color(0xFF222222)
            )
        }
    }
}

@Composable
private fun ActionBadge(
    transaction: TransactionRecord,
    modifier: Modifier = Modifier
) {
    val isPending = transaction.status == TransactionStatus.PENDING
    val background = when {
        transaction.isOutgoing -> Color(0xFF4AA8FF)
        else -> Green
    }
    val iconTint = Color.White

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (isPending) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                strokeWidth = 1.5.dp
            )
        }

        Icon(
            imageVector = when {
                transaction.status == TransactionStatus.FAILED -> Icons.Default.Add
                transaction.isOutgoing -> Icons.Default.ArrowUpward
                else -> Icons.Default.ArrowDownward
            },
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun CounterpartyChip(
    label: String,
    isInternal: Boolean,
    accentColor: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isInternal) Color(0xFFF6F0FF) else Color(0xFFF6E7B9).copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isInternal) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accentColor?.let(::Color) ?: Color(0xFF7C6BFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.take(1).uppercase(),
                    fontSize = 8.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                    color = Color.White
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFD9B35B)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            color = Color(0xFF8F8F96)
        )
    }
}

@Composable
private fun amountColor(transaction: TransactionRecord): Color {
    return when {
        transaction.status == TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
        transaction.isOutgoing -> Color(0xFF9F9FA6)
        else -> Green
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun PreviewTransactionItemLight() {
    MaterialTheme {
        TransactionHistoryItemContent(
            isOutgoing = true,
            status = TransactionStatus.CONFIRMED,
            iconUrl = 0,
            iconNetworkUrl = 0,
            assetTitle = "Ethereum",
            primaryLabel = "ارسال به",
            counterpartyLabel = "0x12...5678",
            amountText = "-0.5 ETH",
            fiatText = "$1,250.00",
            imageLoader = LocalContext.current.imageLoader,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewTransactionItemDark() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TransactionHistoryItemContent(
                isOutgoing = false,
                status = TransactionStatus.PENDING,
                iconUrl = 0,
                iconNetworkUrl = 0,
                assetTitle = "Tether",
                primaryLabel = "دریافت از",
                counterpartyLabel = "0xab...efgh",
                amountText = "+500.0 USDT",
                fiatText = "$500.00",
                imageLoader = LocalContext.current.imageLoader,
                onClick = {}
            )
        }
    }
}
