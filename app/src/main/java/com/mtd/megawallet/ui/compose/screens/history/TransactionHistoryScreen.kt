package com.mtd.megawallet.ui.compose.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionDetailsBottomSheet
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryItem
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    networkName: String? = null,
    userAddress: String? = null,
    viewModel: TransactionHistoryViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTransaction by viewModel.selectedTransaction.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(networkName, userAddress) {
        viewModel.loadHistory(networkName, userAddress)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refresh(networkName, userAddress) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                !errorMessage.isNullOrBlank() -> {
                    Text(
                        text = errorMessage.orEmpty(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                transactions.isEmpty() && !isLoading -> {
                    Text(
                        text = "تراکنشی یافت نشد",
                        modifier = Modifier.align(Alignment.Center),
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }

                else -> {
                    val groupedTransactions by remember(transactions) {
                        derivedStateOf {
                            transactions.groupBy { viewModel.getDateHeader(it.timestamp) }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        groupedTransactions.forEach { (dateHeader, txList) ->
                            item(key = "header_$dateHeader") {
                                Text(
                                    text = dateHeader,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }

                            items(
                                items = txList,
                                key = { tx -> "${tx.networkName?.name}:${tx.hash}:${tx.isOutgoing}:${tx.amount}" }
                            ) { tx ->
                                TransactionHistoryItem(
                                    transaction = tx,
                                    viewModel = viewModel,
                                    onClick = { viewModel.selectTransaction(tx) }
                                )
                            }
                        }
                    }
                }
            }
        }

        TransactionDetailsBottomSheet(
            visible = selectedTransaction != null,
            transaction = selectedTransaction,
            viewModel = viewModel,
            onDismiss = { viewModel.selectTransaction(null) }
        )
    }
}
