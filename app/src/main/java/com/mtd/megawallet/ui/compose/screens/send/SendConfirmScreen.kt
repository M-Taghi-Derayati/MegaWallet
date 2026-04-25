package com.mtd.megawallet.ui.compose.screens.send

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mtd.common_ui.R
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.GaslessDisplayPolicy
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.gassless.FeeState
import com.mtd.domain.model.gassless.FeeTrend
import com.mtd.domain.model.gassless.GaslessAvailability
import com.mtd.domain.model.gassless.GaslessPreviewState
import com.mtd.domain.model.gassless.SubmitState
import com.mtd.megawallet.ui.compose.components.BottomSecuritySection
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.ui.compose.screens.wallet.getLocalIconResId
import com.mtd.megawallet.ui.compose.screens.wallet.getNetworkIconResId
import com.mtd.megawallet.viewmodel.news.SendViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SendConfirmScreen(
    viewModel: SendViewModel,
    onConfirm: (useGasless: Boolean, selectedOption: FeeOption?) -> Unit
) {
    val asset by viewModel.selectedAsset.collectAsState()
    val recipientAddress by viewModel.recipientAddress.collectAsState()
    val amountText by viewModel.amountText.collectAsState()
    val isUsdMode by viewModel.isUsdMode.collectAsState()
    val networkType by viewModel.recipientNetworkType.collectAsState()
    val walletName by viewModel.activeWalletName.collectAsState()
    val gaslessAvailability by viewModel.gaslessAvailability.collectAsState()
    val gaslessPreviewState by viewModel.gaslessPreviewState.collectAsState()
    
    val feeState by viewModel.feeState.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    
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
    val gaslessBlockedReason = (gaslessAvailability as? GaslessAvailability.Unavailable)?.reason
    var useGasless by remember { mutableStateOf(isGaslessEligible) }
    var selectedFeeIndex by remember { mutableIntStateOf(1) } // Default to Normal (index 1)

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
        && (useGasless || selectedFee != null)

    LaunchedEffect(submitState) {
        if (submitState is SubmitState.Success) {
            // Give time for the success animation to be seen
            delay(1500)
            onConfirm(useGasless, selectedFee)
            viewModel.resetSubmitState()
        }
    }

    // --- Color Animation Logic ---
    val feeTrend by viewModel.feeTrend.collectAsState()
    
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { screenCenter = Offset(it.size.width / 2f, it.size.height / 2f) }
    ) {
        // --- Status Text for Submitting/Success ---
        androidx.compose.animation.AnimatedVisibility(
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
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                if (isSubmitting) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "لطفاً چند لحظه منتظر بمانید",
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
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
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            fontSize = 15.sp
                        )
                        Text(
                            text = recipientName ?: displayAddress,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Transaction card
            StaggeredSection(visible = detailsVisible, delayMs = 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth().alpha(contentAlpha),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ConfirmDetailRow(
                            label = "ارسال ${asset.faName}",
                            valueLeft = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = getLocalIconResId(asset.symbol)
                                    if (icon != 0) {
                                        Image(painterResource(icon), null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = displayCrypto,
                                        color = if (isAmountTooSmall) MaterialTheme.colorScheme.error else animatedPrimaryColor,
                                        fontFamily = FontFamily(Font(R.font.inter_bold)),
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        ConfirmDetailRow(
                            label = "ارزش کل",
                            valueLeft = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = displayUsd,
                                        color = animatedPrimaryColor,
                                        fontFamily = FontFamily(Font(R.font.inter_bold)),
                                        fontSize = 15.sp
                                    )
                                    if (displayIrr.isNotBlank()) {
                                        Text(
                                            text = "≈ $displayIrr",
                                            color = animatedSecondaryColor,
                                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        ConfirmDetailRow(
                            label = "از کیف‌ پول",
                            value = walletName
                        )
                        Spacer(Modifier.height(12.dp))
                        ConfirmDetailRow(
                            label = "شبکه",
                            valueLeft = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val netIcon = getNetworkIconResId(asset.networkId)
                                    Image(painterResource(netIcon), null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = asset.networkFaName ?: "نامشخص",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Gasless banner
            AnimatedVisibility(
                visible = gaslessBannerVis && isGaslessEligible && !isProcessing,
                enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it / 2 } + fadeIn(tween(200)),
                exit = slideOutVertically { it / 2 } + fadeOut(tween(150))
            ) {
                GaslessBanner(enabled = useGasless, onToggle = { useGasless = !useGasless })
                Spacer(Modifier.height(12.dp))
            }

            AnimatedVisibility(
                visible = !isGaslessEligible && !gaslessBlockedReason.isNullOrBlank() && !isProcessing,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Text(
                    text = gaslessBlockedReason.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Fee Section
            StaggeredSection(visible = feeVisible, delayMs = 0) {
                Column(modifier = Modifier.alpha(contentAlpha)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))
                    
                    if (feeOptions.isEmpty() && !useGasless && !isLoadingFees) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PriorityHigh, null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("دریافت کارمزد با خطا مواجه شد", color = Color.Yellow, fontSize = 13.sp)
                        }
                    } else {
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
                }
            }

            Spacer(Modifier.weight(1f))

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
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
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

@Composable
private fun RecipientAvatar(
    asset: AssetItem, 
    recipientName: String?, 
    modifier: Modifier = Modifier,
    showBadge: Boolean = true
) {
    val context = LocalContext.current
    val imageLoader = remember { coil.ImageLoader(context) }
    Box(modifier = modifier.size(64.dp)) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            val icon = getLocalIconResId(asset.symbol)
            if (icon != 0) {
                Image(painterResource(icon), null, modifier = Modifier.size(56.dp))
            } else if (!asset.iconUrl.isNullOrBlank()) {
                AsyncImage(model = asset.iconUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), imageLoader = imageLoader)
            } else {
                Text("?", color = MaterialTheme.colorScheme.onTertiary, fontSize = 20.sp)
            }
        }
        if (showBadge) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF1C8A3C)).align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(12.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ConfirmDetailRow(label: String, value: String? = null, valueLeft: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label, 
            color = MaterialTheme.colorScheme.onTertiary, 
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (valueLeft != null) valueLeft()
        else if (value != null) {
            Text(text = value, color = MaterialTheme.colorScheme.tertiary, fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)), fontSize = 15.sp)
        }
    }
}

@Composable
private fun GaslessBanner(enabled: Boolean, onToggle: () -> Unit) {
    val borderColor = if (enabled) Color(0xFF6C63FF).copy(0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
    val bgColor = if (enabled) Brush.horizontalGradient(listOf(Color(0xFF2D2560).copy(0.8f), Color(0xFF1A1440).copy(0.6f)))
                 else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bgColor).border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (enabled) Color(0xFF6C63FF).copy(0.25f) else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Bolt, null, tint = if (enabled) Color(0xFF9C8FFF) else MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("ارسال بدون کارمزد ", color = if (enabled) Color(0xFFCBC6FF) else MaterialTheme.colorScheme.tertiary, fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)), fontSize = 13.sp)
                    Text("کارمزد از موجودی توکن کسر می‌شود", color = Color(0xFFFFFFFF), fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)), fontSize = 11.sp)
                }
            }
            GaslessTogglePill(enabled = enabled)
        }
    }
}

@Composable
private fun GaslessTogglePill(enabled: Boolean) {
    val trackColor by animateColorState(if (enabled) Color(0xFF6C63FF) else MaterialTheme.colorScheme.surfaceVariant)
    Box(
        modifier = Modifier.width(42.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(trackColor).padding(3.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color.White))
    }
}

private fun compactCryptoZeros(text: String): String {
    val parts = text.split(" ")
    if (parts.isEmpty()) return text
    var amountStr = parts[0]
    val symbol = parts.drop(1).joinToString(" ")

    // Only compact if there are 4 or more zeros after the decimal
    val regex = Regex("^(0\\.0{4,})(\\d+)$")
    val match = regex.find(amountStr)
    
    if (match != null) {
        val zerosPart = match.groupValues[1]
        val nonZerosPart = match.groupValues[2]
        
        val zeroCount = zerosPart.length - 2
        val subscriptZeros = zeroCount.toString().map { char ->
            when (char) {
                '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
                '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; '9' -> '₉'
                else -> char
            }
        }.joinToString("")

        // Take at most 4 digits from the non-zero part to prevent long overlapping fractions
        val trimmedNonZeros = nonZerosPart.take(4)
        amountStr = "0.0$subscriptZeros$trimmedNonZeros"
    }
    
    return if (symbol.isNotEmpty()) "$amountStr $symbol" else amountStr
}

@Composable
private fun FeeSection(
    feeOptions: List<FeeOption>, 
    selectedIndex: Int, 
    onIndexSelected: (Int) -> Unit, 
    useGasless: Boolean,
    gaslessPreviewState: GaslessPreviewState,
    hasInsufficientBalance: Boolean = false, 
    isAmountTooSmall: Boolean = false,
    isLoadingFees: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.tertiary,
    secondaryColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val selectedOption = if (!useGasless && feeOptions.isNotEmpty()) feeOptions.getOrNull(selectedIndex) ?: feeOptions.first() else null
    val hasError = hasInsufficientBalance || isAmountTooSmall
    val gaslessPreview = gaslessPreviewState as? GaslessPreviewState.Ready
    val gaslessError = gaslessPreviewState as? GaslessPreviewState.Error
    val gaslessAmount = remember(gaslessPreview) {
        gaslessPreview?.gaslessPolicy?.let { policy ->
            listOfNotNull(
                policy.displayAmount?.takeIf { it.isNotBlank() },
                policy.displayToken?.takeIf { token ->
                    token.isNotBlank() && !(policy.displayAmount ?: "").contains(token)
                }
            ).joinToString(" ").ifBlank { null }
        }
    }
    val sponsorAmount = remember(gaslessPreview) {
        gaslessPreview?.sponsorPolicy?.let { policy ->
            listOfNotNull(
                policy.displayAmount?.takeIf { it.isNotBlank() && it != "0" },
                policy.displayToken?.takeIf { token ->
                    token.isNotBlank() && !(policy.displayAmount ?: "").contains(token)
                }
            ).joinToString(" ").ifBlank { null }
        }
    }
    val sponsorBreakdownText = remember(gaslessPreview, sponsorAmount) {
        if (gaslessPreview?.needsApprove != true) return@remember null
        val policy = gaslessPreview.sponsorPolicy
        when (policy?.mode?.lowercase(Locale.US)) {
            "gift" -> "هزینه Approve: هدیه"
            "debt" -> "هزینه Approve: ${sponsorAmount ?: "بعداً از توکن کسر می‌شود"}"
            "disabled" -> "هزینه Approve: با کارمزد شبکه"
            else -> sponsorAmount?.let { "هزینه Approve: $it" }
        }
    }
    val policyMessage = remember(gaslessPreview, gaslessError) {
        when {
            gaslessPreview?.needsApprove == true &&
                !gaslessPreview.sponsorPolicy?.reasonFa.isNullOrBlank() -> gaslessPreview.sponsorPolicy?.reasonFa
            !gaslessPreview?.gaslessPolicy?.reasonFa.isNullOrBlank() -> gaslessPreview?.gaslessPolicy?.reasonFa
            else -> gaslessError?.message
        }
    }

    val isFetchingInitial = if (useGasless) {
        gaslessPreviewState is GaslessPreviewState.Loading
    } else {
        isLoadingFees && selectedOption == null
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (hasError && !useGasless) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!useGasless && feeOptions.isNotEmpty()) {
                    onIndexSelected((selectedIndex + 1) % feeOptions.size)
                }
            }
            .padding(vertical = 6.dp, horizontal = if (hasError) 8.dp else 0.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT SIDE
        Column(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = isFetchingInitial,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "loadingFeeValues"
            ) { loading ->
                if (loading) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.height(20.dp).width(120.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f)))
                        Box(modifier = Modifier.height(16.dp).width(70.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.4f)))
                        Box(modifier = Modifier.height(14.dp).width(90.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)))
                    }
                } else {
                    AnimatedContent(
                        targetState = useGasless to selectedOption,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "FeeAmount"
                    ) { (gasless, option) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // 1. Native Crypto Amount
                            Text(
                                text = if (gasless) compactCryptoZeros(gaslessAmount ?: "...") else compactCryptoZeros(option?.feeAmountDisplay ?: "..."),
                                color = if (gasless) Color(0xFF9C8FFF) else primaryColor,
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                             
                            if (gasless) {
                                gaslessPreview?.gaslessPolicy?.displayUsd?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        color = secondaryColor.copy(alpha = 1f),
                                        fontFamily = FontFamily(Font(R.font.inter_medium)),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                gaslessPreview?.gaslessPolicy?.displayIrr?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = "≈ $it",
                                        color = secondaryColor.copy(alpha = 0.8f),
                                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                GaslessPolicyDetails(
                                    gaslessPolicy = gaslessPreview?.gaslessPolicy,
                                    sponsorPolicy = if (gaslessPreview?.needsApprove == true) gaslessPreview.sponsorPolicy else null,
                                    fallbackMessage = policyMessage
                                )
                            } else if (option != null) {
                                // 2. USD Equivalent
                                Text(
                                    text = option.feeAmountUsdDisplay,
                                    color = secondaryColor.copy(alpha = 1f),
                                    fontFamily = FontFamily(Font(R.font.inter_medium)),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                // 3. IRR Equivalent
                                Text(
                                    text = "≈ ${option.feeAmountIrrDisplay}",
                                    color = secondaryColor.copy(alpha = 0.8f),
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            AnimatedContent(targetState = hasError and !useGasless, label = "error") { isErr ->
                if (isErr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isAmountTooSmall) "مقدار ارسالی کمتر از کارمزد است" else "موجودی کافی نیست",
                            color = MaterialTheme.colorScheme.error, 
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)), 
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (useGasless) "کارمزد گس‌لس" else "تخمین کارمزد",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // RIGHT SIDE
        AnimatedVisibility(visible = !useGasless, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(targetState = isFetchingInitial, label = "LevelBlock") { loading ->
                        if (loading) {
                            Box(modifier = Modifier.height(18.dp).width(50.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f)))
                        } else {
                            AnimatedContent(targetState = selectedOption?.level, label = "Level") { level ->
                                Text(
                                    text = level ?: "نامشخص",
                                    color = primaryColor,
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(targetState = isFetchingInitial, label = "TimeBlock") { loading ->
                         if (loading) {
                             Box(modifier = Modifier.height(14.dp).width(35.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.4f)))
                         } else {
                            AnimatedContent(targetState = selectedOption?.estimatedTime, label = "Time") { time ->
                                Text(
                                    text = time ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                                    fontSize = 12.sp
                                )
                            }
                         }
                    }
                }
                
                Spacer(Modifier.width(10.dp))
                
                VerticalFeeIndicator(selectedIndex = selectedIndex, totalOptions = if (feeOptions.isEmpty()) 1 else feeOptions.size, isLoading = isFetchingInitial)
            }
        }
    }
}

@Composable
private fun GaslessPolicyDetails(
    gaslessPolicy:GaslessDisplayPolicy?,
    sponsorPolicy: GaslessDisplayPolicy?,
    fallbackMessage: String?
) {
    if (gaslessPolicy == null && sponsorPolicy == null && fallbackMessage.isNullOrBlank()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6C63FF).copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF8E84FF),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "جزئیات هزینه گس‌لس",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "نمایش نحوه پرداخت هزینه برای تراکنش و Approve",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            fontSize = 10.sp
                        )
                    }
                }
                PolicyBadge(
                    text = buildString {
                        if (gaslessPolicy != null) append("تراکنش")
                        if (gaslessPolicy != null && sponsorPolicy != null) append(" + ")
                        if (sponsorPolicy != null) append("Approve")
                    },
                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    content = MaterialTheme.colorScheme.primary
                )
            }

            gaslessPolicy?.let {
                GaslessPolicyItem(
                    title = "هزینه خود تراکنش",
                    subtitle = "کارمزدی که برای اجرای گس‌لس محاسبه شده",
                    policy = it
                )
            }

            if (gaslessPolicy != null && sponsorPolicy != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
                    thickness = 0.8.dp
                )
            }

            sponsorPolicy?.let {
                GaslessPolicyItem(
                    title = "وضعیت Approve",
                    subtitle = "نحوه پرداخت هزینه Approve قبل از ارسال",
                    policy = it
                )
            }

            fallbackMessage?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    thickness = 0.8.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GaslessPolicyItem(
    title: String,
    subtitle: String,
    policy: GaslessDisplayPolicy
) {
    val visual = remember(policy.mode) { policyVisualStyle(policy.mode) }
    val amount = remember(policy) {
        listOfNotNull(
            policy.displayAmount?.takeIf { it.isNotBlank() && it != "0" },
            policy.displayToken?.takeIf { token ->
                token.isNotBlank() && !(policy.displayAmount ?: "").contains(token)
            }
        ).joinToString(" ").ifBlank {
            when (policy.mode?.lowercase(Locale.US)) {
                "gift" -> "هدیه"
                else -> null
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = visual.container.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, visual.container.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(visual.container.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = visual.container,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                            fontSize = 11.sp
                        )
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                PolicyBadge(
                    text = visual.label,
                    background = visual.container.copy(alpha = 0.16f),
                    content = visual.container
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    amount?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily(Font(R.font.inter_medium)),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val meta = listOfNotNull(
                        policy.displayUsd?.takeIf { it.isNotBlank() },
                        policy.displayIrr?.takeIf { it.isNotBlank() }?.let { "≈ $it" }
                    )
                    if (meta.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            meta.forEach { item ->
                                PolicyMetaChip(text = item)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "نحوه پرداخت",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontSize = 9.sp
                    )
                    Text(
                        text = when {
                            policy.willDeductFromUser == false -> "پرداخت توسط سرویس"
                            policy.deductSource.equals("token", ignoreCase = true) -> "کسر از خود توکن"
                            policy.deductSource.equals("native", ignoreCase = true) -> "کارمزد شبکه"
                            else -> "نامشخص"
                        },
                        color = visual.container,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                        fontSize = 10.sp
                    )
                }
            }

            policy.reasonFa?.takeIf { it.isNotBlank() }?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.container,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontSize = 10.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyMetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class PolicyVisualStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color
)

private fun policyVisualStyle(mode: String?): PolicyVisualStyle {
    return when (mode?.lowercase(Locale.US)) {
        "gift" -> PolicyVisualStyle(
            label = "هدیه",
            icon = Icons.Default.Check,
            container = Color(0xFF24C07A)
        )
        "debt" -> PolicyVisualStyle(
            label = "بدهی",
            icon = Icons.Default.Schedule,
            container = Color(0xFFFFA726)
        )
        "disabled" -> PolicyVisualStyle(
            label = "بدون اسپانسر",
            icon = Icons.Default.Info,
            container = Color(0xFFFF6B6B)
        )
        "user_pays" -> PolicyVisualStyle(
            label = "کسر از کاربر",
            icon = Icons.Default.Bolt,
            container = Color(0xFF7C8BFF)
        )
        else -> PolicyVisualStyle(
            label = "نامشخص",
            icon = Icons.Default.PriorityHigh,
            container = Color(0xFF8F96A3)
        )
    }
}

@Composable
private fun PolicyBadge(
    text: String,
    background: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun VerticalFeeIndicator(selectedIndex: Int, totalOptions: Int, isLoading: Boolean = false) {
    val dotsCount = maxOf(3, totalOptions)
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in (dotsCount - 1) downTo 0) {
                val isSelected = (i == selectedIndex) || (selectedIndex > 2 && i == 2)
                
                val size by animateDpAsState(if (isSelected) 18.dp else 5.dp, label = "dotSize")
                val color = when (i) {
                    2 -> Color(0xFFFF7043)
                    1 -> Color(0xFFFFCA28)
                    0 -> Color(0xFF29B6F6)
                    else -> Color.Gray
                }
                
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected || isLoading, 
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) // just keep dot colored without icon
                        } else {
                            Icon(
                                imageVector = when(i){
                                    2 -> Icons.Default.Bolt
                                    1 -> Icons.Default.Check
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmSliderButton(
    enabled: Boolean,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "برای تایید بکشید",
    loadingDurationMs: Long = 2000L,
    isSuccess: Boolean = false,
    isError: Boolean = false
) {
    val density = LocalDensity.current
    val thumbSizeDp = 56.dp
    val trackHeight = 64.dp
    val trackPadding = 4.dp

    var fullTrackWidthDp by remember { mutableStateOf(0.dp) }
    var trackWidthPx by remember { mutableIntStateOf(0) }

    val maxOffsetPx = remember(trackWidthPx) {
        (trackWidthPx - with(density) {
            (thumbSizeDp + trackPadding * 2).toPx()
        }).coerceAtLeast(0f)
    }

    val offsetX = remember { Animatable(0f) }
    var sliderState by remember { mutableStateOf(SliderState.Idle) }
    val scope = rememberCoroutineScope()

    // ───── عرض انیمیشنی ترک ─────
    val animatedTrackWidth by animateDpAsState(
        targetValue = when (sliderState) {
            SliderState.Collapsing,
            SliderState.Loading,
            SliderState.Success -> trackHeight
            else -> fullTrackWidthDp
        },
        animationSpec = when (sliderState) {
            SliderState.Collapsing -> tween(500, easing = FastOutSlowInEasing)
            else -> tween(400, easing = FastOutSlowInEasing)
        },
        label = "trackWidth"
    )

    // ✅ جایگزین مطمئن برای finishedListener
    LaunchedEffect(sliderState) {
        when (sliderState) {
            SliderState.Collapsing -> {
                delay(550)
                if (sliderState == SliderState.Collapsing) {
                    sliderState = SliderState.Loading
                    onConfirmed() // Trigger actual submission once collapsed
                }
            }
            else -> {}
        }
    }

    // Monitor external success state
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            sliderState = SliderState.Success
        }
    }

    // Monitor external error state or manual reset
    LaunchedEffect(isError) {
        if (isError && (sliderState == SliderState.Loading || sliderState == SliderState.Collapsing)) {
            sliderState = SliderState.Idle
            offsetX.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow))
        }
    }

    LaunchedEffect(enabled) {
        if (enabled && sliderState == SliderState.Success) {
            delay(1000)
            sliderState = SliderState.Idle
            offsetX.snapTo(0f)
        }
    }

    val trackColor by animateColorAsState(
        targetValue = when (sliderState) {
            SliderState.Success -> Color(0xFF34C759)
            SliderState.Loading -> Color(0xFFE8E8ED)
            else -> if (enabled) Color(0xFFF2F2F7) else Color(0xFFE8E8E8)
        },
        animationSpec = tween(400),
        label = "trackColor"
    )

    val dragProgress = if (maxOffsetPx > 0f) {
        (offsetX.value / maxOffsetPx).coerceIn(0f, 1f)
    } else 0f

    val textAlpha by animateFloatAsState(
        targetValue = when (sliderState) {
            SliderState.Idle -> 1f
            SliderState.Dragging -> (1f - dragProgress * 1.5f).coerceAtLeast(0f)
            else -> 0f
        },
        animationSpec = tween(200),
        label = "textAlpha"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (sliderState == SliderState.Success) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkScale"
    )


    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(trackHeight)
                .onSizeChanged {
                    trackWidthPx = it.width
                    fullTrackWidthDp = with(density) { it.width.toDp() }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(animatedTrackWidth)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(trackColor),
                contentAlignment = Alignment.Center
            ) {

                if (sliderState == SliderState.Idle ||
                    sliderState == SliderState.Dragging
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(
                                with(density) {
                                    (offsetX.value + (thumbSizeDp + trackPadding * 2).toPx()).toDp()
                                }
                            )
                            .clip(RoundedCornerShape(trackHeight / 2))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF007AFF).copy(alpha = 0.08f),
                                        Color(0xFF007AFF).copy(alpha = 0.18f)
                                    )
                                )
                            )
                    )
                }

                // Text + Chevrons
                if ((sliderState == SliderState.Idle ||
                            sliderState == SliderState.Dragging) && enabled
                ) {

                    CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Rtl
                    ) {
                        Row(
                            modifier = Modifier
                                .alpha(textAlpha)
                                .padding(end = thumbSizeDp + trackPadding * 2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chevronTransition = rememberInfiniteTransition(label = "chevrons")
                            repeat(3) { index ->
                                val chevronAlpha by chevronTransition.animateFloat(
                                    initialValue = 0.15f,
                                    targetValue = 0.7f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, delayMillis = index * 150),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "chevron_${index}"  // âœ… fixed interpolation
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = null,
                                    tint = Color(0xFF8E8E93).copy(alpha = chevronAlpha),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .offset(x = ((-6) * index).dp)
                                )
                            }

                            Text(
                                text = text,
                                color = Color(0xFF8E8E93),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                            )
                        }
                    }
                }


                if ((sliderState == SliderState.Idle ||
                            sliderState == SliderState.Dragging) && !enabled
                ) {
                    Text(
                        text = text,
                        color = Color(0xFFAEAEB2),
                        fontSize = 15.sp,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontWeight = FontWeight.Medium
                    )
                }


                if (sliderState == SliderState.Loading) {
                    val loadingTransition = rememberInfiniteTransition(label = "loading")
                    val spinAngle by loadingTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1100, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spin"
                    )
                    val sweepAngle by loadingTransition.animateFloat(
                        initialValue = 30f, targetValue = 270f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "sweep"
                    )
                    Canvas(modifier = Modifier.size(30.dp)) {
                        val strokeWidth = 3.dp.toPx()
                        val diameter = min(size.width, size.height)
                        val rect = Size(diameter - strokeWidth, diameter - strokeWidth)
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        drawArc(
                            color = Color(0xFF007AFF),
                            startAngle = spinAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = rect,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }


                if (sliderState == SliderState.Success) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "تأیید شد",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).scale(checkScale)
                    )
                }


                if (sliderState == SliderState.Idle ||
                    sliderState == SliderState.Dragging
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(trackPadding)
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .size(thumbSizeDp)
                            .shadow(8.dp, CircleShape,
                                ambientColor = Color(0xFF007AFF),
                                spotColor = Color(0xFF007AFF))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (enabled) {
                                        listOf(Color(0xFF007AFF), Color(0xFF0055D4))
                                    } else {
                                        listOf(Color(0xFFBBBBBB), Color(0xFF999999))
                                    }
                                )
                            )
                            .then(
                                if (enabled) {
                                    Modifier.pointerInput(maxOffsetPx) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                sliderState = SliderState.Dragging
                                            },
                                            onDragEnd = {
                                                scope.launch {
                                                    if (offsetX.value >= maxOffsetPx * 0.85f) {
                                                        offsetX.animateTo(
                                                            maxOffsetPx,
                                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                                        )
                                                        sliderState = SliderState.Collapsing
                                                    } else {
                                                        offsetX.animateTo(
                                                            0f,
                                                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                                                        )
                                                        sliderState = SliderState.Idle
                                                    }
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    offsetX.snapTo(
                                                        (offsetX.value + dragAmount)
                                                            .coerceIn(0f, maxOffsetPx)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "بکشید",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredSection(visible: Boolean, delayMs: Int = 0, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { it / 4 } + fadeIn(tween(250)), exit = fadeOut(tween(150))) { content() }
}

@Composable
private fun animateColorState(target: Color): androidx.compose.runtime.State<Color> = androidx.compose.animation.animateColorAsState(targetValue = target, animationSpec = tween(220), label = "cAnim")



/**
 * وضعیت‌های مختلف اسلایدر
 */
private enum class SliderState {
    Idle,       // آماده کشیدن
    Dragging,   // در حال کشیدن
    Collapsing, // جمع شدن به دایره
    Loading,    // نمایش پراگرس دایره‌ای چرخان
    Success     // تیک سبز ✅
}

@Preview
@Composable
fun sliderpreview(){
    MaterialTheme{
        ConfirmSliderButton(enabled = true, onConfirmed = {})
    }
}






