package com.mtd.megawallet.ui.compose.screens.history.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.core.utils.AddressUtils.shortenAddress
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.EvmTransaction
import com.mtd.core.utils.FiatConversion
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.rememberClipboardCopier
import com.mtd.megawallet.ui.compose.screens.wallet.AutoResizeBalanceRows
import com.mtd.common_ui.theme.NetworkIcon
import com.mtd.megawallet.viewmodel.history.TransactionHistoryViewModel
import java.math.BigDecimal
import java.math.BigInteger
import com.mtd.common_ui.theme.InterMediumMedium
import com.mtd.common_ui.theme.IranSansBoldBold
import com.mtd.common_ui.theme.IranSansLightMedium
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularBold
import com.mtd.common_ui.theme.IranSansRegularMedium

private const val DETAIL_SHEET_TRANSITION_MS = 360

@Composable
fun TransactionDetailsBottomSheet(
    visible: Boolean,
    transaction: TransactionRecord?,
    viewModel: TransactionHistoryViewModel,
    onDismiss: () -> Unit
) {
    var activeTransaction by remember { mutableStateOf<TransactionRecord?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(transaction) {
        if (transaction != null) {
            activeTransaction = transaction
        }
    }

    val currentTx = transaction ?: activeTransaction ?: return

    val iconUrl = remember(transaction) { viewModel.getHistoryAssetIconUrl(currentTx) }
    val assetSymbol = remember(transaction) { viewModel.getHistoryAssetSymbol(currentTx) }
    // TASK-53 — آیکون شبکه از کانفیگ؛ دیگر resource id نیست.
    val networkIconUrl =
        remember(transaction) { viewModel.networkIconUrl(currentTx) }


    LaunchedEffect(visible, currentTx.hash) {
        expanded = false
        if (visible) {
            viewModel.loadTransactionDetails(currentTx)
            sheetVisible = false
            withFrameNanos { }
            sheetVisible = true
        } else {
            sheetVisible = false
        }
    }

    BackHandler(enabled = sheetVisible && expanded) {
        expanded = false
    }

    AnimatedBottomSheetCard(
        visible = sheetVisible,
        title = "",
        onDismiss = {
            if (expanded) {
                expanded = false
            } else {
                sheetVisible = false
                onDismiss()
            }
        }
    ) {
        TransactionDetailsContent(
            transaction = currentTx,
            viewModel = viewModel,
            iconUrl = iconUrl,
            assetSymbol = assetSymbol,
            networkIconUrl = networkIconUrl,
            expanded = expanded,
            onExpand = { expanded = true },
            onDone = { expanded = false }
        )
    }
}

@Composable
private fun TransactionDetailsContent(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel,
    iconUrl: String?,
    assetSymbol: String,
    networkIconUrl: String?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDone: () -> Unit
) {
    val imageLoader = LocalContext.current.imageLoader
    val style = transactionVisualStyle(transaction)
    // TASK-56 — collected so the receipt re-renders on a currency switch / rate update.
    val fiatCurrency by viewModel.fiatCurrency.collectAsStateWithLifecycle()
    val usdToIrrRate by viewModel.usdToIrrRate.collectAsStateWithLifecycle()
    val fiatAmount = remember(transaction, fiatCurrency, usdToIrrRate) {
        viewModel.formatTransactionFiatDetail(transaction, fiatCurrency, usdToIrrRate)
    }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var measuredCollapsedHeight by remember(transaction.hash) { mutableStateOf<Dp?>(null) }
    var hasExpandedOnce by remember(transaction.hash) { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            hasExpandedOnce = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val expandedHeight = maxHeight
        val targetSheetHeight = if (expanded) expandedHeight else measuredCollapsedHeight
        val animatedSheetHeight by animateDpAsState(
            targetValue = targetSheetHeight ?: expandedHeight,
            animationSpec = tween(durationMillis = DETAIL_SHEET_TRANSITION_MS),
            label = "HistoryDetailsSheetHeight"
        )
        val statusHeight = 48.dp
        val statusCollapsedY = 223.dp
        val statusExpandedY = expandedHeight - statusHeight - 16.dp
        val statusY by animateDpAsState(
            targetValue = if (expanded) statusExpandedY else statusCollapsedY,
            animationSpec = tween(durationMillis = DETAIL_SHEET_TRANSITION_MS),
            label = "HistoryStatusActionY"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        targetSheetHeight == null -> Modifier
                        expanded || hasExpandedOnce -> Modifier.height(animatedSheetHeight)
                        else -> Modifier.height(targetSheetHeight)
                    }
                )
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded) {
                            Modifier
                                .verticalScroll(scrollState)
                                .padding(horizontal = 12.dp)
                                .padding(top = 10.dp, bottom = 88.dp)
                        } else {
                            Modifier
                                .padding(horizontal = 12.dp)
                                .padding(top = 10.dp)
                                .onGloballyPositioned { coordinates ->
                                    if (measuredCollapsedHeight == null) {
                                        measuredCollapsedHeight = with(density) {
                                            coordinates.size.height.toDp() + 10.dp
                                        }
                                    }
                                }
                        }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TransactionHeader(
                    transaction = transaction,
                    viewModel = viewModel,
                    iconUrl = iconUrl,
                    assetSymbol = assetSymbol,
                    networkIconUrl = networkIconUrl,
                    style = style
                )

                Spacer(modifier = Modifier.height(if (expanded) 54.dp else 16.dp))

                AmountBlock(
                    fiatAmount = fiatAmount,
                    fiatCurrency = fiatCurrency,
                    cryptoAmount = viewModel.formatTransactionAmount(transaction),
                    large = expanded,
                    framed = !expanded
                )

                Spacer(modifier = Modifier.height(if (expanded) 34.dp else 70.dp))

                if (!expanded) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryRow(
                            icon = R.drawable.ic_wallet,
                            label = "کیف پول",
                            value = viewModel.activeWallet.value?.name ?: "Wallet"
                        )
                        SummaryRow(
                            icon = R.drawable.ic_gas,
                            label = "کارمزد شبکه",
                            value = viewModel.formatTransactionFee(transaction),
                            highlighted = true,
                            loading = viewModel.isTransactionFeeDetailsLoading(transaction)
                        )
                        SummaryRow(
                            icon = R.drawable.ic_chain,
                            label = "شبکه",
                            value = viewModel.getNetworkDisplayName(transaction)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(animationSpec = tween(DETAIL_SHEET_TRANSITION_MS)) +
                            expandVertically(animationSpec = tween(DETAIL_SHEET_TRANSITION_MS)),
                    exit = fadeOut(animationSpec = tween(DETAIL_SHEET_TRANSITION_MS)) +
                            shrinkVertically(animationSpec = tween(DETAIL_SHEET_TRANSITION_MS))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        StatusSummaryRow(
                            transaction = transaction,
                            style = style,
                            viewModel = viewModel
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        GeneralDetailsCard(
                            transaction = transaction,
                            viewModel = viewModel
                        )

                        transactionTokenTransfer(transaction)?.let { transfer ->
                            Spacer(modifier = Modifier.height(16.dp))
                            TokenTransfersCard(
                                transfer = transfer,
                                fallbackTo = transaction.toAddress
                            )
                        }
                    }
                }
            }

            StatusActionButton(
                transaction = transaction,
                style = style,
                expanded = expanded,
                height = statusHeight,
                onClick = if (expanded) onDone else onExpand,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = statusY)
                    .padding(horizontal = 24.dp)
                    .zIndex(1f)
            )
        }
    }
}

@Composable
private fun TransactionHeader(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel,
    iconUrl: String?,
    assetSymbol: String,
    networkIconUrl: String?,
    style: TransactionVisualStyle
) {
    val uriHandler = LocalUriHandler.current
    val copyToClipboard = rememberClipboardCopier()
    val explorerUrl = remember(transaction.hash) { viewModel.buildExplorerUrl(transaction) }
    var menuOpen by remember(transaction.hash) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssetIconWithDirection(
            iconUrl = iconUrl,
            assetSymbol = assetSymbol,
            networkIconUrl = networkIconUrl,
            style = style,
            isOutgoing = transaction.isOutgoing
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${viewModel.getHistoryPrimaryLabel(transaction)} ${
                    viewModel.getHistoryCounterpartyLabel(
                        transaction
                    )
                }",
                fontSize = 15.sp,
                fontFamily = IranSansRegularBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = viewModel.formatTimelineSubmitted(transaction),
                fontSize = 13.sp,
                fontFamily = IranSansRegular,
                color = Color(0xFF8F8F96),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // TASK-51 — this three-dot affordance existed but was a plain, non-interactive Icon. It now
        // opens the actions menu. `buildExplorerUrl` returns null for a network with no configured
        // explorer (or an explorer whose URL shape we can't build), in which case the entry is hidden
        // rather than shown as a dead item.
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "گزینه‌های بیشتر",
                    tint = Color(0xFF8F8F96),
                    modifier = Modifier.size(24.dp)
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                if (explorerUrl != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "مشاهده در اکسپلورر",
                                fontSize = 14.sp,
                                fontFamily = IranSansRegularMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_chain),
                                contentDescription = null,
                                tint = Color(0xFF8F8F96),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuOpen = false
                            // No browser / no activity to handle the intent must not crash the sheet.
                            runCatching { uriHandler.openUri(explorerUrl) }
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "کپی هش تراکنش",
                            fontSize = 14.sp,
                            fontFamily = IranSansRegularMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = null,
                            tint = Color(0xFF8F8F96),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuOpen = false
                        copyToClipboard(transaction.hash)
                    }
                )
            }
        }
    }
}

@Composable
private fun AssetIconWithDirection(
    iconUrl: String?,
    assetSymbol: String,
    networkIconUrl: String?,
    style: TransactionVisualStyle,
    isOutgoing: Boolean
) {


    Box(
        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)
    ) {

        AssetAvatar(
            iconUrl = iconUrl,
            symbol = assetSymbol,
            fallbackLabel = "",
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

            if (style.pending) {
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

            NetworkIcon(
                iconUrl = networkIconUrl,
                contentDescription = "network icon",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING)
            )
        }
    }
}

@Composable
private fun AmountBlock(
    fiatAmount: String?,
    fiatCurrency: FiatCurrency,
    cryptoAmount: String,
    large: Boolean,
    framed: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (framed) Modifier.height(120.dp) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (framed) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFFE0E0E0),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    ),
                    cornerRadius = CornerRadius(24.dp.toPx())
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {


            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                AutoResizeBalanceRows(
                    totalBalance = formatReceiptBalanceValue(fiatAmount),
                    displayCurrency = fiatCurrency,
                    animationDuration = 300,
                    TMNFontSize = 15.sp,
                    USDTFontSize = if (large) 30.sp else 24.sp,
                    textSize = if (large) 44.sp else 34.sp,
                    alignment = Alignment.Center
                )
            }

            Text(
                text = cryptoAmount,
                fontSize = if (large) 14.sp else 16.sp,
                fontFamily = InterMediumMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun StatusActionButton(
    transaction: TransactionRecord,
    style: TransactionVisualStyle,
    expanded: Boolean,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(if (expanded) style.accent else style.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = if (transaction.status == TransactionStatus.FAILED) {
                painterResource(R.drawable.ic_danger)
            } else {
                painterResource(R.drawable.ic_check)
            },
            contentDescription = null,
            tint = style.accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = if (expanded) "تایید" else style.statusLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            fontFamily = IranSansRegularBold,
            color = if (expanded) MaterialTheme.colorScheme.background else style.accent
        )
        if (expanded) {
            Spacer(modifier = Modifier.size(20.dp))
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SummaryRow(
    icon: Int,
    label: String,
    value: String,
    highlighted: Boolean = false,
    loading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(0xFF8F8F96),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontFamily = IranSansRegular,
            color = Color(0xFF8F8F96),
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            Box(
                modifier = Modifier.weight(1f, fill = false),
                contentAlignment = Alignment.CenterEnd
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = value,
                fontSize = 15.sp,
                fontFamily = IranSansRegularBold,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun StatusSummaryRow(
    transaction: TransactionRecord,
    style: TransactionVisualStyle,
    viewModel: TransactionHistoryViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (transaction.status == TransactionStatus.FAILED) {
                    painterResource(R.drawable.ic_danger)
                } else {
                    painterResource(R.drawable.ic_check)
                },
                contentDescription = null,
                tint = style.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = style.statusLabel,
            fontSize = 15.sp,
            fontFamily = IranSansRegularBold,
            color = style.accent,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatStatusDate(viewModel, transaction),
            fontSize = 12.sp,
            fontFamily = IranSansLightMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GeneralDetailsCard(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "عمومی",
            fontSize = 13.sp,
            fontFamily = IranSansBoldBold,
            color = Color(0xFF8F8F96),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        val rows = buildGeneralRows(transaction, viewModel)
        rows.forEachIndexed { index, row ->
            DetailRow(
                icon = row.icon,
                label = row.label,
                value = row.value,
                accent = row.accent,
                loading = row.loading,
                copyValue = row.copyValue
            )
            if (index != rows.lastIndex) {
                DividerLine()
            }
        }
    }
}

@Composable
private fun TokenTransfersCard(
    transfer: TokenTransferDetails,
    fallbackTo: String?
) {
    val toAddress = transfer.to.ifBlank { fallbackTo.orEmpty() }
    val fromAddress = transfer.from

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "انتقال توکن",
            fontSize = 13.sp,
            fontFamily = IranSansBoldBold,
            color = Color(0xFF8F8F96),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        DetailRow(
            icon = R.drawable.ic_download,
            label = "ارسال به",
            value = shortenAddress(toAddress),
            accent = true,
            copyValue = toAddress,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        DividerLine(modifier = Modifier.padding(horizontal = 16.dp))
        DetailRow(
            icon = R.drawable.ic_send,
            label = "ارسال از",
            value = shortenAddress(fromAddress),
            accent = true,
            copyValue = fromAddress,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        DividerLine(modifier = Modifier.padding(horizontal = 16.dp))
        DetailRow(
            icon = R.drawable.ic_amount,
            label = "مقدار",
            value = formatTokenTransferAmount(transfer),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun DetailRow(
    icon: Int,
    label: String,
    value: String,
    accent: Boolean = false,
    loading: Boolean = false,
    copyValue: String? = null,
    modifier: Modifier = Modifier
) {
    // TASK-52 — `copyValue` carries the FULL value; `value` is the shortened display form, so copying
    // the displayed text would hand the user a truncated, useless address.
    val copyToClipboard = rememberClipboardCopier()
    val copyable = !loading && !copyValue.isNullOrBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // Clickable before padding, so the padding is part of the touch target.
                if (copyable) Modifier.clickable { copyToClipboard(copyValue.orEmpty()) } else Modifier
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(0xFF8F8F96),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontFamily = IranSansRegularMedium,
            color = Color(0xFF8F8F96),
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            Box(
                modifier = Modifier.weight(1.2f),
                contentAlignment = Alignment.CenterEnd
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Row(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value.ifBlank { "-" },
                    fontSize = 14.sp,
                    fontFamily = IranSansBoldBold,
                    color = if (accent) Color(0xFF4AA8FF) else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (copyable) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = "کپی $label",
                        tint = Color(0xFF8F8F96),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE7E8EC).copy(alpha = 0.35f))
    )
}

@Composable
private fun transactionVisualStyle(transaction: TransactionRecord): TransactionVisualStyle {
    return when (transaction.status) {
        TransactionStatus.PENDING -> TransactionVisualStyle(
            accent = MaterialTheme.colorScheme.secondary,
            background = MaterialTheme.colorScheme.secondary.copy(0.15f),
            statusLabel = "در حال انجام",
            pending = true
        )

        TransactionStatus.FAILED -> TransactionVisualStyle(
            accent = MaterialTheme.colorScheme.error,
            background = MaterialTheme.colorScheme.error.copy(0.15f),
            statusLabel = "کنسل شده",
            pending = false
        )

        TransactionStatus.CONFIRMED -> TransactionVisualStyle(
            accent = if (transaction.isOutgoing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            background = if (transaction.isOutgoing) MaterialTheme.colorScheme.secondary.copy(0.15f) else MaterialTheme.colorScheme.primary.copy(
                0.15f
            ),
            statusLabel = "انجام شده",
            pending = false
        )
    }
}

private data class TransactionVisualStyle(
    val accent: Color,
    val background: Color,
    val statusLabel: String,
    val pending: Boolean
)

private data class DetailItem(
    val icon: Int,
    val label: String,
    val value: String,
    val accent: Boolean = false,
    val loading: Boolean = false,
    /** TASK-52 — full, un-truncated text to copy; null means the row isn't copyable. */
    val copyValue: String? = null
)

private fun formatNativeValue(
    viewModel: TransactionHistoryViewModel,
    transaction: TransactionRecord
): String {
    if (transactionTokenTransfer(transaction) == null) {
        return viewModel.formatTransactionAmount(transaction)
    }
    val feeText = viewModel.formatTransactionFee(transaction)
    val nativeSymbol = feeText.substringAfterLast(' ', "").ifBlank { "" }
    return "0 $nativeSymbol".trim()
}

private fun formatTokenTransferAmount(transfer: TokenTransferDetails): String {
    val amount = BigDecimal(transfer.amount)
        .movePointLeft(transfer.tokenDecimals)
        .stripTrailingZeros()
        .toPlainString()
    return "$amount ${transfer.tokenSymbol}".trim()
}

private fun formatStatusDate(
    viewModel: TransactionHistoryViewModel,
    transaction: TransactionRecord
): String {
        return (viewModel.formatTimelineCompleted(transaction)
            ?: viewModel.formatTimelineSubmitted(transaction)
                ).replace(", ", " · ")
    }

private fun buildGeneralRows(
    transaction: TransactionRecord,
    viewModel: TransactionHistoryViewModel
): List<DetailItem> {
    val rows = mutableListOf(
        DetailItem(R.drawable.ic_amount, "مقدار", formatNativeValue(viewModel, transaction)),
        DetailItem(R.drawable.ic_gas, "کارمزد شبکه", viewModel.formatTransactionFee(transaction))
    )

    if (viewModel.isTransactionFeeDetailsLoading(transaction)) {
        rows[1] = rows[1].copy(loading = true)
    }

    // TASK-52 — for a NATIVE transfer the counterparties weren't rendered anywhere (TokenTransfersCard
    // only exists for token transfers), so there was no address to copy at all. Added here and only
    // here, so token transfers don't end up with the same two rows twice.
    if (transactionTokenTransfer(transaction) == null) {
        transaction.fromAddress?.takeIf { it.isNotBlank() }?.let { from ->
            rows += DetailItem(
                R.drawable.ic_send,
                "ارسال از",
                shortenAddress(from),
                accent = true,
                copyValue = from
            )
        }
        transaction.toAddress?.takeIf { it.isNotBlank() }?.let { to ->
            rows += DetailItem(
                R.drawable.ic_download,
                "ارسال به",
                shortenAddress(to),
                accent = true,
                copyValue = to
            )
        }
    }

    when (transaction) {
        is EvmTransaction -> {
            transaction.gasPrice?.let {
                rows += DetailItem(R.drawable.ic_gas, "قیمت گاز", "${formatGwei(it)} GWEI")
            }
            transaction.gasUsed?.let {
                rows += DetailItem(
                    R.drawable.ic_gas,
                    "مقدار گاز مصرف شده",
                    "${formatGwei(it)} GWEI"
                )
            }
            transaction.nonce?.let {
                rows += DetailItem(R.drawable.ic_nonce, "نانس", it.toString())
            }
            transactionTokenTransfer(transaction)?.let {
                rows += DetailItem(
                    R.drawable.ic_contract_name,
                    "نام قرارداد هوشمند",
                    viewModel.getHistoryAssetTitle(transaction)
                )
            }
            transaction.contractAddress?.takeIf { it.isNotBlank() }?.let {
                rows += DetailItem(
                    R.drawable.ic_contract,
                    "قرارداد هوشمند",
                    shortenAddress(it),
                    accent = true,
                    copyValue = it
                )
            }
        }

        is TronTransaction -> {
            val isLoadingDetails = viewModel.isTransactionFeeDetailsLoading(transaction)
            if (isLoadingDetails && transaction.energyUsed == null) {
                rows += DetailItem(R.drawable.ic_gas, "انرژی مصرف شده", "", loading = true)
            }
            if (isLoadingDetails && transaction.bandwidthUsed == null) {
                rows += DetailItem(
                    R.drawable.ic_bandwidth,
                    "پهنای باند مصرف شده",
                    "",
                    loading = true
                )
            }

            viewModel.formatTronEnergyFee(transaction)?.let {
                rows += DetailItem(R.drawable.ic_gas, "کارمزد انرژی", it)
            }
            viewModel.formatTronNetworkFee(transaction)?.let {
                rows += DetailItem(R.drawable.ic_bandwidth, "کارمزد پهنای باند", it)
            }

            transaction.energyUsed?.let {
                rows += DetailItem(R.drawable.ic_energy, "انرژی مصرف شده", it.toString())
            }
            transaction.bandwidthUsed?.let {
                rows += DetailItem(R.drawable.ic_bandwidth, "پهنای باند مصرف شده", it.toString())
            }
            transactionTokenTransfer(transaction)?.let {
                rows += DetailItem(
                    R.drawable.ic_contract_name,
                    "نام قرارداد هوشمند",
                    viewModel.getHistoryAssetTitle(transaction)
                )
            }
            transaction.contractAddress?.takeIf { it.isNotBlank() }?.let {
                rows += DetailItem(
                    R.drawable.ic_contract,
                    "قرارداد هوشمند",
                    shortenAddress(it),
                    accent = true,
                    copyValue = it
                )
            }
        }

        is BitcoinTransaction -> {
            transaction.feeRateSatsPerByte?.let {
                rows += DetailItem(R.drawable.ic_gas, "نرخ کارمزد", "$it ساتوشی/بایت ")
            }
        }
    }
    rows += DetailItem(
        R.drawable.ic_hash,
        "هش تراکنش",
        shortenAddress(transaction.hash),
        accent = true,
        copyValue = transaction.hash
    )
    return rows
}

private fun transactionTokenTransfer(transaction: TransactionRecord): TokenTransferDetails? {
    return when (transaction) {
        is EvmTransaction -> transaction.tokenTransferDetails
        is TronTransaction -> transaction.tokenTransferDetails
        is BitcoinTransaction -> null
    }
}

/**
 * TASK-56 — an absent/unknown fiat amount used to fall back to `"0.00"`, which reads as a genuine zero
 * receipt. It now shows [FiatConversion.UNKNOWN_PLACEHOLDER], the same placeholder every other fiat
 * surface uses when the تومان rate is not known.
 */
private fun formatReceiptBalanceValue(fiatAmount: String?): String {
    return fiatAmount
        ?.removePrefix("+")
        ?.removePrefix("$")
        ?.removePrefix("-$")
        ?.let { value ->
            if (fiatAmount.startsWith("-$")) "-$value" else value
        }
        ?.takeIf { it.isNotBlank() }
        ?: FiatConversion.UNKNOWN_PLACEHOLDER
}


private fun formatGwei(value: BigInteger): String {
    return BigDecimal(value)
        .movePointLeft(9)
        .stripTrailingZeros()
        .toPlainString()
}
