package com.mtd.megawallet.ui.compose.screens.send

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.gassless.FeeState
import com.mtd.domain.model.gassless.FeeTrend
import com.mtd.domain.model.gassless.GaslessAvailability
import com.mtd.domain.model.gassless.GaslessPreviewState
import com.mtd.domain.model.gassless.SubmitState
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.viewmodel.news.SendViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular

@Composable
fun SendConfirmScreen(
    viewModel: SendViewModel,
    onConfirm: (useGasless: Boolean, selectedOption: FeeOption?) -> Unit
) {
    val asset by viewModel.selectedAsset.collectAsStateWithLifecycle()
    val recipientAddress by viewModel.recipientAddress.collectAsStateWithLifecycle()
    val amountText by viewModel.amountText.collectAsStateWithLifecycle()
    val isUsdMode by viewModel.isUsdMode.collectAsStateWithLifecycle()
    val networkType by viewModel.recipientNetworkType.collectAsStateWithLifecycle()
    val walletName by viewModel.activeWalletName.collectAsStateWithLifecycle()
    val gaslessAvailability by viewModel.gaslessAvailability.collectAsStateWithLifecycle()
    val gaslessPreviewState by viewModel.gaslessPreviewState.collectAsStateWithLifecycle()

    val feeState by viewModel.feeState.collectAsStateWithLifecycle()
    val submitState by viewModel.submitState.collectAsStateWithLifecycle()

    if (asset == null) return

    val baseInputCrypto = remember(asset, amountText, isUsdMode) {
        viewModel.getBaseCryptoAmount(asset!!, amountText, isUsdMode)
    }

    InternalSendConfirmScreen(
        asset = asset!!,
        baseInputCrypto = baseInputCrypto,
        recipientAddress = recipientAddress,
        networkType = networkType,
        walletName = walletName,
        gaslessAvailability = gaslessAvailability,
        gaslessPreviewState = gaslessPreviewState,
        feeOptions = (feeState as? FeeState.Success)?.options ?: emptyList(),
        isLoadingFees = feeState is FeeState.Loading,
        submitState = submitState,
        onBack = { viewModel.setShowConfirmScreen(false) },
        onConfirm = onConfirm,
        viewModel = viewModel
    )
}

@Composable
private fun InternalSendConfirmScreen(
    asset: AssetItem,
    baseInputCrypto: java.math.BigDecimal,
    recipientAddress: String,
    recipientName: String? = null,
    networkType: NetworkType?,
    walletName: String = "کیف پول من",
    gaslessAvailability: GaslessAvailability,
    gaslessPreviewState: GaslessPreviewState,
    feeOptions: List<FeeOption> = emptyList(),
    isLoadingFees: Boolean = false,
    submitState: SubmitState = SubmitState.Idle,
    onBack: () -> Unit,
    onHelp: () -> Unit = {},
    onConfirm: (useGasless: Boolean, selectedOption: FeeOption?) -> Unit,
    viewModel: SendViewModel
) {
    val isGaslessEligible = gaslessAvailability is GaslessAvailability.Available
    var useGasless by remember { mutableStateOf(isGaslessEligible) }
    var selectedFeeIndex by remember { mutableIntStateOf(1) } // Default to Normal (index 1)
    val gaslessPreview = gaslessPreviewState as? GaslessPreviewState.Ready
    val gaslessPreviewReady = gaslessPreviewState is GaslessPreviewState.Ready
    val gaslessPreviewError = (gaslessPreviewState as? GaslessPreviewState.Error)?.message

    val scope = rememberCoroutineScope()

    // Staggered entry
    var headerVisible    by remember { mutableStateOf(false) }
    var detailsVisible   by remember { mutableStateOf(false) }
    var feeVisible       by remember { mutableStateOf(false) }
    var gaslessBannerVis by remember { mutableStateOf(false) }
    var buttonVisible    by remember { mutableStateOf(false) }

    val isSubmitting = submitState is SubmitState.Submitting
    val isSuccess = submitState is SubmitState.Success
    val isProcessing = isSubmitting || isSuccess

    var avatarOffset by remember { mutableStateOf(Offset.Zero) }
    var buttonOffset by remember { mutableStateOf(Offset.Zero) }
    var screenCenter by remember { mutableStateOf(Offset.Zero) }

    var selectedMode by remember { mutableStateOf(FeeMode.DIRECT) }

    val avatarAnimProgress by animateFloatAsState(
        targetValue = if (isProcessing) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label = "avatarAnim"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isProcessing) 0f else 1f,
        animationSpec = tween(400),
        label = "contentAlpha"
    )

    val handleBack = {
        scope.launch {
            buttonVisible = false
            delay(60)
            gaslessBannerVis = false
            feeVisible = false
            delay(60)
            detailsVisible = false
            delay(60)
            headerVisible = false
            delay(200) // Wait for exit animations
            onBack()
        }
    }

    BackHandler(enabled = submitState !is SubmitState.Submitting) { handleBack() }

    LaunchedEffect(Unit, isGaslessEligible) {
        headerVisible    = true;  delay(120)
        detailsVisible   = true;  delay(100)
        feeVisible       = true;  delay(80)
        if (isGaslessEligible) { gaslessBannerVis = true; delay(80) }
        buttonVisible    = true
    }

    LaunchedEffect(isGaslessEligible) {
        if (!isGaslessEligible) {
            useGasless = false
        } else {
            useGasless = true
            viewModel.refreshGaslessPreviewIfNeeded()
        }
    }

    // Inactivity timeout: 5 minutes
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(300_000) // 5 minutes
        Timber.tag("Restart").d("به دلیل انقضای زمانِ امنیتی تراکنش، لطفاً مجدداً تلاش کنید")
        handleBack()
    }

    val displayAddress = remember(recipientAddress) {
        if (recipientAddress.length > 14)
            "${recipientAddress.take(6)}…${recipientAddress.takeLast(6)}"
        else recipientAddress
    }

    val selectedFee = if (!useGasless && feeOptions.isNotEmpty()) {
        feeOptions.getOrNull(selectedFeeIndex) ?: feeOptions.first()
    } else null


    val feeAmountCrypto = remember(selectedFee) {
        selectedFee?.feeInCoin ?: java.math.BigDecimal.ZERO
    }

    val isMax = remember(baseInputCrypto, asset, viewModel) {
        // Extract only the numerical part of the balance (handle "0.0006 ETH" -> "0.0006")
        val cleanBalance = asset.balance.trim().split(" ").firstOrNull()?.replace(",", "") ?: ""
        val formattedAmount = viewModel.formatCryptoFromRaw(asset, baseInputCrypto).replace(",", "")

        formattedAmount == cleanBalance || baseInputCrypto >= asset.balanceRaw
    }

    val effectiveCrypto = remember(isMax, baseInputCrypto, feeAmountCrypto, asset.isNativeToken, useGasless) {
        if (asset.isNativeToken && isMax && !useGasless) {
            asset.balanceRaw - feeAmountCrypto
        } else {
            baseInputCrypto
        }
    }

    val displayCrypto = remember(effectiveCrypto, asset) { viewModel.formatCryptoFromRaw(asset, effectiveCrypto) }
    val displayUsd = remember(effectiveCrypto, asset) { viewModel.formatUsdFromRaw(asset, effectiveCrypto) }
    val displayIrr = remember(effectiveCrypto, asset) { viewModel.formatIrrFromRaw(asset, effectiveCrypto) }

    val isAmountTooSmall = effectiveCrypto <= java.math.BigDecimal.ZERO

    val hasInsufficientBalance = remember(effectiveCrypto, feeAmountCrypto, asset.isNativeToken, useGasless) {
        if (asset.isNativeToken && !useGasless) {
            (effectiveCrypto + feeAmountCrypto) > asset.balanceRaw
        } else {
            effectiveCrypto > asset.balanceRaw
        }
    }

    val submitErrorMessage = (submitState as? SubmitState.Error)?.message

    val canConfirm = !isAmountTooSmall && !hasInsufficientBalance && !isLoadingFees && !isSubmitting
        && effectiveCrypto > java.math.BigDecimal.ZERO
        && if (useGasless) gaslessPreviewReady else selectedFee != null

    LaunchedEffect(submitState) {
        if (submitState is SubmitState.Success) {
            delay(350)
            onConfirm(useGasless, selectedFee)
            viewModel.resetSubmitState()
        }
    }

    // --- Color Animation Logic ---
    val feeTrend by viewModel.feeTrend.collectAsStateWithLifecycle()

    val flashColor = remember(feeTrend) {
        when (feeTrend) {
            FeeTrend.UP -> Color(0xFFFF9800) // Orange
            FeeTrend.DOWN -> Color(0xFF4CAF50) // Green
            FeeTrend.NONE -> null
        }
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = flashColor ?: MaterialTheme.colorScheme.tertiary,
        animationSpec = tween(400),
        label = "primaryFlash"
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = flashColor?.copy(alpha = 0.8f) ?: MaterialTheme.colorScheme.onTertiary,
        animationSpec = tween(400),
        label = "secondaryFlash"
    )
    val contentScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { screenCenter = Offset(it.size.width / 2f, it.size.height / 2f) }
    ) {
        // --- Status Text for Submitting/Success ---
       AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn(tween(400)) + slideInVertically { 20 },
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp) // Below the centered icon
            ) {
                Text(
                    text = if (isSuccess) "تراکنش با موفقیت ارسال شد" else "در حال ارسال تراکنش...",
                    color = if (isSuccess) Color(0xFF34C759) else MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                if (isSubmitting) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "لطفاً چند لحظه منتظر بمانید",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = IranSansRegular,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            StaggeredSection(visible = headerVisible, delayMs = 0) {
                UnifiedHeader(
                    onBack = { handleBack() },
                    modifier = Modifier.alpha(contentAlpha)
                )
            }

            // Recipient info
            StaggeredSection(visible = headerVisible, delayMs = 60) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned {
                                val pos = it.localToWindow(Offset.Zero)
                                avatarOffset = pos.copy(x = pos.x + it.size.width / 2f, y = pos.y + it.size.height / 2f)
                            }
                    ) {
                        val travelX = (screenCenter.x - avatarOffset.x) * avatarAnimProgress
                        val travelY = (screenCenter.y - with(LocalDensity.current) { 120.dp.toPx() } - avatarOffset.y) * avatarAnimProgress

                        RecipientAvatar(
                            asset = asset,
                            recipientName = recipientName,
                            showBadge = !isProcessing,
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = travelX
                                    translationY = travelY
                                    val s = 1f + (0.3f * avatarAnimProgress)
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                    }

                    Column(modifier = Modifier.alpha(contentAlpha)) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "تأیید تراکنش به",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = IranSansRegular,
                            fontSize = 15.sp
                        )
                        Text(
                            text = recipientName ?: displayAddress,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = IranSansBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(contentScrollState)
            ) {
                // Transaction card
                StaggeredSection(visible = detailsVisible, delayMs = 0) {
                    TransactionDetailCard(
                        asset = asset,
                        displayCrypto = displayCrypto,
                        displayUsd = displayUsd,
                        displayIrr = displayIrr,
                        walletName = walletName,
                        isAmountTooSmall = isAmountTooSmall,
                        primaryColor = animatedPrimaryColor,
                        secondaryColor = animatedSecondaryColor,
                        modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Tab Bar
                FeeTabBar(
                    selectedMode = selectedMode,
                    onModeChange = { selectedMode = it },
                    tabStates =  mapOf(
                        FeeMode.DIRECT to TabState.READY,
                        FeeMode.SMART to TabState.LOADING,
                        FeeMode.CREDIT to TabState.DISABLED
                    ),
                    getTabFee = { mode ->
                        when (mode) {
                            FeeMode.DIRECT -> selectedFee?.feeAmountUsdDisplay ?: "..."
                            FeeMode.SMART -> "ETH 1"
                            FeeMode.CREDIT -> "ETH 1"
                        }
                    }
                )



               /* AnimatedVisibility(
                    visible = gaslessBannerVis && isGaslessEligible && !isProcessing,
                    enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it / 2 } + fadeIn(tween(200)),
                    exit = slideOutVertically { it / 2 } + fadeOut(tween(150))
                ) {
                    GaslessBanner(
                        enabled = useGasless,
                        isPreviewReady = gaslessPreviewReady,
                        errorMessage = gaslessPreviewError,
                        previewMessage = gaslessPreview?.smartFee?.reasonFa
                            ?: gaslessPreview?.gaslessPolicy?.reasonFa,
                        onToggle = { useGasless = !useGasless }
                    )
                    Spacer(Modifier.height(12.dp))
                }*/

                // Fee Section
                StaggeredSection(visible = feeVisible, delayMs = 0) {
                    Column(modifier = Modifier.alpha(contentAlpha)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(Modifier.height(12.dp))


                        AnimatedContent(
                            targetState = selectedMode,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200)) + slideInVertically { 8 })
                                    .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically { -8 })
                            },
                            label = "FeeContent"
                        ) { mode ->
                            when (mode) {
                                FeeMode.DIRECT ->{
                                    FeeSection(
                                        feeOptions = feeOptions,
                                        selectedIndex = selectedFeeIndex,
                                        onIndexSelected = { selectedFeeIndex = it },
                                        useGasless = useGasless,
                                        gaslessPreviewState = gaslessPreviewState,
                                        hasInsufficientBalance = hasInsufficientBalance && !isAmountTooSmall,
                                        isAmountTooSmall = isAmountTooSmall,
                                        isLoadingFees = isLoadingFees,
                                        primaryColor = animatedPrimaryColor,
                                        secondaryColor = animatedSecondaryColor
                                    )
                                }
                                FeeMode.SMART ->{
                                    SmartFeeSection(SmartFeeInfo("12","155","212144",""),true)
                                }
                                FeeMode.CREDIT -> {
                                    CreditFeeSection(
                                        info = CreditInfo("12","32423","254235",""),
                                        isLoading = false
                                    )
                                }
                            }
                        }

                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Warning
            StaggeredSection(visible = buttonVisible, delayMs = 0) {
                Box(modifier = Modifier.alpha(contentAlpha)) {
                    BottomSecuritySection(message = "قبل از تأیید، جزئیات را بررسی کنید. تراکنش‌ های بلاکچین برگشت ‌پذیر نیستند")
                }
            }

            // Confirm Button
            StaggeredSection(visible = buttonVisible, delayMs = 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isProcessing) {
                        submitErrorMessage?.let { message ->
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = IranSansRegular,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned {
                                val pos = it.localToWindow(Offset.Zero)
                                buttonOffset = pos.copy(x = pos.x + it.size.width / 2f, y = pos.y + it.size.height / 2f)
                            }
                    ) {
                        ConfirmSliderButton(
                            enabled = canConfirm,
                            text = if (isSubmitting) "در حال ارسال..." else "برای تایید بکشید",
                            isSuccess = isSuccess,
                            isError = submitState is SubmitState.Error,
                            modifier = Modifier.alpha(if (isProcessing && !isSuccess) 0.7f else 1f),
                            onConfirmed = { viewModel.submitTransfer(useGasless, selectedFee, isMax) }
                        )
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(12.dp))
        }
    }
}
