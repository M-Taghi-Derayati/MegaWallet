package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.theme.Green
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@Composable
fun TransactionDetailsBottomSheet(
    visible: Boolean,
    transaction: TransactionRecord?,
    viewModel: TransactionHistoryViewModel,
    onDismiss: () -> Unit
) {
    var activeTransaction by remember { mutableStateOf<TransactionRecord?>(null) }

    LaunchedEffect(transaction) {
        if (transaction != null) {
            activeTransaction = transaction
        }
    }

    val context = LocalContext.current
    val imageLoader = remember(context) { context.imageLoader }

    val currentTx = transaction ?: activeTransaction ?: return
    val explorerUrl = remember(currentTx.hash, currentTx.networkName) {
        viewModel.buildExplorerUrl(currentTx)
    }

    AnimatedBottomSheetCard(
        visible = visible,
        title = "", // عنوان در هدر کاستوم هندل می‌شود
        onDismiss = onDismiss
    ) {
        val primaryLabel = viewModel.getHistoryPrimaryLabel(currentTx)
        val counterpartyLabel = viewModel.getHistoryCounterpartyLabel(currentTx)
        val dateLabel = viewModel.formatTimelineSubmitted(currentTx)
        val iconUrl = viewModel.getHistoryAssetIconUrl(currentTx)
        val assetTitle = viewModel.getHistoryAssetTitle(currentTx)
        val fiatAmount = viewModel.formatTransactionFiatDetail(currentTx)
        val cryptoAmount = viewModel.formatTransactionAmount(currentTx)
        val networkName = viewModel.getNetworkDisplayName(currentTx)
        val fee = viewModel.formatTransactionFee(currentTx)
        val imageLoader = LocalContext.current.imageLoader
        Spacer(modifier = Modifier.height(15.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    AsyncImage(
                        model = iconUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                    // نشانگر وضعیت کوچک
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopStart)
                            .clip(CircleShape)
                            .background(if (currentTx.isOutgoing) Color(0xFFE3F2FD) else Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentTx.isOutgoing) androidx.compose.material.icons.Icons.Default.ArrowUpward else androidx.compose.material.icons.Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = if (currentTx.isOutgoing) Color(0xFF4AA8FF) else Green
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$primaryLabel $counterpartyLabel",
                        fontSize = 15.sp,
                        fontFamily = FontFamily(Font(R.font.inter_bold, FontWeight.Bold)),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateLabel,
                        fontSize = 13.sp,
                        fontFamily = FontFamily(Font(R.font.inter_regular)),
                        color = Color(0xFF8F8F96),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = Color(0xFF8F8F96),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // باکس مبلغ با حاشیه نقطه‌چین (Dashed Border)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                val dashColor = Color(0xFFE0E0E0)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = dashColor,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TransactionFiatValue(
                        fiatAmount = fiatAmount,
                        transaction = currentTx
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        AsyncImage(
                            model = iconUrl,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cryptoAmount,
                            fontSize = 16.sp,
                            fontFamily = FontFamily(Font(R.font.inter_medium, FontWeight.Medium)),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }






            Spacer(modifier = Modifier.height(24.dp))

            // نوار وضعیت (Status Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        when (currentTx.status) {
                            TransactionStatus.CONFIRMED -> Color(0xFFE8F5E9)
                            TransactionStatus.PENDING -> Color(0xFFE3F2FD)
                            TransactionStatus.FAILED -> Color(0xFFFFEBEE)
                        }
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (currentTx.status == TransactionStatus.CONFIRMED) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = when (currentTx.status) {
                        TransactionStatus.CONFIRMED -> Green
                        TransactionStatus.PENDING -> Color(0xFF4AA8FF)
                        TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = when (currentTx.status) {
                        TransactionStatus.CONFIRMED -> "انجام شده"
                        TransactionStatus.PENDING -> "در حال انجام"
                        TransactionStatus.FAILED -> "کنسل شده"
                    },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                    color = when (currentTx.status) {
                        TransactionStatus.CONFIRMED -> Green
                        TransactionStatus.PENDING -> Color(0xFF4AA8FF)
                        TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
                    }
                )
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = when (currentTx.status) {
                        TransactionStatus.CONFIRMED -> Green
                        TransactionStatus.PENDING -> Color(0xFF4AA8FF)
                        TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // بخش جزئیات نهایی (Wallet, Fee, Chain)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                TransactionDetailSummaryRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    label = "کیف پول",
                    value = viewModel.activeWallet.value?.name ?: "Wallet"
                )

                TransactionDetailSummaryRow(
                    icon = Icons.Default.LocalGasStation,
                    label = "کارمزد شبکه",
                    value = fee,
                    highlighted = true
                )

                TransactionDetailSummaryRow(
                    icon = Icons.Default.Share,
                    label = "شبکه",
                    value = networkName
                )
            }

        }
    }
}

@Composable
private fun TransactionFiatValue(
    fiatAmount: String?,
    transaction: TransactionRecord
) {
    val valueColor = when {
        fiatAmount == null -> Color(0xFF8F8F96)
        transaction.status == TransactionStatus.FAILED -> MaterialTheme.colorScheme.error
        transaction.isOutgoing -> Color(0xFF8F8F96)
        else -> Green
    }

    Text(
        text = fiatAmount ?: "ارزش دلاری نامشخص",
        fontSize = if (fiatAmount == null) 16.sp else 25.sp,
        fontFamily = FontFamily(Font(R.font.inter_medium, FontWeight.Medium)),
        color = valueColor,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TransactionDetailSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    highlighted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlighted) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = if (highlighted) 10.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF8F8F96),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            color = Color(0xFF8F8F96),
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            fontSize = 15.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Bold)),
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}


