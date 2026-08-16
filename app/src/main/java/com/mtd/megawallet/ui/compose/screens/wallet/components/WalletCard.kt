package com.mtd.megawallet.ui.compose.screens.wallet.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansLight
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.domain.model.core.Wallet
import com.mtd.megawallet.ui.compose.components.AnimatedFlipCard
import com.mtd.megawallet.ui.compose.components.FlipCardTargets
import com.mtd.megawallet.ui.compose.components.PrivateKeyWallet
import com.mtd.megawallet.ui.compose.components.SeedPhraseGrid

/** ضلعی که کارت در پایانِ جمع‌شدن به آن می‌رسد — اول ارتفاع، بعد عرض. */
private val COLLAPSE_TARGET = 5.dp

/** مدتِ جمع‌شدنِ ارتفاعِ کارت تا خط. */
private const val COLLAPSE_DURATION_MS = 900

/** مدتِ جمع‌شدنِ عرضِ خط تا یک مربعِ کوچک. */
private const val SHRINK_DURATION_MS = 550

/** مدتِ محوشدنِ مربع. */
private const val COLLAPSE_FADE_MS = 420

/**
 * محتوای داخلِ کارت تا این کسر از جمع‌شدنِ عمودی باید کامل رفته باشد.
 *
 * زودتر از خودِ کارت می‌رود: اگر تا آخر بماند، متن در ارتفاعِ کم بریده می‌شود و لحظهٔ آخر
 * شلوغ به نظر می‌رسد.
 */
private const val CONTENT_FADE_AT = 0.35f

private val PremiumSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 380f
)

private val PremiumSpringDp = spring<androidx.compose.ui.unit.Dp>(
    dampingRatio = 0.82f,
    stiffness = 380f
)


@Composable
fun WalletCard(
    wallet: Wallet,
    balance: String,
    isActive: Boolean,
    isExpanded: Boolean,
    isAnyOtherExpanded: Boolean,
    rootCoordinates: LayoutCoordinates?,
    isManualBackedUp: Boolean,
    isCloudBackedUp: Boolean,
    isPersonalizing: Boolean = false,
    isEditingNickname: Boolean = false,
    editName: String = "",
    editColor: Color = Color.Unspecified,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onSettingsClick: () -> Unit,
    onNameChange: (String) -> Unit = {},
    onEditNicknameToggle: () -> Unit = {},
    hideActions: Boolean = false,
    isRevealingSecret: Boolean = false,
    isBackupSuccess: Boolean = false,
    secretData: String = "",
    focusRequester: FocusRequester? = null,
    /**
     * فلوی حذف. از نظرِ حرکت و اندازه دقیقاً مثلِ «افشای عبارت» است — همان کارت به وسطِ صفحه
     * می‌رود — پس حالتِ تازه‌ای در همان `transition` است، نه کارتِ دومی که جای این را بگیرد.
     */
    isRemoving: Boolean = false,
    /** مرحلهٔ «در حال حذف»: جای نشانِ پشتیبان، اسپینر و متن می‌نشیند. */
    isRemovalInProgress: Boolean = false,
    /**
     * ۰ تا ۱ — کم‌شدنِ **ارتفاعِ واقعیِ** کارت تا یک خطِ باریک. عرض دست نمی‌خورد.
     *
     * فقط هدف است، نه مقدارِ لحظه‌ای: انیمیتِ آن داخلِ همین کامپوننت انجام می‌شود.
     */
    removalCollapse: Float = 0f,
    /**
     * ۰ تا ۱ — گامِ بعدی: کم‌شدنِ **عرضِ** خط تا یک مربعِ کوچک.
     *
     * جدا از [removalCollapse] است تا دو حرکت پشتِ سر هم بیایند نه با هم؛ فراخوان ترتیب را
     * تعیین می‌کند.
     */
    removalShrink: Float = 0f,
    /**
     * محوشدنِ **کلِ** کارت، پس از رسیدن به خط.
     *
     * ⚠️ به `FlipCardTargets.contentAlpha` می‌رود که نامش گمراه‌کننده است: روی `alpha`ِ کلِ
     * لایه می‌نشیند، نه محتوای داخل. برای همین این‌جا مرحلهٔ آخر است و نه پیش از جمع‌شدن.
     *
     * مثلِ [removalCollapse] فقط هدف است؛ انیمیتش همین‌جا انجام می‌شود.
     */
    removalAlpha: Float = 1f,
    /**
     * رنگِ پس‌زمینه را بازنویسی می‌کند (فلوی حذف قرمز می‌فرستد). از همان `animateColorAsState`
     * همیشگی رد می‌شود، پس گذارِ رنگ همراهِ خودِ حرکت انجام می‌شود نه جدا از آن.
     */
    overrideColor: Color? = null
) {
    val density = LocalDensity.current
    var cardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val transition = updateTransition(
        targetState = when {
            // اول از همه: حذف بر هر حالتِ دیگری غلبه می‌کند.
            isRemoving -> "removing"
            isBackupSuccess -> "success"
            isRevealingSecret -> "revealing"
            isExpanded -> "expanded"
            else -> "collapsed"
        },
        label = "wallet_morph"
    )

    // ۱. انیمیشن رشد واقعی متناسب با عرض صفحه
    val screenWidthDp =
        if (rootCoordinates != null) with(density) { rootCoordinates.size.width.toDp() } else 360.dp
    val targetWidth = screenWidthDp - 48.dp
    val mnemonicExtraHeight = if (isRevealingSecret && wallet.hasMnemonic) 40.dp else 0.dp
    val targetHeight = targetWidth * 0.61f + mnemonicExtraHeight // نسبت ابعاد استاندارد کارت بانکی
    val collapsedWidth = (screenWidthDp - 10.dp) / 2
    val collapsedHeight = collapsedWidth * 0.71f

    val cardWidth by transition.animateDp(
        label = "width",
        transitionSpec = { PremiumSpringDp }
    ) { state ->
        when (state) {
            "revealing", "success", "removing" -> targetWidth
            "expanded" -> targetWidth
            else -> collapsedWidth
        }
    }

    val cardHeight by transition.animateDp(
        label = "height",
        transitionSpec = { PremiumSpringDp }
    ) { state ->
        when (state) {
            "revealing", "success", "removing" -> targetHeight
            "expanded" -> targetHeight
            else -> collapsedHeight
        }
    }

    val translationY by transition.animateFloat(
        label = "y",
        transitionSpec = { PremiumSpring }
    ) { state ->
        if (cardCoords != null && rootCoordinates != null) {
            val currentY = rootCoordinates.localPositionOf(cardCoords!!, Offset.Zero).y
            when (state) {
                "revealing", "success", "removing" -> {
                    val screenHeight = rootCoordinates.size.height
                    val currentHeight =
                        with(density) { cardHeight.toPx() } // استفاده از اندازه زمان حال
                    (screenHeight / 2f) - (currentY + currentHeight / 2f)
                }

                "expanded" -> {
                    val baseTargetY = if (isPersonalizing) 220.dp else 150.dp
                    val targetY = with(density) { baseTargetY.toPx() }
                    targetY - currentY
                }

                else -> 0f
            }
        } else 0f
    }

    // یک انیمیشنِ رنگ و نه بیشتر. رنگِ حذف از بیرون می‌آید و از همین مسیر رد می‌شود، پس
    // برگشتنش به رنگِ خودِ کیف‌پول هم خودبه‌خود انیمیت می‌شود.
    val animatedBgColor by animateColorAsState(
        targetValue = when {
            overrideColor != null -> overrideColor
            isPersonalizing -> editColor
            else -> Color(wallet.color)
        },
        animationSpec = tween(500),
        label = "card_color"
    )
    // فاصله تا وسطِ صفحه، بر اساسِ جایگاهِ لحظه‌ایِ کارت.
    val liveCenterOffsetX = if (cardCoords != null && rootCoordinates != null) {
        val currentX = rootCoordinates.localPositionOf(cardCoords!!, Offset.Zero).x
        val currentWidth = cardCoords!!.size.width
        (rootCoordinates.size.width / 2f) - (currentX + currentWidth / 2f)
    } else 0f

    // ⚠️ در فلوی حذف این مقدار **قفل** می‌شود.
    //
    // مرحلهٔ بندها کارت را خودش جابه‌جا نمی‌کند؛ کلِ ستونِ کیف‌پول‌ها با یک `graphicsLayer` به
    // چپ می‌رود. ولی `localPositionOf` جابه‌جاییِ لایه‌های بالادست را هم حساب می‌کند، و هر
    // layout pass ای که وسطِ آن حرکت بیفتد (ورود و خروجِ بندها، عوض‌شدنِ چیپ با اسپینر)
    // `cardCoords` را دوباره گزارش می‌کند — این بار با آفستِ ستون تویش.
    //
    // نتیجه: هدف چند صد dp می‌پرید و `PremiumSpring` (با `dampingRatio` ۰.۸۲ که از هدف رد
    // می‌شود) دنبالش می‌دوید. همان حرکتِ شلاقی. از منحنیِ خودِ ستون نمی‌آمد، برای همین
    // عوض‌کردنِ easing هیچ‌وقت درستش نکرد.
    val lastStableCenterX = remember { mutableFloatStateOf(liveCenterOffsetX) }
    SideEffect { if (!isRemoving) lastStableCenterX.floatValue = liveCenterOffsetX }
    val centerOffsetX = if (isRemoving) lastStableCenterX.floatValue else liveCenterOffsetX

    val translationX by transition.animateFloat(
        label = "x",
        transitionSpec = { PremiumSpring }
    ) { state ->
        if (cardCoords != null && rootCoordinates != null) {
            when (state) {
                "expanded", "revealing", "success", "removing" -> centerOffsetX
                else -> 0f
            }
        } else 0f
    }


    val otherAlpha by animateFloatAsState(
        targetValue = if (isAnyOtherExpanded) 0f else 1f,
        animationSpec = PremiumSpring,
        label = "other_alpha"
    )


    // انیمیشن محو شدن حاشیه (بوردر) همزمان با گسترش
    val borderAlpha by transition.animateFloat(
        label = "border_alpha",
        transitionSpec = { PremiumSpring }
    ) { if (it == "collapsed") 1f else 0f }

    // جمع‌شدنِ پایانی.
    //
    // ⚠️ [AnimatedFlipCard] این‌جا با `animate = false` صدا زده می‌شود، یعنی هیچ‌کدام از مقدارهای
    // `targets` را خودش انیمیت نمی‌کند و `animationSpec`اش بی‌اثر است. بقیهٔ مقدارها از
    // `transition`ِ همین فایل می‌آیند و برای همین نرم‌اند؛ هر مقداری که این‌جا انیمیت نشود،
    // ناگهانی می‌پرد.
    val collapseProgress by animateFloatAsState(
        targetValue = removalCollapse.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = COLLAPSE_DURATION_MS, easing = FastOutSlowInEasing),
        label = "removal_collapse"
    )
    val shrinkProgress by animateFloatAsState(
        targetValue = removalShrink.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = SHRINK_DURATION_MS, easing = FastOutSlowInEasing),
        label = "removal_shrink"
    )
    // خودِ **ابعاد** کم می‌شوند، نه `scaleX`/`scaleY`: مقیاس فقط تصویرِ همان کارتِ بزرگ را
    // می‌فشارد، ولی خواسته این است که کارت واقعاً کوچک شود.
    val collapsedCardHeight = cardHeight + (COLLAPSE_TARGET - cardHeight) * collapseProgress
    val collapsedCardWidth = cardWidth + (COLLAPSE_TARGET - cardWidth) * shrinkProgress
    // کارت از **بالای** [Box]ِ بیرونی چیده می‌شود، پس با کم‌شدنِ ارتفاع مرکزش به بالا می‌رفت.
    // نصفِ ارتفاعِ از دست رفته را برمی‌گردانیم تا خط دقیقاً وسطِ صفحه بماند.
    // برای عرض چنین چیزی لازم نیست: چینش `TopCenter` است، پس افقی از قبل وسط می‌ماند.
    val collapseOffsetY = with(density) { (cardHeight - collapsedCardHeight).toPx() } / 2f

    // محتوا زودتر از خودِ کارت می‌رود و به `removalShrink` کاری ندارد: تا رسیدن به خط،
    // دیگر چیزی برای دیده‌شدن نمانده است.
    val contentFade = (1f - collapseProgress / CONTENT_FADE_AT).coerceIn(0f, 1f)

    val fadeAlpha by animateFloatAsState(
        targetValue = removalAlpha.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = COLLAPSE_FADE_MS),
        label = "removal_alpha"
    )

    val rotationY by transition.animateFloat(
        label = "rotation_y",
        transitionSpec = { tween(800, easing = FastOutSlowInEasing) }
    ) { if (it == "revealing") 180f else 0f }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .aspectRatio(1.3f)
            .onGloballyPositioned { cardCoords = it }
            .zIndex(if (isExpanded) 1000f else 1f)
    ) {
        AnimatedFlipCard(
            targets = FlipCardTargets(
                width = collapsedCardWidth,
                height = collapsedCardHeight,
                offsetX = translationX,
                offsetY = translationY + collapseOffsetY,
                rotationY = rotationY,
                cornerRadius = 15.dp,
                cornerRadiusBoarder = 21.dp,
                contentAlpha = if (isExpanded || isRevealingSecret || isRemoving) {
                    fadeAlpha
                } else otherAlpha,
                borderAlpha = if (isActive) borderAlpha * otherAlpha else 0f
            ),
            backgroundColor = animatedBgColor,
            borderColor = MaterialTheme.colorScheme.primary,
            borderWidth = if (isActive) 3.1.dp else 0.dp,
            cameraDistance = 12f,
            animate = false,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .wrapContentSize(unbounded = true),
            surfaceModifier = Modifier
                .then(
                    if (!isAnyOtherExpanded && !isExpanded) {
                        Modifier
                            .clickable { onSelect() }
                            .padding(6.dp)
                    } else Modifier
                )
                .graphicsLayer { alpha = if (isExpanded) 1f else otherAlpha },
            front = {
                // اینجا و نه `FlipCardTargets.contentAlpha`: آن مقدار روی `alpha`ِ کلِ لایه
                // می‌نشیند و پس‌زمینهٔ قرمز را هم با خود می‌برد. این لایه فقط محتواست.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentFade }
                ) {
                    WalletCardContent(
                        walletName = if (isPersonalizing) editName else wallet.name,
                        balance = balance,
                        isSmall = !isExpanded && !isRevealingSecret && !isBackupSuccess,
                        onMoreClick = {
                            if (!isAnyOtherExpanded) onToggleExpand()
                        },
                        onPersonalizeClick = if (isExpanded && !isPersonalizing && !isRevealingSecret && !isRemoving) onSettingsClick else null,
                        isPersonalizing = isPersonalizing,
                        isEditingNickname = isEditingNickname,
                        hideActions = hideActions,
                        onNameChange = onNameChange,
                        onEditNicknameToggle = onEditNicknameToggle,
                        focusRequester = focusRequester,
                        // `null` یعنی «در حالِ حذف نیستیم» و چیپِ شخصی‌سازی سرِ جایش می‌ماند.
                        removalBackupState = if (isRemoving) {
                            isManualBackedUp || isCloudBackedUp
                        } else null,
                        removalInProgress = isRemovalInProgress
                    )
                }
            },
            back = {
                if (!isRevealingSecret && rotationY <= 90f) {

                    WalletCardContent(
                        walletName = if (isPersonalizing) editName else wallet.name,
                        balance = balance,
                        isSmall = false,
                        onMoreClick = { onToggleExpand() },
                        onPersonalizeClick = if (!isPersonalizing) onSettingsClick else null,
                        isPersonalizing = isPersonalizing,
                        isEditingNickname = isEditingNickname,
                        hideActions=hideActions,
                        onNameChange = onNameChange,
                        onEditNicknameToggle = onEditNicknameToggle,
                        focusRequester = focusRequester
                    )


                } else {
                    WalletCardBack(
                        secret = secretData,
                        isMnemonic = wallet.hasMnemonic, 1f
                    )
                }
            }
        )
    }
}

@Composable
private fun WalletCardContent(
    walletName: String,
    balance: String,
    isSmall: Boolean = true,
    onMoreClick: (() -> Unit)? = null,
    onPersonalizeClick: (() -> Unit)? = null,
    isPersonalizing: Boolean = false,
    isEditingNickname: Boolean = false,
    hideActions: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onEditNicknameToggle: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    /**
     * `null` = حالتِ عادی، چیپِ شخصی‌سازی. غیرِ `null` = فلوی حذف، و مقدارش یعنی پشتیبان دارد
     * یا نه. عمداً سه‌حالته است تا «در حالِ حذف» با «پشتیبان ندارد» قاطی نشود.
     */
    removalBackupState: Boolean? = null,
    /** جای وضعیتِ پشتیبان، اسپینر و «در حال حذف» بنشیند. */
    removalInProgress: Boolean = false
) {

    Box(modifier = Modifier.fillMaxSize()) {
        // ۱. لایه زیرین: محتوای اصلی (آیکون‌ها, بالانس, دکمه‌های عادی)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header Space
            Spacer(modifier = Modifier.height(if (isSmall) 32.dp else 48.dp))

            Spacer(modifier = Modifier.weight(1f))

            // بخش نام والت
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
            ) {
                // همیشه متن را رندر می‌کنیم (اگر در حال ادیت بود، آن را شفاف می‌کنیم تا فضا حفظ شود)
                Text(
                    text = walletName,
                    color = if (isEditingNickname) Color.Transparent else Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = IranSansBold,
                    maxLines = 1,
                    fontSize = if (isSmall) 14.sp else 25.sp
                )
            }

            // Footer (Balance Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isSmall) 22.dp else 30.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = balance,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (balance.contains("$"))InterMedium else IranSansRegularMedium,
                    fontSize = if (isSmall) 12.sp else 22.sp
                )

                if (!isSmall && !isEditingNickname && (removalBackupState != null || removalInProgress)) {
                    // فلوی حذف: جای چیپِ شخصی‌سازی، وضعیتِ پشتیبان می‌نشیند. کاربر در لحظه‌ای که
                    // دارد کیف را پاک می‌کند باید بداند پشتیبان دارد یا نه — این تنها چیزی است
                    // که تصمیمش را عوض می‌کند.
                    AnimatedContent(
                        // `null` در این شاخه یعنی «در حال حذف» — قرص دیگر وضعیت نشان نمی‌دهد،
                        // پیشرفت نشان می‌دهد.
                        targetState = if (removalInProgress) null else removalBackupState,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 120)) + scaleIn(initialScale = 0.85f))
                                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.85f))
                        },
                        label = "removal_backup_state"
                    ) { backedUp ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (backedUp == null) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 1.5.dp,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (backedUp) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = when (backedUp) {
                                    null -> "در حال حذف"
                                    true -> "پشتیبان گرفته شده"
                                    false -> "بدون پشتیبان"
                                },
                                color = Color.White,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = IranSansBold,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                } else if (!isSmall && !isEditingNickname) {
                    if (isPersonalizing) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { onEditNicknameToggle() }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "تغییر نام مستعار",
                                color = Color.White,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = IranSansBold,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                    else if (onPersonalizeClick != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { if(!hideActions) onPersonalizeClick() }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "شخصی سازی",
                                color = Color.White,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontFamily = IranSansBold,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ۲. لایه میانی: هاله تیره (فقط در زمان ادیت)
        if (isEditingNickname) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .zIndex(5f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onEditNicknameToggle() }
            )
            // ۳. لایه رویین: فیلد متنی و دکمه Done (دقیقاً در جایگاه لایه زیرین)
            // از همان ساختار Column لایه ۱ استفاده می‌کنیم تا تراز بماند
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .zIndex(10f)
            ) {
                // دقیقا مشابه لایه اول
                Spacer(modifier = Modifier.height(if (isSmall) 32.dp else 48.dp))
                Spacer(modifier = Modifier.weight(1f))

                // بخش نام والت
                Box(
                    modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                ) {
                    // همیشه متن را رندر می‌کنیم (اگر در حال ادیت بود، آن را شفاف می‌کنیم تا فضا حفظ شود)
                    BasicTextField(
                        value = walletName,
                        onValueChange = onNameChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontFamily = IranSansBold,
                            fontSize = if (isSmall) 14.sp else 25.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                        decorationBox = { innerTextField ->
                            Box {
                                if (walletName.isEmpty()) {
                                    Text(
                                        "نام کیف پول",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = if (isSmall) 14.sp else 25.sp,
                                        fontFamily = IranSansBold
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Footer (Balance Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isSmall) 22.dp else 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = InterMedium,
                        fontSize = if (isSmall) 12.sp else 22.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.White)
                            .clickable { onEditNicknameToggle() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ثبت",
                            color = MaterialTheme.colorScheme.onTertiary,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = IranSansBold
                            )
                        )
                    }
                }

            }
        }

        // ۴. لایه آیکون‌ها (همیشه رو، برای اینکه در زمان ادیت هاله روی آن‌ها بیفتد اما خودشان رندر شوند)
        if (!isEditingNickname) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isSmall) 32.dp else 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSmall) 32.dp else 48.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if(walletName.isNotEmpty()) walletName.take(1).uppercase() else "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = IranSansBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isSmall) 12.sp else 20.sp
                        )
                    }

                    if (isSmall && onMoreClick != null) {
                        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                contentDescription = "گزینه‌های بیشتر",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else if (!isSmall) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { /* Copy */ }
                                .padding(start = 10.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "کپی آدرس",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontFamily = IranSansLight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletCardBack(
    secret: String,
    isMnemonic: Boolean,
    contentAlpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .graphicsLayer {
                alpha = contentAlpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isMnemonic) {
            PrivateKeyWallet(secret)

        } else {
            val words = remember(secret) { secret.split(" ").filter { it.isNotBlank() } }
            SeedPhraseGrid(
                words = words,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
