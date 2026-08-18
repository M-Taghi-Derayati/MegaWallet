package com.mtd.megawallet.ui.compose.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.model.error.ErrorReason
import kotlinx.coroutines.delay

/**
 * دو حالتِ پیامِ بالای صفحه.
 *
 * [ERROR] ممکن است باز شود و دلایل را نشان دهد؛ [SUCCESS] یک تأییدِ ساده است و هرگز چیزی برای
 * باز شدن ندارد.
 */
enum class TopSnackbarStyle { ERROR, SUCCESS }

/* ============================ زمان‌بندی و اندازه‌ها ============================ */

/** محوشدنِ محتوای فعلی، پیش از عوض شدنِ حالت. */
private const val BODY_FADE_OUT_MS = 90

/** فاصله تا شروعِ ظاهر شدنِ محتوای جدید — تا باکس اول کمی راه بیفتد. */
private const val BODY_FADE_IN_DELAY_MS = 110

private const val BODY_FADE_IN_MS = 220

/** فاصلهٔ ورودِ ردیف‌های دلیل، پشتِ سرِ هم. */
private const val ROW_STAGGER_MS = 60

private const val ROW_ENTER_MS = 300

/**
 * سایه.
 *
 * ⚠️ عمداً کم است. در نسخهٔ اول ۱۴dp بود و چون هم اندازه و هم شعاعِ گوشه حین ریخت‌عوض‌کردن
 * متحرک‌اند، سایه در طولِ انیمیشن کشیده و لَکه‌ای دیده می‌شد. جداییِ کارت از پس‌زمینه را لبهٔ
 * نیم‌پیکسلی می‌دهد، نه سایه.
 */
private val SHADOW_ELEVATION = 4.dp

private val PILL_CORNER = 22.dp
private val CARD_CORNER = 26.dp
private val CARD_MAX_WIDTH = 380.dp

/** بالا آمدنِ هر ردیف هنگامِ ورود. */
private val ROW_LIFT = 10.dp

/**
 * فنرِ ریختِ باکس.
 *
 * همان فنرِ همه‌جای برنامه (`0.82 / 380`) است، نه یک عددِ تازه: پیام بالای همان صفحه‌ای می‌نشیند
 * که شیت‌ها و کارت‌هایش با همین فنر حرکت می‌کنند.
 */
private val MorphSpring = spring(
    dampingRatio = 0.82f,
    stiffness = 380f,
    visibilityThreshold = IntSize.VisibilityThreshold
)

/**
 * پیامِ بالای صفحه — یک قرصِ کوچک که با ضربه به کارتِ دلایل باز می‌شود.
 *
 * ### چطور کار می‌کند
 * یک باکسِ واحد است، نه دو تا. `animateContentSize` روی محتوای درونی نشسته و پس‌زمینه و لبه
 * **بیرونِ** آن‌اند، پس اندازهٔ کشیده‌شده دقیقاً همان اندازهٔ متحرک است و شکل، حین باز شدن،
 * ریخت عوض می‌کند به‌جای اینکه جهش کند.
 *
 * ⚠️ ترتیبِ مودیفایرها عمدی است: اگر `animateContentSize` بیرونِ `background` بیاید، پس‌زمینه
 * بی‌درنگ به اندازهٔ نهایی کشیده می‌شود و از باکسِ در حالِ رشد بیرون می‌زند.
 *
 * ### ترتیبِ باز و بسته شدن
 * در هر دو جهت یکسان است: محتوا محو می‌شود، بعد باکس ریخت عوض می‌کند، بعد محتوای تازه می‌آید.
 * برگشت دقیقاً وارونهٔ رفت است.
 *
 * @param expanded حالتِ باز؛ صاحبش [AppMessageHost] است تا پرده و تایمرِ محوشدن با آن هماهنگ بمانند.
 * @param onToggle ضربه روی قرص، وقتی چیزی برای باز کردن هست.
 * @param onClose ضربه روی دستگیره یا روی قرصی که باز نمی‌شود.
 */
@Composable
fun AppSnackbar(
    message: String,
    style: TopSnackbarStyle,
    reasons: List<ErrorReason>,
    technicalDetail: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expandable = reasons.isNotEmpty() || technicalDetail.isNotBlank()

    // `showCard` از `expanded` عقب می‌ماند تا محتوای قبلی فرصتِ محو شدن داشته باشد.
    var showCard by remember { mutableStateOf(false) }
    var bodyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        bodyVisible = false
        delay(BODY_FADE_OUT_MS.toLong())
        showCard = expanded
        delay(BODY_FADE_IN_DELAY_MS.toLong())
        bodyVisible = true
    }

    val bodyAlpha by animateFloatAsState(
        targetValue = if (bodyVisible) 1f else 0f,
        animationSpec = tween(if (bodyVisible) BODY_FADE_IN_MS else BODY_FADE_OUT_MS),
        label = "snackbar_body_alpha"
    )
    val corner by animateDpAsState(
        targetValue = if (showCard) CARD_CORNER else PILL_CORNER,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "snackbar_corner"
    )
    val shape = RoundedCornerShape(corner)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .widthIn(max = CARD_MAX_WIDTH)
            .shadow(elevation = SHADOW_ELEVATION, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // در حالتِ باز، ضربه روی خودِ کارت باید بی‌اثر باشد ولی **مصرف** شود، وگرنه به
                // پردهٔ پشتِ سر می‌رسد و کارت را می‌بندد.
                when {
                    expanded -> Unit
                    expandable -> onToggle()
                    else -> onClose()
                }
            }
    ) {
        // `TopCenter` عمدی است: کارت باید از مرکزِ قرص به دو طرف باز شود و رو به **پایین** رشد
        // کند. با پیش‌فرضِ `TopStart` در چیدمانِ راست‌به‌چپ، محتوا به لبهٔ راست می‌چسبید و باکس
        // فقط به چپ کش می‌آمد.
        Box(
            modifier = Modifier.animateContentSize(
                animationSpec = MorphSpring,
                alignment = Alignment.TopCenter
            )
        ) {
            if (showCard) {
                SnackbarCardBody(
                    message = message,
                    style = style,
                    reasons = reasons,
                    technicalDetail = technicalDetail,
                    bodyAlpha = bodyAlpha,
                    rowsVisible = bodyVisible,
                    onClose = onClose
                )
            } else {
                SnackbarPillBody(
                    message = message,
                    style = style,
                    expandable = expandable,
                    bodyAlpha = bodyAlpha
                )
            }
        }
    }
}

/* ============================ حالتِ بسته ============================ */

@Composable
private fun SnackbarPillBody(
    message: String,
    style: TopSnackbarStyle,
    expandable: Boolean,
    // ⚠️ نامش `alpha` نیست: داخلِ `graphicsLayer` نامِ `alpha` به خودِ آن اسکوپ می‌خورد و
    // انتساب، انتسابِ یک مقدار به خودش می‌شد.
    bodyAlpha: Float
) {
    Row(
        modifier = Modifier
            .graphicsLayer { alpha = bodyAlpha }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = style.icon(),
            contentDescription = style.contentDescription(),
            tint = style.tint(),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // `fill = false` تا قرص با متنِ کوتاه جمع بماند. بدونِ وزن، متنِ بلند تمامِ عرض را
            // می‌گرفت و آیکونِ کنارش صفر عرض می‌شد.
            modifier = Modifier.weight(1f, fill = false)
        )
        // فقط وقتی واقعاً چیزی برای باز کردن هست — نشانهٔ بی‌کار بدتر از نبودنِ نشانه است.
        if (expandable) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "نمایش دلایل",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/* ============================ حالتِ باز ============================ */

@Composable
private fun SnackbarCardBody(
    message: String,
    style: TopSnackbarStyle,
    reasons: List<ErrorReason>,
    technicalDetail: String,
    // ⚠️ به دلیلِ توضیحِ [SnackbarPillBody] نامش `alpha` نیست.
    bodyAlpha: Float,
    rowsVisible: Boolean,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = bodyAlpha }
    ) {
        // پیامِ حالتِ بسته اینجا تکرار می‌شود؛ کاربر برای دیدنِ دلایل روی آن زده و نباید گمش کند.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = style.icon(),
                contentDescription = style.contentDescription(),
                tint = style.tint(),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
        }

        val lastIndex = reasons.lastIndex
        reasons.forEachIndexed { index, reason ->
            SnackbarDivider()
            SnackbarReasonRow(reason = reason, index = index, visible = rowsVisible)
            if (index == lastIndex && technicalDetail.isNotBlank()) SnackbarDivider()
        }

        if (technicalDetail.isNotBlank()) {
            if (reasons.isEmpty()) SnackbarDivider()
            SnackbarTechnicalRow(
                detail = technicalDetail,
                index = reasons.size,
                visible = rowsVisible
            )
        }

        // دستگیره — نوارِ لمسی تمام‌عرض است، چون خودِ خط ۴dp ارتفاع دارد و هدفِ لمسِ درستی نیست.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .padding(top = 14.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            )
        }
    }
}

@Composable
private fun SnackbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            // از `surfaceVariant` استفاده نمی‌کنیم: در پوستهٔ روشن با خودِ `surface` یکی است.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

/**
 * یک دلیل. با تأخیرِ پله‌ای می‌آید تا ردیف‌ها پشتِ سرِ هم بنشینند نه همه با هم.
 *
 * شفافیتِ اینجا روی شفافیتِ کلِ کارت ضرب می‌شود؛ همین باعث می‌شود هنگامِ بسته شدن همه با هم و
 * سریع محو شوند و پله‌ها فقط در جهتِ ورود دیده شوند.
 */
@Composable
private fun SnackbarReasonRow(
    reason: ErrorReason,
    index: Int,
    visible: Boolean
) {
    val enter by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = ROW_ENTER_MS,
            delayMillis = if (visible) index * ROW_STAGGER_MS else 0,
            easing = FastOutSlowInEasing
        ),
        label = "snackbar_reason_$index"
    )
    val lift = with(LocalDensity.current) { ROW_LIFT.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * lift
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Icon(
            imageVector = reason.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reason.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = reason.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/**
 * متنِ فنی — همان چیزی که پیش‌تر پشتِ دیالوگِ «جزئیات» بود.
 *
 * از [com.mtd.domain.model.error.ErrorTextSanitizer] گذشته، پس آدرس و کلید و هَش در آن نیست.
 */
@Composable
private fun SnackbarTechnicalRow(
    detail: String,
    index: Int,
    visible: Boolean
) {
    val enter by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = ROW_ENTER_MS,
            delayMillis = if (visible) index * ROW_STAGGER_MS else 0,
            easing = FastOutSlowInEasing
        ),
        label = "snackbar_technical"
    )
    val lift = with(LocalDensity.current) { ROW_LIFT.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * lift
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "جزئیاتِ فنی",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                lineHeight = 19.sp
            )
        }
    }
}

/* ============================ نگاشتِ آیکون ============================ */

/**
 * آیکونِ هر دلیل.
 *
 * ⚠️ اینجا زندگی می‌کند و نه کنارِ خودِ [ErrorReason]: آن `enum` در `domain` است و `domain` هیچ
 * وابستگیِ اندروید یا Compose ندارد.
 */
private fun ErrorReason.icon(): ImageVector = when (this) {
    ErrorReason.CONNECTION -> Icons.Outlined.WifiOff
    ErrorReason.SLOW_RESPONSE -> Icons.Outlined.HourglassEmpty
    ErrorReason.SERVER -> Icons.Outlined.CloudOff
    ErrorReason.BALANCE -> Icons.Outlined.AccountBalanceWallet
    ErrorReason.ADDRESS -> Icons.Outlined.LinkOff
    ErrorReason.OTHER -> Icons.Outlined.HelpOutline
}

private fun TopSnackbarStyle.icon(): ImageVector = when (this) {
    TopSnackbarStyle.ERROR -> Icons.Outlined.ErrorOutline
    TopSnackbarStyle.SUCCESS -> Icons.Outlined.CheckCircle
}

/** تنها جای رنگی در پیام. خودِ کارت خنثی می‌ماند تا با شیت‌های برنامه یکی باشد. */
@Composable
private fun TopSnackbarStyle.tint(): Color = when (this) {
    TopSnackbarStyle.ERROR -> MaterialTheme.colorScheme.error
    TopSnackbarStyle.SUCCESS -> MaterialTheme.colorScheme.primary
}

/** آیکون تنها حاملِ این اطلاعات است، پس برای TalkBack نام دارد. */
private fun TopSnackbarStyle.contentDescription(): String = when (this) {
    TopSnackbarStyle.ERROR -> "خطا"
    TopSnackbarStyle.SUCCESS -> "انجام شد"
}

/* ============================ پیش‌نمایش ============================ */

@Preview(name = "Snackbar — pill", showBackground = true)
@Composable
private fun AppSnackbarPillPreview() {
    MegaWalletTheme(darkTheme = true) {
        Box(Modifier.padding(16.dp)) {
            AppSnackbar(
                message = "اتصال اینترنت برقرار نیست.",
                style = TopSnackbarStyle.ERROR,
                reasons = ErrorReason.entries.take(3),
                technicalDetail = "",
                expanded = false,
                onToggle = {},
                onClose = {}
            )
        }
    }
}

@Preview(name = "Snackbar — expanded", showBackground = true, heightDp = 560)
@Composable
private fun AppSnackbarExpandedPreview() {
    MegaWalletTheme(darkTheme = true) {
        Box(Modifier.padding(16.dp)) {
            AppSnackbar(
                message = "اتصال اینترنت برقرار نیست.",
                style = TopSnackbarStyle.ERROR,
                reasons = listOf(ErrorReason.CONNECTION, ErrorReason.SERVER, ErrorReason.OTHER),
                technicalDetail = "نوع: UnknownHostException",
                expanded = true,
                onToggle = {},
                onClose = {}
            )
        }
    }
}

@Preview(name = "Snackbar — success", showBackground = true)
@Composable
private fun AppSnackbarSuccessPreview() {
    MegaWalletTheme(darkTheme = false) {
        Box(Modifier.padding(16.dp)) {
            AppSnackbar(
                message = "کپی شد",
                style = TopSnackbarStyle.SUCCESS,
                reasons = emptyList(),
                technicalDetail = "",
                expanded = false,
                onToggle = {},
                onClose = {}
            )
        }
    }
}
