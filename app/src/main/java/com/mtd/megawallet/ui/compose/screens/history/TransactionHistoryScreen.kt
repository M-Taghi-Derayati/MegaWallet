package com.mtd.megawallet.ui.compose.screens.history

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.common_ui.R
import com.mtd.domain.model.TransactionRecord
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionDetailsBottomSheet
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryEmptyState
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryItem
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryShimmer
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel

private sealed interface HistoryRow {
    val stableKey: String

    data class Header(
        val title: String
    ) : HistoryRow {
        override val stableKey: String = "header_$title"
    }

    data class Transaction(
        val item: TransactionRecord
    ) : HistoryRow {
        override val stableKey: String =
            "${item.networkName?.name}:${item.hash}:${item.timestamp}:${item.fromAddress}:${item.toAddress}:${item.amount}"
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    networkName: String? = null,
    userAddress: String? = null,
    viewModel: TransactionHistoryViewModel = hiltViewModel(),
    showDetailsBottomSheet: Boolean = true
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
        PullToRefreshBox(
            isRefreshing = isLoading && transactions.isNotEmpty(),
            onRefresh = { viewModel.refresh(networkName, userAddress) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && transactions.isEmpty() -> {
                    TransactionHistoryShimmer()
                }

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
                    TransactionHistoryEmptyState()
                }

                else -> {
                    val historyRows by remember(transactions) {
                        derivedStateOf {
                            buildList {
                                transactions
                                    .groupBy { viewModel.getHistoryDateHeader(it) }
                                    .forEach { (dateHeader, txList) ->
                                        add(HistoryRow.Header(dateHeader))
                                        txList.forEach { tx ->
                                            add(HistoryRow.Transaction(tx))
                                        }
                                    }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item(key = "history_toolbar") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                            ) {
                                IconButton(
                                    onClick = { },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = "Filter",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                            }
                            }

                            items(
                                items = historyRows,
                                key = { row -> row.stableKey }
                            ) { row ->
                                when (row) {
                                    is HistoryRow.Header -> {
                                        Text(
                                            text = row.title,
                                            fontSize = 18.sp,
                                            fontFamily = FontFamily(
                                                Font(
                                                    R.font.iransansmobile_fa_bold,
                                                    FontWeight.Bold
                                                )
                                            ),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(
                                                horizontal = 24.dp,
                                                vertical = 14.dp
                                            )
                                        )
                                    }

                                    is HistoryRow.Transaction -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .animateItem(
                                                    fadeInSpec = tween(300),
                                                    placementSpec = tween(300)
                                                )
                                        ) {
                                            TransactionHistoryItem(
                                                transaction = row.item,
                                                viewModel = viewModel,
                                                onClick = { viewModel.selectTransaction(row.item) }
                                            )
                                        }
                                    }
                                }
                            }
                    }

                }
            }
        }

        if (showDetailsBottomSheet) {
            TransactionDetailsBottomSheet(
                visible = selectedTransaction != null,
                transaction = selectedTransaction,
                viewModel = viewModel,
                onDismiss = { viewModel.selectTransaction(null) }
            )
        }
    }
}
