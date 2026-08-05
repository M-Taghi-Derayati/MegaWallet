package com.mtd.megawallet.viewmodel

import androidx.lifecycle.viewModelScope
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.FiatConversion
import com.mtd.core.utils.withFiatBalances
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IFeatureAvailabilityResolver
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IUnifiedTransferCoordinator
import com.mtd.domain.interfaceRepository.IFiatCurrencyProvider
import com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.FiatCurrency
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.EvmSponsorMode
import com.mtd.domain.model.FeeOption
import com.mtd.domain.model.GaslessDisplayPolicy
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.PendingTransactionHint
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransferMode
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronSponsorMode
import com.mtd.domain.model.UnifiedGaslessSession
import com.mtd.domain.model.UnifiedTransferRequest
import com.mtd.domain.model.capability.FeatureAvailabilityContext
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import com.mtd.domain.model.error.AppError
import com.mtd.domain.model.error.ErrorMapper
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.model.FeeState
import com.mtd.domain.model.FeeTrend
import com.mtd.domain.model.GaslessAvailability
import com.mtd.domain.model.GaslessPreviewState
import com.mtd.domain.model.SubmitState
import com.mtd.domain.usecase.asset.GetLatestAssetPricesUseCase
import com.mtd.domain.usecase.network.GetNetworkTypeForAddressUseCase
import com.mtd.domain.usecase.network.ValidateAddressForNetworkUseCase
import com.mtd.domain.usecase.send.EstimateSendFeesUseCase
import com.mtd.domain.usecase.send.RefreshSelectedAssetBalanceUseCase
import com.mtd.domain.usecase.wallet.ExpandWalletKeysToNetworksUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class SendViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val unifiedTransferCoordinator: IUnifiedTransferCoordinator,
    // Capability Platform (Phase B) — advisory gasless visibility decision only.
    // Used solely inside refreshGaslessAvailability behind USE_CAPABILITY_RESOLVER;
    // touches no transport/execution path.
    private val featureAvailabilityResolver: IFeatureAvailabilityResolver,
    private val appEventBus: IAppEventBus,
    private val getLatestAssetPricesUseCase: GetLatestAssetPricesUseCase,
    /** TASK-54 — shared observable Toman rate; replaces the one-shot fetch + hardcoded fallback. */
    private val usdToIrrRateProvider: IUsdToIrrRateProvider,
    private val fiatCurrencyProvider: IFiatCurrencyProvider,
    private val refreshSelectedAssetBalanceUseCase: RefreshSelectedAssetBalanceUseCase,
    private val estimateSendFeesUseCase: EstimateSendFeesUseCase,
    private val observeActiveWalletUseCase: ObserveActiveWalletUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
    private val expandWalletKeysToNetworksUseCase: ExpandWalletKeysToNetworksUseCase,
    private val getNetworkTypeForAddressUseCase: GetNetworkTypeForAddressUseCase,
    private val validateAddressForNetworkUseCase: ValidateAddressForNetworkUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    companion object {
        private const val DEFAULT_GASLESS_DEADLINE_SECONDS = 20 * 60L
        private const val GASLESS_APPROVE_POLL_INTERVAL_MS = 4_000L
        private const val GASLESS_APPROVE_TIMEOUT_MS = 2 * 60_000L
        private const val GASLESS_FINAL_TIMEOUT_MS = 5 * 60_000L
        private const val DEFAULT_EVM_PERMIT2_ADDRESS = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        private val MIN_EVM_APPROVE_GAS_LIMIT = BigInteger.valueOf(120_000L)
        private const val APPROVE_FEE_BUFFER_NUMERATOR = 12L
        private const val APPROVE_FEE_BUFFER_DENOMINATOR = 10L

        // Capability Platform (Phase B) feature flag. OFF = the exact legacy gasless
        // visibility path (byte-identical behavior). Flip to true to route the final
        // gasless availability decision through FeatureAvailabilityResolver (capability
        // + token). Kept a compile-time const so the default ships the old path; can be
        // promoted to BuildConfig / remote config for a staged rollout.
        private const val USE_CAPABILITY_RESOLVER = false
    }

    private val _feeState = MutableStateFlow<FeeState>(FeeState.Idle)
    val feeState = _feeState.asStateFlow()

    private val _isSubtractionMode = MutableStateFlow(false)
    val isSubtractionMode = _isSubtractionMode.asStateFlow()


    
    private val _feeTrend = MutableStateFlow(FeeTrend.NONE)
    val feeTrend = _feeTrend.asStateFlow()
    
    private var previousFeeCost: BigDecimal? = null
    private val feeCoinUsdPriceCache = mutableMapOf<String, BigDecimal>()

    private val _recipientAddress = MutableStateFlow("")
    val recipientAddress = _recipientAddress.asStateFlow()

    private val _recipientNetworkType = MutableStateFlow<NetworkType?>(null)
    val recipientNetworkType = _recipientNetworkType.asStateFlow()

    private val _amountText = MutableStateFlow("0")
    val amountText = _amountText.asStateFlow()

    /**
     * TASK-56 — was `isUsdMode`. The amount box toggles between the asset's own unit and **fiat**;
     * which fiat is [fiatCurrency], not always USD. Renamed so no call site can keep assuming the
     * fiat side is dollars.
     */
    private val _isFiatMode = MutableStateFlow(false)
    val isFiatMode = _isFiatMode.asStateFlow()

    /**
     * TASK-56 — set by [useMax], cleared by any manual edit.
     *
     * MAX used to be expressed only as text: the screen wrote the *formatted* balance into the amount
     * box and the send path parsed it back. In fiat mode that round-trip went through a 2-decimal USD
     * string, so the recovered crypto amount was never exactly the balance — which also made the
     * downstream `baseCrypto >= balanceRaw` max-detection fail, so a native MAX skipped the
     * fee subtraction and produced an unsendable transaction. With تومان (0 decimals) the same
     * round-trip is coarser still. The flag keeps the *displayed* amount pretty while the amount that
     * is actually sent stays exact.
     */
    private val _isMaxAmount = MutableStateFlow(false)
    val isMaxAmount = _isMaxAmount.asStateFlow()

    private val _selectedAsset = MutableStateFlow<AssetItem?>(null)
    val selectedAsset = _selectedAsset.asStateFlow()

    private val _gaslessAvailability =
        MutableStateFlow<GaslessAvailability>(GaslessAvailability.Unavailable())
    val gaslessAvailability = _gaslessAvailability.asStateFlow()

    private val _showConfirmScreen = MutableStateFlow(false)
    val showConfirmScreen = _showConfirmScreen.asStateFlow()

    private val _gaslessPreviewState = MutableStateFlow<GaslessPreviewState>(GaslessPreviewState.Idle)
    val gaslessPreviewState = _gaslessPreviewState.asStateFlow()

    // کش کوتاه‌مدتِ پیش‌نمایش گس‌لس: اگر کاربر بین SMART/DIRECT سوییچ کند و دوباره روی گس‌لس بزند،
    // تا وقتی دارایی/مقصد/مبلغ عوض نشده و از این پنجره نگذشته، سرویس‌های گس‌لس دوباره کال نمی‌شوند.
    private val gaslessPreviewTtlMs = 30_000L
    private var lastGaslessPreviewKey: String? = null
    private var lastGaslessPreviewAt: Long = 0L

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState = _submitState.asStateFlow()

    val activeWalletName = observeActiveWalletUseCase()
        .map { it?.name ?: "کیف پول من" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "کیف پول من")

    /**
     * TASK-54 — نرخ تتر به تومان از منبع مشترک.
     *
     * Was `private var currentIrrRate = BigDecimal("70000")`, fetched once in `init` with the failure
     * swallowed by `else -> {}`. That hardcoded fallback silently priced every Toman amount — and the
     * MAX-send math — off a stale magic number whenever the fetch failed, and the value never updated
     * afterwards. Now it tracks the shared provider, and "unknown" is ZERO rather than a fabricated rate.
     */
    val usdToIrrRate: StateFlow<CurrencyRate?> = usdToIrrRateProvider.rate

    private val currentRate: CurrencyRate?
        get() = usdToIrrRateProvider.rate.value

    /** TASK-56 — the shared selected currency; the amount box and every fiat line here follow it. */
    val fiatCurrency: StateFlow<FiatCurrency> = fiatCurrencyProvider.currency

    init {
        launchSafe(connectivitySurface = ErrorSurface.SILENT) { usdToIrrRateProvider.refresh() }
        launchSafe(checkNetwork = false) { fiatCurrencyProvider.ensurePrimed() }
    }

    /** TASK-56 — same formatting the wallet list uses, so the two screens cannot show different text. */
    private fun withFiatStrings(item: AssetItem): AssetItem =
        item.withFiatBalances(fiatCurrencyProvider.currency.value, currentRate)

    fun setSubtractionMode(enabled: Boolean) {
        _isSubtractionMode.value = enabled
    }

    fun setRecipient(address: String) {
        val normalized = address.trim()
        _recipientAddress.value = normalized
        _recipientNetworkType.value = getNetworkTypeForAddressUseCase(normalized)
    }

    fun setAmount(amount: String) {
        _amountText.value = amount
        // Any manual edit means this is no longer "the whole balance".
        _isMaxAmount.value = false
    }

    /**
     * TASK-56 — MAX in whichever unit the box is currently in.
     *
     * The text written here is for the user to read; [getBaseCryptoAmount] ignores it while
     * [isMaxAmount] holds and returns [AssetItem.balanceRaw] exactly. In تومان with an unknown rate
     * there is no honest number to show, so the box is left alone rather than filled with a zero.
     */
    fun useMax(asset: AssetItem) {
        val text = if (_isFiatMode.value) {
            val usd = asset.balanceRaw.multiply(asset.priceUsdRaw)
            // Rounded DOWN, in both currencies: a half-up MAX can land a hair ABOVE the real balance,
            // which the amount box would then correctly flag as insufficient funds.
            when (fiatCurrencyProvider.currency.value) {
                FiatCurrency.USD -> usd.setScale(2, RoundingMode.DOWN).toPlainString()
                FiatCurrency.TOMAN -> FiatConversion.usdToToman(usd, currentRate)
                    ?.setScale(FiatConversion.TOMAN_DISPLAY_SCALE, RoundingMode.DOWN)
                    ?.toPlainString()
                    ?: return
            }
        } else {
            BalanceFormatter.formatBalance(asset.balanceRaw, asset.decimals).replace(",", "")
        }
        _amountText.value = text
        _isMaxAmount.value = true
    }

    fun toggleFiatMode() {
        _isFiatMode.value = !_isFiatMode.value
        // The amount box now holds a number in the other unit; MAX no longer describes it.
        _isMaxAmount.value = false
    }

    fun setSelectedAsset(asset: AssetItem?) {
        _selectedAsset.value = asset
        if (asset != null) {
            updateBalanceForAsset(asset)
            refreshGaslessAvailability(asset)
            _gaslessPreviewState.value = GaslessPreviewState.Idle
        } else {
            _gaslessAvailability.value = GaslessAvailability.Unavailable()
            _gaslessPreviewState.value = GaslessPreviewState.Idle
        }
    }

    private fun refreshGaslessAvailability(asset: AssetItem) {
        val network = networkCatalog.getNetworkInfoById(asset.networkId)
        val tokenAddress = asset.contractAddress

        if (network == null) {
            _gaslessAvailability.value = GaslessAvailability.Unavailable("شبکه این دارایی یافت نشد")
            return
        }

        if (network.networkType != NetworkType.EVM && network.networkType != NetworkType.TVM) {
            _gaslessAvailability.value = GaslessAvailability.Unavailable()
            return
        }

        if (asset.isNativeToken || tokenAddress.isNullOrBlank()) {
            _gaslessAvailability.value = GaslessAvailability.Unavailable("گس‌ لس فقط برای توکن ‌های قراردادی فعال است")
            return
        }

        // توکنی که کاربر خودش اضافه کرده هرگز به مسیرِ gasless نمی‌رود. مسیریابیِ gasless در سطحِ
        // **شبکه** تصمیم می‌گیرد (GaslessRouteResolver)، پس بدونِ این گارد یک ERC-20ِ دلخواه روی یک
        // شبکهٔ gasless-دار وارد جریان می‌شد و تازه سرور ردش می‌کرد — بعد از اینکه کاربر آن را
        // انتخاب کرده بود. فهرستِ gasless جداگانه و curated است.
        if (asset.isUserAdded) {
            _gaslessAvailability.value =
                GaslessAvailability.Unavailable("گس‌لس فقط برای توکن‌های فهرستِ رسمی فعال است")
            return
        }

        viewModelScope.launch {
            _gaslessAvailability.value = GaslessAvailability.Loading
            when (val result = unifiedTransferCoordinator.getSupportedGaslessTokens(asset.networkId)) {
                is ResultResponse.Success -> {
                    val matched = result.data.firstOrNull { supported ->
                        addressesMatchForGasless(network.networkType, supported.token, tokenAddress)
                    }
                    _gaslessAvailability.value = if (USE_CAPABILITY_RESOLVER) {
                        // NEW path: combine capability (backend-authoritative) with the SAME
                        // token signal already fetched above. Fail-safe inside the resolver →
                        // when capability is offline it reproduces the legacy decision below.
                        val ctx = FeatureAvailabilityContext(
                            networkId = asset.networkId,
                            networkType = network.networkType,
                            tokenId = tokenAddress,
                            isContractToken = true, // past the native/contract guard above
                            tokenGaslessEnabled = matched?.gaslessEnabled ?: false, // no match → not enabled
                            tokenNote = matched?.note
                        )
                        val decision = featureAvailabilityResolver.isGaslessAvailable(ctx)
                        if (decision.available) {
                            GaslessAvailability.Available(note = decision.note)
                        } else {
                            GaslessAvailability.Unavailable(
                                decision.note ?: matched?.note ?: "این توکن فعلاً برای سرویس گس‌لس فعال نیست"
                            )
                        }
                    } else {
                        // LEGACY path (default): unchanged behavior.
                        when {
                            matched == null -> GaslessAvailability.Unavailable("این توکن فعلاً برای سرویس گس‌لس فعال نیست")
                            matched.gaslessEnabled -> GaslessAvailability.Available(note = matched.note)
                            else -> GaslessAvailability.Unavailable(
                                matched.note ?: "این توکن فعلاً برای سرویس گس ‌لس فعال نیست"
                            )
                        }
                    }
                }

                is ResultResponse.Error -> {
                    // Shown inline on the fee tab; gasless simply stays disabled, so log only.
                    _gaslessAvailability.value = GaslessAvailability.Unavailable(
                        userMessageFor(result.exception, "امکان بررسی وضعیت گس لس وجود ندارد")
                    )
                    reportError(
                        throwable = result.exception,
                        userAction = "checkGaslessAvailability",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }
        }
    }

    private fun updateBalanceForAsset(asset: AssetItem) {
        val wallet = getActiveWalletUseCase() ?: return

        viewModelScope.launch {
            when (val result = refreshSelectedAssetBalanceUseCase(wallet, asset)) {
                // TASK-56 — the data source returns the balance only; the fiat strings are applied here,
                // by the same rules the wallet list uses, so the two screens cannot drift apart.
                is ResultResponse.Success -> result.data?.let { _selectedAsset.value = withFiatStrings(it) }
                // Background balance top-up; the cached figure stays on screen (ErrorSurface.SILENT).
                is ResultResponse.Error -> reportError(
                    throwable = result.exception,
                    userAction = "updateBalanceForAsset",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
            }
        }
    }

    fun clearState() {
        _recipientAddress.value = ""
        _recipientNetworkType.value = null
        _amountText.value = "0"
        _isFiatMode.value = false
        _isMaxAmount.value = false
        _isSubtractionMode.value = false
        _selectedAsset.value = null
        _gaslessAvailability.value = GaslessAvailability.Unavailable()
        _gaslessPreviewState.value = GaslessPreviewState.Idle
        _showConfirmScreen.value = false
        _feeState.value = FeeState.Idle
        _feeTrend.value = FeeTrend.NONE
        previousFeeCost = null
        stopFeePolling()
        _submitState.value = SubmitState.Idle
    }

    private var feePollingJob: Job? = null

    fun startFeePolling() {
        feePollingJob?.cancel()
        feePollingJob = viewModelScope.launch {
            while (isActive) {
                val asset = _selectedAsset.value
                val network = asset?.networkId?.let { networkCatalog.getNetworkInfoById(it) }
                val pollingInterval = when (network?.networkType) {
                    NetworkType.SOLANA -> 20_000L
                    NetworkType.XRP -> 20_000L
                    NetworkType.TVM, NetworkType.TON -> 60_000L
                    NetworkType.EVM -> 20_000L
                    NetworkType.BITCOIN-> 60_000L
                    NetworkType.UTXO -> 1800_000L
                    else -> 20_000L
                }
                
                delay(pollingInterval)
                
                val recipient = _recipientAddress.value
                if (asset != null && recipient.isNotBlank()) {
                    estimateFees(asset, recipient, silent = true)
                }
            }
        }
    }

    fun stopFeePolling() {
        feePollingJob?.cancel()
        feePollingJob = null
    }

    fun setShowConfirmScreen(show: Boolean) {
        _showConfirmScreen.value = show
        if (show) {
            previousFeeCost = null // reset tracker on new load
            _feeTrend.value = FeeTrend.NONE
            _gaslessPreviewState.value = GaslessPreviewState.Idle
            val asset = _selectedAsset.value
            val recipient = _recipientAddress.value
            if (asset != null && recipient.isNotBlank()) {
                estimateFees(asset, recipient, silent = false)
                startFeePolling()
                if (isGaslessEnabled()) {
                    loadGaslessPreview(asset, recipient)
                }
            }
        } else {
            stopFeePolling()
            _gaslessPreviewState.value = GaslessPreviewState.Idle
            _submitState.value = SubmitState.Idle
        }
    }

    fun refreshGaslessPreviewIfNeeded() {
        val asset = _selectedAsset.value ?: return
        val recipient = _recipientAddress.value.trim()
        if (recipient.isBlank() || !isGaslessEnabled()) return
        if (_gaslessPreviewState.value is GaslessPreviewState.Loading) return
        loadGaslessPreview(asset, recipient)
    }

    
    fun resetSubmitState() {
        _submitState.value = SubmitState.Idle
    }

    fun isGaslessEnabled(): Boolean {
        return _gaslessAvailability.value is GaslessAvailability.Available
    }

    fun submitTransfer(useGasless: Boolean, selectedFee: FeeOption?, isMax: Boolean) {
        val asset = _selectedAsset.value ?: run {
            _submitState.value = SubmitState.Error("دارایی انتخاب نشده است")
            return
        }
        val recipient = _recipientAddress.value.trim()
        if (recipient.isBlank()) {
            _submitState.value = SubmitState.Error("آدرس مقصد خالی است")
            return
        }

        val network = networkCatalog.getNetworkInfoById(asset.networkId)
        if (network == null) {
            _submitState.value = SubmitState.Error(" شبکه ${asset.networkId} یافت نشد ")
            return
        }

        viewModelScope.launch {
            try {
                _submitState.value = SubmitState.Submitting

                val baseCrypto = getBaseCryptoAmount(asset, _amountText.value, _isFiatMode.value)
                val feeCoin = selectedFee?.feeInCoin ?: BigDecimal.ZERO
                
                // Safe detection of Max: if UI says so OR if the amount is >= balance
                val actuallyIsMax = isMax || baseCrypto >= asset.balanceRaw
                
                val effectiveCrypto = if (asset.isNativeToken && actuallyIsMax) {
                    asset.balanceRaw.subtract(feeCoin).coerceAtLeast(BigDecimal.ZERO)
                } else {
                    baseCrypto.coerceAtMost(asset.balanceRaw)
                }

                if (effectiveCrypto <= BigDecimal.ZERO) {
                    sendFailure("مقدار ارسال معتبر نیست")
                }

                val amountSmallest = toSmallestUnit(effectiveCrypto, asset.decimals)
                if (amountSmallest <= BigInteger.ZERO) {
                    sendFailure("مقدار ارسال خیلی کوچک است")
                }

                if (useGasless) {
                    if (!isGaslessEnabled()) {
                        sendFailure("ارسال گس لس برای این دارایی فعال نیست")
                    }
                    if (!validateAddressForNetworkUseCase(recipient, asset.networkId)) {
                        sendFailure("آدرس مقصد برای این شبکه معتبر نیست")
                    }

                    val txHash = submitGaslessTransfer(
                        asset = asset,
                        recipient = recipient,
                        amountSmallest = amountSmallest,
                        selectedFee = selectedFee
                    )
                    notifyTransferRegistered(
                        asset = asset,
                        transactionId = txHash,
                        recipient = recipient,
                        amountSmallest = amountSmallest
                    )
                    _submitState.value = SubmitState.Success(txHash)
                    return@launch
                }

                val request = when (network.networkType) {
                    NetworkType.EVM -> {
                        val gasPrice = selectedFee?.gasPrice
                            ?: sendFailure("گس پرایس دریافت نشد")
                        val gasLimit = selectedFee.gasLimit
                            ?: sendFailure("گس لیمیت دریافت نشد")

                        UnifiedTransferRequest(
                            networkId = asset.networkId,
                            assetId = asset.id,
                            mode = TransferMode.NORMAL,
                            toAddress = recipient,
                            amount = amountSmallest,
                            tokenAddress = asset.contractAddress,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit,
                            // Forward the already-selected tier so PROXY /prepare prices it (DIRECT ignores).
                            feeLevel = selectedFee.level
                        )
                    }

                    NetworkType.TVM -> {
                        UnifiedTransferRequest(
                            networkId = asset.networkId,
                            assetId = asset.id,
                            mode = TransferMode.NORMAL,
                            toAddress = recipient,
                            amount = amountSmallest,
                            tokenAddress = asset.contractAddress,
                            feeLimit = deriveFeeLimit(selectedFee, asset.contractAddress != null),
                            // Forward the already-selected tier so PROXY /prepare prices it (DIRECT ignores).
                            feeLevel = selectedFee?.level
                        )
                    }

                    NetworkType.BITCOIN,
                    NetworkType.UTXO -> {
                        UnifiedTransferRequest(
                            networkId = asset.networkId,
                            assetId = asset.id,
                            mode = TransferMode.NORMAL,
                            toAddress = recipient,
                            amount = amountSmallest,
                            utxoFeeRateInSatsPerByte = selectedFee?.feeRateInSatsPerByte
                        )
                    }

                    else -> sendFailure("ارسال برای این شبکه پشتیبانی نشده است")
                }

                when (val result = unifiedTransferCoordinator.sendNormal(request)) {
                    is ResultResponse.Success -> {
                        notifyTransferRegistered(
                            asset = asset,
                            transactionId = result.data,
                            recipient = recipient,
                            amountSmallest = amountSmallest
                        )
                        _submitState.value = SubmitState.Success(result.data)
                    }

                    is ResultResponse.Error -> {
                        throw result.exception
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // TASK-57 — a failed send is the one case that must always block: the user is left
                // not knowing whether their funds moved. The inline slider state carries the same
                // curated message; the technical detail lives in the dialog.
                _submitState.value = SubmitState.Error(
                    userMessageFor(e, "ارسال تراکنش ناموفق بود")
                )
                reportError(
                    throwable = e,
                    userAction = "submitTransfer",
                    surface = ErrorSurface.BLOCKING,
                    severity = ErrorSeverity.HIGH,
                    title = "ارسال ناموفق",
                    fallbackMessage = "ارسال تراکنش ناموفق بود"
                )
            }
        }
    }

    private suspend fun submitGaslessTransfer(
        asset: AssetItem,
        recipient: String,
        amountSmallest: BigInteger,
        selectedFee: FeeOption?
    ): String {
        ensureGaslessEligible(asset)

        val request = buildGaslessRequest(
            asset = asset,
            recipient = recipient,
            amountSmallest = amountSmallest
        )

        var session = unifiedTransferCoordinator.prepareGasless(request)
            .requireSuccess("آماده سازی مسیر گس لس ناموفق بود")

        if (session.needsApprove()) {
            attemptSponsorApprove(session)
            sendManualApprove(session, selectedFee)
            session = waitForApprovedGaslessSession(request)
        }

        return submitGaslessWithRetry(request, session)
    }

    private fun loadGaslessPreview(asset: AssetItem, recipient: String) {
        if (!isGaslessEnabled()) {
            _gaslessPreviewState.value = GaslessPreviewState.Idle
            return
        }

        val amountSmallest = runCatching {
            val amount = getBaseCryptoAmount(asset, _amountText.value, _isFiatMode.value)
                .coerceAtMost(asset.balanceRaw)
            toSmallestUnit(amount, asset.decimals)
        }.getOrDefault(BigInteger.ZERO)

        if (amountSmallest <= BigInteger.ZERO) {
            _gaslessPreviewState.value = GaslessPreviewState.Idle
            return
        }

        // اگر پیش‌نمایشِ آماده برای همین ورودی‌ها به‌تازگی گرفته شده، دوباره سرویس نزن.
        val cacheKey = "${asset.networkId}|${asset.contractAddress.orEmpty()}|$recipient|$amountSmallest"
        if (_gaslessPreviewState.value is GaslessPreviewState.Ready &&
            cacheKey == lastGaslessPreviewKey &&
            System.currentTimeMillis() - lastGaslessPreviewAt < gaslessPreviewTtlMs
        ) {
            return
        }

        val request = runCatching {
            buildGaslessRequest(
                asset = asset,
                recipient = recipient,
                amountSmallest = amountSmallest
            )
        }.getOrElse { error ->
            // Preview only — the user can still send with a normal fee, so the message lives
            // inline on the fee tab and the failure is logged rather than surfaced.
            _gaslessPreviewState.value = GaslessPreviewState.Error(
                userMessageFor(error, "امکان محاسبه هزینه گس لس وجود ندارد")
            )
            reportErrorAsync(
                throwable = error,
                userAction = "buildGaslessRequest",
                surface = ErrorSurface.SILENT,
                severity = ErrorSeverity.LOW
            )
            return
        }

        viewModelScope.launch {
            _gaslessPreviewState.value = GaslessPreviewState.Loading

            when (
                val eligibility = unifiedTransferCoordinator.checkGaslessEligibility(
                    networkId = asset.networkId,
                    tokenAddress = asset.contractAddress.orEmpty(),
                    service = GaslessServiceType.GASLESS
                )
            ) {
                is ResultResponse.Success -> {
                    if (!eligibility.data.allowed) {
                        _gaslessPreviewState.value = GaslessPreviewState.Error(
                            eligibility.data.bestReasonFa
                                ?: "این تراکنش فعلاً برای گس‌لس مجاز نیست"
                        )
                        return@launch
                    }
                }

                is ResultResponse.Error -> {
                    _gaslessPreviewState.value = GaslessPreviewState.Error(
                        userMessageFor(eligibility.exception, "بررسی مجوز گس‌لس ناموفق بود")
                    )
                    reportError(
                        throwable = eligibility.exception,
                        userAction = "checkGaslessEligibility",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                    return@launch
                }
            }

            when (val preview = unifiedTransferCoordinator.previewGaslessDisplayPolicy(request)) {
                is ResultResponse.Success -> {
                    val quote = preview.data
                    val gaslessPolicy = quote.displayPolicy?.gasless
                    if (gaslessPolicy == null) {
                        _gaslessPreviewState.value = GaslessPreviewState.Error(
                            quote.smartFee?.reasonFa
                                ?: "جزئیات هزینه گس‌لس از سرور دریافت نشد"
                        )
                        return@launch
                    }
                    _gaslessPreviewState.value = GaslessPreviewState.Ready(
                        gaslessPolicy = gaslessPolicy,
                        sponsorPolicy = quote.displayPolicy?.sponsorApprove,
                        needsApprove = quote.needsApprove,
                        smartFee = quote.smartFee
                    )
                    lastGaslessPreviewKey = cacheKey
                    lastGaslessPreviewAt = System.currentTimeMillis()
                }

                is ResultResponse.Error -> {
                    _gaslessPreviewState.value = GaslessPreviewState.Error(
                        userMessageFor(preview.exception, "امکان دریافت هزینه گس‌لس وجود ندارد")
                    )
                    reportError(
                        throwable = preview.exception,
                        userAction = "previewGaslessDisplayPolicy",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }
        }
    }

    private fun buildGaslessRequest(
        asset: AssetItem,
        recipient: String,
        amountSmallest: BigInteger
    ): UnifiedTransferRequest {
        val network = networkCatalog.getNetworkInfoById(asset.networkId)
            ?: sendFailure("شبکه ${asset.networkId} یافت نشد")
        val tokenAddress = asset.contractAddress
            ?: sendFailure("برای گس‌لس، آدرس قرارداد توکن الزامی است")

        val deadline = (System.currentTimeMillis() / 1000L) + DEFAULT_GASLESS_DEADLINE_SECONDS

        return when (network.networkType) {
            NetworkType.EVM -> UnifiedTransferRequest(
                networkId = asset.networkId,
                assetId = asset.id,
                mode = TransferMode.GASLESS,
                toAddress = recipient,
                amount = amountSmallest,
                tokenAddress = tokenAddress,
                permit2Address = DEFAULT_EVM_PERMIT2_ADDRESS,
                deadlineEpochSeconds = deadline
            )

            NetworkType.TVM -> UnifiedTransferRequest(
                networkId = asset.networkId,
                assetId = asset.id,
                mode = TransferMode.GASLESS,
                toAddress = recipient,
                amount = amountSmallest,
                tokenAddress = tokenAddress,
                deadlineEpochSeconds = deadline
            )

            else -> sendFailure("گس‌لس برای این شبکه پشتیبانی نشده است")
        }
    }

    private suspend fun attemptSponsorApprove(session: UnifiedGaslessSession) {
        val sponsorAllowed = checkSponsorEligibility(session)
        if (!sponsorAllowed) return

        when (session) {
            is UnifiedGaslessSession.Evm -> {
                when (val result =
                    unifiedTransferCoordinator.requestEvmSponsorForApprove(
                        session = session,
                        mode = EvmSponsorMode.GIFT
                    )
                ) {
                    is ResultResponse.Success -> {
                        updateSponsorPreviewPolicy(result.data.sponsorDisplayPolicy)
                    }
                    // Optional preview enrichment — the send still works without it.
                    is ResultResponse.Error -> reportError(
                        throwable = result.exception,
                        userAction = "requestSponsorForApprove",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }

            is UnifiedGaslessSession.Tron -> {
                when (val result =
                    unifiedTransferCoordinator.requestTronSponsorForApprove(
                        session = session,
                        mode = TronSponsorMode.GIFT
                    )
                ) {
                    is ResultResponse.Success -> {
                        updateSponsorPreviewPolicy(result.data.sponsorDisplayPolicy)
                    }
                    // Optional preview enrichment — the send still works without it.
                    is ResultResponse.Error -> reportError(
                        throwable = result.exception,
                        userAction = "requestSponsorForApprove",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }
        }
    }

    private fun updateSponsorPreviewPolicy(policy: GaslessDisplayPolicy?) {
        val current = _gaslessPreviewState.value as? GaslessPreviewState.Ready ?: return
        if (policy == null) return
        _gaslessPreviewState.value = current.copy(sponsorPolicy = policy)
    }

    private suspend fun ensureGaslessEligible(asset: AssetItem) {
        val tokenAddress = asset.contractAddress
            ?: sendFailure("آدرس قرارداد توکن برای گس‌لس موجود نیست")

        val eligibility = unifiedTransferCoordinator.checkGaslessEligibility(
            networkId = asset.networkId,
            tokenAddress = tokenAddress,
            service = GaslessServiceType.GASLESS
        ).requireSuccess("بررسی مجوز گس‌لس ناموفق بود")

        if (!eligibility.allowed) {
            sendFailure(
                eligibility.bestReasonFa?.takeIf { it.isNotBlank() }
                    ?: "این تراکنش فعلاً برای سرویس گس‌لس مجاز نیست"
            )
        }
    }

    private suspend fun checkSponsorEligibility(session: UnifiedGaslessSession): Boolean {
        val (networkId, tokenAddress) = when (session) {
            is UnifiedGaslessSession.Evm -> session.value.request.networkId to session.value.request.tokenAddress
            is UnifiedGaslessSession.Tron -> session.value.request.networkId to session.value.request.tokenAddress
        }

        return when (
            val result = unifiedTransferCoordinator.checkGaslessEligibility(
                networkId = networkId,
                tokenAddress = tokenAddress,
                service = GaslessServiceType.SPONSOR
            )
        ) {
            is ResultResponse.Success -> result.data.allowed
            // "Can't confirm sponsorship" is treated as "not sponsored" — the user simply pays the
            // approve fee themselves, so this logs and never interrupts (ErrorSurface.SILENT).
            is ResultResponse.Error -> {
                reportError(
                    throwable = result.exception,
                    userAction = "checkSponsorEligibility",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
                false
            }
        }
    }

    private suspend fun sendManualApprove(
        session: UnifiedGaslessSession,
        selectedFee: FeeOption?
    ) {
        val approveTx = when (session) {
            is UnifiedGaslessSession.Evm -> {
                val approveQuote = unifiedTransferCoordinator.quoteEvmApproveRequirement(session)
                    .requireSuccess("EVM approve quote failed")
                if (!approveQuote.approveRequired) {
                    return
                }

                val approveFee = resolveApproveFeeOption(selectedFee)
                    ?: sendFailure("کارمزد approve برای شبکه EVM در دسترس نیست")
                val gasPrice = approveQuote.approveTxTemplate?.gasPriceWei
                    ?: approveQuote.approveTxTemplate?.maxFeePerGasWei
                    ?: approveQuote.gasPriceWei
                    ?: approveQuote.maxFeePerGasWei
                    ?: approveFee.gasPrice
                    ?: sendFailure("گس پرایس approve دریافت نشد")
                val gasLimit = (approveQuote.approveTxTemplate?.gasLimit
                    ?: approveQuote.estimatedApproveGasLimit
                    ?: approveFee.gasLimit
                    ?: MIN_EVM_APPROVE_GAS_LIMIT)
                    .let(::normalizeApproveGasLimit)

                val approvalAmount = approveQuote.approveTxTemplate?.approvalAmount
                    ?: approveQuote.approvalAmount
                    ?: approveQuote.requiredAllowance
                    ?: sendFailure("مقدار approve از سرور دریافت نشد")

                unifiedTransferCoordinator.buildApproveTransaction(
                    session = session,
                    gasPrice = gasPrice,
                    gasLimit = gasLimit,
                    approveAmount = approvalAmount
                ).requireSuccess("ساخت تراکنش approve ناموفق بود")
            }

            is UnifiedGaslessSession.Tron -> {
                val approveQuote = unifiedTransferCoordinator.quoteTronApproveRequirement(session)
                    .requireSuccess("دریافت quote approve ترون ناموفق بود")
                if (!approveQuote.approveRequired) {
                    return
                }

                val approvalAmount = approveQuote.approveTxTemplate?.approvalAmount
                    ?: approveQuote.approvalAmount
                    ?: approveQuote.requiredAllowance
                    ?: sendFailure("مقدار approve از سرور دریافت نشد")

                val feeLimit = resolveTronApproveFeeLimit(selectedFee, approveQuote)
                unifiedTransferCoordinator.buildApproveTransaction(
                    session = session,
                    tronFeeLimit = feeLimit,
                    approveAmount = approvalAmount
                ).requireSuccess("ساخت تراکنش approve ترون ناموفق بود")
            }
        }

        unifiedTransferCoordinator.sendPreparedTransaction(approveTx)
            .requireSuccess("ارسال approve کاربر ناموفق بود")
    }

    private suspend fun waitForApprovedGaslessSession(
        request: UnifiedTransferRequest
    ): UnifiedGaslessSession {
        val startedAt = System.currentTimeMillis()
        var lastFailure: Throwable? = null

        while (System.currentTimeMillis() - startedAt < GASLESS_APPROVE_TIMEOUT_MS) {
            when (val refreshed = unifiedTransferCoordinator.prepareGasless(request)) {
                is ResultResponse.Success -> {
                    if (!refreshed.data.needsApprove()) {
                        return refreshed.data
                    }
                }

                is ResultResponse.Error -> {
                    lastFailure = refreshed.exception
                }
            }
            delay(GASLESS_APPROVE_POLL_INTERVAL_MS)
        }

        val timeoutMessage = "تأیید approve در شبکه بیش از حد طول کشید"
        sendFailure(
            lastFailure?.let { ErrorMapper.userMessage(it, timeoutMessage) } ?: timeoutMessage,
            lastFailure
        )
    }

    private suspend fun submitGaslessWithRetry(
        request: UnifiedTransferRequest,
        initialSession: UnifiedGaslessSession
    ): String {
        var currentSession = initialSession

        repeat(2) { attempt ->
            try {
                val queued = unifiedTransferCoordinator.submitGasless(currentSession)
                    .requireSuccess("ثبت درخواست گس لس ناموفق بود")
                return queued.queueId
            } catch (e: Exception) {
                if (attempt == 0 && shouldRetryGasless(e)) {
                    currentSession = unifiedTransferCoordinator.prepareGasless(request)
                        .requireSuccess("تازه سازی نشست گس لس ناموفق بود")

                    if (currentSession.needsApprove()) {
                        sendFailure("پس از تازه سازی نشست، approve توکن هنوز کافی نیست")
                    }
                } else {
                    throw e
                }
            }
        }

        sendFailure("مسیر گس لس با وجود تلاش مجدد کامل نشد")
    }

    private suspend fun notifyTransferRegistered(
        asset: AssetItem,
        transactionId: String,
        recipient: String,
        amountSmallest: BigInteger
    ) {
        val network = networkCatalog.getNetworkInfoById(asset.networkId)
        val senderAddress = network?.let { resolvedNetwork ->
            getActiveWalletUseCase()
                ?.keys
                ?.firstOrNull { it.networkId == resolvedNetwork.id }
                ?.address
        }

        runCatching {
            appEventBus.postEvent(
                AppEvent.WalletAssetNeedsRefresh(
                    assetId = asset.id,
                    networkId = asset.networkId,
                    contractAddress = asset.contractAddress
                )
            )
        }
        runCatching {
            appEventBus.postEvent(
                AppEvent.TransactionHistoryNeedsRefresh(
                    // TASK-53 — هویتِ شبکه در این رویدادها networkId است، نه نامِ enum.
                    networkName = network?.id,
                    userAddress = senderAddress,
                    pendingTransaction = network?.let {
                        PendingTransactionHint(
                            hash = transactionId,
                            networkName = it.id,
                            networkType = it.networkType.name,
                            fromAddress = senderAddress,
                            toAddress = recipient,
                            amount = amountSmallest.toString(),
                            tokenSymbol = asset.symbol,
                            tokenDecimals = asset.decimals,
                            contractAddress = asset.contractAddress,
                            isOutgoing = true
                        )
                    }
                )
            )
        }
    }

    private fun resolveApproveFeeOption(selectedFee: FeeOption?): FeeOption? {
        selectedFee?.let { return it }
        val options = (_feeState.value as? FeeState.Success)?.options.orEmpty()
        return options.getOrNull(1) ?: options.firstOrNull()
    }

    private fun resolveTronApproveFeeLimit(
        selectedFee: FeeOption?,
        approveQuote: TronApproveQuoteResult
    ): Long {
        val fallback = deriveFeeLimit(selectedFee, isToken = true)
        val quoted = approveQuote.requiredSun

        val quotedWithBuffer = quoted
            .multiply(BigInteger.valueOf(APPROVE_FEE_BUFFER_NUMERATOR))
            .divide(BigInteger.valueOf(APPROVE_FEE_BUFFER_DENOMINATOR))
            .takeIf { it > BigInteger.ZERO }
            ?.takeIf { it <= BigInteger.valueOf(Long.MAX_VALUE) }
            ?.toLong()

        return max(fallback, quotedWithBuffer ?: 0L)
    }

    private fun normalizeApproveGasLimit(rawGasLimit: BigInteger): BigInteger {
        return if (rawGasLimit < MIN_EVM_APPROVE_GAS_LIMIT) {
            MIN_EVM_APPROVE_GAS_LIMIT
        } else {
            rawGasLimit
        }
    }

    /**
     * TASK-57 — decide on the **typed** error, not the human message (API invariant #3). The
     * previous version matched English substrings in `e.message`, which broke the moment the
     * message became curated Persian; the typed code was always available underneath.
     * The substring pass survives only as a fallback for transports that have no typed code yet.
     */
    private fun shouldRetryGasless(error: Throwable?): Boolean {
        val apiError = (error as? ApiException)?.apiError
            ?: (error?.cause as? ApiException)?.apiError

        if (apiError != null) {
            return apiError == ApiError.RequoteRequired ||
                apiError == ApiError.RaceConditionLock ||
                apiError == ApiError.IdempotencyKeyConflict
        }

        val normalized = (error?.message ?: error?.cause?.message)?.lowercase(Locale.US).orEmpty()
        if (normalized.isBlank()) return false

        return "expired" in normalized ||
            "deadline" in normalized ||
            "duplicate nonce" in normalized ||
            "nonce" in normalized && "used" in normalized ||
            "requote" in normalized ||
            "fee_requote_required" in normalized
    }

    private fun addressesMatchForGasless(
        networkType: NetworkType,
        left: String,
        right: String
    ): Boolean {
        return when (networkType) {
            NetworkType.EVM -> left.equals(right, ignoreCase = true)
            else -> left == right
        }
    }

    private fun UnifiedGaslessSession.needsApprove(): Boolean = when (this) {
        is UnifiedGaslessSession.Evm -> value.needsApprove
        is UnifiedGaslessSession.Tron -> value.needsApprove
    }

    /**
     * TASK-57 — fails with copy that is **already** user-facing Persian.
     *
     * Wrapping it in [AppError.Business.General] makes `ErrorMapper` pass the text through
     * verbatim instead of flattening it to the generic message, while the original failure is kept
     * as the cause so the "جزئیات" dialog (and `shouldRetryGasless`) still see the typed error.
     */
    private fun sendFailure(message: String, cause: Throwable? = null): Nothing {
        val error = AppError.Business.General(message = message)
        cause?.let { runCatching { error.initCause(it) } }
        throw error
    }

    private fun <T> ResultResponse<T>.requireSuccess(errorMessage: String): T {
        return when (this) {
            is ResultResponse.Success -> data
            // Curated copy for the typed cases, the call site's Persian description otherwise —
            // the raw server/exception string is never what the user reads.
            is ResultResponse.Error -> sendFailure(
                ErrorMapper.userMessage(exception, errorMessage),
                exception
            )
        }
    }

    private fun deriveFeeLimit(selectedFee: FeeOption?, isToken: Boolean): Long {
        val estimated = runCatching {
            selectedFee?.feeInSmallestUnit
                ?.setScale(0, RoundingMode.CEILING)
                ?.longValueExact()
        }.getOrNull() ?: 0L
        
        val fallback = if (isToken) 40_000_000L else 10_000_000L
        return max(fallback, estimated)
    }

    private fun toSmallestUnit(amount: BigDecimal, decimals: Int): BigInteger {
        return amount
            .movePointRight(decimals)
            .setScale(0, RoundingMode.DOWN)
            .toBigInteger()
    }
    fun estimateFees(asset: AssetItem, recipientAddress: String, silent: Boolean = false) {
        val wallet = getActiveWalletUseCase() ?: return
        
        // Find the network object for this asset
        val network = networkCatalog.getNetworkInfoById(asset.networkId)
        
        if (network == null) {
            _feeState.value = FeeState.Error("شبکه ${asset.networkId} در سیستم ثبت نشده است")
            return
        }

        // روی کاتالوگِ فعلی باز می‌شود: `wallet.keys` لحظهٔ ساختِ کیف‌پول ذخیره شده و شبکه‌ای که
        // بعداً از باندل آمده در آن نیست، پس تخمینِ کارمزد با «آدرس فرستنده یافت نشد» می‌ایستاد.
        val senderAddress = expandWalletKeysToNetworksUseCase(wallet.keys)
            .find { it.networkId == network.id }?.address
        
        if (senderAddress == null) {
            if (!silent) _feeState.value = FeeState.Error("آدرس فرستنده برای شبکه ${network.name} یافت نشد")
            return
        }

        if (!silent) _feeState.value = FeeState.Loading

        viewModelScope.launch {
            try {
                val feeEstimateAmount = runCatching {
                    val amount = getBaseCryptoAmount(asset, _amountText.value, _isFiatMode.value)
                        .coerceAtMost(asset.balanceRaw)
                    toSmallestUnit(amount, asset.decimals)
                }.getOrDefault(BigInteger.ONE).takeIf { it > BigInteger.ZERO } ?: BigInteger.ONE

                when (val result = estimateSendFeesUseCase(wallet, asset, recipientAddress, feeEstimateAmount)) {
                    is ResultResponse.Success -> {
                        val quote = result.data
                        val firstCoinAmount = quote.options.firstOrNull()?.feeInCoin ?: BigDecimal.ZERO
                        
                        if (previousFeeCost != null && previousFeeCost!!.signum() != 0 && firstCoinAmount.compareTo(previousFeeCost) != 0) {
                            _feeTrend.value = if (firstCoinAmount > previousFeeCost) FeeTrend.UP else FeeTrend.DOWN
                            viewModelScope.launch {
                                delay(600)
                                _feeTrend.value = FeeTrend.NONE
                            }
                        }
                        previousFeeCost = firstCoinAmount

                        val feeCoinUsdPrice = resolveFeeCoinUsdPrice(asset = asset, networkSymbol = quote.networkSymbol)
                        val options = quote.options.map { data ->
                            val coinAmount = data.feeInCoin ?: BigDecimal.ZERO
                            val usdAmount = data.feeInUsd ?: coinAmount.multiply(feeCoinUsdPrice)
                            FeeOption(
                                level = data.level,
                                feeAmountDisplay = "${coinAmount.toPlainString()} ${quote.networkSymbol}",
                                feeAmountUsdDisplay = BalanceFormatter.formatFiatValue(
                                    usdAmount, FiatCurrency.USD, null
                                ),
                                // TASK-56 — through the shared formatter: an unknown rate now renders the
                                // placeholder rather than a "0 تومان" fee the user would trust.
                                feeAmountIrrDisplay = BalanceFormatter.formatFiatValue(
                                    usdAmount, FiatCurrency.TOMAN, currentRate
                                ),
                                estimatedTime = data.estimatedTime,
                                feeInSmallestUnit = data.feeInSmallestUnit,
                                feeInCoin = coinAmount,
                                gasPrice = data.gasPrice,
                                gasLimit = data.gasLimit,
                                feeRateInSatsPerByte = data.feeRateInSatsPerByte
                            )
                        }
                        _feeState.value = FeeState.Success(options)
                    }
                    is ResultResponse.Error -> {
                        // Rendered inline on the fee card (and suppressed entirely on a silent
                        // background poll), so it logs rather than raising a snackbar per tick.
                        if (!silent) {
                            _feeState.value = FeeState.Error(
                                userMessageFor(result.exception, "محاسبه کارمزد ناموفق بود")
                            )
                        }
                        reportError(
                            throwable = result.exception,
                            userAction = "estimateSendFees",
                            surface = ErrorSurface.SILENT,
                            severity = ErrorSeverity.LOW
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silent) {
                    _feeState.value = FeeState.Error(userMessageFor(e, "محاسبه کارمزد ناموفق بود"))
                }
                reportError(
                    throwable = e,
                    userAction = "estimateSendFees",
                    surface = ErrorSurface.SILENT,
                    severity = ErrorSeverity.LOW
                )
            }
        }
    }

    private suspend fun resolveFeeCoinUsdPrice(
        asset: AssetItem,
        networkSymbol: String
    ): BigDecimal {
        if (asset.isNativeToken) return asset.priceUsdRaw

        val symbol = networkSymbol.trim().uppercase(Locale.US)
        if (symbol.isEmpty()) return BigDecimal.ZERO

        feeCoinUsdPriceCache[symbol]?.let { cached ->
            if (cached > BigDecimal.ZERO) return cached
        }

        return when (val result = getLatestAssetPricesUseCase(Pair(listOf(asset.symbol), listOf(asset.name)))) {
            is ResultResponse.Success -> {
                val resolved = result.data
                    .firstOrNull { it.assetId.equals(symbol, ignoreCase = true) }
                    ?.priceUsd
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?: BigDecimal.ZERO
                if (resolved > BigDecimal.ZERO) {
                    feeCoinUsdPriceCache[symbol] = resolved
                }
                resolved
            }
            else -> BigDecimal.ZERO
        }
    }

    fun getCryptoDisplay(asset: AssetItem, amountText: String, isFiatMode: Boolean): String {
        try {
            val bd = BigDecimal(amountText.ifBlank { "0" }.trimEnd('.'))
            if (isFiatMode) {
                val crypto = if (asset.priceUsdRaw > BigDecimal.ZERO)
                    bd.divide(asset.priceUsdRaw, 8, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
                return "${BalanceFormatter.formatBalance(crypto, asset.decimals)} ${asset.symbol}"
            } else {
                return "${BalanceFormatter.formatBalance(bd, asset.decimals)} ${asset.symbol}"
            }
        } catch (e: Exception) { return "0 ${asset.symbol}" }
    }

    /**
     * An amount the user typed **in [currency]**, expressed in USD — the unit prices are quoted in.
     * `null` when تومان is selected and the rate is unknown: there is no defensible USD value then,
     * and returning ZERO would silently price the send at nothing.
     */
    private fun toUsd(amount: BigDecimal, currency: FiatCurrency): BigDecimal? = when (currency) {
        FiatCurrency.USD -> amount
        FiatCurrency.TOMAN -> FiatConversion.tomanToUsd(amount, currentRate)
    }

    /**
     * The crypto amount an entry resolves to. **This is the number that gets sent** — treat changes
     * here as money changes.
     *
     * Two deliberate properties:
     *  - [isMaxAmount] short-circuits to the exact [AssetItem.balanceRaw]. Re-deriving MAX from the
     *    rounded text in the box can only be wrong, and wrong in either direction.
     *  - the fiat→crypto division rounds **DOWN**, not HALF_UP. HALF_UP can round a fiat amount up to
     *    slightly more crypto than the user has, turning a full-balance send into an
     *    insufficient-funds failure at broadcast time.
     */
    fun getBaseCryptoAmount(
        asset: AssetItem,
        amountText: String,
        isFiatMode: Boolean,
        currency: FiatCurrency = fiatCurrencyProvider.currency.value
    ): BigDecimal {
        if (_isMaxAmount.value) return asset.balanceRaw
        return try {
            val bd = BigDecimal(amountText.ifBlank { "0" }.trimEnd('.'))
            if (!isFiatMode) return bd
            if (asset.priceUsdRaw <= BigDecimal.ZERO) return BigDecimal.ZERO
            val usd = toUsd(bd, currency) ?: return BigDecimal.ZERO
            usd.divide(asset.priceUsdRaw, asset.decimals, RoundingMode.DOWN)
        } catch (e: Exception) { BigDecimal.ZERO }
    }

    fun formatCryptoFromRaw(asset: AssetItem, amount: BigDecimal): String {
        return BalanceFormatter.formatBalance(amount.coerceAtLeast(BigDecimal.ZERO), asset.decimals)
    }

    fun formatUsdFromRaw(asset: AssetItem, amount: BigDecimal): String {
        val usdVal = amount.coerceAtLeast(BigDecimal.ZERO).multiply(asset.priceUsdRaw)
        return BalanceFormatter.formatFiatValue(usdVal, FiatCurrency.USD, null)
    }

    /** TASK-56 — placeholder, not "0 تومان", when the rate is unknown on the confirm sheet. */
    fun formatIrrFromRaw(asset: AssetItem, amount: BigDecimal): String {
        val usdVal = amount.coerceAtLeast(BigDecimal.ZERO).multiply(asset.priceUsdRaw)
        return BalanceFormatter.formatFiatValue(usdVal, FiatCurrency.TOMAN, currentRate)
    }


}



