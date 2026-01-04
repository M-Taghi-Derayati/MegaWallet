package com.mtd.megawallet.ui.compose.animations

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.R as common
import com.mtd.megawallet.R
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import com.mtd.megawallet.ui.compose.components.SeedPhraseGrid
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BackupAnimationState {
    IDLE, PROCESSING, SUCCESS
}

/**
 * Complex animation component for wallet generation/import process.
 * Shows animated card with grid lines, particles, and circular reveal effect.
 */
@Composable
fun GeneratingAnimation(
    targetColor: Color,
    walletName: String,
    seedWords: List<String>,
    walletAddressEVM: String = "",
    walletAddressBTC: String = "",
    isImportMode: Boolean = false,
    viewModel: CreateWalletViewModel,
    isFlipped: Boolean,
    onFlippedChange: (Boolean) -> Unit,
    backupState: BackupAnimationState = BackupAnimationState.IDLE,
    totalBalance: String = "0.00",
    onCloudBackupClick: () -> Unit = {},
    onManualBackupClick: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    isRestoreMode: Boolean = false
) {
    // اگر در حال پردازش بک‌آپ هستیم یا قبلاً انیمیشن اجرا شده، حالت "برگشت" فعال است
    // اما در restore mode، همیشه از ابتدا انیمیشن را نمایش می‌دهیم
    val isReturning = if (isRestoreMode) {
        false // در restore mode همیشه از ابتدا شروع می‌کنیم
    } else {
        backupState != BackupAnimationState.IDLE || viewModel.isAnimationFinished
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "GeneratingTask")
    var startAnim by remember { mutableStateOf(isReturning) }
    
    LaunchedEffect(Unit) { 
        if (!isReturning) {
            startAnim = true 
        }
    }

    val initialDuration = if (isReturning) 0 else AnimationConstants.GENERATING_ANIMATION_DURATION

    // ۱. انیمیشن فاصله گرفتن متن‌ها و رشد کارت
    val textPadding by animateDpAsState(
        targetValue = if (startAnim) 24.dp else 0.dp,
        animationSpec = tween(
            durationMillis = initialDuration,
            easing = FastOutSlowInEasing
        ),
        label = "TextPadding"
    )

    // اطلاع‌رسانی به ViewModel وقتی انیمیشن به مرحله نمایش محتوا رسید
    LaunchedEffect(startAnim) {
        if (startAnim && !isReturning) {
            // تاخیر معادل کل زمان انیمیشن لودینگ (LineDraw + Reveal)
            delay((AnimationConstants.LINE_DRAW_DELAY + AnimationConstants.LINE_DRAW_DURATION + AnimationConstants.REVEAL_ANIMATION_DELAY).toLong())
            viewModel.markAnimationFinished()
        }
    }

    val cardHeight by animateDpAsState(
        targetValue = if (startAnim) AnimationConstants.CARD_HEIGHT_FINAL else AnimationConstants.CARD_HEIGHT_INITIAL,
        animationSpec = tween(
            durationMillis = initialDuration,
            easing = FastOutSlowInEasing
        ),
        label = "CardHeight"
    )

    // ۲. انیمیشن رسم خطوط شبکه
    val lineDrawProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isReturning) 0 else AnimationConstants.LINE_DRAW_DURATION,
            delayMillis = if (isReturning) 0 else AnimationConstants.LINE_DRAW_DELAY
        ),
        label = "LineDrawing"
    )

    // ۳. انیمیشن حاشیه (Border)
    val borderAlpha by animateFloatAsState(
        targetValue = if (startAnim && lineDrawProgress > AnimationConstants.BORDER_ALPHA_THRESHOLD) 1f else 0f,
        animationSpec = tween(durationMillis = AnimationConstants.BORDER_ANIMATION_DURATION),
        label = "BorderAlpha"
    )

    // ۴. انیمیشن ظهور دایره‌ای (Circular Reveal)
    val revealProgress by animateFloatAsState(
        targetValue = if (startAnim && (lineDrawProgress == 1f || isReturning)) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isReturning) 0 else AnimationConstants.REVEAL_ANIMATION_DURATION,
            delayMillis = if (isReturning) 0 else AnimationConstants.REVEAL_ANIMATION_DELAY
        ),
        label = "Reveal"
    )

    // ۵. انیمیشن پالس (ضربان قلب)
    val pulseScale by animateFloatAsState(
        targetValue = if (revealProgress > 0.1f && revealProgress < AnimationConstants.REVEAL_PROGRESS_THRESHOLD) {
            AnimationConstants.PULSE_SCALE_MAX
        } else {
            AnimationConstants.PULSE_SCALE_MIN
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Pulse"
    )

    // انیمیشن ظهور محتویات کارت (بعد از اتمام ریویل)
    val contentAlpha by animateFloatAsState(
        targetValue = if (revealProgress > AnimationConstants.CONTENT_ALPHA_THRESHOLD) 1f else 0f,
        animationSpec = tween(durationMillis = AnimationConstants.CONTENT_ALPHA_DURATION),
        label = "ContentAlpha"
    )

    // انیمیشن فاز برای حرکت درخشش دور دکمه
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationConstants.DASH_PHASE_DURATION,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "DashPhase"
    )

    // انیمیشن حرکت ذرات
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationConstants.PARTICLE_ANIMATION_DURATION,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticleProgress"
    )

    // انیمیشن فلیپ (چرخش کارت) و جابجایی عمودی با Animatable برای کنترل دقیق ورود
    val rotationAnim = remember { 
        Animatable(if (isReturning && isFlipped) 180f else 0f) 
    }
    val verticalOffsetAnim = remember { 
        // کارت فقط در صورتی در ابتدا بالا باشد که واقعاً در حالت فلیپ یا پردازش باشد
        Animatable(if (isReturning && (isFlipped || backupState != BackupAnimationState.IDLE)) AnimationConstants.VERTICAL_OFFSET_FLIPPED else 0f) 
    }

    // استفاده از derivedStateOf برای بهینه‌سازی محاسبات
    val isReady by remember {
        derivedStateOf { 
            revealProgress > AnimationConstants.REVEAL_PROGRESS_THRESHOLD 
        }
    }
    
    val currentGridAlpha by remember {
        derivedStateOf { 
            (1f - revealProgress) 
        }
    }

    LaunchedEffect(isFlipped, backupState, isReady) {
        val targetRotation = when {
            backupState == BackupAnimationState.PROCESSING || backupState == BackupAnimationState.SUCCESS -> 0f
            isFlipped -> 180f
            else -> 0f
        }
        val targetVerticalOffset = when {
            // حذف isReturning از اینجا؛ فقط فلیپ یا شروع فرآیند پشتبیان‌گیری باعث بالا رفتن می‌شود
            isFlipped && backupState == BackupAnimationState.IDLE -> AnimationConstants.VERTICAL_OFFSET_FLIPPED
            else -> 0f
        }

        launch {
            rotationAnim.animateTo(
                targetRotation,
                animationSpec = tween(AnimationConstants.CARD_FLIP_DURATION, easing = FastOutSlowInEasing)
            )
        }
        launch {
            verticalOffsetAnim.animateTo(
                targetVerticalOffset,
                animationSpec = tween(
                    durationMillis = AnimationConstants.VERTICAL_OFFSET_ANIMATION_DURATION,
                    // تأخیر برای مکث در مرکز صفحه بعد از زدن دکمه پشتیبان‌گیری
                    delayMillis = if (backupState == BackupAnimationState.IDLE && !isReturning) 0 else AnimationConstants.VERTICAL_OFFSET_ANIMATION_DELAY,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val rotation = rotationAnim.value
    val verticalOffset = verticalOffsetAnim.value.dp

    // انیمیشن جابجایی گرادینت برای افکت رنگین‌کمانی
    val rainbowShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationConstants.RAINBOW_SHIFT_DURATION,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "RainbowShift"
    )

    val density = LocalDensity.current
    val gridColor = remember { Color.Gray.copy(alpha = AnimationConstants.GRID_COLOR_ALPHA) }
    
    // محاسبات بهینه‌شده برای Canvas
    val gridStepPx = remember(density) { 
        with(density) { AnimationConstants.GRID_STEP_DP.toPx() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ۱. گروه کارت و متن (حرکت به بالا با graphicsLayer برای جلوگیری از ری‌لایت و پرش)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationY = verticalOffset.toPx()
            }
        ) {
            // متن بالا
            AnimatedContent(
                targetState = when {
                    backupState == BackupAnimationState.SUCCESS -> 4
                    backupState == BackupAnimationState.PROCESSING -> 3
                    isFlipped -> 2
                    revealProgress > AnimationConstants.REVEAL_PROGRESS_THRESHOLD -> 1
                    else -> 0
                },
                transitionSpec = {
                    (fadeIn(tween(AnimationConstants.FADE_DURATION)) + expandVertically(tween(AnimationConstants.FADE_DURATION)))
                        .togetherWith(fadeOut(tween(AnimationConstants.FADE_DURATION)))
                },
                label = "TopTextChange"
            ) { phase ->
                when (phase) {
                    4 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Text(
                                text = if (isRestoreMode) 
                                    "کیف پول شما با موفقیت بازیابی شد" 
                                else 
                                    "کیف پول شما با موفقیت ایمن شد",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.ExtraBold))
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(AnimationConstants.TEXT_SPACING))
                            Text(
                                text = "اطلاعات پشتیبان در گوگل‌ درایو شما ذخیره گردید.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily(Font(common.font.vazirmatn_medium, FontWeight.Light)),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    3 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Text(
                                text = if (isRestoreMode) 
                                    "در حال بازیابی کیف پول شما" 
                                else 
                                    "در حال ایمن‌سازی اطلاعات شما",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.ExtraBold))
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(AnimationConstants.TEXT_SPACING))
                            Text(
                                text = if (isRestoreMode) 
                                    "لطفاً تا اتمام فرآیند بازیابی منتظر بمانید..." 
                                else 
                                    "لطفاً تا اتمام فرآیند پشتیبان‌گیری ابری منتظر بمانید...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily(Font(common.font.vazirmatn_medium, FontWeight.Light)),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    2 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            Text(
                                text = "عبارت بازیابی خود را یادداشت کنید",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.ExtraBold))
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(AnimationConstants.TEXT_SPACING))
                            Text(
                                text = "این کلید ها برای دیدن شما است لطفا این موارد را با کسی اشتراک نگذارید",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily(Font(common.font.vazirmatn_medium, FontWeight.Light)),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    1 -> {
                        val rainbowBrush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8A2BE2),
                                Color(0xFF0077FF),
                                Color(0xFF8A2BE2)
                            ),
                            start = Offset(rainbowShift, 0f),
                            end = Offset(rainbowShift + AnimationConstants.RAINBOW_GRADIENT_OFFSET, AnimationConstants.RAINBOW_GRADIENT_OFFSET),
                            tileMode = TileMode.Mirror
                        )

                        val readyText = buildAnnotatedString {
                            append("اکنون کیف پول شما ")
                            withStyle(SpanStyle(brush = rainbowBrush)) {
                                append("آماده")
                            }
                            append(" است")
                        }

                        Text(
                            text = readyText,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.ExtraBold))
                            ),
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = when {
                                isRestoreMode -> "در حال بازیابی کیف پول شما"
                                isImportMode -> "در حال بازیابی کیف پول شما"
                                else -> "در حال ایجاد کیف پول شما"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.Normal)),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = textPadding)
                        )
                    }
                }
            }

            // کارت اصلی
            Box(
                modifier = Modifier
                    .width(AnimationConstants.CARD_WIDTH)
                    .height(cardHeight)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density.density
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .border(
                        width = 1.dp,
                        color = Color.Gray.copy(alpha = borderAlpha * 0.3f),
                        shape = RoundedCornerShape(AnimationConstants.CARD_CORNER_RADIUS)
                    )
            ) {
                // محاسبه maxRadius برای reveal animation (خارج از Canvas برای بهینه‌سازی)
                val cardSize = with(density) {
                    androidx.compose.ui.geometry.Size(
                        AnimationConstants.CARD_WIDTH.toPx(),
                        cardHeight.toPx()
                    )
                }
                val maxRadius = remember(cardSize.width, cardSize.height) {
                    kotlin.math.sqrt(cardSize.width * cardSize.width + cardSize.height * cardSize.height) / 
                    AnimationConstants.REVEAL_RADIUS_MULTIPLIER
                }
                
                // ۱. لایه انیمیشن (Canvas)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val gridStep = gridStepPx

                    val clipPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(0f, 0f, width, height),
                                cornerRadius = CornerRadius(AnimationConstants.CARD_CORNER_RADIUS.toPx())
                            )
                        )
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(clipPath)

                    if (lineDrawProgress > 0f) {
                        // بهینه‌سازی: استفاده از Path به جای حلقه‌های while برای کاهش تعداد draw calls
                        val gridAlpha = gridColor.copy(alpha = gridColor.alpha * currentGridAlpha)
                        val drawLenVertical = height * lineDrawProgress
                        val drawLenHorizontal = width * lineDrawProgress
                        val strokeWidth = 1.dp.toPx()
                        
                        // ساخت Path برای خطوط عمودی (یکباره)
                        val verticalPath = Path().apply {
                            var x = 0f
                            while (x <= centerX) {
                                moveTo(centerX + x, centerY - drawLenVertical / 2)
                                lineTo(centerX + x, centerY + drawLenVertical / 2)
                                if (x > 0) {
                                    moveTo(centerX - x, centerY - drawLenVertical / 2)
                                    lineTo(centerX - x, centerY + drawLenVertical / 2)
                                }
                                x += gridStep
                            }
                        }
                        
                        // رسم خطوط عمودی با یک drawPath
                        drawPath(
                            path = verticalPath,
                            color = gridAlpha,
                            style = Stroke(width = strokeWidth)
                        )
                        
                        // ساخت Path برای خطوط افقی (یکباره)
                        val horizontalPath = Path().apply {
                            var y = 0f
                            while (y <= centerY) {
                                moveTo(centerX - drawLenHorizontal / 2, centerY + y)
                                lineTo(centerX + drawLenHorizontal / 2, centerY + y)
                                if (y > 0) {
                                    moveTo(centerX - drawLenHorizontal / 2, centerY - y)
                                    lineTo(centerX + drawLenHorizontal / 2, centerY - y)
                                }
                                y += gridStep
                            }
                        }
                        
                        // رسم خطوط افقی با یک drawPath
                        drawPath(
                            path = horizontalPath,
                            color = gridAlpha,
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    if (lineDrawProgress > 0.5f && revealProgress < 1f) {
                        val pAlpha = (1f - revealProgress)
                        val particleColor = targetColor.copy(alpha = pAlpha)

                        val verticalOffsets = listOf(-gridStep, 0f, gridStep)
                        verticalOffsets.forEach { offX ->
                            val pY = ((particleProgress + (offX / width)) % 1f) * height
                            drawCircle(particleColor, 2.5.dp.toPx(), Offset(centerX + offX, pY))
                        }

                        val horizontalOffsets = listOf(-gridStep, gridStep)
                        horizontalOffsets.forEach { offY ->
                            val pX = ((particleProgress + (offY / height)) % 1f) * width
                            drawCircle(particleColor, 2.5.dp.toPx(), Offset(pX, centerY + offY))
                        }
                    }

                    if (revealProgress > 0f) {
                        val currentRadius = maxRadius * revealProgress

                        drawCircle(
                            color = targetColor,
                            radius = currentRadius,
                            center = Offset(centerX, centerY)
                        )

                        if (revealProgress > AnimationConstants.BORDER_ALPHA_THRESHOLD) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = (revealProgress - AnimationConstants.BORDER_ALPHA_THRESHOLD) * 2f),
                                size = size,
                                cornerRadius = CornerRadius(AnimationConstants.CARD_CORNER_RADIUS.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    drawContext.canvas.restore()
                }

                // ۲. لایه محتویات کارت
                if (contentAlpha > 0f) {
                    if (rotation <= 90f) {
                        // --- روی کارت (اطلاعات ولت) ---
                        WalletCardFront(
                            walletAddressEVM = walletAddressEVM,
                            walletAddressBTC = walletAddressBTC,
                            walletName = walletName,
                            totalBalance = totalBalance,
                            targetColor = targetColor,
                            contentAlpha = contentAlpha,
                            phase = phase,
                            backupState = backupState,
                            onBackupClick = { onFlippedChange(true) },
                            isRestoreMode = isRestoreMode,
                            isImportMode = isImportMode
                        )
                    } else {
                        // --- پشت کارت (کلمات بازیابی) ---
                        WalletCardBack(
                            seedWords = seedWords,
                            viewModel = viewModel,
                            contentAlpha = contentAlpha
                        )
                    }
                }
            }

            // چیپ راهنما زیر کارت (فقط در حالت عادی، نه restore mode)
            AnimatedVisibility(
                visible = contentAlpha > AnimationConstants.CONTENT_ALPHA_THRESHOLD && !isFlipped && !isRestoreMode,
                enter = fadeIn() + expandVertically()
            ) {
                BackupTipChip()
            }

            // متن وضعیت در پایین (فقط در فاز اولیه)
            AnimatedVisibility(
                visible = revealProgress <= AnimationConstants.REVEAL_PROGRESS_THRESHOLD,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = if (isRestoreMode) 
                        "در حال بازیابی کیف پول شما" 
                    else 
                        "در حال تولید کلیدهای رمزنگاری",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily(Font(common.font.vazirmatn_regular, FontWeight.Light)),
                    modifier = Modifier.padding(top = textPadding),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }

            // متن وضعیت زیر کارت (در حین پردازش و موفقیت)
            // در restore mode این متن نمایش داده نمی‌شود
            AnimatedVisibility(
                visible = (backupState == BackupAnimationState.PROCESSING || backupState == BackupAnimationState.SUCCESS) && !isRestoreMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut()
            ) {
                Text(
                    text = if (backupState == BackupAnimationState.PROCESSING) 
                        "در حال آپلود فایل پشتیبان به گوگل درایو..." 
                    else 
                        "فایل پشتیبان با موفقیت ذخیره شد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontFamily = FontFamily(Font(common.font.vazirmatn_regular, FontWeight.Normal)),
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            }
        }

        // ۲. گزینه‌های پشتیبان‌گیری (نمایش بعد از چرخش کارت)
        AnimatedVisibility(
            visible = isFlipped && backupState == BackupAnimationState.IDLE,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = AnimationConstants.BACKUP_OPTIONS_BOTTOM_SPACING),
            enter = fadeIn(tween(AnimationConstants.BACKUP_OPTIONS_ANIMATION_DURATION, delayMillis = AnimationConstants.BACKUP_OPTIONS_ANIMATION_DELAY)) + 
                    slideInVertically(tween(AnimationConstants.BACKUP_OPTIONS_ANIMATION_DURATION, delayMillis = AnimationConstants.BACKUP_OPTIONS_ANIMATION_DELAY)) { it / 2 }
        ) {
            BackupOptionsContent(
                onCloudBackupClick = onCloudBackupClick,
                onManualBackupClick = onManualBackupClick
            )
        }

        // ۴. دکمه نمایش کیف پول (در انتهای فرآیند موفق)
        AnimatedVisibility(
            visible = backupState == BackupAnimationState.SUCCESS,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 2 }
        ) {
            Button(
                onClick = onNavigateToHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = targetColor
                )
            ) {
                Text(
                    text = "مشاهده کیف پول من",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.Bold))
                    ),
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Front side of wallet card showing wallet information.
 */
@Composable
private fun WalletCardFront(
    walletAddressEVM: String,
    walletAddressBTC: String,
    walletName: String,
    totalBalance: String,
    targetColor: Color,
    contentAlpha: Float,
    phase: Float,
    backupState: BackupAnimationState,
    onBackupClick: () -> Unit,
    isRestoreMode: Boolean = false,
    isImportMode: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .graphicsLayer { alpha = contentAlpha }
    ) {
        // آدرس کیف پول EVM
        WalletAddressRow(
            address = walletAddressEVM,
            iconRes = R.drawable.ic_eth_light
        )

        Spacer(modifier = Modifier.height(10.dp))

        // آدرس کیف پول BTC
        WalletAddressRow(
            address = walletAddressBTC,
            iconRes = R.drawable.ic_btc_light
        )

        Spacer(modifier = Modifier.weight(1f))

        // ردیف پایین: دکمه و اطلاعات
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // دکمه پشتیبان‌گیری
            BackupButton(
                targetColor = targetColor,
                contentAlpha = contentAlpha,
                phase = phase,
                backupState = backupState,
                onClick = onBackupClick,
                isRestoreMode = isRestoreMode
            )

            // نام ولت و موجودی
            WalletInfoColumn(
                walletName = walletName,
                totalBalance = totalBalance,
                isLoading = (isRestoreMode || isImportMode) && totalBalance == "..."
            )
        }
    }
}

/**
 * Back side of wallet card showing seed phrase or private key.
 */
@Composable
private fun WalletCardBack(
    seedWords: List<String>,
    viewModel: CreateWalletViewModel,
    contentAlpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .graphicsLayer {
                alpha = contentAlpha
                rotationY = 180f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (seedWords.isEmpty()) {
            Text(
                text = "کلید خصوصی شما",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontFamily = FontFamily(Font(common.font.vazirmatn_medium, FontWeight.Light))
            )
            Spacer(modifier = Modifier.height(10.dp))
            val privateKey = when (val data = viewModel.importData) {
                is ImportData.PrivateKey -> data.key
                else -> ""
            }
            Text(
                text = privateKey,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Start,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            SeedPhraseGrid(
                words = seedWords,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Row showing wallet address with icon.
 */
@Composable
private fun WalletAddressRow(
    address: String,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = if (address.isNotEmpty()) "${address.take(6)}...${address.takeLast(6)}" else "...",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Image(
            painter = painterResource(iconRes),
            contentDescription = "Wallet Icon",
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Backup button with animated border.
 */
@Composable
private fun BackupButton(
    targetColor: Color,
    contentAlpha: Float,
    phase: Float,
    backupState: BackupAnimationState,
    onClick: () -> Unit,
    isRestoreMode: Boolean = false
) {
    val buttonBgColor by animateColorAsState(
        targetValue = if (backupState == BackupAnimationState.SUCCESS) targetColor else Color.White,
        label = "ButtonBg"
    )
    val buttonContentColor by animateColorAsState(
        targetValue = if (backupState == BackupAnimationState.SUCCESS) Color.White else targetColor,
        label = "ButtonContent"
    )

    Box(contentAlignment = Alignment.Center) {
        // ۱. لایه حاشیه (Dashed / Solid Border) - حالا داینامیک است و با اندازه دکمه ست می‌شود
        if (contentAlpha > 0.9f && backupState != BackupAnimationState.PROCESSING) {
            val isSuccess = backupState == BackupAnimationState.SUCCESS
            Canvas(modifier = Modifier.matchParentSize()) {
                // در حالت موفقیت، حاشیه دقیقاً روی لبه دکمه (بدون فاصله) قرار می‌گیرد
                val expansion = if (isSuccess) 0.dp.toPx() else AnimationConstants.BUTTON_BORDER_EXPANSION.toPx()
                val extendedSize = size.copy(
                    width = size.width + expansion * 2,
                    height = size.height + expansion * 2
                )
                val capsulePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                offset = Offset(-expansion, -expansion),
                                size = extendedSize
                            ),
                            cornerRadius = CornerRadius(extendedSize.height / 2f)
                        )
                    )
                }
                
                // در حالت عادی لایه پس‌زمینه محو حاشیه را می‌کشیم
                if (!isSuccess) {
                    drawPath(
                        path = capsulePath,
                        color = Color.White.copy(alpha = 0.3f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // رسم حاشیه اصلی (خط‌چین برای انتظار، خط ممتد برای موفقیت)
                drawPath(
                    path = capsulePath,
                    color = Color.White.copy(alpha = if (isSuccess) 0.8f else 1f),
                    style = Stroke(
                        width = (if (isSuccess) AnimationConstants.BACKUP_BUTTON_SUCCESS_STROKE_WIDTH else AnimationConstants.BACKUP_BUTTON_NORMAL_STROKE_WIDTH).toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (isSuccess) null else PathEffect.dashPathEffect(floatArrayOf(AnimationConstants.DASH_PATH_INTERVAL_1, AnimationConstants.DASH_PATH_INTERVAL_2), phase)
                    )
                )
            }
        }

        // ۲. دکمه اصلی
        Button(
            onClick = onClick,
            enabled = backupState == BackupAnimationState.IDLE,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonBgColor,
                contentColor = buttonContentColor,
                disabledContainerColor = buttonBgColor,
                disabledContentColor = buttonContentColor
            ),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = AnimationConstants.BACKUP_BUTTON_HORIZONTAL_PADDING, vertical = AnimationConstants.BACKUP_BUTTON_VERTICAL_PADDING),
            modifier = Modifier
                .height(AnimationConstants.BACKUP_BUTTON_HEIGHT)
                .animateContentSize() // انیمیشن تغییر عرض دکمه در حالت‌های مختلف
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // آیکون در حال پردازش
                AnimatedVisibility(
                    visible = backupState == BackupAnimationState.PROCESSING,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AnimationConstants.BACKUP_BUTTON_ICON_SIZE),
                            color = buttonContentColor,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                // آیکون موفقیت
                AnimatedVisibility(
                    visible = backupState == BackupAnimationState.SUCCESS,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(AnimationConstants.BACKUP_BUTTON_ICON_SIZE)
                                .border(AnimationConstants.BACKUP_BUTTON_BORDER_WIDTH, Color.White, CircleShape)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Text(
                    text = when {
                        isRestoreMode -> when(backupState) {
                            BackupAnimationState.IDLE -> "بازیابی"
                            BackupAnimationState.PROCESSING -> "در حال بازیابی"
                            BackupAnimationState.SUCCESS -> "بازیابی شد"
                        }
                        else -> when(backupState) {
                            BackupAnimationState.IDLE -> "پشتیبان‌گیری"
                            BackupAnimationState.PROCESSING -> "در حال ذخیره"
                            BackupAnimationState.SUCCESS -> "ایمن شد"
                        }
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.ExtraBold)),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/**
 * Column showing wallet name and balance.
 */
@Composable
private fun WalletInfoColumn(
    walletName: String,
    totalBalance: String,
    isLoading: Boolean = false
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = walletName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.Normal)),
            fontSize = 20.sp
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 4.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.8f)
            )
        } else {
            Text(
                text = "$totalBalance تتر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.8f),
                fontFamily = FontFamily(Font(common.font.vazirmatn_regular, FontWeight.Normal)),
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Tip chip showing backup instructions.
 */
@Composable
private fun BackupTipChip() {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier
            .padding(top = 16.dp)
            .width(AnimationConstants.CARD_WIDTH)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.size(width = AnimationConstants.BACKUP_TIP_ARROW_WIDTH, height = AnimationConstants.BACKUP_TIP_ARROW_HEIGHT)) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color = Color(AnimationConstants.BACKUP_TIP_CHIP_COLOR))
            }
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(AnimationConstants.CARD_CORNER_RADIUS))
                .background(Color(AnimationConstants.BACKUP_TIP_CHIP_COLOR))
                .padding(AnimationConstants.BACKUP_TIP_CHIP_PADDING)
                .fillMaxWidth()
        ) {
            Text(
                text = "بیایید از ولت شما پشتیبان بگیریم!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.Normal)),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AnimationConstants.BACKUP_TIP_CHIP_SPACING))
            Text(
                text = "«پشتیبان‌گیری» به معنای ذخیره کلمات بازیابی در مکانی امن و تحت کنترل شماست تا دارایی‌تان همیشه ایمن بماند.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = FontFamily(Font(common.font.vazirmatn_regular, FontWeight.Normal)),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BackupOptionsContent(
    onCloudBackupClick: () -> Unit,
    onManualBackupClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "برای شروع کار با ولت، لطفاً ابتدا یک روش پشتیبان‌گیری امن انتخاب کنید.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily(Font(common.font.vazirmatn_medium, FontWeight.Light)),
            modifier = Modifier.padding(bottom = 22.dp)
        )

        val lightBlue = Color(AnimationConstants.CLOUD_BACKUP_COLOR)
        BackupOptionItem(title = "پشتیبان‌گیری ابری", badge = "پیشنهادی", description = "عبارت بازیابی خود را با یک رمز عبور در گوگل درایو ذخیره کنید.", icon = Icons.Default.Cloud, isPopular = true, themeColor = lightBlue, onClick = onCloudBackupClick)
        Spacer(modifier = Modifier.height(AnimationConstants.BACKUP_OPTIONS_SPACING))
        BackupOptionItem(title = "پشتیبان‌گیری دستی", badge = "پیشرفته", description = "عبارت بازیابی خود را در مکانی امن یادداشت و نگهداری کنید.", icon = Icons.Default.Edit, isPopular = false, themeColor = Color.Gray, onClick = onManualBackupClick)
        
        Spacer(modifier = Modifier.height(AnimationConstants.BACKUP_OPTIONS_BOTTOM_SPACING))
        Text(
            text = "شما باید حداقل یکی از روش‌های بالا را برای امنیت دارایی خود انتخاب کنید",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            fontSize = AnimationConstants.BACKUP_OPTIONS_WARNING_SIZE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily(Font(common.font.vazirmatn_medium, FontWeight.Light)),
        )
    }
}

@Composable
private fun BackupOptionItem(
    title: String,
    badge: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPopular: Boolean,
    themeColor: Color,
    onClick: () -> Unit
) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(width = if (isPopular) AnimationConstants.BACKUP_OPTION_POPULAR_BORDER_WIDTH else AnimationConstants.BACKUP_OPTION_NORMAL_BORDER_WIDTH, color = if (isPopular) themeColor else Color.LightGray.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(AnimationConstants.BACKUP_OPTION_ITEM_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(AnimationConstants.BACKUP_OPTION_ITEM_ICON_SIZE).clip(CircleShape).background(if (isPopular) themeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = if (isPopular) themeColor else MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.Bold)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isPopular) themeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = AnimationConstants.BACKUP_OPTION_BADGE_HORIZONTAL_PADDING, vertical = AnimationConstants.BACKUP_OPTION_BADGE_VERTICAL_PADDING)) {
                        Text(text = badge, style = MaterialTheme.typography.labelSmall, color = if (isPopular) themeColor else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AnimationConstants.BACKUP_OPTION_BADGE_TEXT_SIZE, fontFamily = FontFamily(Font(common.font.vazirmatn_bold, FontWeight.Bold)))
                    }
                }
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AnimationConstants.BACKUP_OPTION_DESCRIPTION_TEXT_SIZE, modifier = Modifier.padding(top = 4.dp), fontFamily = FontFamily(Font(common.font.vazirmatn_regular, FontWeight.Normal)))
            }
        }

}
