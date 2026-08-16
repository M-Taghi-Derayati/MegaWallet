package com.mtd.megawallet.ui.compose.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.R
import com.mtd.common_ui.theme.InterMedium
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.domain.model.support.SupportArea
import com.mtd.domain.model.support.SupportCategory
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.viewmodel.settings.SupportViewModel

/**
 * رنگِ هر دسته.
 *
 * این‌ها فقط رنگِ کارتِ گامِ اول نیستند: دسته‌ای که کاربر انتخاب می‌کند، رنگِ دکمهٔ «ادامه» و تیکِ
 * انتخاب‌ها را تا آخرِ فلو تعیین می‌کند. به همین دلیل از پالتِ تم نمی‌آیند — سه مسیرِ موازی‌اند و
 * باید از هم تفکیک شوند، نه اینکه همه رنگِ `primary` بگیرند.
 */
private val SupportBugAccent = Color(0xFFFF8A00)
private val SupportFeedbackAccent = Color(0xFF00A3FF)
private val SupportOtherAccent = Color(0xFF00B3A6)

private fun SupportCategory.accent(): Color = when (this) {
    SupportCategory.BUG -> SupportBugAccent
    SupportCategory.FEEDBACK -> SupportFeedbackAccent
    SupportCategory.OTHER -> SupportOtherAccent
}

/**
 * گام‌های فلو، به همان ترتیبی که طی می‌شوند.
 *
 * ⚠️ ترتیبِ اعضا معنادار است: جهتِ انیمیشن و «یک گام عقب» هر دو از `ordinal` خوانده می‌شوند.
 *
 * اول دسته، بعد بخش، بعد شرح: تا وقتی کاربر نگفته کجای برنامه را می‌گوید، نوشتنِ شرح یعنی
 * توضیحِ چیزی در خلأ. با انتخابِ بخش، وقتی به فرم می‌رسد می‌داند دربارهٔ چه می‌نویسد.
 */
private enum class SupportStep { Category, Areas, Form, Details }

/**
 * ایمیلِ به‌دردبخور، نه ایمیلِ استاندارد.
 *
 * عمداً ساده است: هدف گرفتنِ غلط‌های آشکار (جاافتادنِ @ یا دامنه) است، نه پیاده‌سازیِ RFC. یک
 * الگویِ سخت‌گیرانه، ایمیل‌های درستِ کمترشناخته را رد می‌کرد و کاربر را از فرستادنِ گزارش
 * بازمی‌داشت — که دقیقاً همان چیزی است که نمی‌خواهیم.
 */
private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

/** بخش‌های برنامه، با نام و نشانی که کاربر می‌شناسد. */
private val SUPPORT_AREA_LABELS: List<Pair<SupportArea, Pair<String, Int>>> = listOf(
    SupportArea.SEND to ("ارسال" to R.drawable.ic_send),
    SupportArea.RECEIVE to ("دریافت" to R.drawable.ic_download),
    SupportArea.SWAP to ("تبدیل" to R.drawable.ic_swap),
    SupportArea.HISTORY to ("تاریخچه" to R.drawable.ic_history),
    SupportArea.TOKENS to ("توکن‌ها" to R.drawable.ic_contract_name),
    SupportArea.SECURITY to ("امنیت" to R.drawable.ic_security),
    SupportArea.OTHER to ("سایر" to R.drawable.ic_question)
)

/**
 * راهنما و پشتیبانی — چهار گام در یک شیت.
 *
 * شیت است نه صفحه، چون کاربر وسطِ تنظیمات ایستاده و بعد از فرستادنِ گزارش باید همان‌جا برگردد؛
 * فهرستِ تنظیمات پشتِ شیت دیده می‌ماند و همین «موقتی‌بودن» را می‌رساند.
 */
@Composable
fun SupportFlowSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val category by viewModel.category.collectAsStateWithLifecycle()
    val subject by viewModel.subject.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val selectedWalletId by viewModel.selectedWalletId.collectAsStateWithLifecycle()
    val submitState by viewModel.submitState.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(SupportStep.Category) }

    // هر بار که شیت باز می‌شود از گامِ اول شروع می‌شود، وگرنه بازکردنِ دوباره وسطِ فرمِ دفعهٔ قبل
    // می‌افتاد.
    LaunchedEffect(visible) {
        if (visible) {
            step = SupportStep.Category
            viewModel.reset()
        }
    }

    // تنها راهِ بسته‌شدنِ خودکار، پاسخِ موفقِ سرور است.
    LaunchedEffect(submitState) {
        if (submitState is SupportViewModel.SubmitState.Sent) onDismiss()
    }

    // برگشت یعنی «یک گام عقب»، نه «بستنِ همه‌چیز» — و این برای هر سه راهِ خروج یکی است: دکمهٔ
    // ضربدر، برگشتِ گوشی، و ضربه به پس‌زمینه. هر سه به `onDismiss`ِ [AnimatedBottomSheetCard]
    // می‌رسند، پس یک لامبدا کافی است و `BackHandler`ِ جداگانه لازم نیست.
    val handleBack: () -> Unit = {
        val previous = SupportStep.entries.getOrNull(step.ordinal - 1)
        if (previous == null) onDismiss() else step = previous
    }

    val accent = category?.accent() ?: MaterialTheme.colorScheme.primary
    val selectedWallet = wallets.firstOrNull { it.id == selectedWalletId }

    AnimatedBottomSheetCard(
        visible = visible,
        title = when (step) {
            SupportStep.Category -> "چطور می‌توانیم کمک کنیم؟"
            SupportStep.Areas -> "کدام بخش‌ها ؟"
            SupportStep.Form -> when (category) {
                SupportCategory.FEEDBACK -> "ثبت بازخورد"
                SupportCategory.OTHER -> "پیام شما"
                else -> "گزارش اشکال"
            }

            SupportStep.Details -> "مشخصات شما"
        },
        onDismiss = handleBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Spacer(Modifier.height(18.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = { supportStepTransition(forward = targetState > initialState) },
                label = "support_step",
                modifier = Modifier.fillMaxWidth()
            ) { current ->
                // گام‌ها فرزندانشان را پشتِ سرِ هم می‌ریزند و روی `Column` حساب می‌کنند؛ اسلاتِ
                // `AnimatedContent` جعبه است، پس این `Column` لازم است.
                //
                // ⚠️ `current` خوانده می‌شود و نه `step`: در حینِ گذار هر دو نسخه با هم روی صفحه‌اند
                // و با `step` گامِ در حالِ خروج هم محتوای گامِ تازه را می‌گرفت.
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (current) {
                        SupportStep.Category -> CategoryStep(
                            onSelect = {
                                viewModel.selectCategory(it)
                                step = SupportStep.Areas
                            }
                        )

                        SupportStep.Areas -> AreasStep(
                            selected = areas,
                            accent = accent,
                            onToggle = viewModel::toggleArea,
                            onContinue = { step = SupportStep.Form }
                        )

                        SupportStep.Form -> FormStep(
                            subject = subject,
                            description = description,
                            accent = accent,
                            onSubjectChange = viewModel::setSubject,
                            onDescriptionChange = viewModel::setDescription,
                            onContinue = { step = SupportStep.Details }
                        )

                        SupportStep.Details -> DetailsStep(
                            name = name,
                            email = email,
                            accent = accent,
                            wallet = selectedWallet,
                            submitState = submitState,
                            onNameChange = viewModel::setName,
                            onEmailChange = {
                                viewModel.setEmail(it)
                                viewModel.clearSubmitFailure()
                            },
                            onPickWallet = { walletId -> viewModel.selectWallet(walletId) },
                            wallets = wallets,
                            onSubmit = viewModel::submit
                        )
                    }
                }
            }
        }
    }
}

/**
 * گذارِ بینِ گام‌ها: محوشدن، به‌اضافهٔ یک لغزشِ کوتاه که جهت را برساند.
 *
 * لغزش عمداً ۲۴ پیکسل است و نه یک عرضِ کامل: این‌جا یک شیت است که ارتفاعش هم بین گام‌ها عوض
 * می‌شود، و لغزشِ بلند کنارِ تغییرِ ارتفاع شلوغ می‌شود. آن‌قدر هست که «جلو» از «عقب» تشخیص داده
 * شود و نه بیشتر.
 *
 * ورودی کمی تأخیر دارد تا با خروجی روی هم نیفتد، وگرنه وسطِ گذار دو متن هم‌زمان خوانده می‌شوند.
 *
 * [SizeTransform] بخشِ اصلیِ کار است: گام‌ها ارتفاع‌های خیلی متفاوتی دارند (سه کارت در برابر هفت
 * ردیف) و بدونِ آن، خودِ شیت بین گام‌ها می‌پرید.
 */
private fun supportStepTransition(forward: Boolean): ContentTransform {
    val enterOffset = if (forward) SUPPORT_STEP_SLIDE_PX else -SUPPORT_STEP_SLIDE_PX
    // سازندهٔ [ContentTransform] و نه `togetherWith ... using ...`: آن `using` یک اکستنشنِ
    // اکسپریمنتال است و در نسخه‌های تازه‌ترِ compose-animation جایش عوض شده. این‌جا همان سه
    // مقدار مستقیم پاس می‌شوند.
    return ContentTransform(
        targetContentEnter = slideInHorizontally(tween(260, delayMillis = 90)) { enterOffset } +
            fadeIn(tween(220, delayMillis = 90)),
        initialContentExit = slideOutHorizontally(tween(180)) { -enterOffset } +
            fadeOut(tween(150)),
        sizeTransform = SizeTransform(clip = false) { _, _ ->
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        }
    )
}

private const val SUPPORT_STEP_SLIDE_PX = 24

/** گامِ ۱ — سه کارت، سه مسیر، سه رنگ. */
@Composable
private fun CategoryStep(onSelect: (SupportCategory) -> Unit) {
    CategoryCard(
        icon = R.drawable.ic_bug,
        accent = SupportBugAccent,
        title = "گزارش اشکال",
        description = "مشکلی که با آن روبه‌رو شده‌اید را بگویید تا دنبالش کنیم.",
        onClick = { onSelect(SupportCategory.BUG) }
    )
    Spacer(Modifier.height(10.dp))
    CategoryCard(
        icon = R.drawable.ic_chat,
        accent = SupportFeedbackAccent,
        title = "ثبت بازخورد",
        description = "بگویید چه چیزی را می‌شود بهتر کرد؛ خوانده می‌شود.",
        onClick = { onSelect(SupportCategory.FEEDBACK) }
    )
    Spacer(Modifier.height(10.dp))
    CategoryCard(
        icon = R.drawable.ic_other,
        accent = SupportOtherAccent,
        title = "چیز دیگری",
        description = "درخواست قابلیت، یک پیام کوتاه، یا هر چیز دیگر.",
        onClick = { onSelect(SupportCategory.OTHER) }
    )
}

@Composable
private fun CategoryCard(
    icon: Int,
    accent: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = IranSansBold,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/** گامِ ۲ — موضوعِ یک‌خطی و شرحِ چندخطی. تا هر دو پر نشوند، «ادامه» نمی‌دهد. */
@Composable
private fun FormStep(
    subject: String,
    description: String,
    accent: Color,
    onSubjectChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    SupportTextField(
        value = subject,
        placeholder = "موضوع",
        accent = accent,
        onValueChange = onSubjectChange
    )

    Spacer(Modifier.height(10.dp))

    SupportTextField(
        value = description,
        placeholder = "مشکل را با جزئیات بیشتری شرح دهید، از جمله اینکه چطور دوباره اتفاق می‌افتد",
        accent = accent,
        singleLine = false,
        minHeight = 150.dp,
        onValueChange = onDescriptionChange
    )

    Spacer(Modifier.height(20.dp))

    SupportButton(
        text = "ادامه",
        accent = accent,
        enabled = subject.isNotBlank() && description.isNotBlank(),
        onClick = onContinue
    )
}

/** گامِ ۳ — چندانتخابی. تا دستِ‌کم یکی انتخاب نشود، «ادامه» نمی‌دهد. */
@Composable
private fun AreasStep(
    selected: Set<SupportArea>,
    accent: Color,
    onToggle: (SupportArea) -> Unit,
    onContinue: () -> Unit
) {
    // فاصله لازم است چون این فهرست چندانتخابی است: ردیفِ انتخاب‌شده پس‌زمینهٔ گِرد می‌گیرد و دو
    // انتخابِ کنارِ هم بدونِ فاصله به هم می‌چسبند و گوشه‌های گِردشان یک لکهٔ واحد به نظر می‌رسد.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SUPPORT_AREA_LABELS.forEach { (area, labelAndIcon) ->
            val (label, icon) = labelAndIcon
            AreaRow(
                label = label,
                icon = icon,
                selected = area in selected,
                accent = accent,
                onClick = { onToggle(area) }
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    SupportButton(
        text = "ادامه",
        accent = accent,
        enabled = selected.isNotEmpty(),
        onClick = onContinue
    )
}

/**
 * یک بخش در فهرست.
 *
 * ردیفِ انتخاب‌شده پس‌زمینهٔ خاکستریِ گِرد و تیکِ پُرِ رنگی می‌گیرد؛ ردیفِ انتخاب‌نشده هیچ‌کدام را
 * ندارد — نه قابِ خالی، نه دایرهٔ توخالی. جای تیک هم گرفته نمی‌شود چون این‌جا برخلافِ
 * انتخابگرهای تک‌گزینه‌ای، خودِ پس‌زمینه هم عوض می‌شود و جابه‌جایی دیده نمی‌شود.
 */
@Composable
private fun AreaRow(
    label: String,
    icon: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(14.dp))

        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "انتخاب‌شده",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * گامِ ۴ — نام، ایمیل، و کیف‌پولی که آدرسش همراهِ گزارش می‌رود.
 *
 * ⚠️ آدرس **دیده می‌شود**. خودکار پیدا می‌شود چون بیشترِ آدم‌ها نمی‌توانند آدرسشان را درست
 * بنویسند و آدرسِ غلط از نبودنِ آدرس بدتر است — ولی چیزی که با گزارش فرستاده می‌شود باید روی
 * صفحه باشد. کیف‌پولی که بی‌صدا آدرسِ کاربر را ضمیمه کند، مسئلهٔ اعتماد می‌سازد.
 *
 * «ادامه» فقط به ایمیلِ معتبر گره خورده؛ نام و کیف‌پول از پیش پر شده‌اند و دست‌نزدن به آن‌ها
 * حالتِ درستی است.
 */
@Composable
private fun DetailsStep(
    name: String,
    email: String,
    accent: Color,
    wallet: SupportViewModel.SupportWallet?,
    wallets: List<SupportViewModel.SupportWallet>,
    submitState: SupportViewModel.SubmitState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPickWallet: (String) -> Unit,
    onSubmit: () -> Unit
) {
    var walletPickerOpen by remember { mutableStateOf(false) }

    Text(
        text = "ایمیل‌تان را بگذارید تا اگر لازم شد پیگیری کنیم.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = IranSansRegular,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 4.dp)
    )

    Spacer(Modifier.height(14.dp))

    SupportTextField(
        value = name,
        placeholder = "نام",
        accent = accent,
        onValueChange = onNameChange
    )

    Spacer(Modifier.height(10.dp))

    SupportTextField(
        value = email,
        placeholder = "name@email.com",
        accent = accent,
        keyboardType = KeyboardType.Email,
        isLatin = true,
        onValueChange = onEmailChange
    )

    Spacer(Modifier.height(10.dp))

    WalletRow(
        wallet = wallet,
        // فقط وقتی معنی دارد که واقعاً انتخابی وجود داشته باشد.
        onClick = if (wallets.size > 1) {
            { walletPickerOpen = !walletPickerOpen }
        } else {
            null
        }
    )

    if (walletPickerOpen && wallets.size > 1) {
        Spacer(Modifier.height(8.dp))
        wallets.forEach { option ->
            WalletPickerRow(
                wallet = option,
                selected = option.id == wallet?.id,
                accent = accent,
                onClick = {
                    onPickWallet(option.id)
                    walletPickerOpen = false
                }
            )
        }
    }

    // نبودنِ آدرس گفته می‌شود، نه اینکه بی‌صدا رد شود — و جلوی ارسال را هم نمی‌گیرد.
    if (wallet != null && wallet.evmAddress == null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "این کیف پول کلید EVM ندارد، پس گزارش بدون آدرس فرستاده می‌شود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = IranSansRegular,
            fontSize = 11.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    if (submitState is SupportViewModel.SubmitState.Failed) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = submitState.message,
            color = MaterialTheme.colorScheme.error,
            fontFamily = IranSansRegular,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    Spacer(Modifier.height(20.dp))

    SupportButton(
        text = "ارسال گزارش",
        accent = accent,
        enabled = EMAIL_PATTERN.matches(email.trim()),
        isLoading = submitState is SupportViewModel.SubmitState.Submitting,
        onClick = onSubmit
    )
}

/**
 * ردیفِ کیف‌پول: برچسب در ابتدا، آواتارِ رنگی و نام در انتها، و آدرسِ کوتاه‌شده زیرِ نام.
 *
 * ⚠️ کوتاه‌سازی فقط نمایشی است؛ آنچه فرستاده می‌شود آدرسِ کاملِ دست‌نخورده است.
 */
@Composable
private fun WalletRow(
    wallet: SupportViewModel.SupportWallet?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "کیف پول",
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansBold,
            fontSize = 15.sp
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            if (wallet == null) {
                Text(
                    text = "کیف پولی پیدا نشد",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = IranSansRegular,
                    fontSize = 13.sp
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletColorDot(color = wallet.color)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = wallet.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = IranSansBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (wallet.evmAddress != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = truncateForDisplay(wallet.evmAddress),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = InterMedium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** یک گزینه در فهرستِ بازشدهٔ کیف‌پول‌ها. */
@Composable
private fun WalletPickerRow(
    wallet: SupportViewModel.SupportWallet,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WalletColorDot(color = wallet.color)
        Spacer(Modifier.width(10.dp))
        Text(
            text = wallet.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = IranSansRegular,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "انتخاب‌شده",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** آواتارِ کیف‌پول — همان رنگی که کارتِ کیف‌پول در صفحهٔ اصلی دارد. */
@Composable
private fun WalletColorDot(color: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(color))
    )
}

/**
 * فیلدِ متنیِ فلو.
 *
 * [com.mtd.megawallet.ui.compose.components.SearchInputField] استفاده نشد چون آن برچسبِ بالای
 * فیلد دارد و این‌جا فقط راهنمای داخلِ فیلد لازم است؛ ضمن اینکه فیلدِ چندخطیِ بلند و رنگِ مکان‌نمای
 * وابسته به دسته را نمی‌دهد.
 *
 * [isLatin] برای ایمیل است: در یک فیلدِ راست‌به‌چپ، متنِ لاتین با فونتِ فارسی بد چیده می‌شود.
 */
@Composable
private fun SupportTextField(
    value: String,
    placeholder: String,
    accent: Color,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    isLatin: Boolean = false
) {
    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = if (isLatin) InterMedium else IranSansRegular,
        fontSize = 15.sp,
        lineHeight = 24.sp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle,
            singleLine = singleLine,
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            // راهنما و خودِ ورودی باید روی هم بیفتند، نه کنارِ هم؛ بدونِ این Box هر دو سیبلینگِ
            // بی‌چیدمان بودند.
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = if (isLatin) InterMedium else IranSansRegular,
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * دکمهٔ فلو.
 *
 * `PrimaryButton` استفاده نشد چون حالتِ غیرفعالش خاکستریِ `surface` است؛ این‌جا دکمهٔ غیرفعال
 * باید نسخهٔ **کم‌رنگِ همان رنگِ دسته** باشد تا معلوم بماند وقتی فعال شد چه رنگی می‌شود.
 */
@Composable
private fun SupportButton(
    text: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White,
            disabledContainerColor = accent.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.85f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = text,
                fontFamily = IranSansBold,
                fontSize = 15.sp
            )
        }
    }
}
