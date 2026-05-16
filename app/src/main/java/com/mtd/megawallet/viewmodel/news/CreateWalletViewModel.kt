package com.mtd.megawallet.viewmodel.news

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
        viewModelScope.launch {
            try {
                isRestoreMode = true
                restoreId = walletItem.id
                preloadedRestoreBalanceUsdt = walletItem.balanceUsdt
                    .takeIf { it.isNotBlank() && it != "..." }
                totalBalanceUSDT = preloadedRestoreBalanceUsdt ?: "0.00"

                // Reset animation state برای نمایش انیمیشن از ابتدا
                isAnimationFinished = false
                backupAnimationState = BackupAnimationState.IDLE
                backupMethod = BackupMethodType.NONE

                // تنظیم اطلاعات کیف پول
                walletName = walletItem.name
                selectedColor = try {
                    Color(walletItem.colorHex.toColorInt())
                } catch (e: Exception) {
                    Color(0xFF22C55E) // Default green
                }

                // تنظیم importData
                importData = if (walletItem.isMnemonic) {
                    ImportData.Mnemonic(walletItem.key.split(" "))
                } else {
                    ImportData.PrivateKey(walletItem.key)
                }

                // مستقیماً به مرحله SEED_PHRASE_GENERATION برو
                currentStep = CreateWalletStep.SEED_PHRASE_GENERATION

                // شروع فرآیند بازیابی (بدون backup گرفتن)
                // generateWallet یک suspend function است، پس باید منتظر بمانیم
                // اما چون generateWallet در viewModelScope اجرا می‌شود، باید آن را در یک coroutine جداگانه اجرا کنیم
                launch {
                    generateWallet()

                    // منتظر می‌مانیم تا انیمیشن اولیه (خطوط و reveal) تمام شود
                    // قبل از اینکه backupAnimationState را به PROCESSING ببریم
                    val totalAnimationDuration = AnimationConstants.GENERATING_ANIMATION_DURATION +
                            AnimationConstants.LINE_DRAW_DELAY +
                            AnimationConstants.LINE_DRAW_DURATION +
                            AnimationConstants.REVEAL_ANIMATION_DELAY +
                            AnimationConstants.REVEAL_ANIMATION_DURATION
                    delay(totalAnimationDuration.toLong())

                    backupAnimationState = BackupAnimationState.SUCCESS
                }

            } catch (e: Exception) {
                launchLocal {
                    showErrorSnackbar(
                        shortMessage = "خطا در بازیابی کیف پول",
                        detailedMessage = e.message ?: "خطای نامشخص",
                        errorTitle = "خطا"
                    )
                }
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
                    launchLocal {
                        showErrorSnackbar(
                            shortMessage = "خطا در ساخت کیف پول",
                            detailedMessage = result.exception.message ?: "خطای نامشخص",
                            errorTitle = "خطا"
                        )
                    }
                }
            }
        }
    }

    // --- Google Drive Sign In Event ---

    private suspend fun navigateToCloudPasswordStep() {
        hasExistingCloudBackup = try {
            hasCloudBackupUseCase()
        } catch (_: Exception) {
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
                launchLocal {
                    showErrorSnackbar("خطا در بررسی اتصال به گوگل درایو: ${e.message}")
                }
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            when (connectCloudBackupUseCase(data)) {
                is ResultResponse.Success -> navigateToCloudPasswordStep()
                is ResultResponse.Error -> {
                    launchLocal {
                        showSnackbarMessage("ورود به گوگل ناموفق بود. لطفا دوباره تلاش کنید.")
                    }
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
                        showSnackbarMessage(result.exception.message ?: "اطلاعات کیف پول یافت نشد")
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
                            launchSafe {
                                showErrorSnackbar(
                                    shortMessage = "رمز عبور اشتباه است",
                                    detailedMessage = "برای اضافه کردن کیف جدید به بکاپ قبلی، باید رمز بکاپ قبلی را وارد کنید.",
                                    errorTitle = "خطای رمز عبور"
                                )
                            }
                        } else {
                            launchSafe {
                                showErrorSnackbar(
                                    shortMessage = "خطا در آپلود پشتیبان",
                                    detailedMessage = "جزئیات خطا: ${result.exception.message ?: "خطای نامشخص"}",
                                    errorTitle = "خطا در آپلود پشتیبان"
                                )
                            }
                        }
                        backupAnimationState = BackupAnimationState.IDLE
                    }
                }
            } catch (e: Exception) {
                launchSafe {
                    showErrorSnackbar("خطا در پردازش پشتیبان: ${e.message ?: "خطای نامشخص"}")
                }
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
