package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.common_ui.theme.Green
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@Composable
fun TransactionHistoryItem(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel,
    onClick: () -> Unit
) {
    val iconUrl = remember(transaction) { getLocalIconResId(viewModel.getHistoryAssetIconUrl(transaction) ?: "") }
    val localIconNetworkResId = remember(transaction) { getNetworkIconResId(viewModel.networkId(transaction)) }

    val assetTitle = remember(transaction) { viewModel.getHistoryAssetTitle(transaction) }
    val primaryLabel = remember(transaction) { viewModel.getHistoryPrimaryLabel(transaction) }
    val counterpartyLabel = remember(transaction) { viewModel.getHistoryCounterpartyLabel(transaction) }
    val amountText = remember(transaction) { viewModel.formatListAmount(transaction) }
    val fiatText = viewModel.formatTransactionFiat(transaction)

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
                            if (isOutgoing) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
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
                            painter = if (isOutgoing) {
                                painterResource(id = R.drawable.ic_send)
                            } else {
                                painterResource(id = R.drawable.ic_download)
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(2.dp),
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
internal fun AssetAvatar(
    iconUrl: Int,
    fallbackLabel: String,
    modifier: Modifier = Modifier
) {
    if (iconUrl != 0) {
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
