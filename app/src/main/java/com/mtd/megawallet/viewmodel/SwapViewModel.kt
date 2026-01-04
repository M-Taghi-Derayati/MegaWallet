package com.mtd.megawallet.viewmodel


import com.mtd.core.assets.AssetConfig
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.model.NetworkType
import com.mtd.core.model.QuoteResponse
import com.mtd.core.model.QuoteResponse.ReceivingOptionDto
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.Eip712Helper
import com.mtd.core.utils.GlobalEvent
import com.mtd.core.utils.GlobalEventBus
import com.mtd.core.utils.TokenInfoHelper
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IWalletRepository
import com.mtd.data.repository.TransactionParams
import com.mtd.domain.model.ExecuteRequest
import com.mtd.domain.model.ExecuteRequest.PermitParametersDto
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapConfig
import com.mtd.domain.repository.ISwapRepository
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.AssetSelectionListItem
import com.mtd.megawallet.event.SwapAsset
import com.mtd.megawallet.event.SwapNavigationEvent
import com.mtd.megawallet.event.SwapUiState
import com.mtd.megawallet.event.SwapUiState.AssetSelectItem
import com.mtd.megawallet.event.SwapUiState.ReceivingOptionUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.web3j.crypto.Hash
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val swapRepository: ISwapRepository,
    private val walletRepository: IWalletRepository,
    private val keyManager: KeyManager,
    private val assetRegistry: AssetRegistry,
    private val blockchainRegistry: BlockchainRegistry,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val globalEventBus: GlobalEventBus,
    private val swapConfig: SwapConfig,
    private val activeWalletManager: com.mtd.domain.wallet.ActiveWalletManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {


    private val _uiState = MutableStateFlow<SwapUiState>(SwapUiState.InitialLoading)
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<SwapNavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var quoteDebounceJob: Job? = null
    private var userBalances = mutableMapOf<String, BigDecimal>() // کش موجودی‌ها

    init {
        loadInitialData()
    }


    /**
     * داده‌های اولیه برای صفحه سواپ را بارگذاری می‌کند.
     * این تابع ابتدا تمام دارایی‌های درگیر در سواپ را مشخص کرده،
     * سپس موجودی آنها را دریافت می‌کند و در نهایت لیست دارایی‌های مبدا را برای UI آماده می‌کند.
     */
    private fun loadInitialData() {
        _uiState.value = SwapUiState.InitialLoading
        launchSafe {

            // ۱. به جای فراخوانی ریپازیتوری، تمام دارایی‌های درگیر را مستقیماً از SwapConfig می‌گیریم.
            val allAssetConfigs =
                swapConfig.getAvailableFromAssets() + swapConfig.supportedRoutesRaw.mapNotNull {
                    assetRegistry.getAssetById(it.second)
                }
            val uniqueAssetConfigs = allAssetConfigs.distinctBy { it.id }

            val assetsByNetwork = uniqueAssetConfigs.groupBy { it.networkId }

            // ۲. --- منطق دریافت موجودی‌ها (کد شما که بسیار خوب است و بدون تغییر باقی می‌ماند) ---
                val balancesDeferred = assetsByNetwork.map { (networkId, assetsInNetwork) ->
                    async {
                        val network = blockchainRegistry.getNetworkById(networkId)!!
                        // اطمینان از وجود آدرس قبل از ادامه
                        val userAddress = network.chainId?.let { 
                            activeWalletManager.getAddressForNetwork(it) 
                        } ?: return@async
                        val dataSource = dataSourceFactory.create(network.chainId!!)

                        if (network.networkType == NetworkType.EVM) {
                            val evmBalanceResult = dataSource.getBalanceEVM(userAddress)
                            if (evmBalanceResult is ResultResponse.Success) {
                                evmBalanceResult.data.forEach { asset ->
                                    val config = assetsInNetwork.find {
                                        it.symbol == asset.symbol && (it.contractAddress?.equals(
                                            asset.contractAddress,
                                            ignoreCase = true
                                        ) ?: (asset.contractAddress == null))
                                    }
                                    if (config != null) {
                                        val balanceDecimal = BigDecimal(asset.balance).divide(
                                            BigDecimal.TEN.pow(config.decimals)
                                        )
                                        userBalances[config.id] = balanceDecimal
                                    }
                                }
                            }
                        } else { // برای Bitcoin
                            val utxoBalanceResult = dataSource.getBalance(userAddress)
                            if (utxoBalanceResult is ResultResponse.Success) {
                                val config = assetsInNetwork.first()
                                val balanceDecimal = BigDecimal(utxoBalanceResult.data).divide(
                                    BigDecimal.TEN.pow(config.decimals)
                                )
                                userBalances[config.id] = balanceDecimal
                            }
                        }
                    }
                }
                balancesDeferred.awaitAll() // منتظر می‌مانیم تا تمام موجودی‌ها گرفته شوند
            // --- پایان منطق دریافت موجودی‌ها ---

            // ۳. ساخت لیست نهایی دارایی‌های مبدا برای نمایش در UI
            val fromAssets = swapConfig.getAvailableFromAssets().map { config ->
                config.toSwapAsset(userBalances[config.id] ?: BigDecimal.ZERO)
                }

            // ۴. انتشار State نهایی
                _uiState.value = SwapUiState.Ready(
                    fromAssets = fromAssets.sortedBy { it.symbol }, // مرتب‌سازی برای نمایش بهتر
                    toAssets = emptyList(),
                    selectedFromAsset = null,
                    selectedToAsset = null,
                    fromNetworkId = null
                )

        }
    }

    /**
     * فراخوانی می‌شود وقتی کاربر دارایی مبدأ را انتخاب می‌کند.
     * این تابع با استفاده از SwapConfig، لیست دارایی‌های مقصد ممکن را فیلتر کرده
     * و fromNetworkId را در State ذخیره می‌کند.
     */
    fun onFromAssetSelected(selectedAssetId: String) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return

        // ۱. پیدا کردن اطلاعات کامل دارایی انتخاب شده
        val selectedAssetConfig = assetRegistry.getAssetById(selectedAssetId)
        if (selectedAssetConfig == null) {
            _uiState.value = currentState.copy(error = "دارایی انتخاب شده یافت نشد.")
            return
        }

        // ۲. --- بخش جدید: پیدا کردن دارایی‌های مقصد از SwapConfig ---
        // ما از SwapConfig می‌پرسیم که برای این دارایی مبدا، چه مقصدهایی ممکن است
        val toAssetsConfig = swapConfig.getPossibleToAssets(selectedAssetConfig)

        // تبدیل AssetConfig های مقصد به SwapAsset (مدل UI) به همراه موجودی آنها
        val toAssets = toAssetsConfig.map { config ->
            config.toSwapAsset(userBalances[config.id] ?: BigDecimal.ZERO)
        }
        // --- پایان بخش جدید ---

        // ۳. به‌روزرسانی UiState با اطلاعات جدید
        _uiState.value = currentState.copy(
            selectedFromAsset = selectedAssetConfig.toSwapAsset(
                userBalances[selectedAssetId] ?: BigDecimal.ZERO
            ),
            toAssets = toAssets.sortedBy { it.symbol }, // مرتب‌سازی برای نمایش بهتر
            fromNetworkId = selectedAssetConfig.networkId,
            isBottomSheetVisible = false,
            // ریست کردن کامل وضعیت‌های وابسته برای شروع یک سواپ جدید
            selectedToAsset = null,
            quote = null,
            amountIn = "",
            amountOut = "",
            error = null,
            isToAssetSelectorEnabled = toAssets.isNotEmpty(), // اگر مقصدی وجود داشت، فعال کن
            isAmountInputEnabled = false,
            isButtonEnabled = false
        )
    }

    /**
     * مرحله ۴: کاربر ارز مقصد را انتخاب می‌کند.
     */
    fun onToAssetSelected(selectedAssetId: String) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        _uiState.value = currentState.copy(
            selectedToAsset = assetRegistry.getAssetById(selectedAssetId)!!
                .toSwapAsset(userBalances[selectedAssetId]!!),
            isAmountInputEnabled = true, // حالا کاربر می‌تواند مقدار را وارد کند
            isBottomSheetVisible = false
        )
    }


    /**
     * مرحله ۵ و ۶ و ۷: کاربر مقدار را وارد می‌کند و قیمت گرفته می‌شود.
     */
    fun onAmountInChanged(amount: String) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        val amountDecimal = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val hasEnoughBalance =
            amountDecimal <= (currentState.selectedFromAsset?.balance ?: BigDecimal.ZERO)

        _uiState.value = currentState.copy(
            amountIn = amount,
            quote = null,
            amountOut = "",
            isButtonEnabled = hasEnoughBalance && amountDecimal > BigDecimal.ZERO,
            error = if (!hasEnoughBalance) "موجودی کافی نیست" else null
        )

        quoteDebounceJob?.cancel()
        if (hasEnoughBalance && amountDecimal > BigDecimal.ZERO) {
            quoteDebounceJob = launchSafe {
                delay(200L)
                fetchQuote()
            }
        }
    }

    private fun fetchQuote() {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        val fromAsset = currentState.selectedFromAsset ?: return
        val toAsset = currentState.selectedToAsset ?: return
        val fromNetwork = blockchainRegistry.getNetworkById(fromAsset.networkId)!!
        if (currentState.amountIn.isBlank()) return

        _uiState.value = currentState.copy(isQuoteLoading = true, error = null)



        launchSafe {
            val currentAmountIn = (_uiState.value as? SwapUiState.Ready)?.amountIn!!
            val fromAssetConfig = assetRegistry.getAssetById(fromAsset.assetId)!!
            if (fromNetwork.networkType == NetworkType.EVM) {
                if (fromAssetConfig.contractAddress == null) {
                    fetchNativeQuote(fromAsset, toAsset, currentAmountIn)
                } else {
                    fetchErc20Quote(fromAsset, toAsset, currentAmountIn)
                }
            } else {
                fetchErc20Quote(fromAsset, toAsset, currentAmountIn)
            }


        }
    }

    /**
     * پیش‌فاکتور (Quote) را برای یک سواپ ERC20 از بک‌اند دریافت می‌کند.
     * این تابع به صورت داخلی در fetchQuote فراخوانی می‌شود.
     *
     * @param fromAsset دارایی مبدا انتخاب شده توسط کاربر.
     * @param toAsset دارایی مقصد انتخاب شده توسط کاربر.
     * @param amount مقدار ورودی به صورت Double.
     */
    private suspend fun fetchErc20Quote(fromAsset: SwapAsset, toAsset: SwapAsset, amount: String) {
        // ۱. گرفتن اطلاعات کامل AssetConfig برای هر دو دارایی
        val fromAssetConfig = assetRegistry.getAssetById(fromAsset.assetId)
            ?: run {
                _uiState.value = SwapUiState.Failure("اطلاعات دارایی مبدا یافت نشد.")
                return
            }
        val toAssetConfig = assetRegistry.getAssetById(toAsset.assetId)
            ?: run {
                _uiState.value = SwapUiState.Failure("اطلاعات دارایی مقصد یافت نشد.")
                return
            }

        // ۲. فراخوانی ریپازیتوری با پارامترهای صحیح
        when (val quoteResult = swapRepository.getErc20Quote(
            fromAssetSymbol = fromAsset.symbol,
            fromNetworkId = fromAssetConfig.networkId,
            toAssetSymbol = toAsset.symbol,
            toNetworkId = toAssetConfig.networkId, // برای سواپ‌های EVM -> BTC
            amount = amount.toDouble(),
            recipientAddress = blockchainRegistry.getNetworkById(toAssetConfig.networkId)?.chainId?.let {
                activeWalletManager.getAddressForNetwork(it)
            } ?: ""
        )) {
            is ResultResponse.Success -> {
                // ۳. پردازش پاسخ موفقیت‌آمیز
                val quoteResponse = quoteResult.data
                val options = quoteResponse.receivingOptions ?: emptyList()

                // ساخت لیست UI با انتخاب اولین گزینه به صورت پیش‌فرض
                val optionsForUI = options.mapIndexed { index, option ->
                    ReceivingOptionUI(option, isSelected = index == 0)
                }
                val selectedOption = options.firstOrNull()

                // فقط در صورتی UI را آپدیت کن که State هنوز Ready باشد
                val latestState = _uiState.value as? SwapUiState.Ready
                if (latestState != null) {
                    _uiState.value = (latestState.copy(
                        quote = quoteResponse,
                        amountOut = selectedOption?.finalAmount ?: quoteResponse.finalReceiveAmount
                        ?: "",
                        isQuoteLoading = false,
                        selectedOption = selectedOption,
                        receivingOptionsForUI = optionsForUI,
                        isButtonEnabled = true
                    ))
                }
            }

            is ResultResponse.Error -> {
                // ۴. پردازش پاسخ خطا
                val latestState = _uiState.value as? SwapUiState.Ready
                if (latestState != null) {
                    _uiState.value = (latestState.copy(
                        error = quoteResult.exception.message ?: "خطای ناشناخته در دریافت قیمت",
                        isQuoteLoading = false,
                        isButtonEnabled = false
                    ))
                }
            }
        }
    }

    /**
     * پیش‌فاکتور (Quote) را برای یک سواپ توکن اصلی (Native) از بک‌اند دریافت می‌کند
     * و ساختار امضای Meta-Transaction را نیز آماده می‌کند.
     *
     * @param fromAsset دارایی مبدا انتخاب شده توسط کاربر (e.g., ETH).
     * @param toAsset دارایی مقصد انتخاب شده توسط کاربر (e.g., USDT).
     * @param amount مقدار ورودی به صورت Double.
     */
    private suspend fun fetchNativeQuote(fromAsset: SwapAsset, toAsset: SwapAsset, amount: String) {
        // ۱. گرفتن اطلاعات کامل AssetConfig برای هر دو دارایی
        val fromAssetConfig = assetRegistry.getAssetById(fromAsset.assetId)
            ?: run {
                _uiState.value = (SwapUiState.Failure("اطلاعات دارایی مبدا یافت نشد."))
                return
            }
        val toAssetConfig = assetRegistry.getAssetById(toAsset.assetId)
            ?: run {
                _uiState.value = (SwapUiState.Failure("اطلاعات دارایی مقصد یافت نشد."))
                return
            }

        // ما به آدرس فعلی کاربر برای دریافت nonce نیاز داریم
        val userAddress = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)?.chainId?.let {
            activeWalletManager.getAddressForNetwork(it)
        } ?: throw IllegalStateException("Address not found for network")
            ?: run {
                _uiState.value = (SwapUiState.Failure("آدرس کیف پول فعال یافت نشد."))
                return
            }

        // ۲. فراخوانی ریپازیتوری با پارامترهای صحیح
        when (val result = swapRepository.getNativeQuote(
            fromAssetSymbol = fromAsset.symbol,
            fromNetworkId = fromAssetConfig.networkId,
            toAssetSymbol = toAsset.symbol,
            toNetworkId = toAssetConfig.networkId,
            amount = amount.toDouble(),
            userAddress = userAddress,
            recipientAddress = blockchainRegistry.getNetworkById(toAssetConfig.networkId)?.chainId?.let {
                activeWalletManager.getAddressForNetwork(it)
            } ?: ""
        )) {
            is ResultResponse.Success -> {
                // ۳. پردازش پاسخ موفقیت‌آمیز
                val nativeQuoteResponse = result.data
                val quoteResponse = nativeQuoteResponse.quote // بخش quote عادی

                val options = quoteResponse.receivingOptions ?: emptyList()
                val selectedOption = options.firstOrNull()
                // ساخت لیست UI با انتخاب اولین گزینه به صورت پیش‌فرض
                val optionsForUI = options.mapIndexed { index, option ->
                    ReceivingOptionUI(option, isSelected = index == 0)
                }
                val latestState = _uiState.value as? SwapUiState.Ready
                if (latestState != null) {
                    _uiState.value = latestState.copy(
                        quote = quoteResponse,
                        amountOut = selectedOption?.finalAmount ?: quoteResponse.finalReceiveAmount
                        ?: "",
                        isQuoteLoading = false,
                        selectedOption = selectedOption, // در این حالت معمولاً null است
                        receivingOptionsForUI = optionsForUI, // برای Native سواپ گزینه‌ای نداریم
                        isButtonEnabled = true
                    )
                }
            }

            is ResultResponse.Error -> {
                // ۴. پردازش پاسخ خطا
                val latestState = _uiState.value as? SwapUiState.Ready
                if (latestState != null) {
                    _uiState.value = (latestState.copy(
                        error = result.exception.message ?: "خطای ناشناخته در دریافت قیمت",
                        isQuoteLoading = false,
                        isButtonEnabled = false
                    ))
                }
            }
        }
    }


    fun onProceedToConfirmation() {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        val fromAsset = currentState.selectedFromAsset ?: return
        // toAsset دیگر برای این منطق لازم نیست، چون تمام اطلاعات در quote هست
        val quote = currentState.quote ?: return // quote حالا از نوع QuoteResponse است
        val fromNetworkId = currentState.fromNetworkId ?: return


        launchSafe {
            val fromAssetConfig = assetRegistry.getAssetById(fromAsset.assetId)!!
            val fromNetwork = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)!!

            // ۲. بر اساس نوع شبکه مبدا، به صفحه مناسب می‌رویم
            if (fromNetwork.networkType == NetworkType.BITCOIN) {
                // --- مسیر ۱: جریان کاری بیت‌کوین (BTC -> ???) ---
                if (quote.depositAddress == null) {
                    _uiState.value = SwapUiState.Failure("آدرس واریز از سرور دریافت نشد.")
                    return@launchSafe
                }
                val assetToDeposit = AssetItem(
                    id = fromAsset.assetId,
                    name = fromAssetConfig.name,
                    symbol = fromAsset.symbol,
                    networkName = fromAsset.networkName,
                    networkId = fromAssetConfig.networkId,
                    iconUrl = fromAsset.iconUrl,
                    balance = fromAsset.balance.toPlainString(), // از SwapAsset می‌خوانیم
                    balanceUsdt = "", // در این صفحه نیازی به نمایش ارزش دلاری نداریم
                    balanceRaw = fromAsset.balance,
                    priceUsdRaw = BigDecimal.ZERO, // نیازی نیست
                    decimals = fromAssetConfig.decimals,
                    contractAddress = fromAssetConfig.contractAddress,
                    isNativeToken = fromAssetConfig.contractAddress == null
                )

                // تمام اطلاعات لازم در quote وجود دارد
                _uiState.value = SwapUiState.WaitingForDeposit(
                    quote = quote,
                    amountToDeposit = "${quote.fromAmount} ${quote.fromAssetSymbol}",
                    assetToDeposit = assetToDeposit,
                    depositAddress = quote.depositAddress?:""
                )

            } else if (fromNetwork.networkType == NetworkType.EVM) {
                // --- مسیر ۲: جریان کاری EVM (EVM -> ???) ---

                // ما باید اولین (یا انتخاب شده) گزینه دریافت را پیدا کنیم

                if (!quote.receivingOptions.isNullOrEmpty()) {
                    val selectedOption =
                        quote.receivingOptions?.first() // یا گزینه‌ای که کاربر انتخاب کرده
                    val toAssetConfig = assetRegistry.getAssetBySymbol(
                        currentState.selectedToAsset!!.symbol,
                        selectedOption?.networkId ?: ""
                    )!!

                    _uiState.value = SwapUiState.Confirmation(
                        fromDisplay = "${quote.fromAmount} ${quote.fromAssetSymbol}",
                        toDisplay = "${selectedOption?.finalAmount} ${toAssetConfig.symbol}",
                        feeDisplay = "کارمزد کل: ${selectedOption?.fees?.totalFeeInUsd ?: "N/A"} دلار",
                        fromNetworkIcon = fromAsset.iconUrl,
                        toNetworkIcon = currentState.selectedToAsset.iconUrl,
                        fromNetworkId = fromNetworkId,
                        quote = quote,
                        selectedOption = selectedOption!!,
                        selectedFromAsset = fromAsset
                    )

                    // اگر receivingOptions وجود نداشت (EVM -> BTC)

                } else if (quote.finalReceiveAmount != null) {
                    val toAssetConfig = assetRegistry.getAssetBySymbol(
                        currentState.selectedToAsset!!.symbol,
                        currentState.selectedToAsset.networkId
                    )!!

                    _uiState.value = SwapUiState.Confirmation(
                        fromDisplay = "${quote.fromAmount} ${quote.fromAssetSymbol}",
                        toDisplay = "${quote.finalReceiveAmount} ${toAssetConfig.symbol}",
                        feeDisplay = "کارمزد کل: ${quote.fees?.totalFeeInUsd ?: "N/A"} دلار",
                        fromNetworkIcon = fromAsset.iconUrl,
                        toNetworkIcon = currentState.selectedToAsset.iconUrl,
                        fromNetworkId = fromNetworkId,
                        quote = quote,
                        // در این حالت selectedOption نداریم، باید مدل Confirmation را اصلاح کنیم تا nullable باشد
                        // یا یک DTO فیک بسازیم
                        selectedOption = ReceivingOptionDto(
                            toAssetConfig.networkId,
                            toAssetConfig.name,
                            quote.fees!!,
                            quote.finalReceiveAmount ?: "",
                            ""
                        ),
                        selectedFromAsset = fromAsset
                    )
                } else {
                    _uiState.value = SwapUiState.Failure("پاسخ سرور برای تایید نامعتبر است.")
                }

                /*val selectedOption = quote.receivingOptions?.firstOrNull()
                if (selectedOption == null) {
                    _uiState.value = SwapUiState.Failure("گزینه‌ای برای دریافت یافت نشد.")
                    return@launch
                }

                // پیدا کردن نماد ارز مقصد از روی toAssetId
                // (این بخش نیاز به بهبود دارد اگر toAssetId کامل را نداریم)
                val toAssetConfig = assetRegistry.getAssetsForNetwork(selectedOption.networkId)
                    .find { it.symbol == currentState.selectedToAsset?.symbol }
                val toAssetSymbol = toAssetConfig?.symbol ?: ""

                _uiState.value = SwapUiState.Confirmation(
                    fromDisplay = "${quote.fromAmount} ${quote.fromAssetSymbol}",
                    toDisplay = "${selectedOption.finalAmount} $toAssetSymbol",
                    feeDisplay = "کارمزد کل: ${selectedOption.fees.totalFeeInUsd ?: "N/A"} دلار",
                    fromNetworkIcon = fromAsset.iconUrl,
                    toNetworkIcon = currentState.selectedToAsset?.iconUrl,
                    quote = quote,
                    selectedOption = selectedOption,
                    fromNetworkId = fromNetworkId,
                )*/
            }
        }
    }


    /*    */
    /**
     * درخواست نهایی را به همراه امضای Permit به بک‌اند ارسال می‌کند.
     * این تابع با ساختار جدید QuoteResponse کاملاً سازگار است.
     *//*
    fun executeSwap() {
        // ۱. گرفتن وضعیت فعلی و اطمینان از معتبر بودن آن
        val currentState = _uiState.value as? SwapUiState.Confirmation ?: return
        val quote = currentState.quote
        val selectedOption = currentState.selectedOption!!
        val fromNetworkId = currentState.fromNetworkId
        launchSafe (Dispatchers.IO) {
            try {
                _uiState.value = SwapUiState.InProgress("در حال آماده‌سازی امضا...")

                // ۲. پیدا کردن اطلاعات کامل دارایی و شبکه مبدا از روی quote

                val fromAssetConfig =
                    assetRegistry.getAssetBySymbol(quote.fromAssetSymbol, fromNetworkId)
                        ?: throw IllegalStateException("Asset config for ${quote.fromAssetSymbol} on network ${fromNetworkId} not found.")

                val fromNetwork = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)!!
                val fromChainId = fromNetwork.chainId!!

                if (fromAssetConfig.contractAddress == null) {
                    throw NotImplementedError("Native asset swaps are not implemented via Permit.")
                }

                // ۳. آماده‌سازی برای امضا
                val phoenixContractAddress = fromNetwork.phoenixContractAddress
                    ?: throw IllegalStateException("Phoenix contract address not configured for ${fromNetwork.name}")

                val dataSource = dataSourceFactory.create(fromChainId)
                val web3j = dataSource.getWeb3jInstance()

                val credentials = keyManager.getCredentialsForChain(fromChainId)
                    ?: throw IllegalStateException("Wallet is locked or key not found for chain $fromChainId.")

                val nonce = TokenInfoHelper.getNonce(
                    web3j,
                    fromAssetConfig.contractAddress ?: "",
                    credentials.address
                )
                val tokenName =
                    TokenInfoHelper.getName(web3j, fromAssetConfig.contractAddress ?: "")
                val tokenVersion =
                    TokenInfoHelper.getVersion(web3j, fromAssetConfig.contractAddress ?: "")
                val deadline = (System.currentTimeMillis() / 1000L) + 1800 // 30 دقیقه
                val amountInWei =
                    BigDecimal(quote.fromAmount).multiply(BigDecimal.TEN.pow(fromAssetConfig.decimals))
                        .toBigInteger()

                // ۴. ساخت امضای Permit
                val signatureResult = Eip712Helper.signPermit(
                    credentials, tokenName, tokenVersion, fromChainId,
                    fromAssetConfig.contractAddress ?: "", phoenixContractAddress,
                    amountInWei, nonce, deadline
                )

                _uiState.value = SwapUiState.InProgress("در حال ارسال به سرور...")

                // ۵. ساخت آبجکت درخواست برای بک‌اند
                val request = ExecuteRequest(
                    quoteId = quote.quoteId,
                    selectedNetworkId = selectedOption.networkId,
                    recipientAddress = blockchainRegistry.getNetworkById(selectedOption.networkId)?.chainId?.let {
                        activeWalletManager.getAddressForNetwork(it)
                    } ?: throw IllegalStateException("Address not found"),
                    permitParameters = PermitParametersDto(
                        tokenAddress = fromAssetConfig.contractAddress ?: "",
                        userAddress = credentials.address,
                        amount = amountInWei.toString(),
                        deadline = deadline,
                        v = signatureResult.signature.v,
                        r = signatureResult.signature.r,
                        s = signatureResult.signature.s
                    )
                )

                // ۶. فراخوانی Repository
                when (val result = swapRepository.executeErc20Swap(request)) {
                    is ResultResponse.Success -> {
                        globalEventBus.postEvent(GlobalEvent.WalletNeedsRefresh)
                        _uiState.value =
                            SwapUiState.Success(result.data, "معامله شما با موفقیت ارسال شد.")
                    }

                    is ResultResponse.Error -> throw result.exception
                }

            } catch (e: Exception) {
                _uiState.value = SwapUiState.Failure(e.message ?: "خطای ناشناخته در اجرای معامله")
            }
        }
    }*/

    /**
     * اجرای معامله را پس از تایید کاربر آغاز می‌کند.
     * این تابع تشخیص می‌دهد که آیا باید یک سواپ ERC20 (با Permit)
     * یا یک سواپ Native (با Meta-Transaction) را اجرا کند.
     */
    fun executeSwap() {
        val currentState = _uiState.value as? SwapUiState.Confirmation ?: return

        launchSafe {

            val fromAssetConfig = assetRegistry.getAssetBySymbol(
                currentState.quote.fromAssetSymbol,
                currentState.fromNetworkId
            )!!
            // --- بخش کلیدی: تشخیص نوع اجرا ---
            if (fromAssetConfig.contractAddress == null) {
                // اگر اطلاعات Meta-Transaction وجود داشت، این یک سواپ Native است.
                executeNativeSwap(currentState.quote)
            } else if (currentState.selectedOption != null) {
                // اگر گزینه دریافت انتخاب شده بود، این یک سواپ ERC20 است.
                executeErc20Swap(currentState.quote, currentState.selectedOption)
            } else {
                throw IllegalStateException("Invalid state for execution.")
            }

        }
    }

    private fun executeErc20Swap(quote: QuoteResponse, selectedOption: ReceivingOptionDto) {

        // ۱. گرفتن وضعیت فعلی و اطمینان از معتبر بودن آن
        val currentState = _uiState.value as? SwapUiState.Confirmation ?: return
        val fromNetworkId = currentState.fromNetworkId
        _uiState.value = (SwapUiState.InProgress("در حال ارسال درخواست شما..."))
        launchSafe {

            _uiState.value = SwapUiState.InProgress("در حال آماده‌سازی امضا...")

            // ۲. پیدا کردن اطلاعات کامل دارایی و شبکه مبدا از روی quote

            val fromAssetConfig =
                assetRegistry.getAssetBySymbol(quote.fromAssetSymbol, fromNetworkId)
                    ?: throw IllegalStateException("Asset config for ${quote.fromAssetSymbol} on network ${fromNetworkId} not found.")

            val fromNetwork = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)!!
            val fromChainId = fromNetwork.chainId!!

            if (fromAssetConfig.contractAddress == null) {
                throw NotImplementedError("Native asset swaps are not implemented via Permit.")
            }

            // ۳. آماده‌سازی برای امضا
            val phoenixContractAddress = fromNetwork.phoenixContractAddress
                ?: throw IllegalStateException("Phoenix contract address not configured for ${fromNetwork.name}")

            val dataSource = dataSourceFactory.create(fromChainId)
            val web3j = dataSource.getWeb3jInstance()

            val credentials = keyManager.getCredentialsForChain(fromChainId)
                ?: throw IllegalStateException("Wallet is locked or key not found for chain $fromChainId.")

            val nonce = TokenInfoHelper.getNonce(
                web3j,
                fromAssetConfig.contractAddress ?: "",
                credentials.address
            )
            val tokenName =
                TokenInfoHelper.getName(web3j, fromAssetConfig.contractAddress ?: "")
            val tokenVersion =
                TokenInfoHelper.getVersion(web3j, fromAssetConfig.contractAddress ?: "")
            val deadline = (System.currentTimeMillis() / 1000L) + 1800 // 30 دقیقه
            val amountInWei =
                BigDecimal(quote.fromAmount).multiply(BigDecimal.TEN.pow(fromAssetConfig.decimals))
                    .toBigInteger()

            // ۴. ساخت امضای Permit
            val signatureResult = Eip712Helper.signPermit(
                credentials, tokenName, tokenVersion, fromChainId,
                fromAssetConfig.contractAddress ?: "", phoenixContractAddress,
                amountInWei, nonce, deadline
            )

            _uiState.value = SwapUiState.InProgress("در حال ارسال به سرور...")

            logPermitParametersForRemix(
                fromAssetConfig,
                credentials.address,
                amountInWei,
                deadline,
                quote.quoteId,
                signatureResult.signature
            )


            // ۵. ساخت آبجکت درخواست برای بک‌اند
            val network = blockchainRegistry.getNetworkById(selectedOption.networkId)
            val recipientAddress = network?.chainId?.let { 
                activeWalletManager.getAddressForNetwork(it) 
            } ?: throw IllegalStateException("Recipient address not found for network ${selectedOption.networkId}")
            
            val request = ExecuteRequest(
                    quoteId = quote.quoteId,
                selectedNetworkId = selectedOption.networkId,
                recipientAddress = recipientAddress,
                permitParameters = PermitParametersDto(
                    tokenAddress = fromAssetConfig.contractAddress ?: "",
                    userAddress = credentials.address,
                    amount = amountInWei.toString(),
                    deadline = deadline,
                    v = signatureResult.signature.v,
                    r = signatureResult.signature.r,
                    s = signatureResult.signature.s
                )
                )

            // ۶. فراخوانی Repository
            when (val result = swapRepository.executeErc20Swap(request)) {
                    is ResultResponse.Success -> {
                        globalEventBus.postEvent(GlobalEvent.WalletNeedsRefresh)
                        _uiState.value =
                            SwapUiState.Success(result.data, "معامله شما با موفقیت ارسال شد.")
                    }

                    is ResultResponse.Error -> throw result.exception
                }


        }
    }

    /**
     * سواپ یک توکن اصلی (Native) را با استفاده از Meta-Transaction اجرا می‌کند.
     */
    private suspend fun executeNativeSwap(quote: QuoteResponse) {

        val currentState = (_uiState.value as? SwapUiState.Confirmation)
        // ۱. پیدا کردن اطلاعات کامل شبکه و دارایی مبدا
        // ما به fromNetworkId از State نیاز داریم که در مراحل قبلی ذخیره شده.
        val fromNetworkId = currentState?.fromNetworkId
            ?: throw IllegalStateException("Source network ID not found in current state.")


        val fromAssetConfig = assetRegistry.getAssetBySymbol(quote.fromAssetSymbol, fromNetworkId)
            ?: throw IllegalStateException("Source asset config not found.")

        val fromNetwork = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)
            ?: throw IllegalStateException("Source network config not found.")

        // ۲. تخمین هزینه Gas به صورت داینامیک
        // ما از DataSource برای دریافت قیمت لحظه‌ای Gas استفاده می‌کنیم.
        val dataSource = dataSourceFactory.create(fromNetwork.chainId!!)

        // (این بخش نیاز به اضافه کردن getFeeOptions به IChainDataSource دارد اگر از قبل نباشد)
        // ما اولین گزینه کارمزد (معمولی) را به عنوان پیش‌فرض انتخاب می‌کنیم.
        val feeOptionsResult = dataSource.getFeeOptions()
        val feeData =
            (feeOptionsResult as? ResultResponse.Success)?.data?.getOrNull(1) // گزینه "سریع"
                ?: throw IllegalStateException("Could not fetch gas fee options.")

        val gasPrice = feeData.gasPrice!!
        val gasLimit = /*feeData.gasLimit?.multiply(BigInteger("2")) ?:*/ BigInteger("210000")

        // ۲. محاسبه مقدار ارسالی (Amount)
        val amountInBigDecimal = BigDecimal(quote.fromAmount)
        val fromAsset = currentState.selectedFromAsset!!

        // --- بخش بازنویسی شده، تمیز و صحیح ---

        val amountToSendInWei: BigInteger

        // یک تلورانس کوچک برای مقایسه اعداد اعشاری در نظر می‌گیریم
        val tolerance = BigDecimal("0.000001")
        val difference = (fromAsset.balance - amountInBigDecimal).abs()

        // آیا کاربر قصد ارسال "کل موجودی" را دارد؟
        if (difference < tolerance) {
            Timber.d("Max send detected for native token. Adjusting amount for gas fee.")

            // هزینه کل Gas را در کوچکترین واحد (Wei) محاسبه می‌کنیم
            val gasCostInWei = gasPrice * gasLimit

            // کل موجودی را به کوچکترین واحد (Wei) تبدیل می‌کنیم
            val totalBalanceInWei = fromAsset.balance
                .multiply(BigDecimal.TEN.pow(fromAssetConfig.decimals))
                .toBigInteger()

            // مقدار قابل ارسال، کل موجودی منهای هزینه Gas است
            amountToSendInWei = totalBalanceInWei - gasCostInWei

            // چک می‌کنیم که آیا اصلاً پولی برای ارسال باقی می‌ماند
            if (amountToSendInWei <= BigInteger.ZERO) {
                throw IllegalStateException("Insufficient balance to cover the gas fee.")
            }
        } else {
            // اگر "ارسال حداکثر" نیست، از همان مقداری که کاربر وارد کرده استفاده می‌کنیم
            amountToSendInWei = amountInBigDecimal
                .multiply(BigDecimal.TEN.pow(fromAssetConfig.decimals))
                .toBigInteger()
        }
        // ۳. ساخت پارامترهای تراکنش با داده‌های واقعی
        // هش تابع "executeNativeTrade(bytes32)" برابر است با "0x52f02a6a"
        val functionSignature = "0x52f02a6a"
        val quoteIdBytes32 = (quote.quoteId).removePrefix("0x")

        val params = TransactionParams.Evm(
            networkName = fromNetwork.name,
            to = fromNetwork.phoenixContractAddress!!,
            amount = amountToSendInWei,
            data = functionSignature + quoteIdBytes32,
            gasPrice = gasPrice,
            gasLimit = gasLimit // gasLimit را برای اطمینان کمی افزایش می‌دهیم
        )

        _uiState.value = (SwapUiState.InProgress("در حال اماده سازی تراکنش..."))

        // ۴. ارسال تراکنش از طریق ریپازیتوری
        when (val result = walletRepository.sendTransaction(params)) {
            is ResultResponse.Success -> {
                val txHash = result.data
                Timber.i("Native deposit transaction sent successfully: $txHash")
                _uiState.value =
                    SwapUiState.Success(result.data, "معامله شما با موفقیت ارسال شد.")
                // از اینجا به بعد، منتظر پیام WebSocket از بک‌اند برای اطلاع از تکمیل نهایی سواپ می‌مانیم.
            }

            is ResultResponse.Error -> {
                Timber.e(result.exception, "Failed to send native deposit transaction.")
                throw result.exception
            }
        }


    }


    /**
     * فراخوانی می‌شود وقتی کاربر روی دکمه انتخاب ارز (مبدا یا مقصد) کلیک می‌کند.
     */
    fun onAssetSelectionOpened(isSelectingFrom: Boolean) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return

        val title = if (isSelectingFrom) "انتخاب ارز مبدا" else "انتخاب ارز مقصد"
        val assets = if (isSelectingFrom) currentState.fromAssets else currentState.toAssets

        val groupedAssets = assets
            .groupBy { it.networkName } // بر اساس نام شبکه گروه‌بندی کن
            .toSortedMap() // مرتب‌سازی بر اساس نام شبکه
        // تبدیل SwapAsset به AssetSelectItem برای نمایش در UI
        val selectionList = mutableListOf<AssetSelectionListItem>()
        groupedAssets.forEach { (networkName, assetsInNetwork) ->
            selectionList.add(AssetSelectionListItem.Header(networkName)) // اضافه کردن هدر
            assetsInNetwork.forEach { swapAsset ->
                selectionList.add(AssetSelectionListItem.Asset(swapAsset.toAssetSelectItem())) // اضافه کردن آیتم‌های ارز
            }
        }

        _uiState.value = currentState.copy(
            isBottomSheetVisible = true,
            bottomSheetTitle = title,
            assetsForSelection = selectionList,
            searchQuery = "" // ریست کردن جستجو
        )
    }

    /**
     * فراخوانی می‌شود وقتی کاربر BottomSheet را می‌بندد.
     */
    fun onAssetSelectionDismissed() {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        _uiState.value = currentState.copy(isBottomSheetVisible = false)
    }

    /**
     * فراخوانی می‌شود وقتی متن در فیلد جستجوی BottomSheet تغییر می‌کند.
     */
    fun onSearchQueryChanged(query: String) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return

        // ۱. لیست اصلی و فیلتر نشده دارایی‌ها را از state اولیه می‌گیریم
        // اینها از نوع SwapAsset هستند، نه AssetSelectionListItem
        val originalAssetList = if (currentState.bottomSheetTitle.contains("مبدا")) {
            currentState.fromAssets
        } else {
            currentState.toAssets
        }

        // ۲. فیلتر کردن لیست اصلی بر اساس query
        val filteredAssetList = if (query.isBlank()) {
            originalAssetList // اگر جستجو خالی است، کل لیست را برگردان
        } else {
            originalAssetList.filter { swapAsset ->
                // ما به اطلاعات کامل AssetConfig برای جستجو در نام نیاز داریم
                val config = assetRegistry.getAssetById(swapAsset.assetId)
                config != null && (
                        config.name.contains(query, ignoreCase = true) ||
                                config.symbol.contains(query, ignoreCase = true)
                        )
            }
        }

        // ۳. --- بخش جدید: گروه‌بندی مجدد نتیجه فیلتر شده ---
        val groupedAssets = filteredAssetList
            .groupBy { it.networkName }
            .toSortedMap()

        val newSelectionList = mutableListOf<AssetSelectionListItem>()
        groupedAssets.forEach { (networkName, assetsInNetwork) ->
            newSelectionList.add(AssetSelectionListItem.Header(networkName))
            assetsInNetwork.forEach { swapAsset ->
                newSelectionList.add(AssetSelectionListItem.Asset(swapAsset.toAssetSelectItem()))
            }
        }
        // --- پایان بخش جدید ---

        // ۴. به‌روزرسانی UiState با لیست جدید و گروه‌بندی شده
        _uiState.value = currentState.copy(
            searchQuery = query,
            assetsForSelection = newSelectionList
        )
    }

    fun onReceivingOptionSelected(option: ReceivingOptionDto) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        val newList = currentState.receivingOptionsForUI.map {
            it.copy(isSelected = it.option.networkId == option.networkId)
        }
        _uiState.value = currentState.copy(
            selectedOption = option, // <<-- یک فیلد جدید در UiState.Ready
            amountOut = option.finalAmount,
            receivingOptionsForUI = newList
        )
    }

    // یک تابع کمکی جدید برای تبدیل
    private fun SwapAsset.toAssetSelectItem(): AssetSelectItem {
        val config = assetRegistry.getAssetById(this.assetId)!!
        return AssetSelectItem(
            assetId = this.assetId,
            name = config.name,
            symbol = this.symbol,
            iconUrl = this.iconUrl,
            networkName = this.networkName,
            balance = this.balance.toPlainString()
        )
    }
    // --- توابع کمکی ---

/*    private suspend fun getAssetBalance(userAddress: String, assetConfig: AssetConfig): BigDecimal {
        return try {
            val network = blockchainRegistry.getNetworkById(assetConfig.networkId)!!
            val dataSource = dataSourceFactory.create(network.chainId!!)

            val rawBalance: BigInteger = if (assetConfig.contractAddress == null) {
                (dataSource.getBalance(userAddress) as? ResultResponse.Success)?.data
                    ?: BigInteger.ZERO
            } else {
                (dataSource as? EvmDataSource)?.getBalanceEVM(
                    assetConfig.contractAddress,
                    userAddress
                ) ?: BigInteger.ZERO
            }

            BigDecimal(rawBalance).divide(BigDecimal.TEN.pow(assetConfig.decimals))
        } catch (e: Exception) {
            Log.e("SwapViewModel", "Failed to get balance for ${assetConfig.symbol}", e)
            BigDecimal.ZERO
        }
    }*/

    @OptIn(ExperimentalStdlibApi::class)
    private fun logPermitParametersForRemix(
        config: AssetConfig,
        user: String,
        amount: BigInteger,
        deadline: Long,
        quote: String,
        sig: Eip712Helper.Eip712Signature
    ) {
        val quoteIdBytes32 = Hash.sha3String(quote)
        //"0x" + quote.toByteArray(Charsets.UTF_8).toHexString().padEnd(64, '0')
        Timber.d("--- Remix Parameters ---")
        Timber.d("tokenAddress (address): ${config.contractAddress}")
        Timber.d("userAddress (address): $user")
        Timber.d("amount (uint256): $amount")
        Timber.d("deadline (uint256): $deadline")
        Timber.d("quoteId (bytes32): \"$quoteIdBytes32\"")
        Timber.d("v (uint8): ${sig.v}")
        Timber.d("r (bytes32): ${sig.r}")
        Timber.d("s (bytes32): ${sig.s}")
    }


    private fun AssetConfig.toSwapAsset(balance: BigDecimal): SwapAsset {
        return SwapAsset(
            assetId = this.id,
            symbol = this.symbol,
            iconUrl = this.iconUrl,
            networkId = this.networkId,
            networkName = blockchainRegistry.getNetworkById(this.networkId)!!.name.name,
            balance = balance
        )
    }


}