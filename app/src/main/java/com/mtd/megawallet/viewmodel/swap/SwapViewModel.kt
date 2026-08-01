package com.mtd.megawallet.viewmodel.swap

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.FiatConversion
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.IFiatCurrencyProvider
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.PendingTransactionHint
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.SwapQuoteRequest
import com.mtd.domain.model.SwapRoute
import com.mtd.domain.model.SwapTx
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.model.swap.SwapExecutionLeg
import com.mtd.domain.model.swap.SwapExecutionOutcome
import com.mtd.domain.model.swap.SwapExecutionRequest
import com.mtd.domain.model.swap.SwapLegKind
import com.mtd.domain.model.swap.parseHexQuantityOrNull
import com.mtd.domain.model.swap.swapTokenRef
import com.mtd.domain.usecase.send.EstimateSendFeesUseCase
import com.mtd.domain.usecase.swap.ExecuteSwapBundleUseCase
import com.mtd.domain.usecase.swap.GetSwapProvidersUseCase
import com.mtd.domain.usecase.swap.GetSwapQuoteUseCase
import com.mtd.domain.usecase.swap.PrepareSwapUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 * فلوی تبدیل (`/api/v1/swap`).
 *
 * سه محدودیتِ درستی که کلِ این کلاس حولِ آن‌ها ساخته شده:
 *  1. پول همه‌جا [BigInteger] در کوچک‌ترین واحد است. تبدیل به رشته/فیات فقط در لحظهٔ نمایش.
 *  2. هر استعلام یک TTL سختِ [SwapUiState.quoteTtlMs] دارد. بعد از انقضا استعلام مرده است:
 *     دوباره گرفته می‌شود و تأیید تا رسیدنِ استعلامِ تازه بسته است.
 *  3. `prepare` بستهٔ **مرتب** برمی‌گرداند و [ExecuteSwapBundleUseCase] آن را ترتیبی اجرا می‌کند.
 */
@HiltViewModel
class SwapViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog,
    private val fiatCurrencyProvider: IFiatCurrencyProvider,
    private val usdToIrrRateProvider: IUsdToIrrRateProvider,
    private val getSwapProvidersUseCase: GetSwapProvidersUseCase,
    private val getSwapQuoteUseCase: GetSwapQuoteUseCase,
    private val prepareSwapUseCase: PrepareSwapUseCase,
    private val executeSwapBundleUseCase: ExecuteSwapBundleUseCase,
    private val estimateSendFeesUseCase: EstimateSendFeesUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
    private val appEventBus: IAppEventBus,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private val _uiState = MutableStateFlow(SwapUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * `AssetItem` اصلیِ هر توکنِ پرداخت. برای برآورد کارمزد لازم است — مسیرِ موجودِ
     * [EstimateSendFeesUseCase] با `AssetItem` کار می‌کند و ساختِ دوبارهٔ آن از روی state
     * یعنی دو نسخهٔ متفاوت از یک دارایی.
     */
    private val payAssets = mutableMapOf<String, AssetItem>()

    private var quoteJob: Job? = null
    private var expiryJob: Job? = null
    private var feeJob: Job? = null
    private var executionJob: Job? = null

    init {
        usdToIrrRateProvider.rate
            .onEach { rate -> _uiState.update { it.copy(usdToTomanRate = rate) } }
            .launchIn(viewModelScope)

        fiatCurrencyProvider.currency
            .onEach { currency -> _uiState.update { it.copy(fiatCurrency = currency) } }
            .launchIn(viewModelScope)

        launchSafe(checkNetwork = false) { fiatCurrencyProvider.ensurePrimed() }
        launchSafe(connectivitySurface = ErrorSurface.SILENT) { usdToIrrRateProvider.refresh() }

        loadReceiveTokens()
        loadProviders()
    }

    // ────────────────────────────── ورودی ──────────────────────────────

    /**
     * لیستِ دارایی‌های واقعیِ کاربر از صفحهٔ اصلی. همان الگوی `SendScreen`: صفحه دارایی‌ها را
     * می‌دهد، ViewModel آن‌ها را نگه نمی‌دارد که خودش دوباره بخواند.
     */
    fun onAssetsAvailable(assets: List<AssetItem>) {
        val flattened = assets.flatMap { asset ->
            if (asset.isGroupHeader) asset.groupAssets else listOf(asset)
        }

        val swappable = flattened
            .filter { isEvm(it.networkId) }
            .filter { it.balanceRaw.signum() > 0 }
            .sortedByDescending { it.balanceRaw.multiply(it.priceUsdRaw) }

        payAssets.clear()
        swappable.forEach { payAssets[it.id] = it }

        val tokens = swappable.map { it.toPayToken() }
        _uiState.update { state ->
            state.copy(
                isLoadingPayTokens = false,
                payTokens = tokens,
                // اگر موجودی همان توکنِ انتخاب‌شده عوض شده، نسخهٔ تازه جایگزین شود.
                payToken = state.payToken?.let { selected ->
                    tokens.firstOrNull { it.option.id == selected.option.id } ?: selected
                }
            )
        }
    }

    fun onEvent(event: SwapEvent) {
        when (event) {
            is SwapEvent.PayQueryChanged -> _uiState.update { it.copy(payQuery = event.query) }

            is SwapEvent.PayTokenSelected -> {
                _uiState.update {
                    it.copy(
                        payToken = event.token,
                        phase = SwapPhase.AMOUNT,
                        amountInput = "0",
                        // توکنِ دریافتِ قبلی ممکن است همین توکن یا روی شبکهٔ دیگری باشد.
                        receiveToken = it.receiveToken?.takeIf { r -> r.id != event.token.option.id },
                        quoteState = SwapQuoteState.Idle,
                        quoteExpiresAtElapsed = null
                    )
                }
                refreshQuote()
            }

            SwapEvent.BackToPaySelect -> {
                cancelPricing()
                _uiState.update {
                    it.copy(
                        phase = SwapPhase.PAY_TOKEN,
                        amountInput = "0",
                        quoteState = SwapQuoteState.Idle,
                        quoteExpiresAtElapsed = null,
                        prepareState = SwapPrepareState.Idle
                    )
                }
            }

            is SwapEvent.AmountKeyPressed -> {
                _uiState.update { it.copy(amountInput = applyKey(it.amountInput, event.key)) }
                refreshQuote()
            }

            SwapEvent.ToggleFiatInput -> {
                _uiState.update { it.copy(isFiatInput = !it.isFiatInput, amountInput = "0") }
                refreshQuote()
            }

            SwapEvent.UseMax -> {
                val token = _uiState.value.payToken ?: return
                _uiState.update { it.copy(amountInput = it.maxInputText(token)) }
                refreshQuote()
            }

            SwapEvent.OpenReceiveSheet -> _uiState.update {
                it.copy(receiveSheetVisible = true, receiveQuery = "")
            }

            SwapEvent.DismissReceiveSheet -> _uiState.update { it.copy(receiveSheetVisible = false) }

            is SwapEvent.ReceiveQueryChanged -> _uiState.update { it.copy(receiveQuery = event.query) }

            is SwapEvent.ReceiveTokenSelected -> {
                _uiState.update {
                    it.copy(
                        receiveToken = event.token,
                        receiveSheetVisible = false,
                        quoteState = SwapQuoteState.Idle,
                        quoteExpiresAtElapsed = null
                    )
                }
                refreshQuote()
            }

            is SwapEvent.SlippageChanged -> {
                _uiState.update { it.copy(slippageBps = event.bps) }
                refreshQuote()
            }

            is SwapEvent.FeeLevelSelected -> _uiState.update {
                it.copy(fee = it.fee.copy(selectedLevel = event.level))
            }

            SwapEvent.RequoteRequested -> refreshQuote(immediate = true)

            SwapEvent.ContinuePressed -> {
                if (!_uiState.value.canContinueFromAmount) return
                _uiState.update { it.copy(phase = SwapPhase.CONFIRM, prepareState = SwapPrepareState.Idle) }
                estimateGasFee()
            }

            SwapEvent.BackFromConfirm -> _uiState.update {
                it.copy(phase = SwapPhase.AMOUNT, prepareState = SwapPrepareState.Idle)
            }

            SwapEvent.ConfirmPressed -> confirm()

            SwapEvent.ResultDismissed -> reset()
        }
    }

    fun reset() {
        cancelPricing()
        executionJob?.cancel()
        _uiState.update {
            SwapUiState(
                isLoadingPayTokens = false,
                payTokens = it.payTokens,
                receiveTokens = it.receiveTokens,
                providers = it.providers,
                platformFeeBps = it.platformFeeBps,
                quoteTtlMs = it.quoteTtlMs,
                fiatCurrency = it.fiatCurrency,
                usdToTomanRate = it.usdToTomanRate
            )
        }
    }

    // ────────────────────────────── استعلام ──────────────────────────────

    private fun refreshQuote(immediate: Boolean = false) {
        quoteJob?.cancel()
        expiryJob?.cancel()

        val state = _uiState.value
        if (!state.canRequestQuote) {
            _uiState.update { it.copy(quoteState = SwapQuoteState.Idle, quoteExpiresAtElapsed = null) }
            return
        }

        val request = state.toQuoteRequest() ?: return
        _uiState.update { it.copy(quoteState = SwapQuoteState.Loading) }

        quoteJob = launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            if (!immediate) delay(QUOTE_DEBOUNCE_MS)

            when (val result = getSwapQuoteUseCase(request)) {
                is ResultResponse.Success -> {
                    val quote = result.data
                    val route = quote.bestRoute
                        ?: quote.routes.firstOrNull { it.isBestReturn }
                        ?: quote.routes.firstOrNull()

                    if (route == null) {
                        _uiState.update {
                            it.copy(
                                quoteState = SwapQuoteState.Failed("مسیری برای این تبدیل پیدا نشد."),
                                quoteExpiresAtElapsed = null
                            )
                        }
                        return@launchSafe
                    }

                    val ttl = quote.ttlMs?.takeIf { it > 0 } ?: SwapUiState.DEFAULT_QUOTE_TTL_MS
                    _uiState.update {
                        it.copy(
                            quoteState = SwapQuoteState.Ready(quote, route),
                            quoteTtlMs = ttl,
                            quoteExpiresAtElapsed = SystemClock.elapsedRealtime() + ttl,
                            platformFeeBps = quote.platformFeeBps ?: it.platformFeeBps
                        )
                    }
                    scheduleRequoteOnExpiry(ttl)
                }

                is ResultResponse.Error -> {
                    // روی کارتِ استعلام دیده می‌شود؛ طبق TASK-57 اسنک‌بار نمی‌گیرد.
                    _uiState.update {
                        it.copy(
                            quoteState = SwapQuoteState.Failed(
                                userMessageFor(result.exception, "استعلام نرخ تبدیل ناموفق بود")
                            ),
                            quoteExpiresAtElapsed = null
                        )
                    }
                    reportError(
                        throwable = result.exception,
                        userAction = "swapQuote",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }
        }
    }

    /** استعلامِ منقضی مرده است — بی‌صدا تازه می‌شود تا کاربر روی نرخِ کهنه تأیید نزند. */
    private fun scheduleRequoteOnExpiry(ttlMs: Long) {
        expiryJob = launchLocal {
            delay(ttlMs)
            _uiState.update { it.copy(quoteExpiresAtElapsed = null) }
            val phase = _uiState.value.phase
            if (phase == SwapPhase.AMOUNT || phase == SwapPhase.CONFIRM) {
                refreshQuote(immediate = true)
            }
        }
    }

    private fun cancelPricing() {
        quoteJob?.cancel()
        expiryJob?.cancel()
        feeJob?.cancel()
    }

    // ────────────────────────────── کارمزد ──────────────────────────────

    /**
     * `gasPrice` از مسیرِ موجودِ برآوردِ کارمزد می‌آید (مقدارِ سطحِ زنجیره است و به شکلِ تراکنش
     * ربطی ندارد). `gasLimit` را سرور اعلام نمی‌کند، پس در [resolveGasLimits] از
     * `estimatedGas.native` بازسازی می‌شود.
     */
    private fun estimateGasFee() {
        feeJob?.cancel()
        val state = _uiState.value
        val asset = state.payToken?.option?.id?.let { payAssets[it] } ?: return
        val wallet = getActiveWalletUseCase() ?: return
        val ownAddress = wallet.keys.firstOrNull { it.networkId == asset.networkId }?.address ?: return
        val target = state.readyRoute?.tx?.to ?: state.readyRoute?.allowanceTarget ?: ownAddress

        feeJob = launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            val amount = state.amountRaw.takeIf { it.signum() > 0 } ?: BigInteger.ONE
            when (val result = estimateSendFeesUseCase(wallet, asset, target, amount)) {
                is ResultResponse.Success -> _uiState.update {
                    val options = result.data.options
                    it.copy(
                        fee = SwapFeeSelection(
                            options = options,
                            selectedLevel = it.fee.selectedLevel
                                ?: options.getOrNull(options.size / 2)?.level
                                ?: options.firstOrNull()?.level,
                            nativePriceUsd = nativePriceUsdFor(asset.networkId)
                        )
                    )
                }

                is ResultResponse.Error -> reportError(
                    throwable = result.exception,
                    userAction = "swapFeeEstimate",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
            }
        }
    }

    // ────────────────────────────── اجرا ──────────────────────────────

    private fun confirm() {
        val state = _uiState.value
        if (!state.canConfirm) return

        // بازبینیِ TTL در لحظهٔ تأیید، نه فقط در state: کاربر می‌تواند دقیقاً روی مرزِ انقضا بزند.
        val expiresAt = state.quoteExpiresAtElapsed
        if (expiresAt == null || SystemClock.elapsedRealtime() >= expiresAt) {
            _uiState.update {
                it.copy(
                    prepareState = SwapPrepareState.Failed("نرخ منقضی شده است. استعلام تازه گرفته می‌شود.")
                )
            }
            refreshQuote(immediate = true)
            return
        }

        val request = state.toQuoteRequest() ?: return
        val route = state.readyRoute ?: return
        val gasPrice = state.fee.selected?.gasPrice ?: return
        val networkId = state.payToken?.option?.networkId ?: return

        _uiState.update { it.copy(prepareState = SwapPrepareState.Loading) }

        executionJob = launchSafe {
            val prepared = when (val result = prepareSwapUseCase(request, route.provider)) {
                is ResultResponse.Success -> result.data
                is ResultResponse.Error -> {
                    // ۴۲۲ ⇒ SimulationReverted / SwapNoRoutes؛ متنِ واقعی همان است، نه پیام عمومی.
                    _uiState.update {
                        it.copy(
                            prepareState = SwapPrepareState.Failed(
                                userMessageFor(result.exception, "آماده‌سازی تبدیل ناموفق بود")
                            )
                        )
                    }
                    reportError(
                        throwable = result.exception,
                        userAction = "swapPrepare",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.MEDIUM
                    )
                    return@launchSafe
                }
            }

            val legs = buildLegs(prepared.transactions, route, gasPrice)
            if (legs == null) {
                _uiState.update {
                    it.copy(
                        prepareState = SwapPrepareState.Failed(
                            "بستهٔ تراکنشِ دریافتی از سرور قابل اجرا نبود."
                        )
                    )
                }
                return@launchSafe
            }

            // از این‌جا به بعد نرخ قفل شده است — استعلامِ دوباره فقط استیت را می‌کوبد.
            cancelPricing()
            _uiState.update {
                it.copy(phase = SwapPhase.EXECUTING, prepareState = SwapPrepareState.Idle)
            }

            executeSwapBundleUseCase(
                SwapExecutionRequest(networkId = networkId, gasPrice = gasPrice, legs = legs)
            ).collect { progress ->
                _uiState.update { it.copy(execution = progress) }
                progress.outcome?.let { outcome -> onExecutionFinished(outcome, state) }
            }
        }
    }

    private suspend fun onExecutionFinished(outcome: SwapExecutionOutcome, submitted: SwapUiState) {
        val message = when (outcome) {
            is SwapExecutionOutcome.Completed -> null
            is SwapExecutionOutcome.Stalled ->
                "تراکنش ارسال شد ولی هنوز تأیید نشده است. وضعیت آن را در تاریخچه دنبال کنید."
            is SwapExecutionOutcome.Failed -> {
                reportError(
                    throwable = outcome.cause,
                    userAction = "swapExecute",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.HIGH
                )
                userMessageFor(outcome.cause, "اجرای تبدیل ناموفق بود")
            }
        }

        _uiState.update { it.copy(phase = SwapPhase.RESULT, executionMessage = message) }

        val txId = when (outcome) {
            is SwapExecutionOutcome.Completed -> outcome.swapTxId
            is SwapExecutionOutcome.Stalled -> outcome.txId
            is SwapExecutionOutcome.Failed -> null
        }
        if (txId != null) notifySwapRegistered(submitted, txId)
    }

    /**
     * بازسازیِ `gasLimit`: قرارداد سرور فقط `estimatedGas.native` (هزینهٔ ارز بومی) را می‌دهد، نه
     * سقفِ گس. تقسیم بر `gasPrice` سقف را برمی‌گرداند و ضریبِ اطمینان برای نوسانِ مسیر اضافه می‌شود.
     * وقتی مسیر چیزی نگفته، کفِ محافظه‌کارانه برداشته می‌شود.
     */
    private fun resolveGasLimits(
        route: SwapRoute,
        gasPrice: BigInteger,
        legCount: Int
    ): List<BigInteger> {
        val estimated = route.estimatedGas?.native
            ?.takeIf { it.signum() > 0 && gasPrice.signum() > 0 }
            ?.divide(gasPrice)
            ?.multiply(GAS_LIMIT_BUFFER_NUMERATOR)
            ?.divide(GAS_LIMIT_BUFFER_DENOMINATOR)

        val swapLimit = estimated?.coerceAtLeast(MIN_SWAP_GAS_LIMIT) ?: DEFAULT_SWAP_GAS_LIMIT
        return List(legCount) { index ->
            if (index == legCount - 1) swapLimit else MIN_APPROVE_GAS_LIMIT
        }
    }

    private fun buildLegs(
        transactions: List<SwapTx>,
        route: SwapRoute,
        gasPrice: BigInteger
    ): List<SwapExecutionLeg>? {
        if (transactions.isEmpty()) return null
        val limits = resolveGasLimits(route, gasPrice, transactions.size)

        return transactions.mapIndexed { index, tx ->
            // مقدارِ غیرقابل‌تجزیه صفر نمی‌شود؛ کلِ بسته رد می‌شود.
            val value = parseHexQuantityOrNull(tx.value) ?: return null
            SwapExecutionLeg(
                kind = if (index == transactions.lastIndex) SwapLegKind.SWAP else SwapLegKind.APPROVE,
                to = tx.to,
                data = tx.data,
                value = value,
                gasLimit = limits[index]
            )
        }
    }

    private suspend fun notifySwapRegistered(state: SwapUiState, txId: String) {
        val payOption = state.payToken?.option ?: return
        val network = networkCatalog.getNetworkInfoById(payOption.networkId)
        val senderAddress = getActiveWalletUseCase()
            ?.keys
            ?.firstOrNull { it.networkId == payOption.networkId }
            ?.address

        runCatching {
            appEventBus.postEvent(
                AppEvent.WalletAssetNeedsRefresh(
                    assetId = payOption.id,
                    networkId = payOption.networkId,
                    contractAddress = payOption.contractAddress
                )
            )
        }
        state.receiveToken?.let { receive ->
            runCatching {
                appEventBus.postEvent(
                    AppEvent.WalletAssetNeedsRefresh(
                        assetId = receive.id,
                        networkId = receive.networkId,
                        contractAddress = receive.contractAddress
                    )
                )
            }
        }
        runCatching {
            appEventBus.postEvent(
                AppEvent.TransactionHistoryNeedsRefresh(
                    networkName = network?.id,
                    userAddress = senderAddress,
                    pendingTransaction = network?.let {
                        PendingTransactionHint(
                            hash = txId,
                            networkName = it.id,
                            networkType = it.networkType.name,
                            fromAddress = senderAddress,
                            toAddress = state.readyRoute?.tx?.to,
                            amount = state.amountRaw.toString(),
                            tokenSymbol = payOption.symbol,
                            tokenDecimals = payOption.decimals,
                            contractAddress = payOption.contractAddress,
                            isOutgoing = true
                        )
                    }
                )
            )
        }
    }

    // ────────────────────────────── فهرست‌ها ──────────────────────────────

    private fun loadReceiveTokens() {
        launchLocal {
            // قرارداد `/api/v1/swap` فهرستِ توکنِ قابل‌تبدیل ندارد؛ منبع، همان کاتالوگِ
            // داده‌محورِ اپ است (assets.json + باندلِ امضاشده).
            val tokens = assetCatalog.getAllAssetConfigs()
                .filter { isEvm(it.networkId) }
                .map { config ->
                    val network = networkCatalog.getNetworkInfoById(config.networkId)
                    SwapTokenOption(
                        id = config.id,
                        networkId = config.networkId,
                        networkName = network?.faName ?: network?.name?.name ?: config.networkId,
                        symbol = config.symbol,
                        name = config.name,
                        faName = config.faName,
                        iconUrl = config.iconUrl,
                        networkIconUrl = network?.iconUrl,
                        decimals = config.decimals,
                        contractAddress = config.contractAddress
                    )
                }
                .sortedBy { it.symbol }

            _uiState.update { it.copy(receiveTokens = tokens) }
        }
    }

    private fun loadProviders() {
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            // غنی‌سازیِ اختیاری (کارمزد پلتفرم + TTL واقعی). طبق TASK-57 شکستش SILENT است.
            when (val result = getSwapProvidersUseCase()) {
                is ResultResponse.Success -> _uiState.update {
                    it.copy(
                        providers = result.data.providers,
                        platformFeeBps = result.data.platformFeeBps ?: it.platformFeeBps,
                        quoteTtlMs = result.data.quoteTtlMs?.takeIf { ttl -> ttl > 0 } ?: it.quoteTtlMs
                    )
                }

                is ResultResponse.Error -> reportError(
                    throwable = result.exception,
                    userAction = "swapProviders",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
            }
        }
    }

    // ────────────────────────────── کمکی ──────────────────────────────

    private fun isEvm(networkId: String): Boolean =
        networkCatalog.getNetworkTypeForNetworkId(networkId) == NetworkType.EVM

    /**
     * کارمزد به ارزِ بومیِ شبکه پرداخت می‌شود، که لزوماً همان توکنِ در حالِ تبدیل نیست. قیمتش از
     * خودِ دارایی‌های کاربر برداشته می‌شود؛ اگر ارزِ بومیِ آن شبکه را ندارد، صفر یعنی «نامعلوم» و
     * UI فقط مقدارِ ارز را نشان می‌دهد.
     */
    private fun nativePriceUsdFor(networkId: String): BigDecimal =
        payAssets.values
            .firstOrNull { it.networkId == networkId && it.isNativeToken }
            ?.priceUsdRaw
            ?: BigDecimal.ZERO

    private fun SwapUiState.toQuoteRequest(): SwapQuoteRequest? {
        val pay = payToken?.option ?: return null
        val receive = receiveToken ?: return null
        val amount = amountRaw.takeIf { it.signum() > 0 } ?: return null
        val address = getActiveWalletUseCase()
            ?.keys
            ?.firstOrNull { it.networkId == pay.networkId }
            ?.address

        return SwapQuoteRequest(
            fromNetwork = pay.networkId,
            toNetwork = receive.networkId,
            fromToken = swapTokenRef(pay.contractAddress),
            toToken = swapTokenRef(receive.contractAddress),
            amountRaw = amount,
            // قرارداد، واحدِ این فیلد را مشخص نکرده؛ درصد فرستاده می‌شود (۵۰bps ⇒ ۰٫۵).
            slippage = slippageBps.toDouble() / 100.0,
            userAddress = address
        )
    }

    private fun AssetItem.toPayToken(): SwapPayToken {
        val network = networkCatalog.getNetworkInfoById(networkId)
        val option = SwapTokenOption(
            id = id,
            networkId = networkId,
            networkName = networkFaName ?: networkName,
            symbol = symbol,
            name = name,
            faName = faName,
            iconUrl = iconUrl,
            networkIconUrl = networkIconUrl ?: network?.iconUrl,
            decimals = decimals,
            contractAddress = contractAddress
        )
        return SwapPayToken(
            option = option,
            balanceRaw = balanceRaw.movePointRight(decimals).setScale(0, RoundingMode.DOWN).toBigInteger(),
            balanceDisplay = BalanceFormatter.formatBalance(balanceRaw, decimals),
            priceUsd = priceUsdRaw,
            fiatDisplay = balanceUsdt
        )
    }

    private fun SwapUiState.maxInputText(token: SwapPayToken): String {
        val crypto = BigDecimal(token.balanceRaw).movePointLeft(token.option.decimals)
        if (!isFiatInput) return crypto.stripTrailingZeros().toPlainString()

        val usd = crypto.multiply(token.priceUsd)
        val value = when (fiatCurrency) {
            FiatCurrency.USD -> usd.setScale(2, RoundingMode.DOWN)
            FiatCurrency.TOMAN -> FiatConversion.usdToToman(usd, usdToTomanRate)
                ?.setScale(0, RoundingMode.DOWN)
                ?: return "0"
        }
        return value.toPlainString()
    }

    private fun applyKey(current: String, key: String): String = when {
        key == SWAP_KEY_DELETE -> current.dropLast(1).ifEmpty { "0" }
        key == "." && current.contains('.') -> current
        key == "." -> "$current."
        current == "0" -> key
        current.length >= MAX_AMOUNT_DIGITS -> current
        else -> current + key
    }

    companion object {
        private const val QUOTE_DEBOUNCE_MS = 350L
        private const val MAX_AMOUNT_DIGITS = 20

        /** همان کفی که مسیرِ approveِ گس‌لس استفاده می‌کند. */
        private val MIN_APPROVE_GAS_LIMIT = BigInteger.valueOf(120_000L)
        private val MIN_SWAP_GAS_LIMIT = BigInteger.valueOf(250_000L)
        private val DEFAULT_SWAP_GAS_LIMIT = BigInteger.valueOf(500_000L)
        private val GAS_LIMIT_BUFFER_NUMERATOR = BigInteger.valueOf(12L)
        private val GAS_LIMIT_BUFFER_DENOMINATOR = BigInteger.TEN
    }
}
