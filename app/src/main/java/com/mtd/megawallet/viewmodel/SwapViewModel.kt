package com.mtd.megawallet.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.core.assets.AssetConfig
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.model.Eip712Signature
import com.mtd.core.model.ExecuteSwapRequest
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.Eip712Helper
import com.mtd.core.utils.GlobalEvent
import com.mtd.core.utils.GlobalEventBus
import com.mtd.core.utils.TokenInfoHelper
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.Quote
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapPair
import com.mtd.domain.repository.ISwapRepository
import com.mtd.megawallet.event.SwapAsset
import com.mtd.megawallet.event.SwapNavigationEvent
import com.mtd.megawallet.event.SwapUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val globalEventBus: GlobalEventBus
) : ViewModel() {


    private val _uiState = MutableStateFlow<SwapUiState>(SwapUiState.InitialLoading)
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<SwapNavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private var quoteDebounceJob: Job? = null
    private var availablePairs: List<SwapPair> = emptyList()
    private var userBalances = mutableMapOf<String, BigDecimal>() // کش موجودی‌ها

    init {
        loadInitialData()
    }


    /*private fun loadInitialData() {
        _uiState.value = SwapUiState.InitialLoading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ۱. گرفتن تمام جفت‌ارزهای ممکن
                val pairsResult = swapRepository.getAvailablePairs()
                if (pairsResult !is ResultResponse.Success || pairsResult.data.isEmpty()) {
                    throw IllegalStateException("جفت ارزی یافت نشد.")
                }
                availablePairs = pairsResult.data

                // ۲. گرفتن موجودی تمام دارایی‌های درگیر در این جفت‌ارزها
                val allAssetIds =
                    availablePairs.flatMap { listOf(it.fromAssetId, it.toAssetId) }.distinct()
                allAssetIds.forEach { assetId ->
                    val assetConfig = assetRegistry.getAssetById(assetId)!!
                    val userAddress =
                        walletRepository.getActiveAddressForNetwork(assetConfig.networkId)!!
                    userBalances[assetId] = getAssetBalance(userAddress, assetConfig)
                }

                // ۳. ساخت لیست دارایی‌های مبدا
                val fromAssets = availablePairs.map { it.fromAssetId }.distinct().map { assetId ->
                    assetRegistry.getAssetById(assetId)!!.toSwapAsset(userBalances[assetId]!!)
                }

                _uiState.value = SwapUiState.Ready(
                    fromAssets = fromAssets,
                    toAssets = emptyList(), // لیست مقصد در ابتدا خالی است
                    selectedFromAsset = null,
                    selectedToAsset = null
                )

            } catch (e: Exception) {
                _uiState.value = SwapUiState.Failure(e.message ?: "خطا در بارگذاری")
            }
        }
    }*/


    private fun loadInitialData() {
        _uiState.value = SwapUiState.InitialLoading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pairsResult = swapRepository.getAvailablePairs()
                if (pairsResult !is ResultResponse.Success || pairsResult.data.isEmpty()) {
                    throw IllegalStateException("جفت ارزی یافت نشد.")
                }
                availablePairs = pairsResult.data

                val allAssetConfigs = availablePairs
                    .flatMap { listOf(it.fromAssetId, it.toAssetId) }
                    .distinct()
                    .map { assetRegistry.getAssetById(it)!! }

                val assetsByNetwork = allAssetConfigs.groupBy { it.networkId }

                // --- منطق جدید و هوشمندانه برای گرفتن و یکسان‌سازی موجودی‌ها ---
                val balancesDeferred = assetsByNetwork.map { (networkId, assetsInNetwork) ->
                    async {
                        val network = blockchainRegistry.getNetworkById(networkId)!!
                        val userAddress = walletRepository.getActiveAddressForNetwork(networkId)!!
                        val dataSource = dataSourceFactory.create(network.chainId!!)

                        // بر اساس نوع شبکه، تابع مربوطه را صدا می‌زنیم
                        if (network.networkType == com.mtd.core.model.NetworkType.EVM) {
                            val evmBalanceResult = dataSource.getBalanceEVM(userAddress)
                            if (evmBalanceResult is ResultResponse.Success) {
                                // خروجی List<Asset> را به Map تبدیل می‌کنیم
                                evmBalanceResult.data.map { asset ->
                                    val config =
                                        assetsInNetwork.find { it.symbol == asset.symbol }!!
                                    val balanceDecimal =
                                        BigDecimal(asset.balance).divide(BigDecimal.TEN.pow(config.decimals))
                                    userBalances[config.id] = balanceDecimal
                                }
                            }
                        } else { // برای Bitcoin و دیگر شبکه‌های UTXO
                            val utxoBalanceResult = dataSource.getBalance(userAddress)
                            if (utxoBalanceResult is ResultResponse.Success) {
                                val config =
                                    assetsInNetwork.first() // UTXOها فقط یک دارایی اصلی دارند
                                val balanceDecimal = BigDecimal(utxoBalanceResult.data).divide(
                                    BigDecimal.TEN.pow(config.decimals)
                                )
                                userBalances[config.id] = balanceDecimal
                            }
                        }
                    }
                }
                balancesDeferred.awaitAll() // منتظر می‌مانیم تا تمام موجودی‌ها گرفته شوند
                // --- پایان منطق جدید ---

                val fromAssets = availablePairs.map { it.fromAssetId }.distinct().map { assetId ->
                    assetRegistry.getAssetById(assetId)!!
                        .toSwapAsset(userBalances[assetId] ?: BigDecimal.ZERO)
                }

                _uiState.value = SwapUiState.Ready(
                    fromAssets = fromAssets,
                    toAssets = emptyList(),
                    selectedFromAsset = null, // در ابتدا هیچ‌چیز انتخاب نشده است
                    selectedToAsset = null
                )
            } catch (e: Exception) {
                _uiState.value = SwapUiState.Failure(e.message ?: "خطا در بارگذاری")
            }
        }
    }


    /**
     * مرحله ۲: کاربر ارز مبدأ را انتخاب می‌کند.
     */
    fun onFromAssetSelected(selectedAssetId: String) {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return

        // پیدا کردن دارایی‌های مقصد ممکن بر اساس انتخاب مبدأ
        val possibleToAssetIds = availablePairs
            .filter { it.fromAssetId == selectedAssetId }
            .map { it.toAssetId }
            .distinct()

        val toAssets = possibleToAssetIds.map { assetId ->
            assetRegistry.getAssetById(assetId)!!.toSwapAsset(userBalances[assetId]!!)
        }

        _uiState.value = currentState.copy(
            selectedFromAsset = assetRegistry.getAssetById(selectedAssetId)!!
                .toSwapAsset(userBalances[selectedAssetId]!!),
            toAssets = toAssets,
            selectedToAsset = null, // انتخاب مقصد ریست می‌شود
            isToAssetSelectorEnabled = true,
            isAmountInputEnabled = false,
            amountIn = "",
            amountOut = "",
            quote = null
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
            isAmountInputEnabled = true // حالا کاربر می‌تواند مقدار را وارد کند
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
            quoteDebounceJob = viewModelScope.launch {
                delay(700L)
                fetchQuote()
            }
        }
    }

    private fun fetchQuote() {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        val fromAsset = currentState.selectedFromAsset ?: return
        val toAsset = currentState.selectedToAsset ?: return
        if (currentState.amountIn.isBlank()) return

        _uiState.value = currentState.copy(isQuoteLoading = true, error = null)

        viewModelScope.launch {
            when (val quoteResult = swapRepository.getQuote(
                fromAssetId = fromAsset.assetId,
                toAsset.assetId,
                currentState.amountIn
            )) {
                is ResultResponse.Success -> {
                    val currentAmountIn = (_uiState.value as? SwapUiState.Ready)?.amountIn
                    // اطمینان از اینکه قیمت برای آخرین مقدار وارد شده است
                    if (currentAmountIn == quoteResult.data.fromAmount) {
                        _uiState.value = (_uiState.value as SwapUiState.Ready).copy(
                            quote = quoteResult.data,
                            amountOut = quoteResult.data.receiveAmount,
                            isQuoteLoading = false
                        )
                    }
                }

                is ResultResponse.Error -> {
                    _uiState.value = (_uiState.value as SwapUiState.Ready).copy(
                        error = quoteResult.exception.message,
                        isQuoteLoading = false
                    )
                }
            }
        }
    }

    fun onProceedToConfirmation() {
        val currentState = _uiState.value as? SwapUiState.Ready ?: return
        val fromAsset = currentState.selectedFromAsset ?: return
        val toAsset = currentState.selectedToAsset ?: return
        val quote = currentState.quote ?: return



        viewModelScope.launch {
            val fromAssetConfig = assetRegistry.getAssetById(fromAsset.assetId)!!
            val fromNetwork = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)!!

            if (fromNetwork.networkType == NetworkType.BITCOIN) {
                // --- مسیر ۱: جریان کاری بیت‌کوین ---
                if (quote.depositAddress == null) {
                    _uiState.value = SwapUiState.Failure("آدرس واریز از سرور دریافت نشد.")
                    return@launch
                }
                _uiState.value = SwapUiState.WaitingForDeposit(
                    quote = quote,
                    amountToDeposit = "${quote.fromAmount} ${quote.fromAssetSymbol}",
                    depositAddress = quote.depositAddress?:""
                )
            } else {
                // --- مسیر ۲: جریان کاری EVM (بدون تغییر) ---
                _uiState.value = SwapUiState.Confirmation(
                    fromDisplay = "${quote.fromAmount} ${quote.fromAssetSymbol}",
                    toDisplay = "${quote.receiveAmount} ${quote.receiveAssetSymbol}",
                    feeDisplay = "کارمزد: ${quote.feeAmount} ${quote.fromAssetSymbol}",
                    fromNetworkIcon = fromAsset.iconUrl,
                    toNetworkIcon = (currentState.selectedToAsset)?.iconUrl,
                    quote = quote
                )
            }
        }
    }

    /**
     * این تابع جدید توسط فرگمنت فراخوانی می‌شود وقتی کاربر
     * در صفحه WaitingForDeposit دکمه "ارسال" را فشار می‌دهد.
     */
    fun initiateBitcoinTransfer() {
        val currentState = _uiState.value as? SwapUiState.WaitingForDeposit ?: return

        viewModelScope.launch {
            _navigationEvent.emit(
                SwapNavigationEvent.NavigateToSendScreen(
                    assetId = currentState.quote.fromAssetId,
                    recipientAddress = currentState.depositAddress,
                    amount = currentState.quote.fromAmount
                )
            )
        }
    }


    /**
     * این تابع جدید باید از جایی (مثلاً از SendFragment بعد از ارسال موفق) فراخوانی شود
     * تا به بک‌اند اطلاع دهد که تراکنش واریز ارسال شده است.
     */
    fun notifyBitcoinDepositSent(txId: String) {
        val currentState = _uiState.value as? SwapUiState.WaitingForDeposit ?: return

        _uiState.value = SwapUiState.InProgress("در حال انتظار برای تایید بلاکچین...")

        viewModelScope.launch {
            // در آینده، اینجا swapRepository.notifyDeposit(currentState.quote.quoteId, txId) را صدا می‌زنیم
            // فعلاً فقط وضعیت را در UI تغییر می‌دهیم
            delay(5000) // شبیه‌سازی تاخیر
            _uiState.value = SwapUiState.Success("معامله شما در حال پردازش است.", "وضعیت آن به زودی در صفحه اصلی نمایش داده خواهد شد.")
        }
    }

    fun executeSwap() {
        val currentState = _uiState.value as? SwapUiState.Confirmation ?: return
        val quote = currentState.quote

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = SwapUiState.InProgress("در حال آماده‌سازی امضا...")

                // ۱. پیدا کردن اطلاعات کامل دارایی مبدا
                val fromAssetConfig = assetRegistry.getAssetById(quote.fromAssetId)
                    ?: throw IllegalStateException("Asset config not found for ${quote.fromAssetId}")

                // ۲. پیدا کردن اطلاعات کامل شبکه مبدا از روی networkId دارایی
                val fromNetwork = blockchainRegistry.getNetworkById(fromAssetConfig.networkId)
                    ?: throw IllegalStateException("Network config not found for ${fromAssetConfig.networkId}")

                val fromChainId = fromNetwork.chainId
                    ?: throw IllegalStateException("Chain ID not configured for ${fromNetwork.name}")

                if (fromAssetConfig.contractAddress == null) {
                    throw NotImplementedError("Native asset swaps are not implemented via Permit.")
                }

                // ۳. گرفتن آدرس قرارداد Phoenix از اطلاعات شبکه
                val phoenixContractAddress = fromNetwork.phoenixContractAddress
                    ?: throw IllegalStateException("Phoenix contract address not configured for ${fromNetwork.name}")

                // --- بخش اصلاح شده و تمیز ---
                // ساخت DataSource و گرفتن Web3j از آن
                val dataSource = dataSourceFactory.create(fromChainId)
                val web3j = dataSource.getWeb3jInstance()
                // ---

                // ۵. آماده‌سازی برای امضا
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

                // ۶. ساخت امضای Permit
                val signatureResult = Eip712Helper.signPermit(
                    credentials, tokenName, tokenVersion, fromChainId,
                    fromAssetConfig.contractAddress ?: "", phoenixContractAddress,
                    amountInWei, nonce, deadline
                )

                logPermitParametersForRemix(
                    fromAssetConfig,
                    credentials.address,
                    amountInWei,
                    deadline,
                    quote,
                    signatureResult.signature
                )


                _uiState.value = SwapUiState.InProgress("در حال ارسال به سرور...")

                // ۷. گرفتن آدرس مقصد
                val toAssetConfig = assetRegistry.getAssetById(quote.toAssetId)!!
                val destinationAddress =
                    walletRepository.getActiveAddressForNetwork(toAssetConfig.networkId)!!

                // ۸. ساخت درخواست و ارسال به ریپازیتوری
                val request = ExecuteSwapRequest(
                    quoteId = quote.quoteId,
                    fromChainId = fromChainId,
                    tokenAddress = fromAssetConfig.contractAddress ?: "",
                    ownerAddress = credentials.address,
                    amount = amountInWei.toString(),
                    deadline = deadline,
                    signature = signatureResult.signature,
                    destinationAddress = destinationAddress
                )

                when (val result = swapRepository.executeSwap(request)) {
                    is ResultResponse.Success -> {
                        globalEventBus.postEvent(GlobalEvent.WalletNeedsRefresh)
                        _uiState.value = SwapUiState.Success(result.data, "معامله ارسال شد.")
                    }

                    is ResultResponse.Error -> throw result.exception
                }

            } catch (e: Exception) {
                _uiState.value = SwapUiState.Failure(e.message ?: "خطای ناشناخته")
            }
        }
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
        quote: Quote,
        sig: Eip712Signature
    ) {
        val quoteIdBytes32 =
            "0x" + quote.quoteId.toByteArray(Charsets.UTF_8).toHexString().padEnd(64, '0')
        Log.d("PERMIT_TEST", "--- Remix Parameters ---")
        Log.d("PERMIT_TEST", "tokenAddress (address): ${config.contractAddress}")
        Log.d("PERMIT_TEST", "userAddress (address): $user")
        Log.d("PERMIT_TEST", "amount (uint256): $amount")
        Log.d("PERMIT_TEST", "deadline (uint256): $deadline")
        Log.d("PERMIT_TEST", "quoteId (bytes32): \"$quoteIdBytes32\"")
        Log.d("PERMIT_TEST", "v (uint8): ${sig.v}")
        Log.d("PERMIT_TEST", "r (bytes32): ${sig.r}")
        Log.d("PERMIT_TEST", "s (bytes32): ${sig.s}")
    }

    fun navigateBackToReadyState() {
        val currentState = _uiState.value
        if (currentState is SwapUiState.Confirmation || currentState is SwapUiState.Failure) {
            // به حالت Ready قبلی برمی‌گردیم (بدون رفرش کردن جفت‌ارزها)
            // این منطق باید کامل‌تر شود تا حالت قبلی را ذخیره کند
            loadInitialData() // ساده‌ترین راه
        }
    }


    private fun AssetConfig.toSwapAsset(balance: BigDecimal): SwapAsset {
        return SwapAsset(
            assetId = this.id,
            symbol = this.symbol,
            iconUrl = this.iconUrl,
            networkName = blockchainRegistry.getNetworkById(this.networkId)!!.name.name,
            balance = balance
        )
    }

    /*   */
    /**
     * این یک تابع تست برای شروع فرآیند سواپ با داده‌های هاردکد است.
     * این تابع فقط حالت اولیه را تنظیم می‌کند و بقیه فرآیند توسط منطق موجود ViewModel
     * و تعامل کاربر در UI پیش می‌رود.
     *//*
    fun startHardcodedEthToUsdtTest() {
        // این تابع باید از یک دکمه تست در UI فراخوانی شود.
        _uiState.value = SwapUiState.InitialLoading
        viewModelScope.launch {
            try {
                // --- مقادیر هاردکد برای تست ---
                val sepoliaNetwork = blockchainRegistry.getNetworkByName(NetworkName.SEPOLIA)!!
                val ethAssetConfig = assetRegistry.getAssetsForNetwork(sepoliaNetwork.id).find { it.contractAddress == null }!!
                val usdtAssetConfig = assetRegistry.getAssetsForNetwork(sepoliaNetwork.id).find { it.symbol == "USDT" }!!

                // گرفتن موجودی واقعی کاربر
                val userAddress = walletRepository.getActiveAddressForNetwork(sepoliaNetwork.id) ?: ""
                // TODO: این تابع باید موجودی واقعی را برگرداند
                val ethBalance = getAssetBalance(userAddress, ethAssetConfig)
                val usdtBalance = getAssetBalance(userAddress, usdtAssetConfig)

                // --- تنظیم حالت Ready با داده‌های تستی ---
                _uiState.value = SwapUiState.Ready(
                    availablePairs = emptyList(),
                    fromAsset = SwapAsset(
                        assetId = "${ethAssetConfig.symbol}-${sepoliaNetwork.chainId}",
                        symbol = ethAssetConfig.symbol,
                        iconUrl = ethAssetConfig.iconUrl,
                        networkName = sepoliaNetwork.name.name,
                        balance = ethBalance
                    ),
                    toAsset = SwapAsset(
                        assetId = "${usdtAssetConfig.symbol}-${sepoliaNetwork.chainId}",
                        symbol = usdtAssetConfig.symbol,
                        iconUrl = usdtAssetConfig.iconUrl,
                        networkName = sepoliaNetwork.name.name,
                        balance = usdtBalance
                    ),
                    // بقیه فیلدها با مقدار پیش‌فرض
                    amountIn = "",
                    amountOut = "",

                    quote = null,
                    isQuoteLoading = false,
                    isButtonEnabled = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = SwapUiState.Failure("خطا در آماده‌سازی داده‌های تست: ${e.message}")
            }
        }
    }*/
}