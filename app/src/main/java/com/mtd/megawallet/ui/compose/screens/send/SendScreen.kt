package com.mtd.megawallet.ui.compose.screens.send

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.HomeUiState
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.viewmodel.HomeViewModel
import com.mtd.megawallet.viewmodel.SendViewModel
import kotlinx.coroutines.launch


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
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val recipientText by sendViewModel.recipientAddress.collectAsStateWithLifecycle()
    val selectedAsset by sendViewModel.selectedAsset.collectAsStateWithLifecycle()
    val amountText by sendViewModel.amountText.collectAsStateWithLifecycle()
    val isFiatMode by sendViewModel.isFiatMode.collectAsStateWithLifecycle()
    val isMaxAmount by sendViewModel.isMaxAmount.collectAsStateWithLifecycle()
    // TASK-56 — واحد فیات انتخاب‌شده؛ باکس مبلغ و معادلِ زیر آن هر دو از این پیروی می‌کنند.
    val fiatCurrency by sendViewModel.fiatCurrency.collectAsStateWithLifecycle()
    val usdToIrrRate by sendViewModel.usdToIrrRate.collectAsStateWithLifecycle()
    val showConfirmScreen by sendViewModel.showConfirmScreen.collectAsStateWithLifecycle()
    val recipientNetworkType by sendViewModel.recipientNetworkType.collectAsStateWithLifecycle()

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
                        val pastedText = ClipboardUtils.getText().toString()
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
                                isFiatMode = isFiatMode,
                                fiatCurrency = fiatCurrency,
                                usdToIrrRate = usdToIrrRate,
                                isMaxAmount = isMaxAmount,
                                isExiting = isAmountExiting,
                                hasValidAddress = hasValidRecipientAddress,
                                onAmountChanged = { sendViewModel.setAmount(it) },
                                onToggleMode = { sendViewModel.toggleFiatMode() },
                                // TASK-56 — MAX is now a ViewModel operation, not a string the screen
                                // assembles. It has to know the selected currency AND flag the entry as
                                // "the whole balance" so the amount that is actually sent is the exact
                                // balance rather than a value parsed back out of a rounded display string.
                                onUseMax = { sendViewModel.useMax(asset) },
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
                                    val tokenItems = remember(
                                        successState.assets, recipientNetworkType, fiatCurrency, usdToIrrRate
                                    ) {
                                        val networkType = recipientNetworkType ?: return@remember emptyList()
                                        buildSendableAssetList(
                                            fiatCurrency = fiatCurrency,
                                            usdToIrrRate = usdToIrrRate,
                                            source = successState.assets,
                                            networkType = networkType,
                                            networkTypeResolver = { homeViewModel.getNetworkTypeForNetworkId(it) }
                                        )
                                    }

                                    if (tokenItems.isEmpty()) {
                                        HintState(text = "دارایی با موجودی در این شبکه یافت نشد")
                                    } else {
                                        TokenList(
                                            fiatCurrency = fiatCurrency,
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
