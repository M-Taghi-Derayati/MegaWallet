package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TronTransaction
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@Composable
fun TransactionGeneralDetailsCard(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp)
    ) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        
        DetailRow(title = "کارمزد شبکه", value = viewModel.formatTransactionFee(transaction))
        DetailRow(title = "شبکه", value = viewModel.getNetworkDisplayName(transaction))
        
        // هش تراکنش با قابلیت کپی و نمایش کوتاه شده
        val truncatedHash = remember(transaction.hash) {
            if (transaction.hash.length > 16) {
                "${transaction.hash.take(8)}...${transaction.hash.takeLast(8)}"
            } else {
                transaction.hash
            }
        }
        
        DetailRow(
            title = "هش تراکنش", 
            value = truncatedHash,
            onClick = {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(transaction.hash))
                // اینجا می‌توانستیم توست نشان دهیم اما فعلاً کپی ساده انجام می‌شود
            }
        )

        when (transaction) {
            is EvmTransaction -> {
                transaction.gasLimit?.let { DetailRow(title = "Gas Limit", value = it.toString()) }
                transaction.gasPrice?.let { DetailRow(title = "Gas Price", value = it.toString()) }
                transaction.nonce?.let { DetailRow(title = "Nonce", value = it.toString()) }
                transaction.fromAddress.takeIf { it.isNotBlank() }?.let { DetailRow(title = "از", value = it) }
                transaction.toAddress.takeIf { it.isNotBlank() }?.let { DetailRow(title = "به", value = it) }
            }

            is TronTransaction -> {
                transaction.bandwidthUsed?.let { DetailRow(title = "Bandwidth Used", value = it.toString()) }
                transaction.energyUsed?.let { DetailRow(title = "Energy Used", value = it.toString()) }
                transaction.feeLimit?.let { DetailRow(title = "Fee Limit", value = it.toString()) }
                transaction.fromAddress.takeIf { it.isNotBlank() }?.let { DetailRow(title = "از", value = it) }
                transaction.toAddress.takeIf { it.isNotBlank() }?.let { DetailRow(title = "به", value = it) }
            }

            is BitcoinTransaction -> {
                transaction.feeRateSatsPerByte?.let { DetailRow(title = "Fee Rate (sats/vB)", value = it.toString()) }
                transaction.fromAddress?.takeIf { it.isNotBlank() }?.let { DetailRow(title = "از", value = it) }
                transaction.toAddress?.takeIf { it.isNotBlank() }?.let { DetailRow(title = "به", value = it) }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            color = if (onClick != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
