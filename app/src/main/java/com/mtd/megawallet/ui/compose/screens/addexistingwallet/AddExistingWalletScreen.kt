package com.mtd.megawallet.ui.compose.screens.addexistingwallet

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.megawallet.event.GoogleSignInEvent
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.event.ImportScreenState
import com.mtd.megawallet.ui.compose.components.ErrorSnackbarHandler
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.ui.compose.components.WalletStackCard
import com.mtd.megawallet.ui.compose.components.WalletStackCardBackPrivateKey
import com.mtd.megawallet.ui.compose.components.WalletStackCardBackWordKeys
import com.mtd.megawallet.ui.compose.components.WalletStackCardFront
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.manualimport.ManualImportWordKeys
import com.mtd.megawallet.viewmodel.news.WalletImportViewModel
import kotlinx.coroutines.launch

@Composable
fun AddExistingWalletScreen(
    viewModel: WalletImportViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onImportSuccess: (ImportData) -> Unit,
    onRestoreFromCloud: (com.mtd.megawallet.event.CloudWalletItem) -> Unit = {}
) {
    val screenState = viewModel.screenState
    val restoreWalletEvent = viewModel.restoreWalletEvent
    
    // Observe restore event - باید key را به restoreWalletEvent تغییر دهیم
    LaunchedEffect(restoreWalletEvent) {
        restoreWalletEvent?.let { walletItem ->
            onRestoreFromCloud(walletItem)
            // Reset after handling - باید در ViewModel انجام شود
            viewModel.clearRestoreWalletEvent()
        }
    }
    
    // استفاده از derivedStateOf برای کاهش recomposition
    // derivedStateOf باید مستقیماً استفاده شود، نه داخل remember
    val isSeedPhraseVisible by derivedStateOf {
        screenState == ImportScreenState.SEED_PHRASE_AUTO ||
        screenState == ImportScreenState.PRIVATE_KEY_INPUT ||
        screenState == ImportScreenState.SEED_PHRASE_MANUAL ||
        screenState == ImportScreenState.CLOUD_PASSWORD_INPUT||
        screenState == ImportScreenState.CLOUD_WALLET_LIST
    }

    val isGreenCardRevealed by derivedStateOf { 
        isSeedPhraseVisible 
    }

    LaunchedEffect(viewModel.validationSuccessEvent) {
        viewModel.validationSuccessEvent?.let { data ->
            onImportSuccess(data)
            viewModel.clearValidationSuccessEvent()
        }
    }

    // Google Sign-In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    // Observe Events (Google Sign-In, etc.)
    LaunchedEffect(Unit) {
        // جمع‌آوری رویدادهای UI

        // جمع‌آوری رویدادهای ورود گوگل
        launch {
            viewModel.googleSignInEvent.collect { event ->
                when (event) {
                    is GoogleSignInEvent.LaunchIntent -> googleSignInLauncher.launch(event.intent)
                }
            }
        }
    }


    BackHandler(enabled = true) {
        if (!viewModel.handleBack()) {
            onBack()
        }
    }

    // --- Animation Values using Transition for better performance ---
    // استفاده از Transition برای گروه‌بندی انیمیشن‌های مرتبط و کاهش recomposition
    val cardTransition = updateTransition(targetState = screenState, label = "cardTransition")
    
    val greenCardYOffset by cardTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow) },
        label = "greenY"
    ) { state ->
        when (state) {
            ImportScreenState.STACKED -> 0.dp
            ImportScreenState.IMPORT_OPTIONS -> 50.dp
            ImportScreenState.PRIVATE_KEY_INPUT -> 240.dp
            ImportScreenState.SEED_PHRASE_AUTO, ImportScreenState.SEED_PHRASE_MANUAL -> 250.dp
            else -> 0.dp
        }
    }

    val greenCardWidth by cardTransition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "greenWidth"
    ) { state ->
        when (state) {
            ImportScreenState.STACKED -> 180.dp
            ImportScreenState.IMPORT_OPTIONS -> 220.dp
            ImportScreenState.SEED_PHRASE_AUTO, ImportScreenState.PRIVATE_KEY_INPUT, ImportScreenState.SEED_PHRASE_MANUAL -> 300.dp
            else -> 0.dp
        }
    }

    val greenCardHeight by cardTransition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "greenHeight"
    ) { state ->
        when (state) {
            ImportScreenState.STACKED -> 108.dp
            ImportScreenState.IMPORT_OPTIONS -> 138.dp
            ImportScreenState.PRIVATE_KEY_INPUT -> 180.dp
            ImportScreenState.SEED_PHRASE_AUTO, ImportScreenState.SEED_PHRASE_MANUAL -> 270.dp
            else -> 0.dp
        }
    }

    val orangeCardsOffset by cardTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow) },
        label = "orangeOffset"
    ) { state ->
        if (state == ImportScreenState.STACKED) 20.dp else 40.dp
    }

    val blueCardsOffset by cardTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow) },
        label = "blueOffset"
    ) { state ->
        if (state == ImportScreenState.STACKED) 40.dp else 80.dp
    }

    val otherCardsAlpha by cardTransition.animateFloat(
        transitionSpec = { tween(300) },
        label = "cardsAlpha"
    ) { state ->
        if (state == ImportScreenState.STACKED) 1f else 0f
    }

    val greenCardAlpha by cardTransition.animateFloat(
        transitionSpec = { tween(300) },
        label = "greenAlpha"
    ) { state ->
        if (state == ImportScreenState.SEED_PHRASE_MANUAL) 0f else 1f
    }

    val gradientBrush = if (isSystemInDarkTheme()) {
        Brush.verticalGradient(colors = listOf(Color.Black, Color.Black))
    } else {
        Brush.verticalGradient(colors = listOf(Color.White, Color(0xFFE7E7E7)))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)

        ) {
            // Error Snackbar Handler (در بالای همه چیز)
            ErrorSnackbarHandler(
                uiEvents = viewModel.uiEvents,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(100f)
            )

            // ۱. هدر یکپارچه
            UnifiedHeader(
                onBack = { if (!viewModel.handleBack()) onBack() },
                isClose = screenState == ImportScreenState.STACKED,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .zIndex(10f)
            )

            // ۲. پشته کارت‌ها (Wallet Cards)
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.31f),
                contentAlignment = Alignment.Center
            ) {
                // کارت ۳ (سبز نهایی - کارت متحرک اصلی)
                // استفاده از graphicsLayer برای transform animations (بهینه‌سازی performance)
                WalletStackCard(
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = with(density) { greenCardYOffset.toPx() }
                            alpha = greenCardAlpha
                        }
                        .zIndex(if (screenState == ImportScreenState.STACKED) 1f else 10f),
                    width = greenCardWidth,
                    height = greenCardHeight,
                    isRevealed = isGreenCardRevealed,
                    color = Color(0xFF22C55E),
                    frontContent = { WalletStackCardFront() },
                    backContent = {
                        if (screenState == ImportScreenState.PRIVATE_KEY_INPUT) {
                            WalletStackCardBackPrivateKey(
                                privateKey = viewModel.pastedPrivateKey,
                                onPasteClick = { viewModel.onPastePrivateKeyToCard() }
                            )
                        } else {
                            // بهینه‌سازی: بررسی اینکه آیا انیمیشن کارت به target height رسیده یا نه
                            val isAnimationStable by derivedStateOf {
                                // وقتی height به 270.dp نزدیک شد (با tolerance 5.dp)، انیمیشن ثابت شده
                                greenCardHeight >= 265.dp && screenState == ImportScreenState.SEED_PHRASE_AUTO
                            }
                            WalletStackCardBackWordKeys(
                                phrases = viewModel.pastedWords,
                                onPasteClick = { viewModel.onPasteSeedPhraseToCard() },
                                isAnimationStable = isAnimationStable
                            )
                        }
                    }
                )
                // کارت ۲ (نارنجی)
                WalletStackCard(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 1.05f
                            scaleY = 1.05f
                            translationY = with(density) { orangeCardsOffset.toPx() }
                            alpha = otherCardsAlpha
                        },
                    color = Color(0xFFFFA726),
                    frontContent = { WalletStackCardFront() },
                    backContent = { }
                )
                // کارت ۱ (آبی)
                WalletStackCard(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = 1.2f
                            scaleY = 1.2f
                            translationY = with(density) { blueCardsOffset.toPx() }
                            alpha = otherCardsAlpha
                        },
                    color = Color(0xFF42A5F5),
                    frontContent = { WalletStackCardFront() },
                    backContent = { }
                )
            }

            // ۳. بخش محتوا (گزینه‌ها یا فیلدهای ایمپورت)
            AnimatedVisibility(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .align(Alignment.BottomCenter),
                visible = !isSeedPhraseVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(40.dp, 40.dp, 0.dp, 0.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp)
                ) {
                    AnimatedContent(
                        targetState = screenState,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "ScreenTransition"
                    ) { state ->
                        when (state) {
                            ImportScreenState.STACKED -> {
                                WelcomeContent(
                                    onGetStarted = { viewModel.updateScreenState(ImportScreenState.IMPORT_OPTIONS) },
                                    driveBackupState = viewModel.driveBackupState,
                                    onCloudBackupClicked = { viewModel.onCloudBackupClicked() }
                                )
                            }

                            ImportScreenState.IMPORT_OPTIONS -> {
                                ImportOptionsContent(
                                    onImportSeed = { viewModel.updateScreenState(ImportScreenState.SEED_PHRASE_AUTO) },
                                    onImportPrivateKey = {
                                        viewModel.updateScreenState(
                                            ImportScreenState.PRIVATE_KEY_INPUT
                                        )
                                    }
                                )
                            }

                            else -> Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // ۴. لایه بالایی برای محتوای ایمپورت (Seed Phrase / Private Key)
            AnimatedVisibility(
                visible = isSeedPhraseVisible,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = screenState,
                        transitionSpec = {
                            // استفاده از slide + fade برای transition smooth
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            ) + fadeIn(tween(400)) togetherWith
                            slideOutVertically(
                                targetOffsetY = { -it },
                                animationSpec = tween(300, easing = FastOutSlowInEasing)
                            ) + fadeOut(tween(300))
                        },
                        label = "ImportContent"
                    ) { state ->
                        when (state) {
                            ImportScreenState.SEED_PHRASE_AUTO -> {
                                // بهینه‌سازی: استفاده از derivedStateOf برای کاهش recomposition
                                // derivedStateOf باید مستقیماً استفاده شود
                                val isValid by derivedStateOf {
                                    viewModel.isSeedPhraseClipboardValid(viewModel.pastedWords)
                                }
                                AutoImportWordKeys(
                                    words = viewModel.pastedWords,
                                    isValid = isValid,
                                    onClickManual = { viewModel.updateScreenState(ImportScreenState.SEED_PHRASE_MANUAL) },
                                    onVerificationSuccess = { viewModel.importWallet() }
                                )
                            }

                            ImportScreenState.SEED_PHRASE_MANUAL -> {
                                ManualImportWordKeys(
                                    initialWords = viewModel.manualWords,
                                    onWordsChange = { words ->
                                        words.forEachIndexed { index, word ->
                                            viewModel.updateManualWord(
                                                index,
                                                word
                                            )
                                        }
                                    },
                                    onVerificationSuccess = {
                                        viewModel.confirmManualEntry()
                                        viewModel.importWallet()
                                    }
                                )
                            }

                            ImportScreenState.PRIVATE_KEY_INPUT -> {
                                PrivateKeyImport(
                                    isValid = viewModel.isPrivateKeyClipboardValid(viewModel.pastedPrivateKey),
                                    onClickClear = { viewModel.clearPastedPrivateKey() },
                                    onVerificationSuccess = { viewModel.importPrivateKey() }
                                )
                            }

                            ImportScreenState.CLOUD_PASSWORD_INPUT -> {
                                CloudBackupPasswordScreen(
                                    onBack = {
                                        viewModel.handleBack()
                                    },
                                    isLoading = viewModel.isDownloadingBackup,
                                    onPasswordSubmit = { password ->
                                         viewModel.onRestorePasswordConfirm(password)
                                    }
                                )

                            }

                            ImportScreenState.CLOUD_WALLET_LIST -> {
                                CloudBackupWalletListScreen(
                                    wallets = viewModel.cloudWallets,
                                    onBack = { viewModel.handleBack() },
                                    onImportSelected = { selectedIds ->
                                        viewModel.onImportCloudWallets(selectedIds)
                                    },
                                    isCalculatingBalances = viewModel.isCalculatingBalances
                                )
                            }

                            else -> Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

