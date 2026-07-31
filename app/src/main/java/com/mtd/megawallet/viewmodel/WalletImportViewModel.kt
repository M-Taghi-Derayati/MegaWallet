package com.mtd.megawallet.viewmodel

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blankj.utilcode.util.ClipboardUtils
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.domain.model.CloudWalletItem
import com.mtd.domain.model.DriveBackupState
import com.mtd.domain.model.GoogleSignInEvent
import com.mtd.domain.model.ImportData
import com.mtd.domain.model.ImportScreenState
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Bip39Words
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.usecase.wallet.importwallet.CalculateCloudWalletBalancesUseCase
import com.mtd.domain.usecase.wallet.importwallet.ConnectCloudBackupUseCase
import com.mtd.domain.usecase.wallet.importwallet.GetCloudSignInIntentUseCase
import com.mtd.domain.usecase.wallet.importwallet.GetDriveBackupStateUseCase
import com.mtd.domain.usecase.wallet.importwallet.ImportCloudWalletsUseCase
import com.mtd.domain.usecase.wallet.importwallet.RestoreCloudWalletsUseCase
import com.mtd.domain.usecase.wallet.importwallet.ValidateImportSecretUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class WalletImportViewModel @Inject constructor(
    private val validateImportSecretUseCase: ValidateImportSecretUseCase,
    private val getCloudSignInIntentUseCase: GetCloudSignInIntentUseCase,
    private val getDriveBackupStateUseCase: GetDriveBackupStateUseCase,
    private val connectCloudBackupUseCase: ConnectCloudBackupUseCase,
    private val restoreCloudWalletsUseCase: RestoreCloudWalletsUseCase,
    private val calculateCloudWalletBalancesUseCase: CalculateCloudWalletBalancesUseCase,
    private val importCloudWalletsUseCase: ImportCloudWalletsUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    var screenState by mutableStateOf(ImportScreenState.STACKED)
        private set

    var pastedWords = mutableStateListOf<String>()
        private set

    var pastedPrivateKey by mutableStateOf("")
        private set

    var manualWords = mutableStateListOf<String>()
        private set

    var driveBackupState by mutableStateOf<DriveBackupState>(DriveBackupState.Checking)
        private set

    var cloudWallets = mutableStateListOf<CloudWalletItem>()
        private set

    var isDownloadingBackup by mutableStateOf(false)
        private set

    var isCalculatingBalances by mutableStateOf(false)
        private set

    var validationSuccessEvent by mutableStateOf<ImportData?>(null)
        private set

    var restoreWalletEvent by mutableStateOf<CloudWalletItem?>(null)
        private set

    private val _googleSignInEvent = Channel<GoogleSignInEvent>()
    val googleSignInEvent = _googleSignInEvent.receiveAsFlow()

    init {
        repeat(12) { manualWords.add("") }
        checkDriveBackupStatus()
    }

    fun updateScreenState(state: ImportScreenState) {
        screenState = state
    }

    fun handleBack(): Boolean {
        val previous = when (screenState) {
            ImportScreenState.SEED_PHRASE_MANUAL -> ImportScreenState.SEED_PHRASE_AUTO
            ImportScreenState.SEED_PHRASE_AUTO -> {
                resetPastedState()
                ImportScreenState.IMPORT_OPTIONS
            }
            ImportScreenState.PRIVATE_KEY_INPUT -> {
                resetPastedState()
                ImportScreenState.IMPORT_OPTIONS
            }
            ImportScreenState.CLOUD_WALLET_LIST -> ImportScreenState.CLOUD_PASSWORD_INPUT
            ImportScreenState.CLOUD_PASSWORD_INPUT -> ImportScreenState.STACKED
            ImportScreenState.IMPORT_OPTIONS -> ImportScreenState.STACKED
            ImportScreenState.STACKED -> return false
        }
        screenState = previous
        return true
    }

    fun resetToInitialState() {
        screenState = ImportScreenState.STACKED
        pastedWords.clear()
        pastedPrivateKey = ""
        manualWords.clear()
        repeat(12) { manualWords.add("") }
        cloudWallets.clear()
        isDownloadingBackup = false
        isCalculatingBalances = false
        validationSuccessEvent = null
        restoreWalletEvent = null
    }

    private fun resetPastedState() {
        clearPastedWords()
        clearPastedPrivateKey()
    }

    fun clearPastedWords() {
        pastedWords.clear()
    }

    fun clearPastedPrivateKey() {
        pastedPrivateKey = ""
    }

    fun onPasteSeedPhraseToCard() {
        val words = getClipboardWords()
        if (words.isNotEmpty() && isSeedPhraseClipboardValid(words)) {
            pastedWords.clear()
            pastedWords.addAll(words)
        }
    }

    fun onPastePrivateKeyToCard() {
        val key = getClipboardText()
        if (key.isNotEmpty() && isPrivateKeyClipboardValid(key)) {
            pastedPrivateKey = key
        }
    }

    fun updateManualWord(index: Int, word: String) {
        if (index in manualWords.indices) {
            manualWords[index] = word
        }
    }

    fun confirmManualEntry() {
        pastedWords.clear()
        pastedWords.addAll(manualWords)
        screenState = ImportScreenState.SEED_PHRASE_AUTO
    }

    fun getClipboardText(): String {
        return ClipboardUtils.getText().toString().trim()
    }

    fun getClipboardWords(): List<String> {
        val text = getClipboardText()
        return if (text.isEmpty()) emptyList() else text.replace(Regex("\\s+"), " ").split(" ")
    }

    fun isSeedPhraseClipboardValid(seedWords: List<String>): Boolean {
        return (seedWords.size == 12 || seedWords.size == 24) &&
            seedWords.all { word -> Bip39Words.English.contains(word.lowercase()) }
    }

    fun isPrivateKeyClipboardValid(privateKey: String): Boolean {
        return validateImportSecretUseCase(ImportData.PrivateKey(privateKey))
    }

    fun importWallet() {
        val words = if (screenState == ImportScreenState.SEED_PHRASE_AUTO) pastedWords else manualWords
        val importData = ImportData.Mnemonic(words)
        if (words.isEmpty() || !validateImportSecretUseCase(importData)) {
            showSnackbarMessage("کلمات بازیابی نامعتبر است")
            return
        }
        validationSuccessEvent = importData
    }

    fun importPrivateKey() {
        val importData = ImportData.PrivateKey(pastedPrivateKey)
        if (!validateImportSecretUseCase(importData)) {
            showSnackbarMessage("کلید خصوصی نامعتبر است")
            return
        }
        validationSuccessEvent = importData
    }

    fun clearValidationSuccessEvent() {
        validationSuccessEvent = null
    }

    private fun checkDriveBackupStatus() {
        launchSafe {
            driveBackupState = getDriveBackupStateUseCase()
        }
    }

    fun onCloudBackupClicked() {
        launchSafe {
            driveBackupState = DriveBackupState.Checking
            when (val state = getDriveBackupStateUseCase()) {
                DriveBackupState.BackupFound -> {
                    screenState = ImportScreenState.CLOUD_PASSWORD_INPUT
                    driveBackupState = DriveBackupState.BackupFound
                }
                DriveBackupState.NotConnected -> {
                    driveBackupState = DriveBackupState.NotConnected
                    _googleSignInEvent.send(GoogleSignInEvent.LaunchIntent(getCloudSignInIntentUseCase()))
                }
                else -> driveBackupState = state
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        launchSafe {
            when (val result = connectCloudBackupUseCase(data)) {
                is ResultResponse.Success -> {
                    driveBackupState = result.data
                    if (result.data == DriveBackupState.BackupFound) {
                        screenState = ImportScreenState.CLOUD_PASSWORD_INPUT
                    }
                }
                is ResultResponse.Error -> {
                    // The UI already reflects "not connected" and the user can retry the button,
                    // so this logs rather than stacking a snackbar on top of a visible state.
                    driveBackupState = DriveBackupState.NotConnected
                    reportError(
                        throwable = result.exception,
                        userAction = "connectCloudBackup",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }
        }
    }

    fun onRestorePasswordConfirm(password: String) {
        launchSafe {
            try {
                isDownloadingBackup = true
                when (val result = restoreCloudWalletsUseCase(password)) {
                    is ResultResponse.Success -> {
                        cloudWallets.clear()
                        cloudWallets.addAll(result.data)
                        screenState = ImportScreenState.CLOUD_WALLET_LIST
                        calculateAllBalances()
                    }
                    is ResultResponse.Error -> {
                        reportError(
                            throwable = result.exception,
                            userAction = "restoreCloudWallets",
                            surface = ErrorSurface.SNACKBAR,
                            fallbackMessage = "رمز عبور اشتباه است یا خطایی در دریافت فایل رخ داد"
                        )
                    }
                }
            } catch (e: Exception) {
                reportError(
                    throwable = e,
                    userAction = "onRestorePasswordConfirm",
                    surface = ErrorSurface.SNACKBAR,
                    fallbackMessage = "خطای غیرمنتظره در پردازش فایل پشتیبان"
                )
            } finally {
                isDownloadingBackup = false
            }
        }
    }

    private fun calculateAllBalances() {
        launchSafe {
            try {
                isCalculatingBalances = true
                calculateCloudWalletBalancesUseCase(cloudWallets).forEachIndexed { index, wallet ->
                    cloudWallets[index] = wallet
                    kotlinx.coroutines.delay(50)
                }
            } catch (e: Exception) {
                // Balances are decoration on the wallet-picker list; the user can still choose
                // which wallets to restore without them (ErrorSurface.SILENT).
                reportError(
                    throwable = e,
                    userAction = "calculateCloudWalletBalances",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
            } finally {
                isCalculatingBalances = false
            }
        }
    }

    fun onImportCloudWallets(selectedIds: List<String>) {
        launchSafe {
            val selected = cloudWallets.filter { it.id in selectedIds }
            if (selected.isEmpty()) return@launchSafe

            try {
                val first = selected.first()
                val remaining = selected.drop(1)
                if (remaining.isNotEmpty()) {
                    val result = importCloudWalletsUseCase(remaining)
                    if (result.failedCount > 0) {
                        showErrorSnackbar("بخشی از کیف پول‌ها بازیابی نشدند. لطفا دوباره بررسی کنید.")
                    }
                }
                restoreWalletEvent = first
            } catch (e: Exception) {
                // Some wallets may or may not have landed — the user must acknowledge before the
                // flow moves on and hides the ambiguity.
                reportError(
                    throwable = e,
                    userAction = "importCloudWallets",
                    surface = ErrorSurface.BLOCKING,
                    severity = ErrorSeverity.HIGH,
                    fallbackMessage = "خطا در بازیابی کیف پول‌ها"
                )
            }
        }
    }

    fun clearRestoreWalletEvent() {
        restoreWalletEvent = null
    }
}
