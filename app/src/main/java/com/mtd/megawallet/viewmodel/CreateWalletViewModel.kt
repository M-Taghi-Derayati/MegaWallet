package com.mtd.megawallet.viewmodel

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewModelScope
import com.mtd.core.manager.ErrorManager
import com.mtd.domain.model.CloudWalletItem
import com.mtd.domain.model.CreateWalletStep
import com.mtd.domain.model.GoogleSignInEvent
import com.mtd.domain.model.ImportData
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.core.WalletKey
import com.mtd.domain.usecase.wallet.ApplyWalletKeySymbolsUseCase
import com.mtd.domain.usecase.wallet.BackupCloudWalletMetadataUseCase
import com.mtd.domain.usecase.wallet.BuildCloudWalletMetadataUseCase
import com.mtd.domain.usecase.wallet.CreateOrImportWalletUseCase
import com.mtd.domain.usecase.wallet.DeleteCloudBackupUseCase
import com.mtd.domain.usecase.wallet.GetWalletMnemonicUseCase
import com.mtd.domain.usecase.wallet.HasCloudBackupUseCase
import com.mtd.domain.usecase.wallet.UpdateWalletBackupStatusUseCase
import com.mtd.domain.usecase.wallet.importwallet.CalculateCloudWalletBalancesUseCase
import com.mtd.domain.usecase.wallet.importwallet.ConnectCloudBackupUseCase
import com.mtd.domain.usecase.wallet.importwallet.GetCloudSignInIntentUseCase
import com.mtd.domain.usecase.wallet.importwallet.IsCloudBackupConnectedUseCase
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import com.mtd.megawallet.ui.compose.screens.createwallet.BackupAnimationState
import com.mtd.megawallet.ui.compose.screens.createwallet.BackupMethodType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class CreateWalletViewModel @Inject constructor(
    private val applyWalletKeySymbolsUseCase: ApplyWalletKeySymbolsUseCase,
    private val createOrImportWalletUseCase: CreateOrImportWalletUseCase,
    private val getWalletMnemonicUseCase: GetWalletMnemonicUseCase,
    private val updateWalletBackupStatusUseCase: UpdateWalletBackupStatusUseCase,
    private val hasCloudBackupUseCase: HasCloudBackupUseCase,
    private val backupCloudWalletMetadataUseCase: BackupCloudWalletMetadataUseCase,
    private val buildCloudWalletMetadataUseCase: BuildCloudWalletMetadataUseCase,
    private val deleteCloudBackupUseCase: DeleteCloudBackupUseCase,
    private val getCloudSignInIntentUseCase: GetCloudSignInIntentUseCase,
    private val isCloudBackupConnectedUseCase: IsCloudBackupConnectedUseCase,
    private val calculateCloudWalletBalancesUseCase: CalculateCloudWalletBalancesUseCase,
    private val connectCloudBackupUseCase: ConnectCloudBackupUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {
    var currentStep by mutableStateOf(CreateWalletStep.NAME_INPUT)
        private set

    var walletName by mutableStateOf("")

    var selectedColor by mutableStateOf(Color(0xFF22C55E)) // Default green

    var isFlipped by mutableStateOf(false)
        private set

    var backupAnimationState by mutableStateOf(BackupAnimationState.IDLE)
        private set

    var backupMethod by mutableStateOf(BackupMethodType.NONE)
        private set

    // Terms
    var term1Accepted by mutableStateOf(false)
    var term2Accepted by mutableStateOf(false)
    var term3Accepted by mutableStateOf(false)
    var term4Accepted by mutableStateOf(false)

    var isAnimationFinished by mutableStateOf(false)
        private set

    val areTermsAccepted: Boolean
        get() = term1Accepted && term2Accepted && term3Accepted && term4Accepted

    var seedWords = mutableStateListOf<String>()
        private set

    var walletAddress = mutableStateListOf<WalletKey>()
        private set


    var totalBalanceUSDT by mutableStateOf("0.00")
        private set

    // داده‌های ایمپورت (اگر از صفحه AddExistingWallet آمده باشیم)
    var importData: ImportData? = null
        private set

    var creationSuccess by mutableStateOf(false)
        private set

    // حالت بازیابی از cloud backup
    var isRestoreMode by mutableStateOf(false)
        private set

    var hasExistingCloudBackup by mutableStateOf(false)
        private set

    private var restoreId: String? = null
    private var preloadedRestoreBalanceUsdt: String? = null
    private var generatedWalletId: String? = null

    private val _googleSignInEvent = Channel<GoogleSignInEvent>()
    val googleSignInEvent = _googleSignInEvent.receiveAsFlow()

    fun toggleFlipped(flipped: Boolean) {
        isFlipped = flipped
    }

    fun markAnimationFinished() {
        isAnimationFinished = true
    }

    fun nextStep() {
        val steps = CreateWalletStep.entries.toTypedArray()
        val nextIndex = currentStep.ordinal + 1
        if (nextIndex < steps.size) {
            val nextStep = steps[nextIndex]
            if (nextStep == CreateWalletStep.SEED_PHRASE_GENERATION) {
                generateWallet()
            }
            currentStep = nextStep
        }
    }

    fun setPendingImportData(data: ImportData?) {
        // اگر در restore mode هستیم، فقط importData را ست می‌کنیم و کاری نمی‌کنیم
       this.importData=data
        if (data!=null && isRestoreMode){
            resetToInitialState()
        }

    }

    /**
     * Reset تمام state ها به حالت اولیه برای شروع فرآیند ساخت کیف پول
     */
    fun resetToInitialState() {
        currentStep = CreateWalletStep.NAME_INPUT
        walletName = ""
        selectedColor = Color(0xFF22C55E) // Default green
        isFlipped = false
        backupAnimationState = BackupAnimationState.IDLE
        backupMethod = BackupMethodType.NONE
        term1Accepted = false
        term2Accepted = false
        term3Accepted = false
        term4Accepted = false
        isAnimationFinished = false
        creationSuccess = false
        isRestoreMode = false
        hasExistingCloudBackup = false
        restoreId = null
        preloadedRestoreBalanceUsdt = null
        generatedWalletId = null
        importData=null
        seedWords.clear()
        walletAddress.clear()
        totalBalanceUSDT = "0.00"
    }

    /**
     * شروع فرآیند بازیابی کیف پول از cloud backup
     * @param walletItem اطلاعات کیف پول انتخاب شده از cloud
     */
    fun startRestoreFromCloud(walletItem: CloudWalletItem) {
        // استیتِ حیاتی را «سنکرون» ست می‌کنیم (نه داخلِ launch) تا:
        //  ۱) صفحه بلافاصله در مرحلهٔ درست باز شود و یک‌فریم NAME_INPUT فلش نزند،
        //  ۲) isRestoreMode پیش از اجرای ریستِ محافظت‌شدهٔ CreateWalletScreen true باشد و استیتِ بازیابی پاک نشود.
        isRestoreMode = true
        creationSuccess = false
        isAnimationFinished = false
        backupAnimationState = BackupAnimationState.IDLE
        backupMethod = BackupMethodType.NONE
        currentStep = CreateWalletStep.SEED_PHRASE_GENERATION
        restoreId = walletItem.id
        preloadedRestoreBalanceUsdt = walletItem.balanceUsdt
            .takeIf { it.isNotBlank() && it != "..." }
        totalBalanceUSDT = preloadedRestoreBalanceUsdt ?: "0.00"
        walletName = walletItem.name
        selectedColor = try {
            Color(walletItem.colorHex.toColorInt())
        } catch (e: Exception) {
            Color(0xFF22C55E) // Default green
        }
        importData = if (walletItem.isMnemonic) {
            ImportData.Mnemonic(walletItem.key.split(" "))
        } else {
            ImportData.PrivateKey(walletItem.key)
        }
        // پاک‌سازی داده‌های احتمالیِ باقی‌مانده از فلوِ قبلی
        seedWords.clear()
        walletAddress.clear()

        // شروع فرآیند بازیابی (بدون backup گرفتن)
        viewModelScope.launch {
            try {
                generateWallet()

                // منتظر می‌مانیم تا انیمیشن اولیه (خطوط و reveal) تمام شود
                val totalAnimationDuration = AnimationConstants.GENERATING_ANIMATION_DURATION +
                        AnimationConstants.LINE_DRAW_DELAY +
                        AnimationConstants.LINE_DRAW_DURATION +
                        AnimationConstants.REVEAL_ANIMATION_DELAY +
                        AnimationConstants.REVEAL_ANIMATION_DURATION
                delay(totalAnimationDuration.toLong())

                backupAnimationState = BackupAnimationState.SUCCESS
            } catch (e: Exception) {
                // Restoring a wallet is the whole point of this screen; if it fails the user must
                // acknowledge it rather than watch a snackbar slide past mid-animation.
                reportErrorAsync(
                    throwable = e,
                    userAction = "restoreWallet",
                    surface = ErrorSurface.BLOCKING,
                    severity = ErrorSeverity.HIGH,
                    fallbackMessage = "خطا در بازیابی کیف پول"
                )
                isRestoreMode = false
            }
        }
    }


    private fun generateWallet() {
        viewModelScope.launch {
            val currentImportData = importData
            val isCloudRestoreFlow = isRestoreMode && restoreId != null
            val result = createOrImportWalletUseCase(
                name = walletName,
                color = selectedColor.toArgb(),
                importData = currentImportData,
                restoreId = restoreId,
                isCloudRestoreFlow = isCloudRestoreFlow
            )

            when (result) {
                is ResultResponse.Success -> {
                    val wallet = result.data
                    generatedWalletId = wallet.id

                    if (currentImportData == null) {
                        getWalletMnemonicUseCase(wallet.id)?.let { mnemonic ->
                            seedWords.clear()
                            seedWords.addAll(mnemonic.split(" "))
                        }
                    } else if (currentImportData is ImportData.Mnemonic) {
                        seedWords.clear()
                        seedWords.addAll(currentImportData.words)
                    }

                    walletAddress.addAll(applyWalletKeySymbolsUseCase(wallet.keys))

                    val cachedRestoreBalance = preloadedRestoreBalanceUsdt
                    if (isCloudRestoreFlow && !cachedRestoreBalance.isNullOrBlank()) {
                        totalBalanceUSDT = cachedRestoreBalance
                    } else {
                        calculateTotalBalance(wallet)
                    }

                    if (isCloudRestoreFlow) {
                        updateWalletBackupStatusUseCase(
                            walletId = wallet.id,
                            manual = false,
                            cloud = true
                        )
                    }

                    creationSuccess = true
                }

                is ResultResponse.Error -> {
                    // Without a wallet the flow cannot continue — block.
                    reportErrorAsync(
                        throwable = result.exception,
                        userAction = "generateWallet",
                        surface = ErrorSurface.BLOCKING,
                        severity = ErrorSeverity.HIGH,
                        fallbackMessage = "خطا در ساخت کیف پول"
                    )
                }
            }
        }
    }

    // --- Google Drive Sign In Event ---

    private suspend fun navigateToCloudPasswordStep() {
        hasExistingCloudBackup = try {
            hasCloudBackupUseCase()
        } catch (e: Exception) {
            // Probe only — "no existing backup" is a safe answer and the next screen still works.
            reportError(
                throwable = e,
                userAction = "hasCloudBackup",
                surface = ErrorSurface.SILENT,
                severity = ErrorSeverity.LOW
            )
            false
        }
        currentStep = CreateWalletStep.CLOUD_BACKUP_PASSWORD
    }

    fun onCloudBackupClick() {
        viewModelScope.launch {
            try {
                // بررسی اینکه آیا اتصال به Google Drive برقرار است یا نه
                if (!isCloudBackupConnectedUseCase()) {
                    // نیاز به اتصال داریم - راه‌اندازی Google Sign In
                    _googleSignInEvent.send(
                        GoogleSignInEvent.LaunchIntent(getCloudSignInIntentUseCase())
                    )
                } else {
                    // اتصال برقرار است - پس از بررسی وجود بکاپ قبلی به صفحه رمز عبور می‌رویم
                    navigateToCloudPasswordStep()
                }
            } catch (e: Exception) {
                reportError(
                    throwable = e,
                    userAction = "checkCloudBackupConnection",
                    surface = ErrorSurface.SNACKBAR,
                    fallbackMessage = "خطا در بررسی اتصال به گوگل درایو"
                )
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            when (val result = connectCloudBackupUseCase(data)) {
                is ResultResponse.Success -> navigateToCloudPasswordStep()
                is ResultResponse.Error -> {
                    reportError(
                        throwable = result.exception,
                        userAction = "connectCloudBackup",
                        surface = ErrorSurface.SNACKBAR,
                        fallbackMessage = "ورود به گوگل ناموفق بود. لطفا دوباره تلاش کنید."
                    )
                }
            }
        }
    }

    fun onManualBackupClick() {
        viewModelScope.launch {
            backupMethod = BackupMethodType.MANUAL
            backupAnimationState = BackupAnimationState.PROCESSING
            delay(900)
            generatedWalletId?.let { walletId ->
                updateWalletBackupStatusUseCase(walletId = walletId, manual = true)
            }
            backupAnimationState = BackupAnimationState.SUCCESS
        }
    }

    fun onCloudPasswordSubmit(password: String) {
        viewModelScope.launch {
            try {
                currentStep = CreateWalletStep.SEED_PHRASE_GENERATION
                backupMethod = BackupMethodType.CLOUD
                backupAnimationState = BackupAnimationState.PROCESSING

                val walletData = when (val result = buildCloudWalletMetadataUseCase(
                    seedWords = seedWords,
                    importData = importData,
                    walletId = generatedWalletId,
                    walletName = walletName,
                    color = selectedColor.toArgb()
                )) {
                    is ResultResponse.Success -> result.data
                    is ResultResponse.Error -> {
                        reportError(
                            throwable = result.exception,
                            userAction = "buildCloudWalletMetadata",
                            surface = ErrorSurface.SNACKBAR,
                            fallbackMessage = "اطلاعات کیف پول یافت نشد"
                        )
                        backupAnimationState = BackupAnimationState.IDLE
                        return@launch
                    }
                }

                when (val result = backupCloudWalletMetadataUseCase(walletData, password)) {
                    is ResultResponse.Success -> {
                        generatedWalletId?.let { walletId ->
                            updateWalletBackupStatusUseCase(walletId = walletId, cloud = true)
                        }
                        backupAnimationState = BackupAnimationState.SUCCESS
                    }

                    is ResultResponse.Error -> {
                        val isPasswordError = result.exception is IllegalArgumentException &&
                                result.exception.message == "Incorrect cloud backup password"
                        if (isPasswordError) {
                            // Wrong password is a user-correctable input error, not a fault.
                            showErrorSnackbar(
                                shortMessage = "رمز عبور اشتباه است",
                                detailedMessage = "برای اضافه کردن کیف جدید به بکاپ قبلی، باید رمز بکاپ قبلی را وارد کنید.",
                                errorTitle = "خطای رمز عبور"
                            )
                        } else {
                            // The wallet exists but is now unbacked-up — the user must know.
                            reportError(
                                throwable = result.exception,
                                userAction = "backupCloudWalletMetadata",
                                surface = ErrorSurface.BLOCKING,
                                severity = ErrorSeverity.HIGH,
                                title = "خطا در آپلود پشتیبان",
                                fallbackMessage = "خطا در آپلود پشتیبان"
                            )
                        }
                        backupAnimationState = BackupAnimationState.IDLE
                    }
                }
            } catch (e: Exception) {
                reportError(
                    throwable = e,
                    userAction = "onCloudPasswordSubmit",
                    surface = ErrorSurface.BLOCKING,
                    severity = ErrorSeverity.HIGH,
                    fallbackMessage = "خطا در پردازش پشتیبان"
                )
                backupAnimationState = BackupAnimationState.IDLE
            }
        }
    }

    fun prevStep(): Boolean {
        // به محض ورود به صفحه نمایش کلیدها یا کارت، دکمه بک سیستم کاملاً غیرفعال می‌شود
        if (currentStep == CreateWalletStep.SEED_PHRASE_GENERATION) {
            return false
        }

        // اگر در صفحه رمز عبور هستیم، اجازه بازگشت به صفحه کلمات را می‌دهیم تا کاربر بتواند کارت را ببیند یا روش دیگری انتخاب کند
        if (currentStep == CreateWalletStep.CLOUD_BACKUP_PASSWORD) {
            currentStep = CreateWalletStep.SEED_PHRASE_GENERATION
            return true
        }

        if (currentStep.ordinal > 0) {
            val steps = CreateWalletStep.entries.toTypedArray()
            currentStep = steps[currentStep.ordinal - 1]
            return true
        }
        return false
    }


    private fun calculateTotalBalance(wallet: Wallet) {
        totalBalanceUSDT = "..."
        launchSafe {
            val secret = getWalletSecretForBalance() ?: run {
                totalBalanceUSDT = "0.00"
                return@launchSafe
            }

            val walletWithBalance = calculateCloudWalletBalancesUseCase(
                listOf(
                    CloudWalletItem(
                        id = wallet.id,
                        name = wallet.name,
                        key = secret,
                        colorHex = String.format("#%06X", 0xFFFFFF and wallet.color),
                        isMnemonic = wallet.hasMnemonic
                    )
                )
            ).firstOrNull()

            totalBalanceUSDT = walletWithBalance?.balanceUsdt?.replace("$", "") ?: "0.00"
        }
    }

    private fun getWalletSecretForBalance(): String? {
        return when (val currentImportData = importData) {
            is ImportData.Mnemonic -> currentImportData.words.joinToString(" ")
            is ImportData.PrivateKey -> currentImportData.key
            null -> seedWords.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }
    }

    fun deleteCloudBackup() {
        viewModelScope.launch {
            try {
                if (!isCloudBackupConnectedUseCase()) {
                    _googleSignInEvent.send(
                        GoogleSignInEvent.LaunchIntent(getCloudSignInIntentUseCase())
                    )
                    showSnackbarMessage("ابتدا به Google Drive متصل شوید")
                    return@launch
                }

                when (val result = deleteCloudBackupUseCase()) {
                    is ResultResponse.Success -> {
                        showErrorSnackbar("فایل پشتیبان با موفقیت حذف شد")
                    }

                    is ResultResponse.Error -> {
                        showErrorSnackbar("خطا در حذف فایل: ${result.exception.message}")
                    }
                }
            } catch (e: Exception) {
                showErrorSnackbar("خطا در حذف فایل: ${e.message}")
            }
        }
    }

}
