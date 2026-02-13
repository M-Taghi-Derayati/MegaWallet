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
import com.mtd.megawallet.event.CloudWalletItem
import com.mtd.megawallet.event.CloudWalletMetadata
import com.mtd.megawallet.event.CreateWalletStep
import com.mtd.megawallet.event.GoogleSignInEvent
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.ui.compose.animations.constants.AnimationConstants
import com.mtd.megawallet.ui.compose.screens.createwallet.BackupAnimationState
import com.mtd.megawallet.ui.compose.screens.createwallet.BackupMethodType
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
            val isCloudRestoreFlow = isRestoreMode && restoreId != null
            val result = if (currentImportData != null) {
                // حالت ایمپورت
                when (currentImportData) {
                    is ImportData.Mnemonic -> {
                        walletRepository.importWalletFromMnemonic(
                            mnemonic = currentImportData.words.joinToString(" "),
                            name = walletName,
                            color = selectedColor.toArgb(),
                            id = restoreId,
                            isManualBackedUp = if (isCloudRestoreFlow) false else true,
                            isCloudBackedUp = isCloudRestoreFlow
                        )
                    }

                    is ImportData.PrivateKey -> {
                        walletRepository.importWalletFromPrivateKey(
                            privateKey = currentImportData.key,
                            name = walletName,
                            color = selectedColor.toArgb(),
                            id = restoreId,
                            isManualBackedUp = if (isCloudRestoreFlow) false else true,
                            isCloudBackedUp = isCloudRestoreFlow
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
                    generatedWalletId = wallet.id
                    // اگر ساخت جدید بود، کلمات را نمایش می‌دهیم
                    if (currentImportData == null) {
                        val mnemonicResult = walletRepository.getMnemonic(wallet.id)
                        val mnemonic = (mnemonicResult as? ResultResponse.Success)?.data
                        
                        mnemonic?.let { m ->
                            seedWords.clear()
                            seedWords.addAll(m.split(" "))
                        }
                    } else {
                        if (currentImportData is ImportData.Mnemonic) {
                            seedWords.clear()
                            seedWords.addAll(currentImportData.words)
                        }
                    }
                    walletAddress.addAll(wallet.keys)
                    walletAddress.map {
                        val net = blockchainRegistry.getNetworkByName(it.networkName)
                        it.symbol = (net?.currencySymbol ?: "ETH").lowercase()
                    }

                    // در restore از کلود اگر موجودی قبلاً در لیست محاسبه شده بود، مجدد درخواست نمی‌زنیم
                    val cachedRestoreBalance = preloadedRestoreBalanceUsdt
                    if (isCloudRestoreFlow && !cachedRestoreBalance.isNullOrBlank()) {
                        totalBalanceUSDT = cachedRestoreBalance
                    } else {
                        calculateTotalBalance(wallet)
                    }

                    if (isCloudRestoreFlow) {
                        walletRepository.updateBackupStatus(
                            walletId = wallet.id,
                            manual = false,
                            cloud = true
                        )
                    }

                    creationSuccess = true
                }

                is ResultResponse.Error -> {
                    // TODO: Handle error state in UI
                }
            }
        }
    }

    // --- Google Drive Sign In Event ---

    private suspend fun navigateToCloudPasswordStep() {
        hasExistingCloudBackup = try {
            backupRepository.hasCloudBackup()
        } catch (_: Exception) {
            false
        }
        currentStep = CreateWalletStep.CLOUD_BACKUP_PASSWORD
    }

    fun onCloudBackupClick() {
        viewModelScope.launch {
            try {
                // بررسی اینکه آیا اتصال به Google Drive برقرار است یا نه
                if (!cloudDataSource.isInitialized()) {
                    // نیاز به اتصال داریم - راه‌اندازی Google Sign In
                    val intent = authManager.getSignInIntent()
                    _googleSignInEvent.send(GoogleSignInEvent.LaunchIntent(intent))
                } else {
                    // اتصال برقرار است - پس از بررسی وجود بکاپ قبلی به صفحه رمز عبور می‌رویم
                    navigateToCloudPasswordStep()
                }
            } catch (e: Exception) {
                launchSafe {
                    showErrorSnackbar("خطا در بررسی اتصال به گوگل درایو: ${e.message}")
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
                        // بعد از اتصال موفق، با بررسی وضعیت بکاپ به صفحه رمز عبور می‌رویم
                        navigateToCloudPasswordStep()
                    } catch (e: Exception) {
                        launchSafe {
                            showErrorSnackbar("خطا در اتصال به گوگل درایو: ${e.message}")
                        }
                    }
                }

                is ResultResponse.Error -> {
                    launchSafe {
                        showErrorSnackbar("ورود به گوگل ناموفق بود. لطفاً دوباره تلاش کنید.")
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
                walletRepository.updateBackupStatus(walletId = walletId, manual = true)
            }
            backupAnimationState = BackupAnimationState.SUCCESS
        }
    }

    fun onCloudPasswordSubmit(password: String) {
        viewModelScope.launch {
            try {
                // ۱. بازگشت به صفحه انیمیشن
                currentStep = CreateWalletStep.SEED_PHRASE_GENERATION

                // ۲. شروع انیمیشن لودینگ (برگشت کارت و نمایش در حال آپلود)
                backupMethod = BackupMethodType.CLOUD
                backupAnimationState = BackupAnimationState.PROCESSING

                // ۳. آماده‌سازی اطلاعات کیف پول برای backup
                val walletData: CloudWalletMetadata = when {
                    // حالت ساخت جدید - استفاده از seedWords
                    seedWords.isNotEmpty() && importData == null -> {
                        val mnemonic = seedWords.joinToString(" ")
                        if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                            launchSafe { showErrorSnackbar("عبارت بازیابی نامعتبر است") }
                            backupAnimationState = BackupAnimationState.IDLE
                            return@launch
                        }
                        CloudWalletMetadata(
                            id = generatedWalletId ?: UUID.randomUUID().toString(),
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
                        when (val pendingImport = importData) {
                            is ImportData.Mnemonic -> {
                                val mnemonic =
                                    pendingImport.words.joinToString(" ")
                                if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                                    launchSafe { showErrorSnackbar("عبارت بازیابی نامعتبر است") }
                                    backupAnimationState = BackupAnimationState.IDLE
                                    return@launch
                                }
                                CloudWalletMetadata(
                                    id = generatedWalletId ?: UUID.randomUUID().toString(),
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
                                if (!MnemonicHelper.isPrivateKeyValid(pendingImport.key)) {
                                    launchSafe { showErrorSnackbar("کلید خصوصی نامعتبر است") }
                                    backupAnimationState = BackupAnimationState.IDLE
                                    return@launch
                                }
                                CloudWalletMetadata(
                                    id = generatedWalletId ?: UUID.randomUUID().toString(),
                                    name = walletName.ifEmpty { "Wallet ${System.currentTimeMillis()}" },
                                    key = pendingImport.key,
                                    colorHex = String.format(
                                        "#%06X",
                                        (0xFFFFFF and selectedColor.toArgb())
                                    ),
                                    isMnemonic = false
                                )
                            }

                            null -> {
                                launchSafe { showErrorSnackbar("اطلاعات کیف پول یافت نشد") }
                                backupAnimationState = BackupAnimationState.IDLE
                                return@launch
                            }
                        }
                    }

                    else -> {
                        launchSafe { showErrorSnackbar("اطلاعات کیف پول یافت نشد") }
                        backupAnimationState = BackupAnimationState.IDLE
                        return@launch
                    }
                }

                // ۴. بررسی وجود backup قبلی و اعتبارسنجی رمز در صورت وجود آن
                val hasExistingBackup = try {
                    backupRepository.hasCloudBackup()
                } catch (e: Exception) {
                    launchSafe {
                        showErrorSnackbar(
                            shortMessage = "خطا در بررسی وضعیت بکاپ ابری",
                            detailedMessage = e.message ?: "خطای نامشخص",
                            errorTitle = "خطا"
                        )
                    }
                    backupAnimationState = BackupAnimationState.IDLE
                    return@launch
                }

                val existingBackup: List<CloudWalletMetadata> = if (hasExistingBackup) {
                    when (val restoreResult = backupRepository.restoreData(password)) {
                        is ResultResponse.Success -> {
                            try {
                                val type = object : TypeToken<List<CloudWalletMetadata>>() {}.type
                                gson.fromJson<List<CloudWalletMetadata>>(restoreResult.data, type)
                                    ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }

                        is ResultResponse.Error -> {
                            launchSafe {
                                showErrorSnackbar(
                                    shortMessage = "رمز عبور اشتباه است",
                                    detailedMessage = "برای اضافه کردن کیف جدید به بکاپ قبلی، باید رمز بکاپ قبلی را وارد کنید.",
                                    errorTitle = "خطای رمز عبور"
                                )
                            }
                            backupAnimationState = BackupAnimationState.IDLE
                            return@launch
                        }
                    }
                } else {
                    emptyList()
                }

                // ۵. اضافه کردن کیف پول جدید به لیست موجود (یا ایجاد لیست جدید) با حذف تکراری‌ها
                val allWallets = existingBackup
                    .filterNot { it.id == walletData.id }
                    .plus(walletData)

                // ۶. تبدیل به JSON
                val jsonData = gson.toJson(allWallets)

                // ۷. آپلود به کلود با رمز عبور
                when (val result = backupRepository.backupData(jsonData, password)) {
                    is ResultResponse.Success -> {
                        generatedWalletId?.let { walletId ->
                            walletRepository.updateBackupStatus(walletId = walletId, cloud = true)
                        }
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
            val allAssetIds = allAssets.map { it.symbol }.distinct()

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

            if (key.networkType == NetworkType.EVM || key.networkType == NetworkType.TVM) {
                val result = dataSource.getBalanceAssets(key.address)
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
                            val price =
                                pricesMap[assetConfig.symbol]?.priceUsd ?: BigDecimal.ZERO
                            total += assetBalance.balance * price
                        }
                    }
                }
            } else {
                val result = dataSource.getBalance(key.address)
                if (result is ResultResponse.Success) {
                    val assetConfig =
                        allAssets.find { it.networkId == blockchainRegistry.getNetworkByName(key.networkName)?.id }
                    if (assetConfig != null) {
                        val price = pricesMap[assetConfig.symbol]?.priceUsd ?: BigDecimal.ZERO
                        total += result.data * price
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
