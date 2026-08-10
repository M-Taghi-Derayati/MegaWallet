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
import com.mtd.domain.model.SwapToken
import com.mtd.domain.model.SwapTx
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.model.swap.SwapExecutionLeg
import com.mtd.domain.model.swap.SwapExecutionOutcome
import com.mtd.domain.model.swap.SwapExecutionRequest
import com.mtd.domain.model.swap.SwapLegKind
import com.mtd.domain.model.swap.parseHexQuantityOrNull
import com.mtd.domain.model.swap.sameSwapAddress
import com.mtd.domain.model.swap.swapTokenRef
import com.mtd.domain.usecase.network.ValidateAddressForNetworkUseCase
import com.mtd.domain.usecase.send.EstimateSendFeesUseCase
import com.mtd.domain.usecase.swap.ExecuteSwapBundleUseCase
import com.mtd.domain.usecase.swap.FindSwapTokenUseCase
import com.mtd.domain.usecase.swap.GetSwapChainsUseCase
import com.mtd.domain.usecase.swap.GetSwapProvidersUseCase
import com.mtd.domain.usecase.swap.GetSwapQuoteUseCase
import com.mtd.domain.usecase.swap.GetSwapTokensUseCase
import com.mtd.domain.usecase.swap.PrepareSwapUseCase
import com.mtd.domain.usecase.wallet.ExpandWalletKeysToNetworksUseCase
import com.mtd.domain.usecase.wallet.GetActiveAddressForNetworkUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.viewmodel.swap.SwapViewModel.Companion.TOKEN_SEARCH_DEBOUNCE_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
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
    private val getSwapChainsUseCase: GetSwapChainsUseCase,
    private val getSwapTokensUseCase: GetSwapTokensUseCase,
    private val findSwapTokenUseCase: FindSwapTokenUseCase,
    private val getSwapQuoteUseCase: GetSwapQuoteUseCase,
    private val prepareSwapUseCase: PrepareSwapUseCase,
    private val executeSwapBundleUseCase: ExecuteSwapBundleUseCase,
    private val estimateSendFeesUseCase: EstimateSendFeesUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
    private val getActiveAddressForNetworkUseCase: GetActiveAddressForNetworkUseCase,
    private val validateAddressForNetworkUseCase: ValidateAddressForNetworkUseCase,
    private val expandWalletKeysToNetworksUseCase: ExpandWalletKeysToNetworksUseCase,
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

    /** همهٔ دارایی‌های کاربر (نه فقط آن‌هایی که موجودی دارند) — منبعِ اولِ فهرستِ دریافت. */
    private val heldAssets = mutableListOf<AssetItem>()

    /**
     * نگاشتِ `AssetItem.id` فهرستِ دریافت به گزینهٔ واقعی. فهرست از نوعِ `AssetItem` است تا
     * کامپوننت‌های مشترکِ ارسال بتوانند رندرش کنند، ولی چیزی که به استعلام می‌رود این است.
     */
    private val receiveOptions = mutableMapOf<String, SwapTokenOption>()

    /**
     * مجموعهٔ رتبه‌بندی‌شدهٔ سرور برای نمای پیش‌فرضِ شیتِ دریافت (قبل از تایپ‌کردن). یک بار گرفته
     * می‌شود و تا [DEFAULT_LIST_TTL_MS] می‌ماند؛ هر بار بازکردنِ شیت یک راند از ۱۰ درخواست نیست.
     */
    private var defaultReceiveTokens: List<SwapToken> = emptyList()
    private var defaultReceiveLoadedAtMs = 0L

    /** توکن‌هایی که کاربر با چسباندنِ آدرس اضافه کرده. تا پایانِ فلو بالای فهرست می‌مانند. */
    private val pinnedTokens = mutableListOf<SwapToken>()

    /**
     * کاربر خودش آدرسِ مقصد را وارد کرده. تا وقتی `true` است، حلِ دوبارهٔ آدرس آن را بازنویسی
     * نمی‌کند — انتخابِ صریحِ کاربر نباید بی‌صدا با آدرسِ کیف‌پولِ خودش عوض شود.
     */
    private var destinationEdited = false

    private var quoteJob: Job? = null
    private var expiryJob: Job? = null
    private var feeJob: Job? = null
    private var executionJob: Job? = null
    private var searchJob: Job? = null
    private var defaultListJob: Job? = null

    init {
        usdToIrrRateProvider.rate
            .onEach { rate -> _uiState.update { it.copy(usdToTomanRate = rate) } }
            .launchIn(viewModelScope)

        fiatCurrencyProvider.currency
            .onEach { currency -> _uiState.update { it.copy(fiatCurrency = currency) } }
            .launchIn(viewModelScope)

        launchSafe(checkNetwork = false) { fiatCurrencyProvider.ensurePrimed() }
        launchSafe(connectivitySurface = ErrorSurface.SILENT) { usdToIrrRateProvider.refresh() }

        loadChains()
        loadProviders()
    }

    /**
     * تازه‌سازیِ فهرستِ شبکه‌های تبدیل‌پذیر موقعِ برگشت به اپ. خودِ ریپازیتوری کش دارد، پس این
     * فراخوانی صریحاً `forceRefresh` است — وگرنه هر resume فقط کش را می‌خواند و تغییرِ سمتِ
     * سرور تا ۶ ساعت دیده نمی‌شود.
     */
    fun onResumed() = loadChains(forceRefresh = true)

    // ────────────────────────────── ورودی ──────────────────────────────

    /**
     * لیستِ دارایی‌های واقعیِ کاربر از صفحهٔ اصلی. همان الگوی `SendScreen`: صفحه دارایی‌ها را
     * می‌دهد، ViewModel آن‌ها را نگه نمی‌دارد که خودش دوباره بخواند.
     */
    fun onAssetsAvailable(assets: List<AssetItem>) {
        val flattened = assets.flatMap { asset ->
            if (asset.isGroupHeader) asset.groupAssets else listOf(asset)
        }

        heldAssets.clear()
        heldAssets.addAll(flattened)

        val swappable = flattened
            .filter { canPayWith(it.networkId) }
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
                },
                payAsset = state.payAsset?.let { selected ->
                    payAssets[selected.id] ?: selected
                }
            )
        }

        // فقط موجودی‌های روی ردیف‌های موجود تازه می‌شوند. ساختنِ یک فهرستِ محلی این‌جا یعنی نمای
        // پیش‌فرض لحظه‌ای چیزی غیر از مجموعهٔ رتبه‌بندی‌شدهٔ سرور نشان بدهد.
        if (defaultReceiveTokens.isNotEmpty() || _uiState.value.receiveListFellBack) {
            refreshReceiveList()
        }
    }

    fun onEvent(event: SwapEvent) {
        when (event) {
            is SwapEvent.PayQueryChanged -> _uiState.update { it.copy(payQuery = event.query) }

            is SwapEvent.PayAssetSelected -> {
                // فهرست همهٔ دارایی‌های دارای موجودی را نشان می‌دهد؛ [payTokens] فقط آن‌هایی را
                // دارد که ریلِ اجرا پشتیبانی می‌کند، پس نبودنِ دارایی در آن یعنی «قابل تبدیل نیست».
                val token = _uiState.value.payTokens.firstOrNull { it.option.id == event.asset.id }
                if (token == null) {
                    showSnackbarMessage(payRejectionMessage(event.asset.networkId))
                    return
                }
                _uiState.update {
                    val receive = it.receiveToken?.takeIf { r -> !r.isSameTokenAs(token.option) }
                    it.copy(
                        payToken = token,
                        payAsset = payAssets[event.asset.id] ?: event.asset,
                        payNetworkType = networkCatalog.getNetworkTypeForNetworkId(token.option.networkId),
                        phase = SwapPhase.AMOUNT,
                        amountInput = "0",
                        // توکنِ دریافت اگر دقیقاً همین توکن روی همین شبکه است پاک می‌شود؛ تبدیلِ
                        // یک ارز به خودش مسیری ندارد. مقایسه روی شناسه انجام نمی‌شود چون دو سمت
                        // از دو منبعِ متفاوت شناسه می‌گیرند.
                        receiveToken = receive,
                        receiveNetworkType = receive?.let { r ->
                            networkCatalog.getNetworkTypeForNetworkId(r.networkId)
                        },
                        quoteState = SwapQuoteState.Idle,
                        quoteExpiresAtElapsed = null
                    )
                }
                // عوض‌شدنِ شبکهٔ مبدأ می‌تواند جفت را بین‌خانوادگی کند و آدرسِ مقصد را اجباری.
                resolveDestinationAddress()
            }

            SwapEvent.BackToPaySelect -> {
                cancelPricing()
                destinationEdited = false
                _uiState.update {
                    it.copy(
                        phase = SwapPhase.PAY_TOKEN,
                        amountInput = "0",
                        quoteState = SwapQuoteState.Idle,
                        quoteExpiresAtElapsed = null,
                        prepareState = SwapPrepareState.Idle,
                        // مقصد به جفتِ قبلی گره خورده بود؛ حمل‌کردنش به انتخابِ بعدی یعنی آدرسی
                        // که کاربر برای زنجیرهٔ دیگری وارد کرده بی‌سروصدا دوباره استفاده شود.
                        destinationInput = "",
                        destinationOwnAddress = null,
                        destinationError = null,
                        destinationEditing = false
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

            SwapEvent.OpenReceiveSheet -> {
                _uiState.update {
                    it.copy(
                        receiveSheetVisible = true,
                        receiveQuery = "",
                        importQuery = "",
                        importState = SwapImportState.Idle,
                        receiveSearchState = SwapTokenSearchState.Idle
                    )
                }
                loadDefaultReceiveTokens()
            }

            SwapEvent.DismissReceiveSheet -> {
                searchJob?.cancel()
                defaultListJob?.cancel()
                _uiState.update { it.copy(receiveSheetVisible = false) }
            }

            is SwapEvent.ReceiveQueryChanged -> onReceiveQueryChanged(event.query)

            is SwapEvent.ReceiveAssetSelected -> {
                val option = receiveOptions[event.asset.id]
                if (option == null) {
                    showSnackbarMessage("این ارز فعلاً قابل انتخاب نیست.")
                    return
                }
                _uiState.update {
                    it.copy(
                        receiveToken = option,
                        receiveNetworkType = networkCatalog.getNetworkTypeForNetworkId(option.networkId),
                        receiveSheetVisible = false,
                        quoteState = SwapQuoteState.Idle,
                        quoteExpiresAtElapsed = null
                    )
                }
                // شبکهٔ مقصد عوض شد ⇒ آدرسِ مقصد دوباره حل و اعتبارسنجی می‌شود، و استعلام فقط
                // بعد از آن می‌رود: کشِ ۱۵ ثانیه‌ایِ سرور به ازای هر مقصد جداست.
                resolveDestinationAddress()
            }

            is SwapEvent.ImportQueryChanged -> _uiState.update {
                it.copy(importQuery = event.query, importState = SwapImportState.Idle)
            }

            SwapEvent.ImportSubmitted -> importPastedToken()

            is SwapEvent.DestinationAddressChanged -> onDestinationAddressChanged(event.address)

            SwapEvent.DestinationEditorToggled -> _uiState.update {
                it.copy(destinationEditing = !it.destinationEditing)
            }

            SwapEvent.DestinationResetToOwnWallet -> {
                val own = _uiState.value.destinationOwnAddress ?: return
                onDestinationAddressChanged(own)
                _uiState.update { it.copy(destinationEditing = false) }
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
        searchJob?.cancel()
        defaultListJob?.cancel()
        destinationEdited = false
        pinnedTokens.clear()
        _uiState.update {
            SwapUiState(
                isLoadingPayTokens = false,
                payTokens = it.payTokens,
                receiveTokens = it.receiveTokens,
                providers = it.providers,
                quoteTtlMs = it.quoteTtlMs,
                fiatCurrency = it.fiatCurrency,
                usdToTomanRate = it.usdToTomanRate,
                // فهرستِ شبکه‌ها مالِ فلوی فعلی نیست؛ دور ریختنش یعنی یک درخواستِ دیگر و یک
                // بازهٔ بدونِ گِیت بعد از هر بار بستنِ صفحه.
                swappableNetworkIds = it.swappableNetworkIds,
                swapChainsLoaded = it.swapChainsLoaded
            )
        }
    }

    // ────────────────────────────── آدرسِ مقصد ──────────────────────────────

    /**
     * حلِ آدرسِ گیرندهٔ خروجی و بعد استعلام.
     *
     * پیش‌فرض، آدرسِ **خودِ کاربر روی شبکهٔ مقصد** است — این یک کیف‌پولِ چندزنجیره‌ای است، پس در
     * حالتِ عادی کاربر لازم نیست چیزی بچسباند حتی وقتی قالبِ دو سر فرق دارد. اگر کاربر خودش آدرسی
     * وارد کرده باشد دست‌نخورده می‌ماند: بازنویسیِ خاموشِ آن یعنی فرستادنِ پول به جایی غیر از آن‌چه
     * او انتخاب کرده.
     */
    private fun resolveDestinationAddress() {
        val receive = _uiState.value.receiveToken
        if (receive == null) {
            _uiState.update {
                it.copy(destinationOwnAddress = null, destinationError = null)
            }
            refreshQuote()
            return
        }

        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            val own = getActiveAddressForNetworkUseCase(receive.networkId)
                ?.takeIf { it.isNotBlank() }

            // انتخابِ دستیِ کاربر برندهٔ پیش‌فرض است.
            val address = if (destinationEdited) _uiState.value.destinationInput else own.orEmpty()
            val error = validateDestination(address, receive.networkId)

            _uiState.update {
                it.copy(
                    destinationOwnAddress = own,
                    destinationInput = address,
                    destinationError = error,
                    quoteState = SwapQuoteState.Idle,
                    quoteExpiresAtElapsed = null
                )
            }
            refreshQuote()
        }
    }

    private fun onDestinationAddressChanged(address: String) {
        destinationEdited = true
        val error = _uiState.value.receiveToken?.networkId
            ?.let { networkId -> validateDestination(address, networkId) }

        _uiState.update {
            it.copy(
                // متن دست‌نخورده ذخیره می‌شود؛ trim فقط موقعِ اعتبارسنجی و ارسال.
                destinationInput = address,
                destinationError = error,
                quoteState = SwapQuoteState.Idle,
                quoteExpiresAtElapsed = null
            )
        }
        refreshQuote()
    }

    /**
     * اعتبارسنجیِ **سمتِ کلاینت** آدرسِ مقصد. سرور هم آدرسِ خانوادهٔ اشتباه را رد می‌کند، ولی
     * پل‌زدن به آدرسی که هیچ کلیدی روی آن زنجیره نمی‌تواند خرجش کند یعنی سوختنِ دارایی — پس
     * این‌جا هم گرفته می‌شود، قبل از این‌که کاربر منتظرِ یک استعلامِ محکوم به شکست بماند.
     *
     * آدرسِ خالی وقتی اجباری نیست خطا نیست: سرور خودش `userAddress` را می‌گذارد.
     */
    private fun validateDestination(address: String, networkId: String): String? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) {
            return if (_uiState.value.requiresDestinationAddress) DESTINATION_REQUIRED_MESSAGE else null
        }
        if (!validateAddressForNetworkUseCase(trimmed, networkId)) {
            val network = _uiState.value.receiveToken?.networkName ?: networkId
            return "این آدرس برای شبکهٔ $network معتبر نیست."
        }
        return null
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

        _uiState.update { it.copy(quoteState = SwapQuoteState.Loading) }

        quoteJob = launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            if (!immediate) delay(QUOTE_DEBOUNCE_MS)

            // آدرس بعد از debounce حل می‌شود چون مشتق‌کردنش I/O دارد و روی هر ضربهٔ کلید لازم نیست.
            val request = _uiState.value.toQuoteRequest()
            if (request == null) {
                _uiState.update {
                    it.copy(
                        quoteState = SwapQuoteState.Failed(NO_ADDRESS_MESSAGE, isRetryable = false),
                        quoteExpiresAtElapsed = null
                    )
                }
                return@launchSafe
            }

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
                            // آدرسِ مبدأ طبق پاسخِ سرور، تا «گیرنده کسِ دیگری است» از روی همان
                            // چیزی سنجیده شود که مسیر واقعاً برایش ساخته شده.
                            senderAddress = quote.quotedUserAddress ?: request.userAddress
                        )
                    }
                    scheduleRequoteOnExpiry(ttl)
                }

                is ResultResponse.Error -> {
                    // روی کارتِ استعلام دیده می‌شود؛ طبق TASK-57 اسنک‌بار نمی‌گیرد.
                    val api = result.exception as? ApiException
                    _uiState.update {
                        it.copy(
                            quoteState = SwapQuoteState.Failed(
                                message = swapErrorMessage(result.exception, "استعلام نرخ تبدیل ناموفق بود"),
                                // شبکهٔ پشتیبانی‌نشده با تلاشِ دوباره درست نمی‌شود.
                                isRetryable = api?.apiError != ApiError.SwapChainUnsupported
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
        // مثل SendViewModel: کلیدهای ذخیره‌شده منجمدند، کاتالوگ نه.
        val ownAddress = expandWalletKeysToNetworksUseCase(wallet.keys)
            .firstOrNull { it.networkId == asset.networkId }?.address ?: return
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

        val route = state.readyRoute ?: return
        val gasPrice = state.fee.selected?.gasPrice ?: return
        val networkId = state.payToken?.option?.networkId ?: return

        _uiState.update { it.copy(prepareState = SwapPrepareState.Loading) }

        executionJob = launchSafe {
            // همان درخواستی که استعلام با آن گرفته شد — شبیه‌سازیِ سمتِ سرور روی ورودیِ دیگری
            // نتیجهٔ دیگری می‌دهد.
            val request = state.toQuoteRequest()
            if (request == null) {
                _uiState.update {
                    it.copy(prepareState = SwapPrepareState.Failed(NO_ADDRESS_MESSAGE))
                }
                return@launchSafe
            }

            // مسیر (نه فقط ارائه‌دهنده‌اش) فرستاده می‌شود تا `toAddress`ی که بسته برایش ساخته
            // می‌شود همانِ مسیرِ استعلام‌شده باشد؛ اختلاف، همان‌جا رد می‌شود نه روی زنجیره.
            val prepared = when (val result = prepareSwapUseCase(request, route)) {
                is ResultResponse.Success -> result.data
                is ResultResponse.Error -> {
                    // ۴۲۲ ⇒ SimulationReverted / SwapNoRoutes؛ متنِ واقعی همان است، نه پیام عمومی.
                    _uiState.update {
                        it.copy(
                            prepareState = SwapPrepareState.Failed(
                                swapErrorMessage(result.exception, "آماده‌سازی تبدیل ناموفق بود")
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
            // ⚠️ پل: تأییدِ تراکنشِ مبدأ یعنی «پا اول تمام شد»، نه «پول رسید». سرور پای مقصد را
            // دنبال نمی‌کند، پس تنها چیزی که راست است همین است.
            is SwapExecutionOutcome.Completed -> submitted.bridgePendingMessage()
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

    /**
     * فهرستِ شبکه‌های تبدیل‌پذیر. شکستش عمداً بی‌صداست و [SwapUiState.swapChainsLoaded] را
     * `true` نمی‌کند: نبودِ پاسخ نباید ورودیِ تبدیل را ببندد.
     */
    private fun loadChains(forceRefresh: Boolean = false) {
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            when (val result = getSwapChainsUseCase(forceRefresh)) {
                is ResultResponse.Success -> {
                    val ids = result.data.map { it.networkId.lowercase(Locale.US) }.toSet()
                    if (ids.isEmpty()) return@launchSafe
                    _uiState.update {
                        it.copy(swappableNetworkIds = ids, swapChainsLoaded = true)
                    }
                    // فهرستِ پرداخت قبل از رسیدنِ این پاسخ ساخته شده و گِیت نخورده بود.
                    onAssetsAvailable(heldAssets.toList())
                    // تا این لحظه شبکهٔ مقصدی نمی‌شناختیم، پس فهرستِ دریافت هم درخواستی نداده بود.
                    if (_uiState.value.receiveSheetVisible) loadDefaultReceiveTokens()
                }

                is ResultResponse.Error -> reportError(
                    throwable = result.exception,
                    userAction = "swapChains",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
            }
        }
    }

    // ────────────────────────────── فهرستِ دریافت ──────────────────────────────

    /**
     * جست‌وجو در جهانِ قابل‌تبدیل.
     *
     * درخواستِ در جریان با هر ضربهٔ کلید لغو می‌شود و بعد از [TOKEN_SEARCH_DEBOUNCE_MS] دوباره
     * فرستاده می‌شود. سرویس از یک آینهٔ سمتِ سرور خوانده می‌شود (بدون تماسِ upstream)، پس هزینهٔ
     * واقعی‌اش شبکهٔ گوشی است نه سرور.
     */
    private fun onReceiveQueryChanged(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(receiveQuery = query) }

        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            // پاک‌کردنِ جست‌وجو ⇒ برگشت به نمای پیش‌فرضِ رتبه‌بندی‌شده، نه ماندنِ نتیجهٔ جهانی.
            _uiState.update { it.copy(receiveSearchState = SwapTokenSearchState.Idle) }
            loadDefaultReceiveTokens()
            return
        }

        _uiState.update { it.copy(receiveSearchState = SwapTokenSearchState.Loading) }
        searchJob = launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            delay(TOKEN_SEARCH_DEBOUNCE_MS)

            val networks = receiveNetworkIds()
            val results = coroutineScope {
                networks.map { id ->
                    async { getSwapTokensUseCase(id, trimmed, SEARCH_LIMIT_PER_NETWORK) }
                }.awaitAll()
            }

            val found = results.mapNotNull { (it as? ResultResponse.Success)?.data }.flatten()
            val firstError = results.mapNotNull { (it as? ResultResponse.Error)?.exception }.firstOrNull()

            _uiState.update {
                it.copy(
                    // ۵۰۳ SWAP_TOKENS_UNAVAILABLE ⇒ آینه خاموش است؛ فهرستِ باندل همچنان نشان
                    // داده می‌شود، فقط جست‌وجوی سراسری کار نمی‌کند.
                    receiveSearchState = if (found.isEmpty() && firstError != null) {
                        SwapTokenSearchState.Failed(swapErrorMessage(firstError, "جست‌وجوی ارز ناموفق بود"))
                    } else {
                        SwapTokenSearchState.Idle
                    }
                )
            }
            // شکستِ کاملِ جست‌وجو تنها حالتی است که فهرستِ محلی جای جهانی را می‌گیرد.
            rebuildReceiveList(found, allowLocalFallback = found.isEmpty() && firstError != null)
        }
    }

    /**
     * «افزودن با آدرس». ۴۰۴ یعنی قابلِ مسیریابی نیست و import رد می‌شود.
     *
     * قراردادِ `/swap/tokens/{networkId}/{address}` شبکه می‌خواهد ولی یک آدرسِ خام خودش نمی‌گوید
     * مالِ کدام زنجیره است، پس روی همهٔ شبکه‌های مقصد هم‌زمان پرسیده می‌شود. یک آدرس روی دو
     * زنجیره واقعاً دو توکنِ متفاوت است (همان آدرسِ USDC روی اتریوم ۶ رقم دارد و روی BSC ۱۸)، پس
     * انتخابِ یکی به‌جای کاربر یک حدس است — همهٔ نتیجه‌ها pin می‌شوند و شبکه در شیتِ بعدی
     * انتخاب می‌شود.
     */
    private fun importPastedToken() {
        val state = _uiState.value
        // آدرس دقیقاً همان‌طور که چسبانده شده می‌ماند: base58 ترون به حروف حساس است و
        // کوچک‌کردنش خواندنِ روی‌زنجیره را برای توکنِ خارج از فهرست غیرممکن می‌کند.
        val address = state.importQuery.trim()
        if (address.isEmpty()) return

        val networks = receiveNetworkIds()
        if (networks.isEmpty()) {
            _uiState.update {
                it.copy(importState = SwapImportState.Failed("فهرست شبکه‌های تبدیل هنوز آماده نیست."))
            }
            return
        }

        _uiState.update { it.copy(importState = SwapImportState.Loading) }
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            val results = coroutineScope {
                networks.map { id -> async { findSwapTokenUseCase(id, address) } }.awaitAll()
            }
            val found = results.mapNotNull { (it as? ResultResponse.Success)?.data }
            val firstError = results.mapNotNull { (it as? ResultResponse.Error)?.exception }.firstOrNull()

            val result: ResultResponse<List<SwapToken>> = when {
                found.isNotEmpty() -> ResultResponse.Success(found)
                firstError != null -> ResultResponse.Error(firstError)
                else -> ResultResponse.Error(
                    ApiException(
                        apiError = ApiError.AssetNotFound,
                        reasonFa = "این آدرس روی هیچ‌کدام از شبکه‌های تبدیل شناخته نشد."
                    )
                )
            }

            when (result) {
                is ResultResponse.Success -> {
                    result.data.asReversed().forEach { token ->
                        pinnedTokens.removeAll { existing ->
                            existing.networkId.equals(token.networkId, ignoreCase = true) &&
                                existing.contractAddress.equals(token.contractAddress, ignoreCase = true)
                        }
                        pinnedTokens.add(0, token)
                    }
                    _uiState.update {
                        it.copy(
                            importState = SwapImportState.Idle,
                            importQuery = "",
                            // جست‌وجوی باز، توکنِ تازه‌افزوده را پنهان می‌کند؛ نمای پیش‌فرض برمی‌گردد.
                            receiveQuery = ""
                        )
                    }
                    refreshReceiveList()
                }

                is ResultResponse.Error -> _uiState.update {
                    it.copy(
                        importState = SwapImportState.Failed(
                            swapErrorMessage(result.exception, "این آدرس قابل افزودن نبود")
                        )
                    )
                }
            }
        }
    }

    /**
     * فهرستِ **پیش‌فرض** (قبل از تایپ‌کردن) = مجموعهٔ رتبه‌بندی‌شدهٔ سرور، و بس.
     *
     * این یک تصمیمِ محصولی است نه بهینه‌سازی: آرایهٔ خامِ aggregator هیچ سیگنالِ کیفیتی ندارد و
     * روی ترون سرش عملاً `US DT` و `U S D T` و `UDST` است — سه بدلِ تتر جلوتر از قراردادِ واقعی،
     * با قیمت‌های ساختگی. سرور همین فهرست را رتبه‌بندی می‌کند، پس جهانِ کاملِ ~۱۱ هزارتایی فقط
     * **بعد از** جست‌وجو دیده می‌شود؛ ادغامِ آن در نمای پیش‌فرض یعنی هُل‌دادنِ کاربر وسطِ بدل‌ها.
     *
     * فهرست به موجودیِ کاربر محدود **نمی‌شود**: سمتِ دریافت معمولاً توکنی است که کاربر اصلاً
     * ندارد. موجودی فقط روی ردیف نمایش داده می‌شود.
     */
    private fun loadDefaultReceiveTokens() {
        val networks = receiveNetworkIds()
        if (networks.isEmpty()) {
            // هنوز نمی‌دانیم مقصدهای معتبر کدام‌اند؛ `loadChains` دوباره این‌جا را صدا می‌زند.
            if (!_uiState.value.swapChainsLoaded) {
                _uiState.update { it.copy(receiveSearchState = SwapTokenSearchState.Loading) }
            }
            return
        }

        val isFresh = System.currentTimeMillis() - defaultReceiveLoadedAtMs < DEFAULT_LIST_TTL_MS
        if (defaultReceiveTokens.isNotEmpty() && isFresh) {
            refreshReceiveList()
            return
        }

        defaultListJob?.cancel()
        _uiState.update { it.copy(receiveSearchState = SwapTokenSearchState.Loading) }

        defaultListJob = launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            val results = coroutineScope {
                networks.map { id ->
                    async { getSwapTokensUseCase(id, null, DEFAULT_LIST_LIMIT_PER_NETWORK) }
                }.awaitAll()
            }

            val perNetwork = results.mapNotNull { (it as? ResultResponse.Success)?.data }
                .filter { it.isNotEmpty() }
            val firstError = results.mapNotNull { (it as? ResultResponse.Error)?.exception }.firstOrNull()

            val merged = interleaveByRank(perNetwork)
            if (merged.isNotEmpty()) {
                defaultReceiveTokens = merged
                defaultReceiveLoadedAtMs = System.currentTimeMillis()
            }

            _uiState.update {
                it.copy(
                    receiveSearchState = if (merged.isEmpty() && firstError != null) {
                        SwapTokenSearchState.Failed(
                            swapErrorMessage(firstError, "فهرست ارزها در دسترس نیست")
                        )
                    } else {
                        SwapTokenSearchState.Idle
                    },
                    // ۵۰۳ SWAP_TOKENS_UNAVAILABLE ⇒ آینه خاموش است؛ باندل جای فهرستِ رتبه‌بندی‌شده
                    // را می‌گیرد تا کاربر با شیتِ خالی روبه‌رو نشود.
                    receiveListFellBack = merged.isEmpty()
                )
            }
            refreshReceiveList()
        }
    }

    /**
     * درهم‌بافتنِ فهرستِ چند شبکه، رتبه‌به‌رتبه: اول ردهٔ اولِ همهٔ شبکه‌ها، بعد ردهٔ دوم، …
     *
     * ترتیبِ **درونِ** هر شبکه دست‌نخورده می‌ماند (سرور رتبه‌بندی‌اش کرده و بازچینشِ آن یعنی
     * بالاآمدنِ بدل‌ها)؛ این فقط تصمیم می‌گیرد سرِ فهرست از کدام زنجیره باشد. پشتِ سرِ هم چسباندنِ
     * شبکه‌ها یعنی صد ردیفِ اولِ شیت همه از یک زنجیره.
     */
    private fun interleaveByRank(perNetwork: List<List<SwapToken>>): List<SwapToken> {
        if (perNetwork.size <= 1) return perNetwork.firstOrNull().orEmpty()
        val merged = mutableListOf<SwapToken>()
        val deepest = perNetwork.maxOf { it.size }
        for (rank in 0 until deepest) {
            perNetwork.forEach { list -> list.getOrNull(rank)?.let { merged += it } }
        }
        return merged
    }

    private fun refreshReceiveList() {
        launchLocal { rebuildReceiveList(defaultReceiveTokens, allowLocalFallback = true) }
    }

    /**
     * ساختِ ردیف‌های شیتِ دریافت از [fromServer]، با حذفِ تکراریِ آدرس‌محور در
     * [buildReceiveGroups]. ترتیبِ سرور دست‌کاری نمی‌شود.
     *
     * توکن‌هایی که کاربر با آدرس اضافه کرده ([pinnedTokens]) همیشه بالای فهرست می‌مانند، وگرنه
     * چیزی را که خودش وارد کرده در ردیفِ هزارم پیدا نمی‌کند.
     *
     * [allowLocalFallback] فقط برای نمای پیش‌فرض است: وقتی آینهٔ سرور چیزی نداد، دارایی‌های کاربر
     * و باندلِ کیوریت‌شده جای آن را می‌گیرند. در نمای جست‌وجو این کار انجام نمی‌شود — نتیجهٔ محلی
     * به‌جای نتیجهٔ جهانی، یعنی گفتنِ «چنین توکنی نیست» دربارهٔ توکنی که هست.
     */
    private suspend fun rebuildReceiveList(
        fromServer: List<SwapToken>,
        allowLocalFallback: Boolean = false
    ) {
        val state = _uiState.value
        val query = state.receiveQuery.trim()
        val candidates = mutableListOf<SwapReceiveCandidate>()

        pinnedTokens
            .filter { canReceiveOn(it.networkId) }
            .forEach { candidates += it.toCandidate() }

        fromServer
            .filter { canReceiveOn(it.networkId) }
            .forEach { candidates += it.toCandidate() }

        if (allowLocalFallback && fromServer.isEmpty()) {
            heldAssets
                .filter { canReceiveOn(it.networkId) }
                .filter { query.isEmpty() || it.matchesQuery(query) }
                .sortedByDescending { it.balanceRaw.multiply(it.priceUsdRaw) }
                .forEach { candidates += it.toReceiveCandidate() }

            assetCatalog.getAllAssetConfigs()
                .filter { canReceiveOn(it.networkId) }
                .filter { query.isEmpty() || it.matchesQuery(query) }
                .forEach { candidates += it.toCandidate() }
        }

        receiveOptions.clear()
        candidates.forEach { candidate ->
            // اولین منبع برنده است، پس نسخهٔ pin‌شده/کیوریت‌شده جای نسخهٔ خامِ آینه را نمی‌گیرد.
            receiveOptions.getOrPut(candidate.assetId) { candidate.toOption() }
        }

        _uiState.update {
            it.copy(receiveTokens = buildReceiveGroups(candidates, it.fiatCurrency, it.usdToTomanRate))
        }
    }

    private fun loadProviders() {
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            // غنی‌سازیِ اختیاری (فهرست ارائه‌دهنده‌ها + TTL واقعی). طبق TASK-57 شکستش SILENT است.
            //
            // `platformFeeBps` عمداً از این‌جا برداشته نمی‌شود: نرخ در زمانِ اجرا قابلِ تغییر است و
            // نگه‌داشتنِ نسخهٔ زمانِ استارت یعنی نمایشِ نرخی که دیگر معتبر نیست. کارمزد فقط از
            // `fees` همان مسیرِ استعلام‌شده خوانده می‌شود.
            when (val result = getSwapProvidersUseCase()) {
                is ResultResponse.Success -> _uiState.update {
                    it.copy(
                        providers = result.data.providers,
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
     * پرداخت روی این شبکه ممکن است؟ دو شرطِ مستقل: سرور شبکه را تبدیل‌پذیر اعلام کرده، و **ریلِ
     * امضای خودِ اپ** می‌تواند بستهٔ `prepare` را روی آن اجرا کند.
     *
     * ⚠️ شرطِ دوم امروز یعنی EVM. `prepare` تراکنش را به شکلِ `{to, data, value}` می‌دهد و
     * [ExecuteSwapBundleUseCase] آن را با `TransactionParams.Evm` می‌فرستد؛ مسیرِ TVM آدرسِ قرارداد
     * به‌همراه **امضای متنیِ تابع** می‌خواهد (`transfer(address,uint256)`) که از چهار بایتِ اولِ
     * `data` قابل بازسازی نیست. تا وقتی شکلِ تراکنشِ ترون در قراردادِ `prepare` مشخص و ریلِ TVM
     * برای calldataیِ خام باز نشده، ترون فقط **مقصد** است، نه مبدأ — حدس‌زدنِ هر دو قرارداد
     * هم‌زمان یعنی امضای چیزی که نمی‌دانیم چیست.
     */
    private fun canPayWith(networkId: String): Boolean =
        isEvm(networkId) && _uiState.value.isNetworkSwappable(networkId)

    /**
     * مقصدِ پل. برخلافِ مبدأ، به EVM محدود نیست: هیچ تراکنشی روی زنجیرهٔ مقصد امضا نمی‌شود، پس
     * تنها شرط این است که سرور آن را تبدیل‌پذیر اعلام کرده باشد. همین است که پلِ EVM → ترون را
     * ممکن می‌کند؛ آدرسِ مقصد در آن حالت اجباری می‌شود
     * ([SwapUiState.requiresDestinationAddress]).
     */
    private fun canReceiveOn(networkId: String): Boolean =
        _uiState.value.isNetworkSwappable(networkId)

    /**
     * شبکه‌هایی که واقعاً به سرور درخواست فرستاده می‌شود (فهرستِ پیش‌فرض، جست‌وجو، افزودن با آدرس).
     *
     * برخلافِ [canReceiveOn] — که فقط تصمیم می‌گیرد یک ردیف نمایش داده شود یا نه و تا نیامدنِ
     * `/swap/chains` سخت‌گیری نمی‌کند — این‌جا فهرستِ صریحِ سرور لازم است. وگرنه پیش از رسیدنِ آن
     * پاسخ، درخواست روی **همهٔ** زنجیره‌های کاتالوگ پخش می‌شود، از جمله تست‌نت‌ها و زنجیره‌های
     * UTXO که هیچ‌وقت مقصدِ تبدیل نیستند.
     */
    private fun receiveNetworkIds(): List<String> {
        val state = _uiState.value
        if (!state.swapChainsLoaded) return emptyList()
        return networkCatalog.getAllNetworkInfos()
            .map { it.id }
            .filter { state.swappableNetworkIds.contains(it.lowercase(Locale.US)) }
    }

    /**
     * چرا این دارایی قابلِ تبدیل نیست — دو دلیلِ کاملاً متفاوت که نباید یک پیام بگیرند.
     */
    private fun payRejectionMessage(networkId: String): String = when {
        // مقصدبودن ممکن است، مبدأبودن نه — و کاربر باید همین را بشنود، نه «پشتیبانی نمی‌شود».
        !isEvm(networkId) ->
            "فعلاً نمی‌توانید از این شبکه تبدیل کنید؛ ولی می‌توانید آن را به‌عنوان مقصد انتخاب کنید."
        else -> "تبدیل روی این شبکه پشتیبانی نمی‌شود."
    }

    /**
     * پیامِ کاربرپسند بر مبنای **کدِ ماشینیِ** خطا، نه متنِ خامِ سرور.
     *
     * چند مورد معنیِ عملیاتیِ متفاوتی دارند که پیامِ عمومی پنهانشان می‌کند: ۴۲۲ بدونِ مسیر یعنی
     * «الان نقدینگی نیست، دوباره امتحان کن»، ولی ۴۲۲ِ شبیه‌سازی یعنی «امضا نکن».
     */
    private fun swapErrorMessage(throwable: Throwable?, fallback: String): String {
        val api = throwable as? ApiException ?: return userMessageFor(throwable!!, fallback)
        return when (api.apiError) {
            ApiError.ValidationError -> api.reasonFa ?: "ورودی این تبدیل معتبر نیست."
            ApiError.NetworkNotFound -> "این شبکه روی سرویس تبدیل شناخته نشد."
            ApiError.AssetNotFound -> "این توکن روی این شبکه قابل تبدیل نیست."
            ApiError.SwapChainUnsupported -> "تبدیل روی این شبکه پشتیبانی نمی‌شود."
            ApiError.SwapNoRoutes -> "الان مسیری برای این تبدیل پیدا نشد. کمی بعد دوباره امتحان کنید."
            ApiError.SimulationReverted ->
                "شبیه‌سازی این تبدیل روی زنجیره برگشت خورد؛ برای جلوگیری از سوختن کارمزد، امضا انجام نشد."
            ApiError.SwapTokensUnavailable -> "فهرست کامل ارزها موقتاً در دسترس نیست."
            ApiError.SwapUnavailable, ApiError.ServiceUnavailable -> "سرویس تبدیل موقتاً در دسترس نیست."
            else -> userMessageFor(throwable, fallback)
        }
    }

    /**
     * متنِ زیرِ نتیجه برای پل. `null` برای تبدیلِ درون‌زنجیره‌ای که واقعاً همان‌جا تمام شده است.
     */
    private fun SwapUiState.bridgePendingMessage(): String? {
        if (!isBridge) return null
        val destination = destinationNetworkName ?: "شبکهٔ مقصد"
        return "تراکنش روی شبکهٔ مبدأ ثبت شد. دارایی شما تا چند دقیقهٔ دیگر روی $destination می‌نشیند."
    }

    private fun AssetItem.matchesQuery(query: String): Boolean =
        symbol.contains(query, ignoreCase = true) ||
            name.contains(query, ignoreCase = true) ||
            faName?.contains(query, ignoreCase = true) == true

    private fun AssetConfig.matchesQuery(query: String): Boolean =
        symbol.contains(query, ignoreCase = true) ||
            name.contains(query, ignoreCase = true) ||
            faName?.contains(query, ignoreCase = true) == true

    private fun AssetItem.toReceiveCandidate(): SwapReceiveCandidate {
        val network = networkCatalog.getNetworkInfoById(networkId)
        return SwapReceiveCandidate(
            optionId = id,
            networkId = networkId,
            networkName = networkFaName ?: networkName,
            networkFaName = networkFaName,
            networkIconUrl = networkIconUrl ?: network?.iconUrl,
            symbol = symbol,
            name = name,
            faName = faName,
            iconUrl = iconUrl,
            decimals = decimals,
            contractAddress = contractAddress,
            // دارایی‌های کاربر از باندل می‌آیند مگر خودش اضافه کرده باشد.
            isSponsorable = !isUserAdded,
            held = this
        )
    }

    private fun AssetConfig.toCandidate(): SwapReceiveCandidate {
        val network = networkCatalog.getNetworkInfoById(networkId)
        return SwapReceiveCandidate(
            // شناسهٔ کاتالوگ (`ETH-ETHEREUM`) مرجعِ سمتِ سرور نیست. `swapTokenRef` وقتی آدرسِ
            // قرارداد نداریم همین را می‌فرستد، پس باید نماد باشد — قرارداد صراحتاً می‌گوید
            // داراییِ کیوریت‌شده را می‌شود با نماد فرستاد.
            optionId = contractAddress ?: symbol,
            networkId = networkId,
            networkName = network?.faName ?: network?.name?.name ?: networkId,
            networkFaName = network?.faName,
            networkIconUrl = network?.iconUrl,
            symbol = symbol,
            name = name,
            faName = faName,
            iconUrl = iconUrl,
            decimals = decimals,
            contractAddress = contractAddress,
            isSponsorable = true,
            held = heldAssets.firstOrNull { it.id == id }
        )
    }

    private fun SwapToken.toCandidate(): SwapReceiveCandidate {
        val network = networkCatalog.getNetworkInfoById(networkId)
        return SwapReceiveCandidate(
            optionId = contractAddress ?: symbol,
            networkId = networkId,
            networkName = network?.faName ?: network?.name?.name ?: networkId,
            networkFaName = network?.faName,
            networkIconUrl = network?.iconUrl,
            symbol = symbol,
            name = name,
            faName = null,
            iconUrl = iconUrl,
            decimals = decimals,
            contractAddress = contractAddress,
            isSponsorable = isSponsorable,
            verified = verified,
            held = heldAssets.firstOrNull {
                it.networkId.equals(networkId, ignoreCase = true) &&
                    it.contractAddress?.equals(contractAddress, ignoreCase = true) == true
            }
        )
    }

    private fun SwapReceiveCandidate.toOption() = SwapTokenOption(
        id = optionId,
        networkId = networkId,
        networkName = networkName,
        symbol = symbol,
        name = name,
        faName = faName,
        iconUrl = iconUrl,
        networkIconUrl = networkIconUrl,
        decimals = decimals,
        contractAddress = contractAddress,
        isSponsorable = isSponsorable,
        verified = verified
    )

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

    /**
     * `null` یعنی «استعلام نگیر». آدرس اجباری است: سرور مسیر را برای همان کیف‌پول می‌سازد و
     * `tx.data`ی برگشتی به همان آدرس پرداخت می‌کند، پس فرستادنِ یک آدرسِ جای‌گزین یعنی ساختنِ
     * تراکنشی که پولِ کاربر را به جای دیگری می‌ریزد.
     *
     * منبعِ آدرس عمداً `wallet.keys` نیست: آن فهرست عکسِ لحظهٔ ساختِ کیف‌پول است و برای شبکه‌ای که
     * بعداً از باندل آمده خالی است — دقیقاً همان شبکه‌های mainnetی که سوآپ روی‌شان کار می‌کند.
     */
    private suspend fun SwapUiState.toQuoteRequest(): SwapQuoteRequest? {
        val pay = payToken?.option ?: return null
        val receive = receiveToken ?: return null
        val amount = amountRaw.takeIf { it.signum() > 0 } ?: return null
        val address = getActiveAddressForNetworkUseCase(pay.networkId)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val destination = usableDestinationAddress
        // جفتِ بین‌خانوادگی بدونِ مقصدِ معتبر اصلاً استعلام نمی‌شود.
        if (requiresDestinationAddress && destination == null) return null

        return SwapQuoteRequest(
            fromNetwork = pay.networkId,
            toNetwork = receive.networkId,
            fromToken = swapTokenRef(pay.contractAddress,pay.id),
            toToken = swapTokenRef(receive.contractAddress,receive.id),
            amountRaw = amount,
            userAddress = address,
            // فقط وقتی فرستاده می‌شود که واقعاً معنایی داشته باشد: یا اجباری است، یا کاربر مقصدی
            // غیر از آدرسِ خودش انتخاب کرده. فرستادنِ همان آدرسِ فرستنده چیزی اضافه نمی‌کند و
            // کشِ ۱۵ ثانیه‌ایِ سرور را بی‌دلیل به ازای هر مقصد تکه‌تکه می‌کند.
            toAddress = destination?.takeIf {
                requiresDestinationAddress || !sameSwapAddress(it, address)
            },
            // قرارداد، واحدِ این فیلد را مشخص نکرده؛ درصد فرستاده می‌شود (۵۰bps ⇒ ۰٫۵).
            slippage = slippageBps.toDouble() / 100.0
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
            contractAddress = contractAddress,
            isSponsorable = !isUserAdded
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

        /** آینهٔ سمتِ سرور است و تماسِ upstream ندارد؛ این debounce برای باتری است، نه سرور. */
        private const val TOKEN_SEARCH_DEBOUNCE_MS = 200L

        /**
         * جست‌وجو روی چند شبکه هم‌زمان پخش می‌شود، پس سهمِ هر شبکه کوچک نگه داشته می‌شود.
         * سقفِ سرور ۲۰۰ است.
         */
        private const val SEARCH_LIMIT_PER_NETWORK = 20

        /** پیش‌فرضِ قرارداد. سقفِ سرور ۲۰۰ است و ریپازیتوری آن را محدود می‌کند. */
        private const val DEFAULT_LIST_LIMIT_PER_NETWORK = 50

        /** فهرستِ رتبه‌بندی‌شده در بازهٔ یک نشست عوض نمی‌شود؛ هر بار بازکردنِ شیت درخواست نمی‌دهد. */
        private const val DEFAULT_LIST_TTL_MS = 10 * 60 * 1000L

        private const val NO_ADDRESS_MESSAGE =
            "آدرس کیف پول روی این شبکه در دسترس نیست؛ تبدیل انجام نمی‌شود."

        private const val DESTINATION_REQUIRED_MESSAGE =
            "قالب آدرس این دو شبکه فرق دارد؛ آدرس مقصد را وارد کنید."

        /** همان کفی که مسیرِ approveِ گس‌لس استفاده می‌کند. */
        private val MIN_APPROVE_GAS_LIMIT = BigInteger.valueOf(120_000L)
        private val MIN_SWAP_GAS_LIMIT = BigInteger.valueOf(250_000L)
        private val DEFAULT_SWAP_GAS_LIMIT = BigInteger.valueOf(500_000L)
        private val GAS_LIMIT_BUFFER_NUMERATOR = BigInteger.valueOf(12L)
        private val GAS_LIMIT_BUFFER_DENOMINATOR = BigInteger.TEN
    }
}
