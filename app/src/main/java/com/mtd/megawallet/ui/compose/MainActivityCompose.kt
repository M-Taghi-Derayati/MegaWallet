package com.mtd.megawallet.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtd.common_ui.theme.MegaWalletTheme
import com.mtd.core.manager.ErrorManager
import com.mtd.domain.model.AuthPurpose
import com.mtd.megawallet.security.BiometricAuthHelper
import com.mtd.megawallet.session.RealtimeLifecycleCoordinator
import com.mtd.megawallet.session.WalletLockKeyCoordinator
import com.mtd.megawallet.session.WalletSessionAuthCoordinator
import com.mtd.megawallet.ui.compose.components.AppMessageHost
import com.mtd.megawallet.ui.compose.screens.main.MainScreen
import com.mtd.megawallet.ui.compose.screens.security.LockedFingerprintOverlay
import com.mtd.megawallet.ui.compose.screens.security.PasscodeKeypadSheet
import com.mtd.megawallet.ui.compose.screens.security.PasscodeSetupSheet
import com.mtd.megawallet.ui.compose.screens.security.SecuritySettingsSheet
import com.mtd.megawallet.viewmodel.AppLockViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activity اصلی اپلیکیشن که MainScreen را نمایش می‌دهد.
 * این صفحه بعد از ایجاد یا بازیابی کیف پول نمایش داده می‌شود.
 */
@AndroidEntryPoint
class MainActivityCompose : FragmentActivity() {

    private val appLockViewModel: AppLockViewModel by viewModels()

    // Phase 2 — drives Web3 sign-in off the wallet lifecycle (mints JWT + connects /ws after unlock).
    @Inject lateinit var sessionAuthCoordinator: WalletSessionAuthCoordinator

    // TASK-06 — clears decrypted keys from the heap on lock/background, re-hydrates on unlock/foreground.
    @Inject lateinit var lockKeyCoordinator: WalletLockKeyCoordinator

    // TASK-22 — disconnects the realtime socket in the background, reconnects it in the foreground.
    @Inject lateinit var realtimeLifecycleCoordinator: RealtimeLifecycleCoordinator

    // TASK-57 — the app-wide message bus; one AppMessageHost per Activity renders it.
    @Inject lateinit var errorManager: ErrorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appLockViewModel.initialize()
        sessionAuthCoordinator.start()
        lockKeyCoordinator.start()
        realtimeLifecycleCoordinator.start()
        setContent {
            MegaWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val lockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
                    var showSecuritySettings by remember { mutableStateOf(false) }
                    var showPasscodeSetup by remember { mutableStateOf(false) }
                    var showPasscodeUnlock by remember { mutableStateOf(false) }
                    var biometricFailedAttempts by remember { mutableIntStateOf(0) }
                    var biometricPromptInFlight by remember { mutableStateOf(false) }
                    val biometricAvailable = remember {
                        BiometricAuthHelper.isBiometricAvailable(this@MainActivityCompose)
                    }
                    val canUseBiometricUnlock =
                        biometricAvailable && lockUiState.snapshot.biometricEnabled
                    val overlayVisible = lockUiState.isInitialized && lockUiState.isLocked
                    val showLockedFingerprint =
                        overlayVisible && canUseBiometricUnlock && !showPasscodeUnlock
                    // KAN-NEW-02: reduced blur radius (was 72.dp) to cut per-frame RenderEffect
                    // cost on mid-range devices; the 0.9-alpha scrim below already occludes content.
                    val blurRadius by animateDpAsState(
                        targetValue = if (overlayVisible) 24.dp else 0.dp,
                        animationSpec = tween(durationMillis = 280),
                        label = "app_lock_blur"
                    )

                    val launchBiometricPrompt = {
                        if (biometricPromptInFlight || !canUseBiometricUnlock) {
                            Unit
                        } else {
                            biometricPromptInFlight = true
                            BiometricAuthHelper.showPrompt(
                                activity = this@MainActivityCompose,
                                subtitle = "برای باز کردن برنامه اثر انگشت را تایید کنید",
                                onSuccess = {
                                    biometricPromptInFlight = false
                                    biometricFailedAttempts = 0
                                    appLockViewModel.completeBiometricUnlock()
                                },
                                onError = { code, message ->
                                    biometricPromptInFlight = false
                                    when (code) {
                                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                        BiometricPrompt.ERROR_USER_CANCELED,
                                        BiometricPrompt.ERROR_CANCELED -> {
                                            showPasscodeUnlock = true
                                        }

                                        else -> {
                                            appLockViewModel.onBiometricError(message)
                                            showPasscodeUnlock = true
                                        }
                                    }
                                },
                                onFailed = {
                                    biometricFailedAttempts += 1
                                    if (biometricFailedAttempts >= 3) {
                                        showPasscodeUnlock = true
                                        appLockViewModel.onBiometricError("تعداد تلاش اثر انگشت بیش از حد مجاز بود. رمز عبور را وارد کنید.")
                                    }
                                }
                            )
                        }
                    }

                    LaunchedEffect(lockUiState.isLocked, canUseBiometricUnlock) {
                        if (!lockUiState.isLocked) {
                            showPasscodeUnlock = false
                            biometricFailedAttempts = 0
                            biometricPromptInFlight = false
                            appLockViewModel.clearUnlockError()
                        } else if (!canUseBiometricUnlock) {
                            showPasscodeUnlock = true
                        }
                    }

                    LaunchedEffect(showLockedFingerprint) {
                        if (showLockedFingerprint) {
                            launchBiometricPrompt()
                        }
                    }

                    // TASK-23 — request POST_NOTIFICATIONS on Android 13+. Deferred until the app is
                    // unlocked (i.e. past onboarding + any app-lock) so the prompt lands in context, not
                    // over the lock screen. Denial is non-fatal: the socket/NotificationService guards
                    // already no-op without the permission. rememberSaveable → asked at most once per
                    // process (survives rotation); a fresh launch retries until the OS stops re-prompting.
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { /* granted or denied — no blocking follow-up either way */ }
                    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(lockUiState.isInitialized, lockUiState.isLocked) {
                        val unlocked = lockUiState.isInitialized && !lockUiState.isLocked
                        val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    this@MainActivityCompose,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                        if (unlocked && !notificationPermissionAsked && needsRequest) {
                            notificationPermissionAsked = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(blurRadius)
                        ) {
                            MainScreen(
                                onNavigateToWalletManagement = {
                                    // TODO: Navigate to wallet management screen
                                },
                                onMoreOptionsClick = {},
                                onFabClick = {
                                    // TODO: Handle FAB click (e.g., show send/receive options)
                                },
                                onHistoryClick = {
                                    // TODO: Navigate to history screen
                                },
                                onExploreClick = {
                                    // TODO: Navigate to explore screen
                                }
                            )

                            if (overlayVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(Modifier),
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.9f)
                                    ) {}
                                }
                            }

                            SecuritySettingsSheet(
                                visible = showSecuritySettings,
                                snapshot = lockUiState.snapshot,
                                biometricAvailable = biometricAvailable,
                                onClose = { showSecuritySettings = false },
                                onEnableAppLock = { showPasscodeSetup = true },
                                onDisableAppLock = { appLockViewModel.disableAppLock() },
                                onChangePasscode = { showPasscodeSetup = true },
                                onBiometricToggle = { appLockViewModel.setBiometricEnabled(it) },
                                onTimeoutSelect = { appLockViewModel.setTimeoutSeconds(it) }
                            )

                            PasscodeSetupSheet(
                                visible = showPasscodeSetup,
                                biometricAvailable = biometricAvailable,
                                defaultBiometricEnabled = lockUiState.snapshot.biometricEnabled,
                                onClose = { showPasscodeSetup = false },
                                onSubmit = { passcode, biometricEnabled ->
                                    appLockViewModel.saveNewPasscode(
                                        passcode,
                                        biometricEnabled
                                    ) { ok ->
                                        if (ok) {
                                            showPasscodeSetup = false
                                            showSecuritySettings = false
                                        }
                                    }
                                }
                            )

                            LockedFingerprintOverlay(
                                visible = showLockedFingerprint,
                                onFingerprintClick = { launchBiometricPrompt() }
                            )

                            PasscodeKeypadSheet(
                                visible = overlayVisible && showPasscodeUnlock,
                                title = "رمز عبور برنامه را وارد کنید",
                                subtitle = "برای دسترسی به کیف پول نیاز به تایید هویت دارید",
                                errorMessage = lockUiState.unlockError,
                                remainingLockoutSeconds = lockUiState.lockoutRemainingSeconds,
                                onSubmitPasscode = { passcode ->
                                    appLockViewModel.unlockWithPasscode(passcode)
                                },
                                onCancel = if (lockUiState.authPurpose == AuthPurpose.SENSITIVE_ACTION || canUseBiometricUnlock) {
                                    {
                                        biometricFailedAttempts = 0
                                        appLockViewModel.clearUnlockError()
                                        if (lockUiState.authPurpose == AuthPurpose.SENSITIVE_ACTION) {
                                            showPasscodeUnlock = false
                                            appLockViewModel.cancelSensitiveAuthRequest()
                                        } else {
                                            showPasscodeUnlock = false
                                        }
                                    }
                                } else null,
                                cancelLabel = if (lockUiState.authPurpose == AuthPurpose.SENSITIVE_ACTION) "لغو عملیات" else "بازگشت",
                                onExitApp = { finishAffinity() }
                            )

                            // TASK-57 — single message host for the whole main app. Mounted last so the
                            // snackbar/dialog draws above the sheets and the app-lock overlay.
                            AppMessageHost(uiMessages = errorManager.uiMessages)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockViewModel.onAppForegrounded()
    }

    override fun onStop() {
        appLockViewModel.onAppBackgrounded()
        super.onStop()
    }
}



