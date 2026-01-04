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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.core.assets.AssetConfig
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.manager.ErrorManager
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.repository.IBackupRepository
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.AssetPrice
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.Wallet
import com.mtd.domain.repository.IAuthManager
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.CloudWalletMetadata
import com.mtd.megawallet.event.CreateWalletStep
import com.mtd.megawallet.event.GoogleSignInEvent
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.ui.compose.animations.BackupAnimationState
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject


@HiltViewModel
class CreateWalletViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val keyManager: KeyManager,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val blockchainRegistry: BlockchainRegistry,
    private val assetRegistry: AssetRegistry,
    private val marketDataRepository: IMarketDataRepository,
    private val backupRepository: IBackupRepository,
    private val gson: Gson,
    private val authManager: IAuthManager,
    private val cloudDataSource: ICloudDataSource,
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

    var walletAddressEVM by mutableStateOf("")
        private set

    var walletAddressBTC by mutableStateOf("")
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

    private var restoreId: String? = null

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
        this.importData = data
        // وقتی importData پاس داده می‌شود، باید از اول شروع کنیم
        // اما اگر در restore mode هستیم، نباید reset کنیم
        if (data != null && !isRestoreMode) {
            resetToInitialState()
        }
    }

    /**
     * Reset تمام state ها به حالت اولیه برای شروع فرآیند ساخت کیف پول
     */
    private fun resetToInitialState() {
        currentStep = CreateWalletStep.NAME_INPUT
        walletName = ""
        selectedColor = Color(0xFF22C55E) // Default green
        isFlipped = false
        backupAnimationState = BackupAnimationState.IDLE
        term1Accepted = false
        term2Accepted = false
        term3Accepted = false
        term4Accepted = false
        isAnimationFinished = false
        creationSuccess = false
        isRestoreMode = false
        restoreId = null
    }

    /**
     * شروع فرآیند بازیابی کیف پول از cloud backup
     * @param walletItem اطلاعات کیف پول انتخاب شده از cloud
     */
    fun startRestoreFromCloud(walletItem: com.mtd.megawallet.event.CloudWalletItem) {
        viewModelScope.launch {
            try {
                isRestoreMode = true
                restoreId = walletItem.id

                // Reset animation state برای نمایش انیمیشن از ابتدا
                isAnimationFinished = false
                backupAnimationState = BackupAnimationState.IDLE

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
                launchSafe {
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
            val result = if (currentImportData != null) {
                // حالت ایمپورت
                when (currentImportData) {
                    is ImportData.Mnemonic -> {
                        walletRepository.importWalletFromMnemonic(
                            mnemonic = currentImportData.words.joinToString(" "),
                            name = walletName,
                            color = selectedColor.toArgb(),
                            id = restoreId
                        )
                    }

                    is ImportData.PrivateKey -> {
                        walletRepository.importWalletFromPrivateKey(
                            privateKey = currentImportData.key,
                            name = walletName,
                            color = selectedColor.toArgb(),
                            id = restoreId
                        )
                    }
                }
            } else {
                // حالت ساخت جدید
                walletRepository.createNewWallet(
                    name = walletName,
                    color = selectedColor.toArgb()
                )
            }

            when (result) {
                is ResultResponse.Success -> {
                    val wallet = result.data
                    // اگر ساخت جدید بود، کلمات را نمایش می‌دهیم
                    if (currentImportData == null) {
                        wallet.mnemonic?.let { mnemonic ->
                            seedWords.clear()
                            seedWords.addAll(mnemonic.split(" "))
                        }
                    } else {
                        // در حالت ایمپورت، کلمات را نمایش نمی‌دهیم (چون کاربر خودش وارد کرده)
                        // اما برای اینکه UI فعلی (SeedPhrasePart) کار کند، شاید لازم باشد کاری کنیم.
                        // فعلاً لیست خالی می‌گذاریم یا کلمات را پر می‌کنیم (بسته به تصمیم UX)
                        // کاربر گفت: "use this info to recover"
                        if (currentImportData is ImportData.Mnemonic) {
                            seedWords.clear()
                            seedWords.addAll(currentImportData.words)
                        }
                    }
                    // استخراج آدرس (فرض بر این است که حداقل یک کلید وجود دارد)
                    wallet.keys.find { itFind -> itFind.networkType == NetworkType.EVM }
                        ?.let { key ->
                            walletAddressEVM = key.address
                        }
                    wallet.keys.find { itFind -> itFind.networkType == NetworkType.BITCOIN }
                        ?.let { key ->
                            walletAddressBTC = key.address
                        }

                    // محاسبه موجودی
                    calculateTotalBalance(wallet)

                    creationSuccess = true
                }

                is ResultResponse.Error -> {
                    // TODO: Handle error state in UI
                }
            }
        }
    }

    // --- Google Drive Sign In Event ---

    fun onCloudBackupClick() {
        viewModelScope.launch {
            try {
                // بررسی اینکه آیا اتصال به Google Drive برقرار است یا نه
                if (!cloudDataSource.isInitialized()) {
                    // نیاز به اتصال داریم - راه‌اندازی Google Sign In
                    val intent = authManager.getSignInIntent()
                    _googleSignInEvent.send(GoogleSignInEvent.LaunchIntent(intent))
                } else {
                    // اتصال برقرار است - مستقیماً به صفحه رمز عبور می‌رویم
                    currentStep = CreateWalletStep.CLOUD_BACKUP_PASSWORD
                }
            } catch (e: Exception) {
                launchSafe {
                    showErrorSnackbar("خطا در بررسی اتصال به Google Drive: ${e.message}")
                }
            }
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            when (val result = authManager.processSignInResult(data)) {
                is ResultResponse.Success -> {
                    val authCode = result.data
                    try {
                        // راه‌اندازی اتصال به Google Drive با auth code
                        cloudDataSource.initializeWithAuthCode(authCode)
                        // بعد از اتصال موفق، به صفحه رمز عبور می‌رویم
                        currentStep = CreateWalletStep.CLOUD_BACKUP_PASSWORD
                    } catch (e: Exception) {
                        launchSafe {
                            showErrorSnackbar("خطا در اتصال به Google Drive: ${e.message}")
                        }
                    }
                }

                is ResultResponse.Error -> {
                    launchSafe {
                        showErrorSnackbar("ورود به Google ناموفق بود. لطفاً دوباره تلاش کنید.")
                    }
                }
            }
        }
    }

    fun onManualBackupClick() {
        // برای این مرحله فعلاً تصمیم خاصی نگرفتیم، شاید مستقیم به تایید برود
    }

    fun onCloudPasswordSubmit(password: String) {
        viewModelScope.launch {
            try {
                // ۱. بازگشت به صفحه انیمیشن
                currentStep = CreateWalletStep.SEED_PHRASE_GENERATION

                // ۲. شروع انیمیشن لودینگ (برگشت کارت و نمایش در حال آپلود)
                backupAnimationState = BackupAnimationState.PROCESSING

                // ۳. آماده‌سازی اطلاعات کیف پول برای backup
                val walletData = when {
                    // حالت ساخت جدید - استفاده از seedWords
                    seedWords.isNotEmpty() && importData == null -> {
                        val mnemonic = seedWords.joinToString(" ")
                        if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                            launchSafe { showErrorSnackbar("عبارت بازیابی نامعتبر است") }
                            backupAnimationState = BackupAnimationState.IDLE
                            return@launch
                        }
                        CloudWalletMetadata(
                            id = UUID.randomUUID().toString(),
                            name = walletName.ifEmpty { "Wallet ${System.currentTimeMillis()}" },
                            key = mnemonic,
                            colorHex = String.format(
                                "#%06X",
                                (0xFFFFFF and selectedColor.toArgb())
                            ),
                            isMnemonic = true
                        )
                    }
                    // حالت import - استفاده از importData
                    importData != null -> {
                        when (importData) {
                            is ImportData.Mnemonic -> {
                                val mnemonic =
                                    (importData as ImportData.Mnemonic).words.joinToString(" ")
                                if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                                    launchSafe { showErrorSnackbar("عبارت بازیابی نامعتبر است") }
                                    backupAnimationState = BackupAnimationState.IDLE
                                    return@launch
                                }
                                CloudWalletMetadata(
                                    id = UUID.randomUUID().toString(),
                                    name = walletName.ifEmpty { "Wallet ${System.currentTimeMillis()}" },
                                    key = mnemonic,
                                    colorHex = String.format(
                                        "#%06X",
                                        (0xFFFFFF and selectedColor.toArgb())
                                    ),
                                    isMnemonic = true
                                )
                            }

                            is ImportData.PrivateKey -> {
                                if (!MnemonicHelper.isPrivateKeyValid((importData as ImportData.PrivateKey).key)) {
                                    launchSafe { showErrorSnackbar("کلید خصوصی نامعتبر است") }
                                    backupAnimationState = BackupAnimationState.IDLE
                                    return@launch
                                }
                                CloudWalletMetadata(
                                    id = UUID.randomUUID().toString(),
                                    name = walletName.ifEmpty { "Wallet ${System.currentTimeMillis()}" },
                                    key = (importData as ImportData.PrivateKey).key,
                                    colorHex = String.format(
                                        "#%06X",
                                        (0xFFFFFF and selectedColor.toArgb())
                                    ),
                                    isMnemonic = false
                                )
                            }

                            else -> {}
                        }
                    }

                    else -> {
                        launchSafe { showErrorSnackbar("اطلاعات کیف پول یافت نشد") }
                        backupAnimationState = BackupAnimationState.IDLE
                        return@launch
                    }
                }

                // ۴. بررسی backup قبلی و اضافه کردن کیف پول جدید
                val existingBackup = try {
                    backupRepository.restoreData(password).let {
                        if (it is ResultResponse.Success) {
                            val type = object : TypeToken<List<CloudWalletMetadata>>() {}.type
                            gson.fromJson<List<CloudWalletMetadata>>(it.data, type) ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }
                } catch (e: Exception) {
                    // اگر backup قبلی وجود نداشت یا رمز عبور اشتباه بود، لیست خالی برمی‌گردانیم
                    emptyList()
                }

                // ۵. اضافه کردن کیف پول جدید به لیست موجود (یا ایجاد لیست جدید)
                val allWallets = if (existingBackup.isNotEmpty()) {
                    existingBackup + walletData
                } else {
                    listOf(walletData)
                }

                // ۶. تبدیل به JSON
                val jsonData = gson.toJson(allWallets)

                // ۷. آپلود به کلود با رمز عبور
                when (val result = backupRepository.backupData(jsonData, password)) {
                    is ResultResponse.Success -> {
                        // ۸. نمایش وضعیت موفقیت
                        backupAnimationState = BackupAnimationState.SUCCESS
                    }

                    is ResultResponse.Error -> {
                        launchSafe {
                            showErrorSnackbar(
                                shortMessage = "خطا در آپلود پشتیبان",
                                detailedMessage = "متأسفانه در هنگام آپلود فایل پشتیبان به Google Drive خطایی رخ داد.\n\n" +
                                        "جزئیات خطا: ${result.exception.message ?: "خطای نامشخص"}\n\n" +
                                        "لطفاً اتصال اینترنت خود را بررسی کنید و دوباره تلاش کنید.",
                                errorTitle = "خطا در آپلود پشتیبان"
                            )
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
            // دریافت لیست تمام دارایی‌ها و قیمت‌های لحظه‌ای
            val allAssets = assetRegistry.getAllAssets()
            val allAssetIds = allAssets.mapNotNull { it.coinGeckoId }.distinct()

            val pricesMap = if (allAssetIds.isNotEmpty()) {
                val pricesResult = marketDataRepository.getLatestPrices(allAssetIds)
                if (pricesResult is ResultResponse.Success) {
                    pricesResult.data.associateBy { it.assetId }
                } else emptyMap()
            } else emptyMap()

            // محاسبه موجودی کیف پول
            val totalUsd = calculateSingleWalletBalance(wallet.keys, allAssets, pricesMap)

            totalBalanceUSDT = BalanceFormatter.formatUsdValue(totalUsd).replace("$", "")
        }
    }

    /**
     * محاسبه ارزش یک کیف پول خاص بر اساس کلیدهای آن
     * ساختار مشابه calculateSingleWalletBalance در WalletImportViewModel
     */
    private suspend fun calculateSingleWalletBalance(
        keys: List<WalletKey>,
        allAssets: List<AssetConfig>,
        pricesMap: Map<String, AssetPrice>
    ): BigDecimal {
        var total = BigDecimal.ZERO

        keys.forEach { key ->
            val chainId = key.chainId ?: return@forEach
            val dataSource = dataSourceFactory.create(chainId)

            if (key.networkType == NetworkType.EVM) {
                val result = dataSource.getBalanceEVM(key.address)
                if (result is ResultResponse.Success) {
                    result.data.forEach { assetBalance ->
                        val assetConfig = allAssets.find { asset ->
                            asset.networkId == blockchainRegistry.getNetworkByName(key.networkName)?.id &&
                                    (asset.contractAddress.equals(
                                        assetBalance.contractAddress,
                                        true
                                    ) ||
                                            (asset.contractAddress == null && assetBalance.contractAddress == null))
                        }

                        if (assetConfig != null) {
                            val balanceDecimal = BalanceFormatter.formatBalance(
                                assetBalance.balance,
                                assetConfig.decimals
                            ).toBigDecimal()
                            val price =
                                pricesMap[assetConfig.coinGeckoId]?.priceUsd ?: BigDecimal.ZERO
                            total += balanceDecimal * price
                        }
                    }
                }
            } else {
                val result = dataSource.getBalance(key.address)
                if (result is ResultResponse.Success) {
                    val assetConfig =
                        allAssets.find { it.networkId == blockchainRegistry.getNetworkByName(key.networkName)?.id }
                    if (assetConfig != null) {
                        val balanceDecimal =
                            BalanceFormatter.formatBalance(result.data, assetConfig.decimals)
                                .toBigDecimal()
                        val price = pricesMap[assetConfig.coinGeckoId]?.priceUsd ?: BigDecimal.ZERO
                        total += balanceDecimal * price
                    }
                }
            }
        }
        return total
    }

    fun deleteCloudBackup() {
        viewModelScope.launch {
            try {
                if (!cloudDataSource.isInitialized()) {
                    val intent = authManager.getSignInIntent()
                    _googleSignInEvent.send(GoogleSignInEvent.LaunchIntent(intent))
                    showErrorSnackbar("ابتدا به Google Drive متصل شوید")
                    return@launch
                }

                when (val result = backupRepository.deleteBackup()) {
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
