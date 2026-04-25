package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.megawallet.ui.compose.theme.Green
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@Composable
fun TransactionHistoryItem(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel,
    onClick: () -> Unit
) {
    val isOutgoing = transaction.isOutgoing
    val isPending = transaction.status == TransactionStatus.PENDING
    val isFailed = transaction.status == TransactionStatus.FAILED

    val iconBgColor = when {
        isPending -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
        isFailed -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        isOutgoing -> MaterialTheme.colorScheme.surface
        else -> Green.copy(alpha = 0.1f)
    }

    val iconColor = when {
        isPending -> MaterialTheme.colorScheme.secondary
        isFailed -> MaterialTheme.colorScheme.error
        isOutgoing -> MaterialTheme.colorScheme.tertiary
        else -> Green
    }

    val timeFormat = remember(transaction.timestamp) {
        viewModel.formatTransactionTime(transaction.timestamp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            if (isPending) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 2.dp
                )
            }
            Icon(
                imageVector = if (isOutgoing) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = viewModel.getTransactionTypeLabel(transaction),
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = viewModel.getNetworkDisplayName(transaction),
                    fontSize = 11.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = viewModel.getTransactionStatusLabel(transaction.status),
                    fontSize = 12.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    color = when {
                        isPending -> MaterialTheme.colorScheme.secondary
                        isFailed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onTertiary
                    }
                )
                Text(
                    text = " • $timeFormat",
                    fontSize = 12.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    color = MaterialTheme.colorScheme.onTertiary
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = viewModel.formatTransactionAmount(transaction),
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                color = when {
                    isFailed -> MaterialTheme.colorScheme.error
                    isOutgoing -> MaterialTheme.colorScheme.tertiary
                    else -> Green
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = viewModel.formatTransactionFiat(transaction) ?: "—",
                fontSize = 12.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}
