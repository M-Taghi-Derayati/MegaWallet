package com.mtd.megawallet.viewmodel.news

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.toColorInt
import com.blankj.utilcode.util.ClipboardUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.core.assets.AssetConfig
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.model.Bip39Words
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.repository.IBackupRepository
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.repository.IAuthManager
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.CloudWalletItem
import com.mtd.megawallet.event.CloudWalletMetadata
import com.mtd.megawallet.event.DriveBackupState
import com.mtd.megawallet.event.GoogleSignInEvent
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.event.ImportScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class WalletImportViewModel @Inject constructor(
    private val authManager: IAuthManager,
    private val cloudDataSource: ICloudDataSource,
    private val backupRepository: IBackupRepository,
    private val walletRepository: IWalletRepository,
    private val gson: Gson,
    private val assetRegistry: AssetRegistry,
    private val blockchainRegistry: BlockchainRegistry,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val marketDataRepository: IMarketDataRepository,
    private val keyManager: KeyManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    // --- Screen State ---
    var screenState by mutableStateOf(ImportScreenState.STACKED)
        private set

    // --- Seed Phrase State ---
    var pastedWords = mutableStateListOf<String>()
        private set

    // --- Private Key State ---
    var pastedPrivateKey by mutableStateOf("")
        private set

    // --- Manual Input State ---
    var manualWords = mutableStateListOf<String>()
        private set



    // --- Google Drive Backup Status ---
    var driveBackupState by mutableStateOf<DriveBackupState>(DriveBackupState.Checking)
        private set

    // --- Cloud Restore State ---
    var cloudWallets = mutableStateListOf<CloudWalletItem>()
        private set

    var isDownloadingBackup by mutableStateOf(false)
        private set

    var isCalculatingBalances by mutableStateOf(false)
        private set

    init {
        // Initialize with empty words
        repeat(12) { manualWords.add("") }

        // Check Drive backup status
        checkDriveBackupStatus()
    }

    fun updateScreenState(state: ImportScreenState) {
        screenState = state
    }

    fun handleBack(): Boolean {
        val prevState = when (screenState) {
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
        screenState = prevState
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
        return (seedWords.size == 12 || seedWords.size == 24) && seedWords.all { word ->
            try {
                Bip39Words.English.contains(word.lowercase())
            } catch (e: Exception) {
                false
            }
        }
    }

    fun isPrivateKeyClipboardValid(privateKey: String): Boolean {
        return MnemonicHelper.isPrivateKeyValid(privateKey)
    }

    // رویداد موفقیت اعتبارسنجی برای انتقال به صفحه بعد
    var validationSuccessEvent by mutableStateOf<ImportData?>(null)
        private set

    fun importWallet() {
        val words = if (screenState == ImportScreenState.SEED_PHRASE_AUTO) pastedWords else manualWords
        if (words.isEmpty() || !MnemonicHelper.isValidMnemonic(words.joinToString(" "))) {
            launchSafe { showErrorSnackbar("کلمات بازیابی نامعتبر است") }
            return
        }

        // موفقیت اعتبارسنجی -> ارسال داده‌ها
        validationSuccessEvent = ImportData.Mnemonic(words)
    }

    fun importPrivateKey() {
        if (!isPrivateKeyClipboardValid(getClipboardText())) {
            launchSafe { showErrorSnackbar("کلید خصوصی نامعتبر است") }
            return
        }

        // موفقیت اعتبارسنجی -> ارسال داده‌ها
        validationSuccessEvent = ImportData.PrivateKey(pastedPrivateKey)
    }

    fun clearValidationSuccessEvent() {
        validationSuccessEvent = null
    }

    // --- Google Drive Logic ---

    private fun checkDriveBackupStatus() {
        launchSafe {
            try {
                if (!cloudDataSource.isInitialized()) {
                    driveBackupState = DriveBackupState.NotConnected
                } else {
                    val hasBackup = cloudDataSource.hasCloudBackup()
                    driveBackupState = if (hasBackup) DriveBackupState.BackupFound else DriveBackupState.NoBackup
                }
            } catch (e: Exception) {
                driveBackupState = DriveBackupState.NoBackup
            }
        }
    }

    fun onCloudBackupClicked() {
        launchSafe {
            try {
                if (driveBackupState != DriveBackupState.BackupFound) {
                    driveBackupState = DriveBackupState.Checking
                } else if (driveBackupState == DriveBackupState.BackupFound) {
                    screenState = ImportScreenState.CLOUD_PASSWORD_INPUT
                    driveBackupState = DriveBackupState.BackupFound

                    return@launchSafe
                }
                if (!cloudDataSource.isInitialized()) {
                    // نیاز به اتصال داریم
                    val intent = authManager.getSignInIntent()
                    _googleSignInEvent.send(GoogleSignInEvent.LaunchIntent(intent))
                } else {
                    // متصل است، بررسی می‌کنیم آیا backup دارد
                    val hasBackup = cloudDataSource.hasCloudBackup()
                    if (hasBackup) {
                        // فایل پشتیبان پیدا شد، به صفحه رمز می‌رویم
                        screenState = ImportScreenState.CLOUD_PASSWORD_INPUT
                        driveBackupState = DriveBackupState.BackupFound
                    } else {
                        driveBackupState = DriveBackupState.NoBackup
                    }
                }
            } catch (e: Exception) {
                driveBackupState = DriveBackupState.NoBackup
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        launchSafe {
            when (val result = authManager.processSignInResult(data)) {
                is ResultResponse.Success -> {
                    val authCode = result.data
                    try {
                        cloudDataSource.initializeWithAuthCode(authCode)
                        // بعد از اتصال، مجدداً بررسی می‌کنیم
                        checkDriveBackupStatus()
                    } catch (e: Exception) {
                        driveBackupState = DriveBackupState.NoBackup
                    }
                }
                is ResultResponse.Error -> {
                    driveBackupState = DriveBackupState.NotConnected
                }
            }
        }
    }

    // --- Restore Flow ---

    fun onRestorePasswordConfirm(password: String) {
        launchSafe {
            try {
                isDownloadingBackup = true
                
                // ۱. فرآیند بازیابی متادیتا از کلود
                when (val result = backupRepository.restoreData(password)) {
                    is ResultResponse.Success -> {
                        val jsonData = result.data
                        val type = object : TypeToken<List<CloudWalletMetadata>>() {}.type
                        val metadataList: List<CloudWalletMetadata> = gson.fromJson(jsonData, type)
                        
                        // ۲. به‌روزرسانی لیست اولیه ولت‌ها
                        cloudWallets.clear()
                        cloudWallets.addAll(metadataList.map { meta ->
                            CloudWalletItem(
                                id = meta.id,
                                name = meta.name,
                                key = meta.key,
                                colorHex = meta.colorHex,
                                isMnemonic = meta.isMnemonic
                            )
                        })
                        
                        // ۳. انتقال به صفحه لیست (قبل از محاسبه balance ها)
                        screenState = ImportScreenState.CLOUD_WALLET_LIST
                        
                        // ۴. شروع محاسبه موجودی برای تمام ولت‌ها (به صورت async و incremental)
                        calculateAllBalances()
                    }
                    is ResultResponse.Error -> {
                        showErrorSnackbar("رمز عبور اشتباه است یا خطایی در دریافت فایل رخ داد","مشکل از رمز هست")
                    }
                }
            } catch (e: Exception) {
                showErrorSnackbar("خطای غیرمنتظره در پردازش فایل: ${e.message}")
            } finally {
                isDownloadingBackup = false
            }
        }
    }

    /**
     * محاسبه مجموع موجودی برای تمام کیف پول‌های یافت شده
     * به صورت incremental برای جلوگیری از block کردن UI
     */
    private fun calculateAllBalances() {
        launchSafe {
            try {
                isCalculatingBalances = true
                
                // دریافت لیست تمام دارایی‌ها و قیمت‌های لحظه‌ای
                val allAssets = assetRegistry.getAllAssets()
                val allAssetIds = allAssets.map { it.symbol }.distinct()
                
                val pricesMap = if (allAssetIds.isNotEmpty()) {
                    val pricesResult = marketDataRepository.getLatestPrices(allAssetIds)
                    if (pricesResult is ResultResponse.Success) {
                        pricesResult.data.associateBy { it.assetId }
                    } else emptyMap()
                } else emptyMap()

                // پیمایش روی تک‌تک ولت‌های کلود برای محاسبه ارزش هر کدام
                // استفاده از withContext(Dispatchers.Default) برای انجام محاسبات در background thread
                cloudWallets.forEachIndexed { index, cloudWallet ->
                    val totalUsd = calculateSingleWalletBalance(cloudWallet, allAssets, pricesMap)
                    
                    // به‌روزرسانی مقدار در لیست (Trigger Recomposition)
                    cloudWallets[index] = cloudWallet.copy(
                        balanceUsdt = BalanceFormatter.formatUsdValue(totalUsd).replace("$", "")
                    )
                    
                    // اضافه کردن delay کوچک برای جلوگیری از block کردن UI
                    kotlinx.coroutines.delay(50)
                }
            } catch (e: Exception) {
                // در صورت خطا، فقط لاگ می‌کنیم و ادامه می‌دهیم
                Timber.tag("WalletImportVM").e(e, "Error calculating balances")
            } finally {
                isCalculatingBalances = false
            }
        }
    }

    /**
     * محاسبه ارزش یک کیف پول خاص بر اساس کلید یا Seed آن
     */
    private suspend fun calculateSingleWalletBalance(
        cloudWallet: CloudWalletItem,
        allAssets: List<AssetConfig>,
        pricesMap: Map<String, AssetPrice>
    ): BigDecimal {
        var total = BigDecimal.ZERO
        
        // تولید کلیدهای آدرس برای شبکه‌های مختلف
        val keys = if (cloudWallet.isMnemonic) {
            keyManager.generateWalletKeysFromMnemonic(cloudWallet.key)
        } else {
            keyManager.generateWalletKeysFromPrivateKey(cloudWallet.key)
        }

        keys.forEach { key ->
            val chainId = key.chainId ?: return@forEach
            val dataSource = dataSourceFactory.create(chainId)
            
            if (key.networkType == NetworkType.EVM) {
                val result = dataSource.getBalanceAssets(key.address)
                if (result is ResultResponse.Success) {
                    result.data.forEach { assetBalance ->
                        val assetConfig = allAssets.find { asset ->
                            asset.networkId == blockchainRegistry.getNetworkByName(key.networkName)?.id &&
                            (asset.contractAddress.equals(assetBalance.contractAddress, true) || 
                             (asset.contractAddress == null && assetBalance.contractAddress == null))
                        }
                        
                        if (assetConfig != null) {
                            val balanceDecimal = BalanceFormatter.formatBalance(assetBalance.balance, assetConfig.decimals).toBigDecimal()
                            val price = pricesMap[assetConfig.symbol]?.priceUsd ?: BigDecimal.ZERO
                            total += balanceDecimal * price
                        }
                    }
                }
            } else {
                val result = dataSource.getBalance(key.address)
                if (result is ResultResponse.Success) {
                    val assetConfig = allAssets.find { it.networkId == blockchainRegistry.getNetworkByName(key.networkName)?.id }
                    if (assetConfig != null) {
                        val balanceDecimal = BalanceFormatter.formatBalance(result.data, assetConfig.decimals).toBigDecimal()
                        val price = pricesMap[assetConfig.symbol]?.priceUsd ?: BigDecimal.ZERO
                        total += balanceDecimal * price
                    }
                }
            }
        }
        return total
    }

    // Event برای navigation به CreateWalletScreen در حالت restore
    var restoreWalletEvent by mutableStateOf<CloudWalletItem?>(null)
        private set

    fun onImportCloudWallets(selectedIds: List<String>) {
        launchSafe {
            val selected = cloudWallets.filter { it.id in selectedIds }
            if (selected.isEmpty()) return@launchSafe

            try {
                // برای حفظ UX فعلی، اولین کیف پول با انیمیشن restore می‌شود
                // و بقیه به صورت مستقیم در پس‌زمینه وارد می‌شوند.
                val first = selected.first()

                val remaining = selected.drop(1)
                if (remaining.isNotEmpty()) {
                    var failedCount = 0
                    remaining.forEach { walletItem ->
                        val walletColor = runCatching { walletItem.colorHex.toColorInt() }
                            .getOrElse { 0xFF22C55E.toInt() }

                        val importResult = if (walletItem.isMnemonic) {
                            walletRepository.importWalletFromMnemonic(
                                mnemonic = walletItem.key,
                                name = walletItem.name,
                                color = walletColor,
                                id = walletItem.id,
                                isManualBackedUp = false,
                                isCloudBackedUp = true
                            )
                        } else {
                            walletRepository.importWalletFromPrivateKey(
                                privateKey = walletItem.key,
                                name = walletItem.name,
                                color = walletColor,
                                id = walletItem.id,
                                isManualBackedUp = false,
                                isCloudBackedUp = true
                            )
                        }

                        if (importResult is ResultResponse.Error) {
                            failedCount++
                            Timber.tag("WalletImportVM").e(
                                importResult.exception,
                                "Failed importing cloud wallet id=${walletItem.id}"
                            )
                        }
                    }

                    if (failedCount > 0) {
                        showErrorSnackbar("بخشی از کیف پول‌ها بازیابی نشدند. لطفاً دوباره بررسی کنید.")
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

    private val _googleSignInEvent = Channel<GoogleSignInEvent>()
    val googleSignInEvent = _googleSignInEvent.receiveAsFlow()
}
