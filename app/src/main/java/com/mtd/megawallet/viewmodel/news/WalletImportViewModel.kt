package com.mtd.megawallet.viewmodel.news

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blankj.utilcode.util.ClipboardUtils
import com.mtd.core.manager.ErrorManager
import com.mtd.domain.model.CloudWalletItem
import com.mtd.domain.model.DriveBackupState
import com.mtd.domain.model.GoogleSignInEvent
import com.mtd.domain.model.ImportData
import com.mtd.domain.model.ImportScreenState
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Bip39Words
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
import timber.log.Timber
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
                    driveBackupState = DriveBackupState.NotConnected
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
                        showErrorSnackbar(
                            shortMessage = "رمز عبور اشتباه است یا خطایی در دریافت فایل رخ داد",
                            detailedMessage = result.exception.message.orEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                showErrorSnackbar("خطای غیرمنتظره در پردازش فایل: ${e.message}")
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
                Timber.tag("WalletImportVM").e(e, "Error calculating balances")
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
                showErrorSnackbar("خطا در ایمپورت ولت‌ها: ${e.message}")
            }
        }
    }

    fun clearRestoreWalletEvent() {
        restoreWalletEvent = null
    }
}
