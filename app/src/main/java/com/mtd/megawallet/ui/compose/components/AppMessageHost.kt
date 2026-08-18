package com.mtd.megawallet.ui.compose.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.domain.model.error.ErrorReason
import com.mtd.domain.model.ui.UiEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** چقدر یک تأییدِ موفقیت می‌ماند و بعد خودش می‌رود. */
private const val SUCCESS_SNACKBAR_DURATION_MS = 2_400L

/**
 * چقدر یک خطا می‌ماند.
 *
 * پیش‌تر خطا اصلاً خودش نمی‌رفت و تا ضربهٔ کاربر سرِ صفحه می‌ماند. حالا که با یک ضربه باز می‌شود
 * و همان‌جا دلایل را می‌گوید، ماندنِ همیشگی فقط مزاحمت است — ولی از تأییدِ موفقیت بیشتر فرصت
 * می‌خواهد، چون کاربر باید بتواند تصمیم بگیرد بازش کند.
 */
private const val ERROR_SNACKBAR_DURATION_MS = 5_000L

/** فرصتِ جمع شدنِ کارت به قرص، پیش از اینکه پیام از صفحه برود. */
private const val COLLAPSE_BEFORE_DISMISS_MS = 340L

private const val SCRIM_ALPHA = 0.45f

/**
 * TASK-57 — تنها جایی که پیام‌های سراسری کشیده می‌شوند.
 *
 * دقیقاً یکی از این‌ها را در ریشهٔ هر Activity سوار کنید و `ErrorManager.uiMessages` را به آن
 * بدهید. سه سطحِ سیاستِ شدت را می‌کشد:
 *
 * - `ShowErrorSnackbar` → قرصِ بالای صفحه؛ با ضربه به کارتِ دلایل باز می‌شود، وگرنه خودش می‌رود.
 * - `ShowSuccessSnackbar` → همان قرص با آیکونِ سبز؛ هرگز باز نمی‌شود.
 * - `ShowDialog` → مودالِ مسدودکننده که کاربر باید تأیید کند.
 *
 * `SILENT` هرگز به اینجا نمی‌رسد؛ `ErrorManager` لاگ می‌کند و می‌ایستد.
 *
 * ### بسته شدن
 * سه راه دارد و هر سه به یک جا می‌رسند: ضربه بیرونِ کارت، دستگیرهٔ پایینِ کارت، دکمهٔ بازگشت.
 * در هر سه، کارت **اول** وارونهٔ باز شدن جمع می‌شود و بعد پیام از صفحه می‌رود؛ ناپدید شدنِ
 * ناگهانی، انیمیشنِ باز شدن را بی‌معنا می‌کرد.
 */
@Composable
fun AppMessageHost(
    uiMessages: Flow<UiEvent>,
    modifier: Modifier = Modifier
) {
    // پیام پس از رفتن هم نگه داشته می‌شود تا انیمیشنِ خروج چیزی برای کشیدن داشته باشد؛
    // دیده شدن را `visible` تعیین می‌کند، نه null بودنِ پیام.
    var message by remember { mutableStateOf<SnackbarMessage?>(null) }
    var visible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var collapsing by remember { mutableStateOf(false) }
    var blockingDialog by remember { mutableStateOf<UiEvent.ShowDialog?>(null) }
    // دو پیامِ یکسانِ پشتِ سرِ هم را از هم جدا می‌کند تا تایمرِ محو شدن دوباره شروع شود.
    val nextId = remember { mutableLongStateOf(0L) }

    fun show(next: SnackbarMessage) {
        message = next
        expanded = false
        collapsing = false
        visible = true
    }

    fun collapseThenDismiss() {
        expanded = false
        collapsing = true
    }

    LaunchedEffect(uiMessages) {
        uiMessages.collect { event ->
            when (event) {
                is UiEvent.ShowErrorSnackbar -> show(
                    SnackbarMessage(
                        id = nextId.longValue++,
                        message = event.shortMessage,
                        detail = event.detailedMessage,
                        reasons = event.reasons,
                        style = TopSnackbarStyle.ERROR
                    )
                )

                is UiEvent.ShowSuccessSnackbar -> show(
                    SnackbarMessage(
                        id = nextId.longValue++,
                        message = event.message,
                        detail = "",
                        reasons = emptyList(),
                        style = TopSnackbarStyle.SUCCESS
                    )
                )

                is UiEvent.ShowDialog -> blockingDialog = event

                is UiEvent.Navigate,
                UiEvent.DismissLoading -> Unit
            }
        }
    }

    val current = message

    // تایمرِ خودمحوی. با باز شدنِ کارت متوقف می‌شود — کسی که دارد دلایل را می‌خواند نباید پیام
    // زیرِ دستش برود — و با بسته شدن از نو شروع می‌شود.
    LaunchedEffect(current?.id, visible, expanded, collapsing) {
        val active = current ?: return@LaunchedEffect
        if (!visible || expanded || collapsing) return@LaunchedEffect
        delay(
            when (active.style) {
                TopSnackbarStyle.SUCCESS -> SUCCESS_SNACKBAR_DURATION_MS
                TopSnackbarStyle.ERROR -> ERROR_SNACKBAR_DURATION_MS
            }
        )
        if (message?.id == active.id) visible = false
    }

    LaunchedEffect(collapsing) {
        if (!collapsing) return@LaunchedEffect
        delay(COLLAPSE_BEFORE_DISMISS_MS)
        visible = false
        collapsing = false
    }

    BackHandler(enabled = expanded) { collapseThenDismiss() }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) SCRIM_ALPHA else 0f,
        animationSpec = tween(220),
        label = "snackbar_scrim"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // فقط وقتی کارت باز است وجود دارد، وگرنه یک لایهٔ تمام‌صفحه همیشه جلوی لمسِ برنامه بود.
        if (scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(99f)
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { collapseThenDismiss() }
                    )
            )
        }

        AnimatedVisibility(
            visible = visible && current != null,
            enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(250)) { -it } + fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .zIndex(100f)
        ) {
            current?.let { active ->
                AppSnackbar(
                    message = active.message,
                    style = active.style,
                    reasons = active.reasons,
                    technicalDetail = active.detail,
                    expanded = expanded,
                    onToggle = { expanded = true },
                    onClose = { collapseThenDismiss() }
                )
            }
        }
    }

    blockingDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { /* blocking — acknowledgement is required */ },
            // ⚠️ خانوادهٔ فونت صریح داده می‌شود. `Typography` برنامه فقط هشت استایل را تعریف
            // می‌کند و `titleMedium` جزوشان نیست، پس با تکیه بر آن، متن به فونتِ پیش‌فرضِ
            // متریال برمی‌گشت و لاتین دیده می‌شد.
            title = {
                Text(text = dialog.title, fontFamily = IranSansBold, fontSize = 17.sp)
            },
            text = {
                Text(
                    text = dialog.message,
                    fontFamily = IranSansRegular,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    blockingDialog = null
                    dialog.onPositive()
                }) {
                    Text(text = dialog.positiveButton, fontFamily = IranSansBold, fontSize = 15.sp)
                }
            },
            dismissButton = dialog.negativeButton?.let { negative ->
                {
                    TextButton(onClick = {
                        blockingDialog = null
                        dialog.onNegative()
                    }) {
                        Text(text = negative, fontFamily = IranSansRegular, fontSize = 15.sp)
                    }
                }
            }
        )
    }
}

private data class SnackbarMessage(
    val id: Long,
    val message: String,
    val detail: String,
    val reasons: List<ErrorReason>,
    val style: TopSnackbarStyle
)
