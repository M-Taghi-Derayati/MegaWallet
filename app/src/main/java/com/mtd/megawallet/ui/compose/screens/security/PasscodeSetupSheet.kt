package com.mtd.megawallet.ui.compose.screens.security

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.domain.security.AppLockManager
import com.mtd.megawallet.ui.compose.components.AnimatedBottomSheetCard
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.SecureFlagEffect
import com.mtd.megawallet.ui.compose.screens.settings.PickerSheetBody
import com.mtd.megawallet.ui.compose.screens.settings.SwitchCaption
import com.mtd.megawallet.ui.compose.screens.settings.SwitchRow
import kotlinx.coroutines.delay

/**
 * گام‌های ساختِ رمز. ترتیبِ اعضا معنادار است — جهتِ انیمیشن و «یک گام عقب» از `ordinal` می‌آیند.
 */
private enum class PasscodeStep { Create, Confirm, Finish }

private const val STEP_SLIDE_PX = 24

/**
 * کلیدها این‌جا کوچک‌ترند از صفحهٔ قفل.
 *
 * آن‌جا کلِ صفحه در اختیارِ صفحه‌کلید است؛ این‌جا نوارِ عنوان و توضیح و نقطه‌ها هم بالای آن
 * می‌نشینند و با کلیدِ ۸۸ روی گوشی‌های کوتاه، ردیفِ آخر از شیت بیرون می‌زد — و این شیت اسکرول
 * ندارد.
 */
private val SHEET_KEY_SIZE = 72.dp

/**
 * تنظیمِ رمزِ برنامه.
 *
 * ### چرا از نو نوشته شد
 * نسخهٔ قبلی رمز را با **دو `OutlinedTextField`** می‌گرفت، در حالی که همان رمز بعداً باید با
 * صفحه‌کلیدِ عددیِ خودِ برنامه وارد می‌شد؛ کاربر چیزی می‌ساخت که شکلِ واردکردنش را ندیده بود.
 * ضمن اینکه یک `Box` + `Surface`ِ دست‌ساز بود با `if (!visible) return`، یعنی نه ورود و خروجی
 * داشت و نه دکمهٔ برگشتِ دستگاه را می‌گرفت.
 *
 * حالا همان [PasscodeKeypad] و [PasscodeDots]ِ صفحهٔ بازکردنِ قفل را دارد و روی
 * [AnimatedBottomSheetCard] می‌نشیند.
 *
 * ### تأیید
 * رمز دو بار گرفته می‌شود و مرحلهٔ دوم با پُرشدنِ نقطه‌ها خودش سنجیده می‌شود — دکمهٔ «تأیید»
 * ندارد چون کارِ اضافه‌ای برای کاربر می‌سازد. اگر یکی نبود، نقطه‌ها قرمز می‌شوند و همان مرحله از
 * نو شروع می‌شود؛ برگشتن به مرحلهٔ اول یعنی کاربر رمزِ اولش را هم دوباره بزند، که تنبیهِ بی‌دلیل
 * است.
 */
@Composable
fun PasscodeSetupSheet(
    visible: Boolean,
    biometricAvailable: Boolean,
    defaultBiometricEnabled: Boolean,
    onClose: () -> Unit,
    onSubmit: (passcode: String, biometricEnabled: Boolean) -> Unit
) {
    // TASK-02/TD-36 — block screen capture during passcode setup.
    SecureFlagEffect(active = visible)

    var step by remember { mutableStateOf(PasscodeStep.Create) }
    var passcode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    var biometricEnabled by remember { mutableStateOf(defaultBiometricEnabled) }

    // ⚠️ `rememberSaveable` نیست و نباید باشد: رمز نباید در `savedInstanceState` بنویسد.
    // بازکردنِ دوباره هم همه‌چیز را از نو شروع می‌کند.
    LaunchedEffect(visible) {
        if (visible) {
            step = PasscodeStep.Create
            passcode = ""
            confirm = ""
            mismatch = false
            biometricEnabled = defaultBiometricEnabled
        }
    }

    // پُرشدنِ نقطه‌های مرحلهٔ اول یعنی گامِ بعد؛ منتظرِ دکمه نمی‌ماند.
    LaunchedEffect(passcode) {
        if (step == PasscodeStep.Create && passcode.length == AppLockManager.PASSCODE_LENGTH) {
            step = PasscodeStep.Confirm
        }
    }

    LaunchedEffect(confirm) {
        if (confirm.length != AppLockManager.PASSCODE_LENGTH) return@LaunchedEffect
        if (confirm == passcode) {
            mismatch = false
            step = PasscodeStep.Finish
        } else {
            // نقطه‌ها یک لحظه قرمز می‌مانند و بعد پاک می‌شوند؛ پاک‌کردنِ بی‌درنگ، خطا را
            // نادیدنی می‌کرد.
            mismatch = true
        }
    }

    // پاک‌کردنِ خطا و ورودی با هم انجام می‌شود؛ اگر فقط `confirm` پاک شود، `mismatch` روشن
    // می‌ماند و صفحه‌کلید (که به `!mismatch` گره خورده) برای همیشه قفل می‌شود.
    LaunchedEffect(mismatch) {
        if (!mismatch) return@LaunchedEffect
        delay(600)
        confirm = ""
        mismatch = false
    }

    val handleBack: () -> Unit = {
        when (step) {
            PasscodeStep.Create -> onClose()
            PasscodeStep.Confirm -> {
                confirm = ""
                mismatch = false
                passcode = ""
                step = PasscodeStep.Create
            }

            PasscodeStep.Finish -> {
                confirm = ""
                step = PasscodeStep.Confirm
            }
        }
    }

    AnimatedBottomSheetCard(
        visible = visible,
        title = when (step) {
            PasscodeStep.Create -> "یک رمز بسازید"
            PasscodeStep.Confirm -> "دوباره وارد کنید"
            PasscodeStep.Finish -> "تقریباً تمام است"
        },
        onDismiss = handleBack
    ) {
        PickerSheetBody {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = { passcodeStepTransition(targetState > initialState) },
                label = "passcode_step",
                modifier = Modifier.fillMaxWidth()
            ) { current ->
                // ⚠️ `current` و نه `step`: در حینِ گذار هر دو گام روی صفحه‌اند.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (current) {
                        PasscodeStep.Create -> KeypadStep(
                            caption = "یک رمز ${AppLockManager.PASSCODE_LENGTH} رقمی انتخاب کنید. هر بار که برنامه را باز کنید همین را می‌خواهیم.",
                            digits = passcode,
                            errorText = null,
                            onDigit = { passcode += it },
                            onBackspace = { passcode = passcode.dropLast(1) }
                        )

                        PasscodeStep.Confirm -> KeypadStep(
                            caption = "همان رمز را یک بار دیگر بزنید تا مطمئن شویم درست به خاطر سپرده‌اید.",
                            digits = confirm,
                            errorText = if (mismatch) "با رمزِ قبلی یکی نیست." else null,
                            // ورودی وقتی خطا نشان داده می‌شود قفل است تا رقمِ تازه وسطِ پاک‌شدن
                            // ننشیند.
                            enabled = !mismatch,
                            onDigit = { confirm += it },
                            onBackspace = { confirm = confirm.dropLast(1) }
                        )

                        PasscodeStep.Finish -> FinishStep(
                            biometricAvailable = biometricAvailable,
                            biometricEnabled = biometricEnabled,
                            onBiometricChange = { biometricEnabled = it },
                            onConfirm = {
                                onSubmit(passcode, biometricEnabled && biometricAvailable)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** گامِ ورودِ رقم — توضیح، نقطه‌ها، خطا، و صفحه‌کلید. */
@Composable
private fun KeypadStep(
    caption: String,
    digits: String,
    errorText: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true
) {
    Spacer(Modifier.height(16.dp))

    Text(
        text = caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = IranSansRegular,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )

    Spacer(Modifier.height(22.dp))

    PasscodeDots(filledCount = digits.length, errorColor = errorText != null)

    // جای پیام همیشه گرفته می‌شود تا با آمدن و رفتنِ خطا، صفحه‌کلید بالا و پایین نپرد.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        contentAlignment = Alignment.Center
    ) {
        if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                fontFamily = IranSansRegular,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    PasscodeKeypad(
        enabled = enabled,
        canBackspace = digits.isNotEmpty(),
        keySize = SHEET_KEY_SIZE,
        onDigit = { if (digits.length < AppLockManager.PASSCODE_LENGTH) onDigit(it) },
        onBackspace = onBackspace
    )

    Spacer(Modifier.height(12.dp))
}

/** گامِ پایانی — اثر انگشت و تأیید. */
@Composable
private fun FinishStep(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onBiometricChange: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    Spacer(Modifier.height(16.dp))

    SwitchRow(
        label = "ورود با اثر انگشت",
        checked = biometricEnabled && biometricAvailable,
        enabled = biometricAvailable,
        onCheckedChange = onBiometricChange
    )

    Spacer(Modifier.height(8.dp))
    SwitchCaption(
        if (biometricAvailable) {
            "به‌جای زدنِ رمز، با اثر انگشت وارد می‌شوید. رمز هم سرِ جایش می‌ماند و هر وقت اثر انگشت جواب ندهد از آن استفاده می‌کنید."
        } else {
            "این دستگاه اثر انگشت یا تشخیص چهره ندارد، پس ورود فقط با رمز خواهد بود."
        }
    )

    Spacer(Modifier.height(16.dp))
    SwitchCaption(
        "اگر این رمز را فراموش کنید، راهی برای بازیابی‌اش نیست. تنها راه، افزودنِ دوبارهٔ کیف پول با عبارت بازیابی است."
    )

    Spacer(Modifier.height(22.dp))
    PrimaryButton(text = "فعال‌سازی قفل", onClick = onConfirm)
}

/**
 * همان گذارِ گام‌به‌گامِ شیتِ پشتیبانی: محو + لغزشِ کوتاه + [SizeTransform].
 *
 * `SizeTransform` این‌جا از آن‌جا مهم‌تر است — گامِ پایانی صفحه‌کلید ندارد و ارتفاعِ شیت
 * ناگهان نصف می‌شود.
 */
private fun passcodeStepTransition(forward: Boolean): ContentTransform {
    val enterOffset = if (forward) STEP_SLIDE_PX else -STEP_SLIDE_PX
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

@Preview
@Composable
private fun PasscodeSetupSheetPreview() {
    MegaWalletTheme {
        Box(
            modifier = Modifier
                .width(360.dp)
                .height(800.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            PasscodeSetupSheet(
                visible = true,
                biometricAvailable = true,
                defaultBiometricEnabled = true,
                onClose = {},
                onSubmit = { _, _ -> }
            )
        }
    }
}
