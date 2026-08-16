package com.mtd.megawallet.ui.compose.screens.wallet

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.theme.Green
import com.mtd.common_ui.theme.IranSansBold
import com.mtd.common_ui.theme.IranSansRegular
import com.mtd.common_ui.theme.IranSansRegularMedium
import com.mtd.domain.model.ResultResponse
import com.mtd.megawallet.security.BiometricAuthHelper
import com.mtd.megawallet.ui.compose.WelcomeActivityCompose
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.CloudBackupPasswordScreen
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.CloudPasswordMode
import com.mtd.megawallet.ui.compose.screens.createwallet.TermsPart
import com.mtd.megawallet.ui.compose.screens.security.PasscodeSetupSheet
import com.mtd.megawallet.ui.compose.screens.security.SecuritySettingsSheet
import com.mtd.megawallet.ui.compose.screens.settings.SettingsScreen
import com.mtd.megawallet.ui.compose.screens.wallet.components.REMOVAL_TERMS

import com.mtd.megawallet.ui.compose.screens.wallet.components.SecretRecoveryPromptBottomSheet
import com.mtd.megawallet.ui.compose.screens.wallet.components.SecretRevealOverlay
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletCard
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletManagementMenuContent
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletPersonalizationContent
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletRecoveryMethodsContent
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletRemovalDone
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletRemovalIntro
import com.mtd.megawallet.viewmodel.AppLockViewModel
import com.mtd.megawallet.viewmodel.MultiWalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val PremiumSpringInt = spring<IntOffset>(
    dampingRatio = 0.82f,
    stiffness = 380f
)

private enum class BackupFlowStep {
    None,
    Revealing,
    VerifyingManual,
    CloudPassword,
    Success
}



@OptIn(ExperimentalAnimationApi::class)
private enum class RemovalStep { Intro, Terms, Deleting, Collapsing, Shrinking, Fading, Done }

/** کارت پس از تاییدِ نهایی چند ثانیه دیده می‌شود، تا معلوم باشد کدام کیف در دستِ حذف است. */
private const val REMOVAL_VISIBLE_MS = 5_000L

/**
 * مدتِ هر گامِ جمع‌شدن.
 *
 * ⚠️ هرکدام باید از انیمیشنِ متناظرش در `WalletCard` بلندتر باشد (`COLLAPSE_DURATION_MS`،
 * `SHRINK_DURATION_MS`، `COLLAPSE_FADE_MS`)، وگرنه گامِ بعدی پیش از تمام‌شدنِ گامِ قبلی شروع
 * می‌شود و از بیرون شبیهِ «یکباره رفت» به نظر می‌رسد. اختلاف، همان مکثی است که کارت را در
 * حالتِ خط و مربع نگه می‌دارد.
 */
private const val REMOVAL_COLLAPSE_MS = 1_100L
private const val REMOVAL_SHRINK_MS = 600L
private const val REMOVAL_FADE_MS = 450L

/**
 * منحنیِ **قرینهٔ** حرکتِ کارت در فلوی حذف: آرام راه می‌افتد، وسطِ مسیر تند می‌شود، آرام
 * می‌نشیند.
 *
 * نکته‌اش قرینه‌بودن است: وارونِ یک بزیهٔ مکعبی `(x1, y1, x2, y2)` برابرِ
 * `(1-x2, 1-y2, 1-x1, 1-y1)` است، و برای `(0.42, 0, 0.58, 1)` همان خودش درمی‌آید. پس رفت و
 * برگشت بدونِ هیچ شرطی دقیقاً یک حرکت‌اند.
 *
 * `FastOutSlowIn` این خاصیت را ندارد — تند شروع می‌کند و آرام تمام. اگر در هر دو جهت به کار
 * برود برگشت شبیهِ پرتاب‌شدن به وسط می‌شود، و اگر آینه‌اش را در برگشت بگذاریم کارت با سرعت
 * به وسط می‌رسد. هر دو را امتحان کردیم و هیچ‌کدام درست نبود.
 */
private val SymmetricSlideEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)



@Composable
fun MultiWalletScreen(
    onNavigateBack: () -> Unit,
    onAddNewWallet: () -> Unit,
    onImportExisting: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MultiWalletViewModel = hiltViewModel()
) {
    val appLockViewModel: AppLockViewModel = hiltViewModel()
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSecuritySettings by remember { mutableStateOf(false) }
    var showPasscodeSetup by remember { mutableStateOf(false) }
    val activity = LocalActivity.current as? FragmentActivity
    val biometricAvailable = remember(activity) {
        activity?.let(BiometricAuthHelper::isBiometricAvailable) ?: false
    }
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val activeWalletId by viewModel.activeWalletId.collectAsStateWithLifecycle()

    var showAddWalletSheet by remember { mutableStateOf(false) }
    var showSecretPromptSheet by remember { mutableStateOf(false) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectedWalletId by remember { mutableStateOf<String?>(null) }
    val isAnyCardExpanded = selectedWalletId != null

    // فلوی حذف. `selectedWalletId` عمداً پاک نمی‌شود: کارت باید همان کارتِ بازشده بماند تا
    // حرکتش به وسط ادامهٔ همان انیمیشن باشد، نه یک ورودِ تازه.
    var removingWalletId by remember { mutableStateOf<String?>(null) }
    val isRemoving = removingWalletId != null

    // مرحلهٔ فلوی حذف. از دو بولین گذشت، پس enum شد تا حالتِ ناممکن ساخته نشود.
    var removalStep by remember { mutableStateOf(RemovalStep.Intro) }
    val removalTermsOpen = removalStep == RemovalStep.Terms

    // هر سه پله‌ای‌اند، نه انیمیت‌شده: نرم‌کردنِ حرکت کارِ خودِ `WalletCard` است و اگر این‌جا هم
    // انیمیت می‌شد، دو انیمیشن روی هم می‌افتادند.
    val removalCollapse = if (removalStep >= RemovalStep.Collapsing) 1f else 0f
    val removalShrink = if (removalStep >= RemovalStep.Shrinking) 1f else 0f
    val removalAlpha = if (removalStep >= RemovalStep.Fading) 0f else 1f
    var removalAccepted by remember { mutableStateOf(List(REMOVAL_TERMS.size) { false }) }

    // دروازهٔ احراز هویت. `sawLock` جلوی یک ریسِ واقعی را می‌گیرد: بدونِ آن، `!isLocked` ممکن
    // است از حالتِ *قبلیِ* بازبودن صادق باشد و کارِ حذف پیش از هر احرازِ تازه‌ای اجرا شود.
    var pendingRemovalAuth by remember { mutableStateOf(false) }
    var removalAuthSawLock by remember { mutableStateOf(false) }

    // پیش از حذف خوانده می‌شود؛ بعدش فهرست خالی است و دیگر نمی‌شود فهمید آخرین بوده یا نه.
    var removalWasLastWallet by remember { mutableStateOf(false) }

    // کلیدِ توالیِ پایانی. عمداً جدا از `removalStep` است: افکت نباید روی چیزی کلید بخورد که
    // خودش آن را عوض می‌کند، وگرنه با اولین تغییرِ مرحله کنسل و از نو اجرا می‌شود و بقیهٔ
    // توالی هرگز نمی‌رسد.
    var removalConfirmed by remember { mutableStateOf(false) }

    // با بسته‌شدنِ فلو همه‌چیز به حالتِ اولش برمی‌گردد، وگرنه بارِ بعد بندها از قبل تیک‌خورده‌اند.
    LaunchedEffect(isRemoving) {
        if (!isRemoving) {
            removalStep = RemovalStep.Intro
            removalAccepted = List(REMOVAL_TERMS.size) { false }
            pendingRemovalAuth = false
            removalAuthSawLock = false
            removalConfirmed = false
        }
    }

    // توالیِ پایانیِ حذف. زمان‌محور است و منتظرِ `deleteWallet` نمی‌ماند: برای آخرین کیف، حذف
    // برنامه را قفل می‌کند و وضعیت را عوض می‌کند، و انتظار برای آن یعنی اسپینر تا ابد می‌چرخد.
    LaunchedEffect(removalConfirmed) {
        if (!removalConfirmed) return@LaunchedEffect
        // پیش از حذف خوانده می‌شود؛ بعدش فهرست خالی است.
        removalWasLastWallet = wallets.size <= 1
        val idToRemove = removingWalletId

        delay(REMOVAL_VISIBLE_MS)
        // محتوا محو می‌شود و ارتفاعِ کارت تا یک خطِ باریکِ تمام‌عرض کم می‌شود.
        removalStep = RemovalStep.Collapsing
        delay(REMOVAL_COLLAPSE_MS)
        // خط از دو طرف جمع می‌شود تا یک مربعِ کوچک.
        removalStep = RemovalStep.Shrinking
        delay(REMOVAL_SHRINK_MS)
        // مربع محو می‌شود و هم‌زمان صفحهٔ پایان ظاهر می‌شود — هر دو از همین یک تغییرِ حالت.
        removalStep = RemovalStep.Fading
        delay(REMOVAL_FADE_MS)

        // حذفِ واقعی، پس از پایانِ همهٔ انیمیشن‌ها. `deleteWallet` بی‌درنگ برمی‌گردد ولی کارش
        // در `viewModelScope` روی ترد اصلی ادامه دارد (و `loadWallets` هم پشتش است)؛ اگر
        // وسطِ محوشدن صدا زده شود، همان‌جا فریم می‌افتد و بینِ کارت و متن‌ها وقفه دیده می‌شود.
        // خطا را خودِ ViewModel نشان می‌دهد.
        idToRemove?.let { viewModel.deleteWallet(it) }

        removalStep = RemovalStep.Done
    }

    // Personalization states
    var isPersonalizing by remember { mutableStateOf(false) }
    var isShowingRecovery by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editColor by remember { mutableStateOf(Color.Unspecified) }
    var isEditingNickname by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()
    var backupFlowStep by remember { mutableStateOf(BackupFlowStep.None) }
    var revealMethod by remember { mutableStateOf("") } // "cloud" or "manual"
    var secretData by remember { mutableStateOf("") }
    var isCloudRecoveryMode by remember { mutableStateOf(false) }
    var isCloudBackupLoading by remember { mutableStateOf(false) }
    var pendingCloudSignInFlow by remember { mutableStateOf(false) }
    var cloudPasswordError by remember { mutableStateOf<String?>(null) }
    var pendingSecureReveal by remember { mutableStateOf(false) }
    var pendingSecureRevealNonce by remember { mutableStateOf(0L) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!pendingCloudSignInFlow) return@rememberLauncherForActivityResult

        scope.launch {
            isCloudBackupLoading = true
            when (val signInResult = viewModel.handleCloudGoogleSignInResult(result.data)) {
                is ResultResponse.Success -> {
                    isCloudRecoveryMode = signInResult.data
                    cloudPasswordError = null
                    backupFlowStep = BackupFlowStep.CloudPassword
                }

                is ResultResponse.Error -> {
                    isCloudRecoveryMode = true
                    cloudPasswordError = "اتصال به گوگل درایو برقرار نشد. دوباره تلاش کنید."
                    backupFlowStep = BackupFlowStep.CloudPassword
                }
            }
            isCloudBackupLoading = false
            pendingCloudSignInFlow = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadWallets()
    }

    val triggerSecretReveal: () -> Unit = {
        backupFlowStep = BackupFlowStep.Revealing
        scope.launch {
            selectedWalletId?.let { id ->
                val secret = viewModel.getMnemonic(id)
                if (secret != null) {
                    secretData = secret
                } else {
                    backupFlowStep = BackupFlowStep.None
                }
            }
        }
    }

    LaunchedEffect(pendingSecureReveal, appLockUiState.isLocked) {
        if (pendingSecureReveal && !appLockUiState.isLocked) {
            pendingSecureReveal = false
            if (appLockUiState.authCancelNonce == pendingSecureRevealNonce) {
                triggerSecretReveal()
            } else {
                backupFlowStep = BackupFlowStep.None
            }
        }
    }

    LaunchedEffect(appLockUiState.authCancelNonce) {
        if (pendingSecureReveal && appLockUiState.authCancelNonce > 0L) {
            pendingSecureReveal = false
            pendingSecureRevealNonce = appLockUiState.authCancelNonce
            backupFlowStep = BackupFlowStep.None
        }
    }

    // قفل باید *واقعاً* افتاده باشد. بدونِ این پرچم، `!isLocked` از حالتِ قبلیِ باز صادق است و
    // حذف بدونِ هیچ احرازِ تازه‌ای رد می‌شود.
    LaunchedEffect(pendingRemovalAuth, appLockUiState.isLocked) {
        if (pendingRemovalAuth && appLockUiState.isLocked) {
            removalAuthSawLock = true
        } else if (pendingRemovalAuth && removalAuthSawLock && !appLockUiState.isLocked) {
            pendingRemovalAuth = false
            removalAuthSawLock = false
            removalStep = RemovalStep.Deleting
            removalConfirmed = true
        }
    }

    // انصرافِ کاربر از احراز، فلو را در همان مرحلهٔ بندها نگه می‌دارد و چیزی حذف نمی‌شود.
    LaunchedEffect(appLockUiState.authCancelNonce) {
        if (pendingRemovalAuth && appLockUiState.authCancelNonce > 0L) {
            pendingRemovalAuth = false
            removalAuthSawLock = false
        }
    }

    val handleBack = {
        when {
            // اولین چیزی که برگشت می‌خورد: کارت به جایگاهش و رنگِ خودش برمی‌گردد و منوی کیف
            // دوباره می‌آید. چون همه‌چیز از روی همین یک state خوانده می‌شود، معکوسِ انیمیشن
            // خودبه‌خود اجرا می‌شود و جایی دستی نوشته نشده.
            // از لحظهٔ شروعِ حذف به بعد برگشت معنا ندارد — کاری که انجام شده برنمی‌گردد.
            removingWalletId != null && removalStep >= RemovalStep.Deleting -> Unit
            removalStep == RemovalStep.Terms -> removalStep = RemovalStep.Intro
            removingWalletId != null -> removingWalletId = null

            backupFlowStep == BackupFlowStep.VerifyingManual -> backupFlowStep =
                BackupFlowStep.Revealing

            backupFlowStep == BackupFlowStep.CloudPassword -> backupFlowStep =
                BackupFlowStep.Revealing

            backupFlowStep != BackupFlowStep.None -> backupFlowStep = BackupFlowStep.None
            isShowingRecovery -> isShowingRecovery = false
            isEditingNickname -> isEditingNickname = false
            isPersonalizing -> isPersonalizing = false
            else -> {
                selectedWalletId = null
            }
        }
    }

    BackHandler(enabled = showSettings || isAnyCardExpanded || backupFlowStep != BackupFlowStep.None) {
        if (showSettings) showSettings = false else handleBack()
    }

    // نرم کردن تغییر آلفای اجزای صفحه
    val contentAlpha by animateFloatAsState(
        targetValue = if (isAnyCardExpanded || backupFlowStep != BackupFlowStep.None) 0f else 1f,
        animationSpec = tween(400),
        label = "content_alpha"
    )

    // مسافتِ خروجِ فلوی حذف: به‌اندازهٔ عرضِ واقعیِ صفحه، نه عددِ ثابتِ ۱۰۰۰dp.
    //
    // در پشتیبانِ دستی ۱۰۰۰dp بی‌ایراد است چون چیزی به وسط برنمی‌گردد. این‌جا برمی‌گردد، و
    // روی صفحهٔ ~۴۰۰dp بیشترِ آن ۸۰۰ms صرفِ حرکتِ نادیدنیِ بیرونِ صفحه می‌شد: رفتن تند دیده
    // می‌شد و برگشت یک مکثِ طولانی و بعد یک خزیدنِ کند به وسط. با عرضِ صفحه، رفت و برگشت
    // آینهٔ هم می‌شوند.
    val removalSlideDensity = LocalDensity.current
    val removalSlideOut = rootCoordinates?.let { coords ->
        with(removalSlideDensity) { coords.size.width.toDp() } * 1.1f
    } ?: 420.dp

    // انیمیشن خروج لیست والت‌ها به چپ هنگام شروع تست بک‌آپ.
    // مرحلهٔ بندهای حذف هم از همین‌جا رد می‌شود، نه با حرکتِ جداگانهٔ خودِ کارت: هر دو یک کارِ
    // واحدند (کنار رفتنِ صفحهٔ کیف‌پول‌ها تا صفحهٔ بعدی بیاید) و باید یک‌جور دیده شوند.
    val listOffsetX by animateDpAsState(
        targetValue = when {
            backupFlowStep == BackupFlowStep.VerifyingManual ||
                backupFlowStep == BackupFlowStep.CloudPassword -> (-1000).dp

            removalTermsOpen -> -removalSlideOut

            else -> 0.dp
        },
        animationSpec = tween(
            durationMillis = 800,
            // فلوی حذف منحنیِ قرینه می‌گیرد تا رفت و برگشتش یک حرکتِ واحد باشد؛ پشتیبانِ دستی
            // دست‌نخورده می‌ماند چون آن‌جا چیزی به وسط برنمی‌گردد که تفاوت دیده شود.
            easing = if (isRemoving) SymmetricSlideEasing else FastOutSlowInEasing
        ),
        label = "list_offset_x"
    )


    val settingsOffset by animateDpAsState(
        targetValue = if (showSettings) 96.dp else 0.dp,
        animationSpec = tween(420),
        label = "multi_wallet_settings_offset"
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(modifier)
                .offset(y = settingsOffset)
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { rootCoordinates = it }
        ) {
            // ۱. لیست اصلی والت‌ها
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = !isAnyCardExpanded)
                    .padding(bottom = 16.dp)
                    .graphicsLayer { translationX = listOffsetX.toPx() } // Apply offset here
            ) {
                // فضای خالی برای هدر ثابت
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(72.dp)
                )

                Text(
                    text = "کیف پول‌های شما",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = IranSansBold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { alpha = contentAlpha }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // در فلوی حذف خالی‌شدنِ فهرست نتیجهٔ خودِ حذف است، نه بارگذاری. بدونِ این شرط،
                // اسپینر پشتِ صفحهٔ «کیف پول حذف شد» ظاهر می‌شد.
                if (wallets.isEmpty() && !isRemoving) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    wallets.chunked(2).forEach { rowWallets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(25.dp)
                        ) {
                            rowWallets.forEach { item ->
                                val isExpanded = selectedWalletId == item.wallet.id
                                val isAnyOtherExpanded = isAnyCardExpanded && !isExpanded
                                val keepRevealState =
                                    backupFlowStep == BackupFlowStep.Revealing || backupFlowStep == BackupFlowStep.VerifyingManual || backupFlowStep == BackupFlowStep.CloudPassword

                                Box(modifier = Modifier.weight(1f)) {
                                    WalletCard(
                                        wallet = item.wallet,
                                        balance = item.totalBalance,
                                        isActive = item.wallet.id == activeWalletId,
                                        isExpanded = isExpanded,
                                        isAnyOtherExpanded = isAnyOtherExpanded,
                                        rootCoordinates = rootCoordinates,
                                        isManualBackedUp = item.isManualBackedUp || (backupFlowStep == BackupFlowStep.Success && selectedWalletId == item.wallet.id && revealMethod == "manual"),
                                        isCloudBackedUp = item.isCloudBackedUp || (backupFlowStep == BackupFlowStep.Success && selectedWalletId == item.wallet.id && revealMethod == "cloud"),
                                        isPersonalizing = isPersonalizing && isExpanded,
                                        isEditingNickname = isEditingNickname && isExpanded,
                                        hideActions = backupFlowStep != BackupFlowStep.None,
                                        editName = editName,
                                        editColor = editColor,
                                        onSelect = {
                                            if (!isAnyCardExpanded) {
                                                viewModel.switchWallet(item.wallet.id)
                                                onNavigateBack()
                                            }
                                        },
                                        onToggleExpand = {
                                            if (!isAnyOtherExpanded) {
                                                if (isExpanded) {
                                                    isPersonalizing = false
                                                    isEditingNickname = false
                                                    selectedWalletId = null
                                                } else {
                                                    selectedWalletId = item.wallet.id
                                                }
                                            }
                                        },
                                        onSettingsClick = {
                                            editName = item.wallet.name
                                            editColor = Color(item.wallet.color)
                                            isPersonalizing = true
                                        },
                                        onNameChange = { editName = it },
                                        onEditNicknameToggle = {
                                            isEditingNickname = !isEditingNickname
                                        },
                                        isRevealingSecret = keepRevealState && isExpanded,
                                        isBackupSuccess = backupFlowStep == BackupFlowStep.Success && isExpanded,
                                        isRemoving = removingWalletId == item.wallet.id,
                                        isRemovalInProgress = removalStep >= RemovalStep.Deleting,
                                        removalCollapse = if (removingWalletId == item.wallet.id) {
                                            removalCollapse
                                        } else 0f,
                                        removalShrink = if (removingWalletId == item.wallet.id) {
                                            removalShrink
                                        } else 0f,
                                        removalAlpha = if (removingWalletId == item.wallet.id) {
                                            removalAlpha
                                        } else 1f,
                                        overrideColor = if (removingWalletId == item.wallet.id) {
                                            MaterialTheme.colorScheme.error
                                        } else null,
                                        secretData = secretData,
                                        focusRequester = focusRequester
                                    )
                                }
                            }
                            if (rowWallets.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(64.dp))
            }

            // ۲. متن توضیحات و هدر در یک ستون ساختاریافته
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(3000f)
            ) {
                MultiWalletHeader(
                    isExpanded = isAnyCardExpanded,
                    // از شروعِ حذف به بعد برگشت معنا ندارد و `handleBack` هم نادیده‌اش می‌گیرد،
                    // پس دکمه‌ای که کاری نمی‌کند نباید دیده شود.
                    showBack = removalStep < RemovalStep.Deleting,
                    onBackClick = onNavigateBack,
                    onSettingsClick = {
                        selectedWalletId = null
                        showSettings = true
                    },
                    onAddClick = { showAddWalletSheet = true },
                    onCollapse = handleBack,
                    contentAlpha = contentAlpha
                )

                AnimatedVisibility(
                    visible = isPersonalizing,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { -20 },
                        animationSpec = PremiumSpringInt
                    ),
                    exit = fadeOut(tween(300)) + slideOutVertically(
                        targetOffsetY = { -20 },
                        animationSpec = PremiumSpringInt
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "نام مستعار و رنگ کیف پول شما خصوصی است و فقط برای شما قابل مشاهده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        textAlign = TextAlign.Center,
                        fontFamily = IranSansRegular,
                        fontSize = 14.sp
                    )
                }
            }

            // ۳. لایه منوی مدیریت (با موقعیت داینامیک زیر کارت)
            AnimatedVisibility(
                // با شروعِ حذف، آیتم‌های منو با همین exit از صفحه می‌روند.
                visible = isAnyCardExpanded && backupFlowStep == BackupFlowStep.None && !isRemoving,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2000f)
            ) {
                val density = LocalDensity.current
                val screenWidthDp =
                    if (rootCoordinates != null) with(density) { rootCoordinates!!.size.width.toDp() } else 360.dp
                val cardTargetHeight = (screenWidthDp - 48.dp) * 0.61f
                val cardTargetY = if (isPersonalizing) 180.dp else 120.dp
                val menuTopPadding = cardTargetY + cardTargetHeight + 20.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = menuTopPadding, start = 24.dp, end = 24.dp)
                ) {
                    val selectedWalletUiItem = wallets.find { it.wallet.id == selectedWalletId }

                    val menuState = when {
                        isPersonalizing -> "personalize"
                        isShowingRecovery -> "recovery"
                        else -> "main"
                    }

                    AnimatedContent(
                        targetState = menuState,
                        transitionSpec = {
                            if (targetState == "recovery" || (initialState == "main" && targetState == "personalize")) {
                                slideInHorizontally { it } + fadeIn() togetherWith (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith (slideOutHorizontally { it } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "menu_transition"
                    ) { state ->
                        when (state) {
                            "personalize" -> {
                                WalletPersonalizationContent(
                                    selectedColor = editColor,
                                    onColorSelect = { editColor = it },
                                    onSave = {
                                        selectedWalletId?.let { id ->
                                            scope.launch {
                                                viewModel.updateWalletName(id, editName)
                                                viewModel.updateWalletColor(id, editColor.toArgb())
                                                isPersonalizing = false
                                            }
                                        }
                                    }
                                )
                            }

                            "recovery" -> {
                                val selectedWalletUiItem =
                                    wallets.find { it.wallet.id == selectedWalletId }
                                WalletRecoveryMethodsContent(
                                    isManualBackedUp = selectedWalletUiItem?.isManualBackedUp == true,
                                    isCloudBackedUp = selectedWalletUiItem?.isCloudBackedUp == true,
                                    onMethodClick = { type ->
                                        revealMethod = type
                                        showSecretPromptSheet = true
                                    }
                                )
                            }

                            else -> {
                                WalletManagementMenuContent(
                                    isBackedUp = selectedWalletUiItem?.isManualBackedUp == true || selectedWalletUiItem?.isCloudBackedUp == true,
                                    onBackupClick = { isShowingRecovery = true },
                                    onSettings = {
                                        editName = selectedWalletUiItem?.wallet?.name ?: ""
                                        editColor = Color(
                                            selectedWalletUiItem?.wallet?.color
                                                ?: 0xFF00FF00.toInt()
                                        )
                                        isPersonalizing = true
                                    },
                                    // دیگر همین‌جا حذف نمی‌کند: فلو را باز می‌کند. خودِ حذف در
                                    // انتهای فلو و پس از تاییدها انجام می‌شود.
                                    onDelete = {
                                        selectedWalletId?.let { id -> removingWalletId = id }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ۴. لایهٔ فلوی حذف — هم‌مرتبه با لایهٔ منو، چون جایگزینِ آن می‌شود نه رویش.
            AnimatedVisibility(
                // با شروعِ حذف، متن‌های بالا و پایین هم با همین exit محو می‌شوند و فقط کارت
                // وسطِ صفحه می‌ماند.
                visible = isRemoving && removalStep == RemovalStep.Intro,
                enter = fadeIn(tween(280, delayMillis = 120)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2000f)
            ) {
                WalletRemovalIntro(
                    onClose = { removingWalletId = null },
                    onContinue = { removalStep = RemovalStep.Terms }
                )
            }

            // ۵. مرحلهٔ دوم — بندهایی که باید تایید شوند. کارت تا این لحظه کوچک شده و بالای
            // صفحه نشسته، پس این لایه از زیرِ آن شروع می‌شود.
            AnimatedVisibility(
                visible = isRemoving && removalTermsOpen,
                enter = fadeIn(tween(280, delayMillis = 140)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2000f)
            ) {
                // ستونِ کیف‌پول‌ها با کارتِ داخلش به چپ رفته، پس بندها کلِ صفحه را دارند.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    TermsPart(
                        terms = REMOVAL_TERMS,
                        accepted = removalAccepted,
                        accentColor = MaterialTheme.colorScheme.error,
                        onToggle = { index ->
                            removalAccepted = removalAccepted.toMutableList()
                                .also { it[index] = !it[index] }
                        },
                        onContinue = {
                            // اگر قفلِ برنامه روشن است، حذف تا احرازِ تازه اجرا نمی‌شود.
                            if (appLockUiState.snapshot.appLockEnabled) {
                                removalAuthSawLock = false
                                pendingRemovalAuth = true
                                appLockViewModel.lockNowForSensitiveAction()
                            } else {
                                removalStep = RemovalStep.Deleting
                                removalConfirmed = true
                            }
                        },
                        title = "پیش از ادامه",
                        subtitle = "برای ادامه، باید موارد زیر را مطالعه کرده و تایید کنید.",
                        footerNote = "با تیک‌زدن موارد بالا، آن‌ها را پذیرفته‌اید.",
                        buttonText = "تایید و حذف کیف پول"
                    )
                }
            }

            // ۶. صفحهٔ پایان. از لحظهٔ جمع‌شدنِ عرض می‌آید، نه از محوشدنِ alpha: کارت وقتی به
            // مربعِ ۵dp می‌رسد عملاً دیده نمی‌شود، پس «محو شدنِ کارت» از دیدِ کاربر همان‌جا
            // تمام شده و هر چیزی بعد از آن وقفه به نظر می‌رسد.
            AnimatedVisibility(
                visible = isRemoving && removalStep >= RemovalStep.Shrinking,
                enter = fadeIn(tween(320)),
                exit = fadeOut(tween(160)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2000f)
            ) {
                WalletRemovalDone(
                    onDone = {
                        // متن‌ها پیش از پایانِ حذف دیده می‌شوند، پس دکمه تا آن لحظه بی‌اثر است.
                        if (removalStep != RemovalStep.Done) return@WalletRemovalDone

                        if (removalWasLastWallet) {
                            // کیفی نمانده، پس ماندن در این صفحه بی‌معناست: برنامه از ابتدا
                            // بالا می‌آید و کاربر همان مسیرِ اولِ نصب را می‌بیند.
                            activity?.let { act ->
                                act.startActivity(
                                    Intent(act, WelcomeActivityCompose::class.java).apply {
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        )
                                        // اسپلش تصمیم می‌گیرد «خانه» یا «شروع»؛ اینجا جواب
                                        // معلوم است، پس فقط یک لوگوی تکراری بود.
                                        putExtra(
                                            WelcomeActivityCompose.EXTRA_SKIP_SPLASH,
                                            true
                                        )
                                    }
                                )
                                act.finish()
                            }
                        } else {
                            removingWalletId = null
                            selectedWalletId = null
                        }
                    }
                )
            }

            AddWalletBottomSheet(
                visible = showAddWalletSheet,
                onDismiss = { showAddWalletSheet = false },
                onCreateNew = onAddNewWallet,
                onImportExisting = onImportExisting
            )

            val selectedWalletUiItem = wallets.find { it.wallet.id == selectedWalletId }
            val effectiveManualBackedUp =
                selectedWalletUiItem?.isManualBackedUp == true ||
                        (backupFlowStep == BackupFlowStep.Success && revealMethod == "manual")
            val effectiveCloudBackedUp =
                selectedWalletUiItem?.isCloudBackedUp == true ||
                        (backupFlowStep == BackupFlowStep.Success && revealMethod == "cloud")
            SecretRecoveryPromptBottomSheet(
                visible = showSecretPromptSheet,
                isMnemonic = selectedWalletUiItem?.wallet?.hasMnemonic == true,
                onDismiss = {
                    showSecretPromptSheet = false
                    pendingSecureReveal = false
                    pendingSecureRevealNonce = appLockUiState.authCancelNonce
                },
                onReveal = {
                    showSecretPromptSheet = false
                    if (appLockUiState.snapshot.appLockEnabled) {
                        pendingSecureReveal = true
                        pendingSecureRevealNonce = appLockUiState.authCancelNonce
                        appLockViewModel.lockNowForSensitiveAction()
                    } else {
                        triggerSecretReveal()
                    }
                }
            )

            // ۴. لایه‌ی نمایش کلمات (Reveal Overlay)
            SecretRevealOverlay(
                visible = backupFlowStep == BackupFlowStep.Revealing ||
                        backupFlowStep == BackupFlowStep.VerifyingManual ||
                        backupFlowStep == BackupFlowStep.Success,
                isMnemonic = selectedWalletUiItem?.wallet?.hasMnemonic == true,
                methodType = revealMethod,
                walletColor = selectedWalletUiItem?.wallet?.color
                    ?: MaterialTheme.colorScheme.primary.toArgb(),
                isManualBackedUp = effectiveManualBackedUp,
                isCloudBackedUp = effectiveCloudBackedUp,
                isVerifyingBackup = backupFlowStep == BackupFlowStep.VerifyingManual,
                isBackupSuccess = backupFlowStep == BackupFlowStep.Success,
                isCloudActionLoading = isCloudBackupLoading,
                mnemonic = secretData,
                onStartVerification = { backupFlowStep = BackupFlowStep.VerifyingManual },
                onStartCloudBackup = {
                    selectedWalletId?.let {
                        scope.launch {
                            cloudPasswordError = null
                            isCloudBackupLoading = true
                            val hasLocalCloudHint =
                                selectedWalletUiItem?.isCloudBackedUp == true ||
                                        wallets.any { walletUi -> walletUi.isCloudBackedUp }
                            if (!viewModel.isCloudConnected()) {
                                pendingCloudSignInFlow = true
                                isCloudBackupLoading = false
                                googleSignInLauncher.launch(viewModel.getCloudSignInIntent())
                                return@launch
                            }

                            isCloudRecoveryMode = hasLocalCloudHint || viewModel.hasCloudBackup()
                            isCloudBackupLoading = false
                            backupFlowStep = BackupFlowStep.CloudPassword
                        }
                    }
                },
                onBackupConfirmed = {
                    backupFlowStep = BackupFlowStep.Revealing
                    selectedWalletId?.let { id ->
                        scope.launch {
                            viewModel.updateBackupStatus(id, manual = true)
                            delay(800)
                            backupFlowStep = BackupFlowStep.Success
                        }
                    }
                },
                onClose = {
                    backupFlowStep = BackupFlowStep.None
                }
            )

            AnimatedVisibility(
                visible = backupFlowStep == BackupFlowStep.CloudPassword,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(450)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp)
                    .zIndex(6000f)
            ) {
                CloudBackupPasswordScreen(
                    onBack = {
                        cloudPasswordError = null
                        backupFlowStep = BackupFlowStep.Revealing
                    },
                    targetColor = Color(
                        selectedWalletUiItem?.wallet?.color
                            ?: MaterialTheme.colorScheme.primary.toArgb()
                    ),
                    mode = if (isCloudRecoveryMode) {
                        CloudPasswordMode.APPEND_TO_EXISTING_BACKUP
                    } else {
                        CloudPasswordMode.CREATE_NEW_BACKUP
                    },
                    isLoading = isCloudBackupLoading,
                    errorMessage = cloudPasswordError,
                    onPasswordSubmit = { password ->
                        selectedWalletId?.let { walletId ->
                            scope.launch {
                                cloudPasswordError = null
                                isCloudBackupLoading = true
                                when (val result =
                                    viewModel.backupWalletToCloud(walletId, password)) {
                                    is com.mtd.domain.model.ResultResponse.Success -> {
                                        viewModel.updateBackupStatus(walletId, cloud = true)
                                        backupFlowStep = BackupFlowStep.Success
                                    }

                                    is com.mtd.domain.model.ResultResponse.Error -> {
                                        val errorText = result.exception.message.orEmpty()
                                        cloudPasswordError = if (errorText.contains(
                                                "initialized",
                                                ignoreCase = true
                                            )
                                        ) {
                                            "ابتدا اتصال گوگل درایو را فعال کنید."
                                        } else if (isCloudRecoveryMode) {
                                            "رمز عبور پشتیبان ابری اشتباه است."
                                        } else {
                                            "خطا در ذخیره نسخه پشتیبان ابری."
                                        }
                                    }
                                }
                                isCloudBackupLoading = false
                            }
                        }
                    }
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showSettings,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(160)) +
                slideInVertically(initialOffsetY = { -it }, animationSpec = tween(420)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(140)) +
                slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(260)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10_000f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SettingsScreen(
                    onSecurityClick = { showSecuritySettings = true },
                    onClose = { showSettings = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // zIndexِ صریح ترتیبِ اعلانِ خواهرها را باطل می‌کند: پوششِ تنظیمات روی ۱۰٬۰۰۰ است و این دو
        // شیت با zIndexِ پیش‌فرضِ صفر زیرش کشیده می‌شدند — ساخته و چیده می‌شدند، ولی دیده نمی‌شدند.
        Box(modifier = Modifier.zIndex(10_001f)) {
            SecuritySettingsSheet(
                visible = showSecuritySettings,
                snapshot = appLockUiState.snapshot,
                biometricAvailable = biometricAvailable,
                onClose = { showSecuritySettings = false },
                onEnableAppLock = { showPasscodeSetup = true },
                onDisableAppLock = { appLockViewModel.disableAppLock() },
                onChangePasscode = { showPasscodeSetup = true },
                onBiometricToggle = { appLockViewModel.setBiometricEnabled(it) },
                onTimeoutSelect = { appLockViewModel.setTimeoutSeconds(it) }
            )
        }
        // بالاتر از شیتِ امنیت، چون از داخلِ همان باز می‌شود.
        Box(modifier = Modifier.zIndex(10_002f)) {
            PasscodeSetupSheet(
                visible = showPasscodeSetup,
                biometricAvailable = biometricAvailable,
                defaultBiometricEnabled = appLockUiState.snapshot.biometricEnabled,
                onClose = { showPasscodeSetup = false },
                onSubmit = { passcode, biometricEnabled ->
                    appLockViewModel.saveNewPasscode(passcode, biometricEnabled) { ok ->
                        if (ok) {
                            showPasscodeSetup = false
                            showSecuritySettings = false
                        }
                    }
                }
            )
        }

    }
}


@Composable
private fun MultiWalletHeader(
    isExpanded: Boolean,
    showBack: Boolean = true,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onCollapse: () -> Unit,
    contentAlpha: Float
) {
    val contentColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // حالت عادی (Add + Title + Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "افزودن کیف پول",
                    tint = contentColor,
                    modifier = Modifier.size(25.dp)
                )
            }
            Text(
                text = "کیف پول‌ها",
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                fontFamily = IranSansBold
            )
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "تنظیمات",
                    tint = contentColor,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        // حالت گسترده فقط هنگام باز بودن کارت compose می‌شود؛ alpha صفر هنوز touch را می‌گیرد.
        if (isExpanded && showBack) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = contentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }


}




@Composable
private fun AddWalletBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onImportExisting: () -> Unit
) {
    // استفاده از یک باکس ریشه برای مدیریت هر دو لایه
    Box(modifier = Modifier.fillMaxSize()) {

        // ۱. لایه مشکی پشت (فقط Fade)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        // ۲. باکس اصلی محتوا (Slide + Fade)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300)
                    ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // محتوای اصلی (Floating Card)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED))
                    .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                    .clickable(enabled = false) {} // برای جلوگیری از کلیک روی لایه پشت
                    .padding(bottom = 24.dp)
            ) {
                // هدر
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کیف جدید",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = IranSansRegularMedium
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Divider(
                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.1f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ۱. Create New
                AddWalletItem(
                    title = "ساخت کیف جدید",
                    subtitle = "ایجاد یک کیف پول جدید بدون سابقه",
                    icon = Icons.Default.Add,
                    iconBgColor = Color(0xFF42A5F5),
                    onClick = onCreateNew
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ۲. Add Existing
                AddWalletItem(
                    title = "وارد کردن",
                    subtitle = "افزودن کیف پول موجود با عبارت بازیابی یا کلید خصوصی",
                    icon = Icons.Default.ArrowDownward,
                    iconBgColor = Green,
                    onClick = onImportExisting
                )
            }
        }
    }
}


@Composable
private fun AddWalletItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(MainScreenConstants.FAB_MENU_ITEM_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = IranSansRegular
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                fontSize = 12.sp,
                fontFamily = IranSansRegular
            )
        }
    }
}

@Preview
@Composable
fun previewCard() {
    MaterialTheme {
//        ManagementMenuContent(false,{}, {}, {})
//       RecoveryMethodsContent(false,false,{},{})

       // SecretRecoveryPromptBottomSheet(true, isMnemonic = true, onDismiss = {}, onReveal = {})

    }
}
