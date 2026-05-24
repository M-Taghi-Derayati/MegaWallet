package com.mtd.megawallet.ui.compose.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mtd.common_ui.R
import com.mtd.domain.model.HistoryNetworkOption
import com.mtd.domain.model.HistoryRow
import com.mtd.domain.model.HomeUiState
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionDetailsBottomSheet
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryEmptyState
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryItem
import com.mtd.megawallet.ui.compose.screens.history.components.TransactionHistoryShimmer
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel
import com.mtd.megawallet.viewmodel.news.HomeViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    networkName: String? = null,
    userAddress: String? = null,
    viewModel: TransactionHistoryViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    showDetailsBottomSheet: Boolean = true
) {
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedTransaction by viewModel.selectedTransaction.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val networkOptions by viewModel.networkOptions.collectAsState()
    var showNetworkSheet by remember { mutableStateOf(false) }
    val selectedNetworkOption = remember(networkOptions) {
        networkOptions.firstOrNull { it.isSelected } ?: networkOptions.firstOrNull()
    }
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

    LaunchedEffect(networkName, userAddress) {
        if (!networkName.isNullOrBlank() || !userAddress.isNullOrBlank()) {
            viewModel.loadHistory(networkName, userAddress)
        }
    }

        val homeUiState by homeViewModel.uiState.collectAsState()
        LaunchedEffect(homeUiState) {
            val success = homeUiState as? HomeUiState.Success ?: return@LaunchedEffect
            viewModel.syncAssetPricesFromHomeAssets(success.assets)
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refreshSelectedNetwork(networkName, userAddress) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "history_network_input") {
                    HistoryNetworkInputSection(
                        option = selectedNetworkOption,
                        onClick = { showNetworkSheet = true }
                    )
                }

                when {
                    isLoading && transactions.isEmpty() -> {
                        item(key = "history_shimmer") {
                            TransactionHistoryShimmer(modifier = Modifier.fillParentMaxSize())
                        }
                    }

                    !errorMessage.isNullOrBlank() -> {
                        item(key = "history_error") {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    transactions.isEmpty() && !isLoading -> {
                        item(key = "history_empty") {
                            TransactionHistoryEmptyState(modifier = Modifier.fillParentMaxSize())
                        }
                    }

                    else -> {

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

        ChooseHistoryNetworkBottomSheet(
            visible = showNetworkSheet,
            options = networkOptions,
            onDismiss = { showNetworkSheet = false },
            onNetworkSelected = { option ->
                showNetworkSheet = false
                viewModel.selectNetwork(option)
            }
        )

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

@Composable
private fun HistoryNetworkInputSection(
    option: HistoryNetworkOption?,
    onClick: () -> Unit
) {
    if (option == null) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "شبکه",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryNetworkIcon(option = option, size = 20)

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = option.displayTitle(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (option.isRefreshing || option.isStale) {
                    Spacer(modifier = Modifier.width(8.dp))
                    HistoryNetworkStatusIndicator(option = option, light = true)
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ChooseHistoryNetworkBottomSheet(
    visible: Boolean,
    options: List<HistoryNetworkOption>,
    onDismiss: () -> Unit,
    onNetworkSelected: (HistoryNetworkOption) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9999f)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300))
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

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(360)
            ),
            exit = fadeOut(animationSpec = tween(240)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(260)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 1.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "انتخاب شبکه",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                        fontSize = 20.sp
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                options.forEachIndexed { index, option ->
                    ChooseHistoryNetworkRow(
                        option = option,
                        onClick = { onNetworkSelected(option) }
                    )
                    if (index < options.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseHistoryNetworkRow(
    option: HistoryNetworkOption,
    onClick: () -> Unit
) {
    val backgroundColor = if (option.isSelected) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }
    val borderColor = if (option.isSelected) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HistoryNetworkIcon(option = option, size = 36)

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = option.displayTitle(),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontSize = 17.sp
                    )

                    if (option.isRefreshing || option.isStale) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (option.isRefreshing) "در حال بروزرسانی" else "نیاز به بروزرسانی",
                            color = if (option.isRefreshing) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            },
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            HistoryNetworkStatusIndicator(option = option, light = false)
        }
    }
}

@Composable
private fun HistoryNetworkIcon(
    option: HistoryNetworkOption,
    size: Int
) {
    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader(context) }
    val localIcon = remember(option.networkId, option.isAllNetworks) {
        if (option.isAllNetworks) R.drawable.ic_wallet else getNetworkIconResId(option.networkId)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        if (localIcon != 0 && (option.isAllNetworks || localIcon != R.drawable.ic_wallet)) {
            Image(
                painter = painterResource(id = localIcon),
                contentDescription = null,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
        } else {
            AsyncImage(
                model = option.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size((size - 4).dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
                imageLoader = imageLoader
            )
        }
    }
}

@Composable
private fun HistoryNetworkStatusIndicator(
    option: HistoryNetworkOption,
    light: Boolean
) {
    when {
        option.isRefreshing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (light) Color.White else MaterialTheme.colorScheme.tertiary
            )
        }

        option.isStale -> {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Stale history",
                modifier = Modifier.size(16.dp),
                tint = if (light) Color.White else MaterialTheme.colorScheme.error.copy(alpha = 0.80f)
            )
        }

        option.isSelected -> {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}

private fun HistoryNetworkOption.displayTitle(): String {
    return if (isAllNetworks) {
        "همه شبکه‌ها"
    } else {
        networkFaName ?: networkName
    }
}
