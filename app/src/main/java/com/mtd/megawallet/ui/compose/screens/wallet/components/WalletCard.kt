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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.R
import com.mtd.domain.model.Wallet
import com.mtd.megawallet.ui.compose.components.AnimatedFlipCard
import com.mtd.megawallet.ui.compose.components.FlipCardTargets
import com.mtd.megawallet.ui.compose.components.PrivateKeyWallet
import com.mtd.megawallet.ui.compose.components.SeedPhraseGrid

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
    onDeleteClick: () -> Unit,
    onNameChange: (String) -> Unit = {},
    onEditNicknameToggle: () -> Unit = {},
    hideActions: Boolean = false,
    isRevealingSecret: Boolean = false,
    isBackupSuccess: Boolean = false,
    secretData: String = "",
    focusRequester: FocusRequester? = null
) {
    val density = LocalDensity.current
    var cardCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val transition = updateTransition(
        targetState = when {
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
            "revealing", "success" -> targetWidth
            "expanded" -> targetWidth
            else -> collapsedWidth
        }
    }

    val cardHeight by transition.animateDp(
        label = "height",
        transitionSpec = { PremiumSpringDp }
    ) { state ->
        when (state) {
            "revealing", "success" -> targetHeight
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
                "revealing", "success" -> {
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

    val animatedBgColor by animateColorAsState(
        targetValue = if (isPersonalizing) editColor else Color(wallet.color),
        animationSpec = tween(500),
        label = "card_color"
    )
    val translationX by transition.animateFloat(
        label = "x",
        transitionSpec = { PremiumSpring }
    ) { state ->
        if (cardCoords != null && rootCoordinates != null) {
            val currentX = rootCoordinates.localPositionOf(cardCoords!!, Offset.Zero).x
            val currentWidth = cardCoords!!.size.width
            val screenWidth = rootCoordinates.size.width
            val targetX = (screenWidth / 2f) - (currentX + currentWidth / 2f)
            if (state == "expanded" || state == "revealing" || state == "success") targetX else 0f
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
                width = cardWidth,
                height = cardHeight,
                offsetX = translationX,
                offsetY = translationY,
                rotationY = rotationY,
                cornerRadius = 15.dp,
                cornerRadiusBoarder = 21.dp,
                contentAlpha = if (isExpanded || isRevealingSecret) 1f else otherAlpha,
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
                WalletCardContent(
                    walletName = if (isPersonalizing) editName else wallet.name,
                    balance = balance,
                    isSmall = !isExpanded && !isRevealingSecret && !isBackupSuccess,
                    onMoreClick = {
                        if (!isAnyOtherExpanded) onToggleExpand()
                    },
                    onPersonalizeClick = if (isExpanded && !isPersonalizing && !isRevealingSecret) onSettingsClick else null,
                    isPersonalizing = isPersonalizing,
                    isEditingNickname = isEditingNickname,
                    hideActions=hideActions,
                    onNameChange = onNameChange,
                    onEditNicknameToggle = onEditNicknameToggle,
                    focusRequester = focusRequester
                )
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
    focusRequester: FocusRequester? = null
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
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
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
                    fontFamily = FontFamily(Font(R.font.inter_medium)),
                    fontSize = if (isSmall) 12.sp else 22.sp
                )

                if (!isSmall && !isEditingNickname) {
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
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
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
                                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
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
                    .clip(RoundedCornerShape(21.dp))
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
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
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
                                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
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
                        fontFamily = FontFamily(Font(R.font.inter_medium)),
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
                            color = Color.Black,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
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
                            fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isSmall) 12.sp else 20.sp
                        )
                    }

                    if (isSmall && onMoreClick != null) {
                        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.MoreHoriz,
                                contentDescription = null,
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
                                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_light))
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
