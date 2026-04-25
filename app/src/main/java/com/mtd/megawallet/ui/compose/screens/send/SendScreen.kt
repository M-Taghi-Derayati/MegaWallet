package com.mtd.megawallet.ui.compose.screens.send

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.imageLoader
import com.mtd.common_ui.R
import com.mtd.domain.model.core.NetworkType
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.HomeUiState
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.AnimatedCounter
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getPlaceholderIconResId
import com.mtd.megawallet.viewmodel.news.HomeViewModel
import com.mtd.megawallet.viewmodel.news.SendViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

private enum class SendTab {
    TOKENS,
    COLLECTIBLES
}

@Composable
fun SendScreen(
    homeViewModel: HomeViewModel,
    initialSelectedAssetId: String? = null,
    initialRecipient: String = "",
    onDismiss: () -> Unit,
    onScanClick: () -> Unit = {},
    onAssetSelected: (AssetItem, String) -> Unit = { _, _ -> },
    sendViewModel: SendViewModel = hiltViewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val recipientText by sendViewModel.recipientAddress.collectAsState()
    val selectedAsset by sendViewModel.selectedAsset.collectAsState()
    val amountText by sendViewModel.amountText.collectAsState()
    val isUsdMode by sendViewModel.isUsdMode.collectAsState()
    val showConfirmScreen by sendViewModel.showConfirmScreen.collectAsState()
    val recipientNetworkType by sendViewModel.recipientNetworkType.collectAsState()
    
    val clipboardManager = LocalClipboard.current
    var chooseBalanceAsset by remember { mutableStateOf<AssetItem?>(null) }
    var initialAssetApplied by rememberSaveable { mutableStateOf(false) }

    val recipientAddress = recipientText.trim()
    val hasRecipientAddress = recipientAddress.isNotBlank()
    
    val expectedNetworkType = selectedAsset?.networkId?.let { homeViewModel.getNetworkTypeForNetworkId(it) }
    val hasValidRecipientAddress = recipientNetworkType != null && (expectedNetworkType == null || expectedNetworkType == recipientNetworkType)

    // Initialize initial values
    LaunchedEffect(initialRecipient) {
        if (initialRecipient.isNotBlank() && recipientText.isBlank()) {
            sendViewModel.setRecipient(initialRecipient)
        }
    }

    LaunchedEffect(initialSelectedAssetId, uiState) {
        if (!initialAssetApplied && !initialSelectedAssetId.isNullOrBlank()
            && uiState is HomeUiState.Success) {
            val assets = (uiState as HomeUiState.Success).assets
            // Search in top level and inside groups
            val found = assets.find { it.id == initialSelectedAssetId } 
                ?: assets.filter { it.isGroupHeader }.flatMap { it.groupAssets }.find { it.id == initialSelectedAssetId }
            
            if (found != null) {
                sendViewModel.setSelectedAsset(found)
                initialAssetApplied = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sendViewModel.clearState()
        }
    }

    val scope = rememberCoroutineScope()
    var isAmountExiting by remember { mutableStateOf(false) }
    val isAmountPhase = selectedAsset != null

    val handleBack = {
        if (isAmountPhase && !isAmountExiting) {
            scope.launch {
                isAmountExiting = true
                sendViewModel.setSelectedAsset(null)
                sendViewModel.setAmount("0")
                isAmountExiting = false
            }
        } else if (!isAmountPhase) {
            onDismiss()
        }
    }

    BackHandler { handleBack() }

 /*   AnimatedContent(
        targetState = showConfirmScreen,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(spring(0.85f, 400f)) { it } + fadeIn(tween(250)))
                    .togetherWith(scaleOut(tween(200), 0.96f) + fadeOut(tween(200)))
            } else {
                (scaleIn(tween(200), 0.96f) + fadeIn(tween(250)))
                    .togetherWith(slideOutVertically(tween(300)) { it } + fadeOut(tween(200)))
            }
        }
    ) { isConfirm ->}*/

    if (showConfirmScreen) {
        SendConfirmScreen(
            viewModel = sendViewModel,
            onConfirm = { _, _ ->
                selectedAsset?.let { asset ->
                    onAssetSelected(asset, recipientAddress)
                }
                onDismiss()
            }
        )
        return
    }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit){detectTapGestures { }}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                
                UnifiedHeader(
                    onBack = { handleBack() }, 
                    title = "ارسال",
                    isClose = !isAmountPhase
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                RecipientInputSection(
                    recipientText = recipientText,
                    isValidAddress = hasValidRecipientAddress,
                    onRecipientChanged = { sendViewModel.setRecipient(it) },
                    onPaste = {
                        val pastedText = clipboardManager.nativeClipboard.text.toString()
                        if (pastedText.isNotBlank()) sendViewModel.setRecipient(pastedText)
                    },
                    onClear = { sendViewModel.setRecipient("") },
                    readOnly = isAmountPhase && hasValidRecipientAddress
                )

                Spacer(modifier = Modifier.height(14.dp))

                val successState = uiState as? HomeUiState.Success

                AnimatedContent(
                    targetState = isAmountPhase,
                    transitionSpec = {
                        if (targetState) {
                            // iOS-style: new content springs up from below with slight scale
                            (
                                slideInVertically(
                                    animationSpec = spring(
                                        dampingRatio = 0.78f,
                                        stiffness = 370f
                                    ),
                                    initialOffsetY = { it / 3 }
                                ) + scaleIn(
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 370f),
                                    initialScale = 0.93f
                                ) + fadeIn(tween(220))
                            ).togetherWith(
                                scaleOut(tween(200), targetScale = 1.04f) +
                                fadeOut(tween(180)) +
                                slideOutVertically(tween(200)) { -(it / 8) }
                            )
                        } else {
                            // Returning to selection: slides back down
                            (
                                slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -(it / 6) } +
                                fadeIn(tween(220))
                            ).togetherWith(
                                slideOutVertically(
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                    targetOffsetY = { it / 3 }
                                ) + scaleOut(tween(220), targetScale = 0.94f) +
                                fadeOut(tween(200))
                            )
                        }
                    },
                    label = "PhaseTransition",
                    modifier = Modifier.weight(1f)
                ) { amountPhase ->
                    if (amountPhase) {
                        val snapshotAsset = remember { selectedAsset }
                        snapshotAsset?.let { asset ->
                            AmountInputPhase(
                                asset = asset,
                                amountText = amountText,
                                isUsdMode = isUsdMode,
                                isExiting = isAmountExiting,
                                hasValidAddress = hasValidRecipientAddress,
                                onAmountChanged = { sendViewModel.setAmount(it) },
                                onToggleMode = { sendViewModel.toggleUsdMode() },
                                onUseMax = {
                                    val maxAmount = if (isUsdMode) {
                                        asset.balanceUsdt.replace("$", "").replace(",", "").trim()
                                    } else {
                                        asset.balance.replace(",", "").trim()
                                    }
                                    sendViewModel.setAmount(maxAmount)
                                },
                                onContinue = { sendViewModel.setShowConfirmScreen(true) }
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ScanAddressRow(onScanClick = onScanClick)
                            Spacer(modifier = Modifier.height(18.dp))
                            when {
                                successState == null -> HintState(text = "Loading...")

                                !hasRecipientAddress -> HintState(text = "برای شروع، آدرس مقصد را وارد کنید")
                                !hasValidRecipientAddress -> HintState(text = "آدرس وارد شده معتبر نیست", isError = true)
                                else -> {
                                    val tokenItems = remember(successState.assets, recipientNetworkType) {
                                        val networkType = recipientNetworkType ?: return@remember emptyList()
                                        buildSendableAssetList(
                                            source = successState.assets,
                                            networkType = networkType,
                                            networkTypeResolver = { homeViewModel.getNetworkTypeForNetworkId(it) }
                                        )
                                    }

                                    if (tokenItems.isEmpty()) {
                                        HintState(text = "دارایی با موجودی در این شبکه یافت نشد")
                                    } else {
                                        TokenList(
                                            assets = tokenItems,
                                            selectedAssetId = selectedAsset?.id,
                                            onTokenClick = { asset ->
                                                if (asset.isGroupHeader && asset.groupAssets.size > 1) {
                                                    chooseBalanceAsset = asset
                                                } else {
                                                    sendViewModel.setSelectedAsset(asset)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                    }
                }
            }

            ChooseBalanceBottomSheet(
                asset = chooseBalanceAsset,
                onDismiss = { chooseBalanceAsset = null },
                onNetworkSelected = { selected ->
                    chooseBalanceAsset = null
                    sendViewModel.setSelectedAsset(selected)
                }
            )
        }

}



@Composable
private fun RecipientInputSection(
    recipientText: String,
    isValidAddress: Boolean,
    onRecipientChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    readOnly: Boolean = false
) {
    val hasInput = recipientText.isNotBlank()
    val showInvalidAddressError = recipientText.isNotBlank() && !isValidAddress

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ارسال به",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontSize = 14.sp
                    )
                }



                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (isValidAddress && hasInput) {
                        // Address Chip/Pill mode
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .widthIn(max = 230.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayAddress = if (recipientText.length > 12) {
                                "${recipientText.take(6)}...${recipientText.takeLast(6)}"
                            } else {
                                recipientText
                            }
                            Text(
                                text = displayAddress,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = FontFamily(Font(R.font.inter_regular)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                        }
                    } else {
                        BasicTextField(
                            value = recipientText,
                            onValueChange = onRecipientChanged,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = if (showInvalidAddressError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily(Font(R.font.inter_regular)),
                                textAlign = TextAlign.Right
                            ),
                            decorationBox = { inner ->
                                if (recipientText.isBlank()) {
                                    Text(
                                        text = "آدرس مقصد را وارد کنید",
                                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.5f),
                                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Crossfade(
                    targetState = hasInput,
                    animationSpec = tween(durationMillis = 220),
                    label = "RecipientAction"
                ) { showClear ->
                    if (readOnly) {
                        // Empty box to keep layout consistent but hide buttons
                        Box(modifier = Modifier.width(34.dp))
                    } else if (showClear) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onClear),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onPaste)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "جایگذاری",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            if (showInvalidAddressError) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "آدرس وارد شده معتبر نیست",
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AmountInputPhase(
    asset: AssetItem,
    amountText: String,
    isUsdMode: Boolean,
    isExiting: Boolean,
    hasValidAddress: Boolean,
    onAmountChanged: (String) -> Unit,
    onToggleMode: () -> Unit,
    onUseMax: () -> Unit,
    onContinue: () -> Unit
) {
    // Determine if entered amount exceeds available balance
    val calculationAmount = when {
        amountText.isBlank() || amountText == "0" -> "0"
        amountText == "." -> "0"
        amountText.endsWith(".") -> amountText + "0"
        else -> amountText
    }
    val isOverBalance = remember(calculationAmount, isUsdMode, asset) {
        try {
            val bdVal = BigDecimal(calculationAmount)
            if (bdVal <= BigDecimal.ZERO) false
            else {
                // Determine the exact max string that is displayed in UI based on mode
                val maxStr = if (isUsdMode) asset.balanceUsdt.replace("$", "").replace(",", "").trim()
                             else asset.balance.replace(",", "").trim()
                
                // If they typed exactly what is max formatted, trust it's fine!
                if (calculationAmount == maxStr) false
                else if (isUsdMode) bdVal > asset.balanceRaw.multiply(asset.priceUsdRaw)
                else bdVal > asset.balanceRaw
            }
        } catch (e: Exception) { false }
    }
    val canContinue = !isOverBalance && amountText != "0" && amountText.isNotBlank() && hasValidAddress

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Amount Display (Flexible Space)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isExiting,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 }
            ) {
                AmountDisplaySection(
                    asset = asset,
                    amount = amountText,
                    calculationAmount = calculationAmount,
                    isUsdMode = isUsdMode,
                    isOverBalance = isOverBalance,
                    onToggle = onToggleMode
                )
            }
        }

        // Bottom Section (Fixed Space)
        AnimatedVisibility(
            visible = !isExiting,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AssetInfoCard(
                    asset = asset,
                    onUseMax = onUseMax
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                NumericKeypad(
                    onKeyPress = { key ->
                        val newAmount = when (key) {
                            "del" -> if (amountText.length <= 1) "0" else amountText.dropLast(1)
                            "." -> if (amountText.contains(".")) amountText else if (amountText == "0") "0." else "$amountText."
                            else -> if (amountText == "0") key else amountText + key
                        }
                        onAmountChanged(newAmount)
                    }
                )
                
                Spacer(modifier = Modifier.height(20.dp))



                PrimaryButton("ادامه", onContinue,canContinue,false, Modifier)

            }
        }
    }
}

@Composable
private fun AmountDisplaySection(
    asset: AssetItem,
    amount: String,
    calculationAmount: String,
    isUsdMode: Boolean,
    isOverBalance: Boolean,
    onToggle: () -> Unit
) {
    val price = asset.priceUsdRaw
    val equivalent = remember(calculationAmount, isUsdMode) {
        try {
            val bdVal = BigDecimal(calculationAmount)
            if (isUsdMode) {
                val cryptoVal = bdVal.divide(price, 8, RoundingMode.HALF_UP)
                "${BalanceFormatter.formatBalance(cryptoVal, asset.decimals)} ${asset.symbol}"
            } else {
                val usdVal = bdVal.multiply(price)
                "$${BalanceFormatter.formatUsdValue(usdVal, false)}"
            }
        } catch (e: Exception) { "—" }
    }

    val amountColor = if (isOverBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    val subColor = if (isOverBalance) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Main Amount Row with Slot-Machine Digit Animation ---
        // AnimatedContent keyed on the raw `amount` string:
        // Adding digits  -> new number rolls UP from below
        // Removing digits -> new number rolls DOWN from above
        AnimatedContent(
            targetState = amount,
            transitionSpec = {
                val isGrowing = targetState.length >= initialState.length
                if (isGrowing) {
                    (
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
                            initialOffsetY = { it / 2 }
                        ) + fadeIn(tween(110))
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(90, easing = FastOutSlowInEasing),
                            targetOffsetY = { -(it / 3) }
                        ) + fadeOut(tween(80))
                    )
                } else {
                    (
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
                            initialOffsetY = { -(it / 2) }
                        ) + fadeIn(tween(110))
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(90, easing = FastOutSlowInEasing),
                            targetOffsetY = { it / 3 }
                        ) + fadeOut(tween(80))
                    )
                }
            },
            label = "DigitChange",
            contentAlignment = Alignment.Center
        ) { displayAmount ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayAmount,
                    style = TextStyle(
                        fontSize = 52.sp,
                        color = amountColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily(Font(R.font.inter_bold))
                    ),
                    maxLines = 1,
                    softWrap = false
                )

                AnimatedVisibility(
                    visible = isUsdMode,
                    enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically { it / 2 }
                ) {
                    Text(
                        text = "$",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = WalletScreenConstants.CURRENCY_SYMBOL_FONT_SIZE,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily(Font(R.font.inter_regular, FontWeight.Medium))
                        ),
                        modifier = Modifier.padding(top = WalletScreenConstants.CURRENCY_SYMBOL_PADDING_TOP)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Equivalent / Swap Row ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isUsdMode) {
                val iconRes = getLocalIconResId(asset.symbol).let { if (it == 0) R.drawable.ic_wallet else it }
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = equivalent,
                color = subColor,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.inter_medium))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = subColor
            )
        }

        // --- Insufficient Balance Error ---
        AnimatedVisibility(
            visible = isOverBalance,
            enter = fadeIn(tween(200)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "موجودی کافی نیست",
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    fontSize = 14.sp
                )
            }
        }
    }
}


@Composable
private fun AssetInfoCard(
    asset: AssetItem,
    onUseMax: () -> Unit
) {
    val localIconResId = remember(asset.symbol) {
        getLocalIconResId(asset.symbol)
    }
    val localIconNetworkResId = remember(asset.networkId) {
        getNetworkIconResId(asset.networkId)
    }
    val imageLoader = LocalContext.current.imageLoader
    val placeholderResId = remember { getPlaceholderIconResId() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_SIZE)
            ) {
                // آیکون اصلی ارز
                if (localIconResId != 0) {
                    Box(
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = localIconResId),
                            contentDescription = "${asset.name} icon",
                            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                            contentScale = ContentScale.Fit,
                            colorFilter = null
                        )
                    }
                }
                else {
                    Box(
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = asset.iconUrl,
                            contentDescription = "${asset.name} icon",
                            modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                            contentScale = ContentScale.Fit,
                            placeholder = painterResource(id = placeholderResId),
                            error = painterResource(id = placeholderResId),
                            fallback = painterResource(id = placeholderResId),
                            imageLoader = imageLoader
                        )
                    }
                }

                // بج شبکه (پایین سمت راست)
                if (asset.networkName.isNotEmpty()) {
                    val isDark = isSystemInDarkTheme()
                    Box(
                        modifier = Modifier
                            .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pls),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            tint = if (isDark) Color.Black else Color.White
                        )

                        Image(
                            painter = painterResource(id = localIconNetworkResId),
                            contentDescription = "${asset.networkName} network icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING),
                            contentScale = ContentScale.Fit,
                            colorFilter = null
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.faName?:"",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                )
                Text(
                    text = "${asset.balance} ${asset.symbol}",
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily(Font(R.font.inter_medium))
                )
            }
            
            Surface(
                onClick = onUseMax,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "حداکثر",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
                )
            }
        }
    }
}

@Composable
private fun NumericKeypad(onKeyPress: (String) -> Unit) {
    val keys = listOf(
        listOf("3", "2", "1"),
        listOf("6", "5", "4"),
        listOf("9", "8", "7"),
        listOf("del", "0", ".")
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        keys.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable(indication = null, interactionSource = null){onKeyPress(key)},
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "del") {
                            Icon(
                                imageVector = Icons.Default.Backspace, 
                                contentDescription = "Delete", 
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Text(
                                text = key,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 24.sp,
                                fontFamily = FontFamily(Font(R.font.inter_bold))
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}


@Composable
private fun ScanAddressRow(onScanClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onScanClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_scan),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "QR اسکن کد",
                color = MaterialTheme.colorScheme.tertiary,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium)),
                fontSize = 17.sp,
                textAlign = TextAlign.Right
            )
            Text(
                text = "برای اسکن آدرس ضربه بزنید",
                color = MaterialTheme.colorScheme.onTertiary,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                fontSize = 14.sp,
                textAlign = TextAlign.Right
            )
        }
    }
}

@Composable
private fun HintState(
    text: String,
    isError: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiary,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TokenList(
    assets: List<AssetItem>,
    selectedAssetId: String?,
    onTokenClick: (AssetItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(items = assets, key = { _, it -> it.id }) { index, asset ->
            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                isVisible = true
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(400)),
                label = "StaggeredItem"
            ) {

                AssetListItems(modifier = Modifier,asset,onClick = { onTokenClick(asset) })
            }
        }
    }
}



@Composable
private fun AssetListItems(
    modifier: Modifier = Modifier,
    asset: AssetItem,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val imageLoader = LocalContext.current.imageLoader


    // تلاش برای پیدا کردن آیکون لوکال با فرمت ic_symbol (مثلا ic_btc)
    val localIconResId = remember(asset.symbol) {
        getLocalIconResId(asset.symbol)
    }
    val localIconNetworkResId = remember(asset.networkId) {
        getNetworkIconResId(asset.networkId)
    }

    // جداسازی مقدار و نماد برای انیمیشن
    val balanceAmount = remember(asset.balance, asset.symbol) {
        asset.balance
    }

    Row(
        modifier = modifier // ✅ modifier از بیرون اعمال می‌شود
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        // بخش آیکون‌ها (اصلی + بج شبکه)
        Box(
            modifier = Modifier
                .size(WalletScreenConstants.ASSET_ICON_SIZE)
        ) {
            // آیکون اصلی ارز (Local یا Remote)
            if (localIconResId != 0) {
                Box(
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = localIconResId),
                        contentDescription = "${asset.name} icon",
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentScale = ContentScale.Fit,
                        colorFilter = null
                    )
                }
            }
            else {
                val placeholderResId = remember { getPlaceholderIconResId() }
                Box(
                    modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = asset.iconUrl,
                        contentDescription = "${asset.name} icon",
                        modifier = Modifier.size(WalletScreenConstants.ASSET_ICON_MAIN_SIZE),
                        contentScale = ContentScale.Fit,
                        placeholder = painterResource(id = placeholderResId),
                        error = painterResource(id = placeholderResId),
                        fallback = painterResource(id = placeholderResId),
                        imageLoader = imageLoader
                    )
                }
            }

            // بج شبکه (پایین سمت راست)
            if (asset.networkName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(WalletScreenConstants.ASSET_ICON_NETWORK_SIZE_LARGE)
                        .align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pls),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = if (isDark) Color.Black else Color.White
                    )

                    Image(
                        painter = painterResource(id = localIconNetworkResId),
                        contentDescription = "${asset.networkName} network icon",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WalletScreenConstants.ASSET_ICON_NETWORK_PADDING),
                        contentScale = ContentScale.Fit,
                        colorFilter = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_ICON_SPACING))


        // بخش نام و بالانس نمادین (وسط)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = asset.faName ?: asset.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold)),
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
            )

            // انیمیشن موجودی: عدد خارج می‌شود، نماد جای آن را می‌گیرد
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize() // انیمیشن برای تغییر سایز و مکان
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // نمایش Circle Chart برای گروه‌ها (داخل انیمیشن)

                    // نمایش مقدار عددی
                    AnimatedCounter(
                        text = balanceAmount,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily(
                                Font(
                                    R.font.iransansmobile_fa_regular,
                                    FontWeight.Bold
                                )
                            )
                        ),
                        animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION
                    )
                    Spacer(modifier = Modifier.width(WalletScreenConstants.ASSET_BALANCE_SPACING))
                }

                // نمایش نماد ارز (همیشه ثابت)
                // وقتی عدد حذف شود، این متن به سمت چپ (جای عدد) منتقل می‌شود
                Spacer(modifier = Modifier.width(1.dp))
                Text(
                    text = asset.symbol,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = FontFamily(Font(R.font.inter_bold, FontWeight.Bold))
                    )
                )
            }
        }

        // بخش قیمت و درصد تغییرات (چپ)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.animateContentSize()
        ) {
            // انیمیشن تغییر بین مقدار دلار/تومان و ستاره
            Row(verticalAlignment = Alignment.CenterVertically) {

                AnimatedCounter(
                    text = asset.balanceUsdt.trim(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(
                            Font(
                                R.font.inter_regular,
                                FontWeight.Medium
                            )
                        )
                    ),
                    animationDuration = WalletScreenConstants.ASSET_ANIMATION_DURATION
                )
                Text(
                    text = "$",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = WalletScreenConstants.ASSET_PRICE_SYMBOL_FONT_SIZE,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(
                            Font(
                                R.font.inter_regular,
                                FontWeight.Medium
                            )
                        )
                    ),
                    modifier = Modifier.padding(start = WalletScreenConstants.ASSET_PRICE_SYMBOL_PADDING_END)
                )


            }
        }
    }


}



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
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
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
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                    fontSize = 17.sp
                )
            }

            // Right: Token Icon (diamond/symbol) + Balance
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = asset.balance,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily(Font(R.font.inter_regular)),
                    fontSize = 17.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun buildSendableAssetList(
    source: List<AssetItem>,
    networkType: NetworkType,
    networkTypeResolver: (String) -> NetworkType?
): List<AssetItem> {
    return source.mapNotNull { asset ->
        if (asset.isGroupHeader && asset.groupAssets.isNotEmpty()) {
            val matched = asset.groupAssets.filter { sub ->
                sub.balanceRaw > BigDecimal.ZERO && networkTypeResolver(sub.networkId) == networkType
            }
            when {
                matched.isEmpty() -> null
                matched.size == 1 -> matched.first()
                else -> {
                    val first = matched.first()
                    val totalRaw = matched.fold(BigDecimal.ZERO) { acc, sub -> acc + sub.balanceRaw }
                    val totalUsd = matched.fold(BigDecimal.ZERO) { acc, sub -> acc + (sub.balanceRaw * sub.priceUsdRaw) }
                    val avgPrice = if (totalRaw > BigDecimal.ZERO) {
                        totalUsd.divide(totalRaw, 18, RoundingMode.HALF_UP)
                    } else {
                        BigDecimal.ZERO
                    }

                    asset.copy(
                        id = "send_${asset.id}_${networkType.name}",
                        networkId = "GROUP",
                        networkName = "",
                        networkFaName = null,
                        balance = BalanceFormatter.formatBalance(totalRaw, first.decimals),
                        balanceUsdt = "${BalanceFormatter.formatUsdValue(totalUsd, false)} ",
                        balanceRaw = totalRaw,
                        priceUsdRaw = avgPrice,
                        isGroupHeader = true,
                        groupAssets = matched
                    )
                }
            }
        } else {
            if (asset.balanceRaw <= BigDecimal.ZERO) {
                null
            } else {
                val itemNetworkType = networkTypeResolver(asset.networkId)
                if (itemNetworkType == networkType) asset else null
            }
        }
    }.sortedByDescending { it.balanceRaw * it.priceUsdRaw }
}



@Preview
@Composable
fun previewRecipientInputSection(){
    MaterialTheme {
    }
}

