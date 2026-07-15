package com.mtd.megawallet.viewmodel.news

import androidx.lifecycle.viewModelScope
import com.mtd.core.manager.ErrorManager
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.IFeatureAvailabilityResolver
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IUnifiedTransferCoordinator
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
import com.mtd.domain.model.gassless.FeeState
import com.mtd.domain.model.gassless.FeeTrend
import com.mtd.domain.model.gassless.GaslessAvailability
import com.mtd.domain.model.gassless.GaslessPreviewState
import com.mtd.domain.model.gassless.SubmitState
import com.mtd.domain.usecase.asset.GetLatestAssetPricesUseCase
import com.mtd.domain.usecase.asset.GetUsdToIrrRateUseCase
import com.mtd.domain.usecase.network.GetNetworkTypeForAddressUseCase
import com.mtd.domain.usecase.network.ValidateAddressForNetworkUseCase
import com.mtd.domain.usecase.send.EstimateSendFeesUseCase
import com.mtd.domain.usecase.send.RefreshSelectedAssetBalanceUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
    private val getUsdToIrrRateUseCase: GetUsdToIrrRateUseCase,
    private val refreshSelectedAssetBalanceUseCase: RefreshSelectedAssetBalanceUseCase,
    private val estimateSendFeesUseCase: EstimateSendFeesUseCase,
    private val observeActiveWalletUseCase: ObserveActiveWalletUseCase,
    private val getActiveWalletUseCase: GetActiveWalletUseCase,
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

    private val _isUsdMode = MutableStateFlow(false)
    val isUsdMode = _isUsdMode.asStateFlow()

    private val _selectedAsset = MutableStateFlow<AssetItem?>(null)
    val selectedAsset = _selectedAsset.asStateFlow()

    private val _gaslessAvailability =
        MutableStateFlow<GaslessAvailability>(GaslessAvailability.Unavailable())
    val gaslessAvailability = _gaslessAvailability.asStateFlow()

    private val _showConfirmScreen = MutableStateFlow(false)
    val showConfirmScreen = _showConfirmScreen.asStateFlow()

    private val _gaslessPreviewState = MutableStateFlow<GaslessPreviewState>(GaslessPreviewState.Idle)
    val gaslessPreviewState = _gaslessPreviewState.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState = _submitState.asStateFlow()

    val activeWalletName = observeActiveWalletUseCase()
        .map { it?.name ?: "کیف پول من" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "کیف پول من")

    private var currentIrrRate: BigDecimal = BigDecimal("70000") // Default fallback

    init {
        fetchIrrRate()
    }

    private fun fetchIrrRate() {
        launchSafe {
            when (val result = getUsdToIrrRateUseCase()) {
                is ResultResponse.Success -> {
                    currentIrrRate = result.data.rate
                }
                else -> {}
            }
        }
    }

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
    }

    fun toggleUsdMode() {
        _isUsdMode.value = !_isUsdMode.value
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
                    _gaslessAvailability.value = GaslessAvailability.Unavailable(
                        result.exception.message ?: "امکان بررسی وضعیت گس لس وجود ندارد"
                    )
                }
            }
        }
    }

    private fun updateBalanceForAsset(asset: AssetItem) {
        val wallet = getActiveWalletUseCase() ?: return

        viewModelScope.launch {
            when (val result = refreshSelectedAssetBalanceUseCase(wallet, asset, currentIrrRate)) {
                is ResultResponse.Success -> result.data?.let { _selectedAsset.value = it }
                is ResultResponse.Error -> Unit
            }
        }
    }

    fun clearState() {
        _recipientAddress.value = ""
        _recipientNetworkType.value = null
        _amountText.value = "0"
        _isUsdMode.value = false
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

                val baseCrypto = getBaseCryptoAmount(asset, _amountText.value, _isUsdMode.value)
                val feeCoin = selectedFee?.feeInCoin ?: BigDecimal.ZERO
                
                // Safe detection of Max: if UI says so OR if the amount is >= balance
                val actuallyIsMax = isMax || baseCrypto >= asset.balanceRaw
                
                val effectiveCrypto = if (asset.isNativeToken && actuallyIsMax) {
                    asset.balanceRaw.subtract(feeCoin).coerceAtLeast(BigDecimal.ZERO)
                } else {
                    baseCrypto.coerceAtMost(asset.balanceRaw)
                }

                if (effectiveCrypto <= BigDecimal.ZERO) {
                    throw IllegalStateException("مقدار ارسال معتبر نیست")
                }

                val amountSmallest = toSmallestUnit(effectiveCrypto, asset.decimals)
                if (amountSmallest <= BigInteger.ZERO) {
                    throw IllegalStateException("مقدار ارسال خیلی کوچک است")
                }

                if (useGasless) {
                    if (!isGaslessEnabled()) {
                        throw IllegalStateException("ارسال گس لس برای این دارایی فعال نیست")
                    }
                    if (!validateAddressForNetworkUseCase(recipient, asset.networkId)) {
                        throw IllegalStateException("آدرس مقصد برای این شبکه معتبر نیست")
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
                            ?: throw IllegalStateException("گس پرایس دریافت نشد")
                        val gasLimit = selectedFee.gasLimit
                            ?: throw IllegalStateException("گس لیمیت دریافت نشد")

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

                    else -> throw IllegalStateException("ارسال برای این شبکه پشتیبانی نشده است")
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
            } catch (e: Exception) {
                _submitState.value = SubmitState.Error(e.message ?: "ارسال تراکنش ناموفق بود")
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
            val amount = getBaseCryptoAmount(asset, _amountText.value, _isUsdMode.value)
                .coerceAtMost(asset.balanceRaw)
            toSmallestUnit(amount, asset.decimals)
        }.getOrDefault(BigInteger.ZERO)

        if (amountSmallest <= BigInteger.ZERO) {
            _gaslessPreviewState.value = GaslessPreviewState.Idle
            return
        }

        val request = runCatching {
            buildGaslessRequest(
                asset = asset,
                recipient = recipient,
                amountSmallest = amountSmallest
            )
        }.getOrElse { error ->
            _gaslessPreviewState.value = GaslessPreviewState.Error(
                error.message ?: "امکان محاسبه هزینه گس لس وجود ندارد"
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
                        eligibility.exception.message ?: "بررسی مجوز گس‌لس ناموفق بود"
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
                }

                is ResultResponse.Error -> {
                    _gaslessPreviewState.value = GaslessPreviewState.Error(
                        preview.exception.message ?: "امکان دریافت هزینه گس‌لس وجود ندارد"
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
            ?: throw IllegalStateException("شبکه ${asset.networkId} یافت نشد")
        val tokenAddress = asset.contractAddress
            ?: throw IllegalStateException("برای گس‌لس، آدرس قرارداد توکن الزامی است")

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

            else -> throw IllegalStateException("گس‌لس برای این شبکه پشتیبانی نشده است")
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
                    is ResultResponse.Error -> Unit
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
                    is ResultResponse.Error -> Unit
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
            ?: throw IllegalStateException("آدرس قرارداد توکن برای گس‌لس موجود نیست")

        val eligibility = unifiedTransferCoordinator.checkGaslessEligibility(
            networkId = asset.networkId,
            tokenAddress = tokenAddress,
            service = GaslessServiceType.GASLESS
        ).requireSuccess("بررسی مجوز گس‌لس ناموفق بود")

        if (!eligibility.allowed) {
            throw IllegalStateException(
                eligibility.bestReasonFa
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
            is ResultResponse.Error -> false
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
                    ?: throw IllegalStateException("کارمزد approve برای شبکه EVM در دسترس نیست")
                val gasPrice = approveQuote.approveTxTemplate?.gasPriceWei
                    ?: approveQuote.approveTxTemplate?.maxFeePerGasWei
                    ?: approveQuote.gasPriceWei
                    ?: approveQuote.maxFeePerGasWei
                    ?: approveFee.gasPrice
                    ?: throw IllegalStateException("گس پرایس approve دریافت نشد")
                val gasLimit = (approveQuote.approveTxTemplate?.gasLimit
                    ?: approveQuote.estimatedApproveGasLimit
                    ?: approveFee.gasLimit
                    ?: MIN_EVM_APPROVE_GAS_LIMIT)
                    .let(::normalizeApproveGasLimit)

                val approvalAmount = approveQuote.approveTxTemplate?.approvalAmount
                    ?: approveQuote.approvalAmount
                    ?: approveQuote.requiredAllowance
                    ?: throw IllegalStateException("EVM approve amount was not returned by server")

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
                    ?: throw IllegalStateException("مقدار approve از سرور دریافت نشد")

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

        throw IllegalStateException(
            lastFailure?.message ?: "تأیید approve در شبکه بیش از حد طول کشید"
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
                if (attempt == 0 && shouldRetryGasless(e.message)) {
                    currentSession = unifiedTransferCoordinator.prepareGasless(request)
                        .requireSuccess("تازه سازی نشست گس لس ناموفق بود")

                    if (currentSession.needsApprove()) {
                        throw IllegalStateException("پس از تازه سازی نشست، approve توکن هنوز کافی نیست")
                    }
                } else {
                    throw e
                }
            }
        }

        throw IllegalStateException("مسیر گس لس با وجود تلاش مجدد کامل نشد")
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
                ?.firstOrNull { it.networkName == resolvedNetwork.name }
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
                    networkName = network?.name?.name,
                    userAddress = senderAddress,
                    pendingTransaction = network?.let {
                        PendingTransactionHint(
                            hash = transactionId,
                            networkName = it.name.name,
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

    private fun shouldRetryGasless(message: String?): Boolean {
        val normalized = message?.lowercase(Locale.US).orEmpty()
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

    private fun <T> ResultResponse<T>.requireSuccess(errorMessage: String): T {
        return when (this) {
            is ResultResponse.Success -> data
            is ResultResponse.Error -> throw IllegalStateException(
                exception.message?.takeIf { it.isNotBlank() } ?: errorMessage,
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

        // Find the corresponding wallet key for this network using the NetworkName enum
        val senderAddress = wallet.keys.find { it.networkName == network.name }?.address
        
        if (senderAddress == null) {
            if (!silent) _feeState.value = FeeState.Error("آدرس فرستنده برای شبکه ${network.name} یافت نشد")
            return
        }

        if (!silent) _feeState.value = FeeState.Loading

        viewModelScope.launch {
            try {
                val feeEstimateAmount = runCatching {
                    val amount = getBaseCryptoAmount(asset, _amountText.value, _isUsdMode.value)
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
                            val irrAmount = usdAmount.multiply(currentIrrRate)

                            FeeOption(
                                level = data.level,
                                feeAmountDisplay = "${coinAmount.toPlainString()} ${quote.networkSymbol}",
                                feeAmountUsdDisplay = "$${BalanceFormatter.formatUsdValue(usdAmount)}",
                                feeAmountIrrDisplay = "${BalanceFormatter.formatNumberWithSeparator(irrAmount)} تومان",
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
                        if (!silent) _feeState.value = FeeState.Error(result.exception.message ?: "Fee estimation failed")
                    }
                }
            } catch (e: Exception) {
                if (!silent) _feeState.value = FeeState.Error(e.message ?: "Unknown error")
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

    fun getCryptoDisplay(asset: AssetItem, amountText: String, isUsdMode: Boolean): String {
        try {
            val bd = BigDecimal(amountText.ifBlank { "0" }.trimEnd('.'))
            if (isUsdMode) {
                val crypto = if (asset.priceUsdRaw > BigDecimal.ZERO)
                    bd.divide(asset.priceUsdRaw, 8, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
                return "${BalanceFormatter.formatBalance(crypto, asset.decimals)} ${asset.symbol}"
            } else {
                return "${BalanceFormatter.formatBalance(bd, asset.decimals)} ${asset.symbol}"
            }
        } catch (e: Exception) { return "0 ${asset.symbol}" }
    }

    fun getUsdDisplay(asset: AssetItem, amountText: String, isUsdMode: Boolean): String {
        try {
            val bd = BigDecimal(amountText.ifBlank { "0" }.trimEnd('.'))
            if (isUsdMode) {
                return "$${BalanceFormatter.formatUsdValue(bd, false)}"
            } else {
                val usdVal = bd.multiply(asset.priceUsdRaw)
                return "$${BalanceFormatter.formatUsdValue(usdVal, false)}"
            }
        } catch (e: Exception) { return "$0.00" }
    }

    fun getIrrDisplay(asset: AssetItem, amountText: String, isUsdMode: Boolean): String {
        try {
            val bd = BigDecimal(amountText.ifBlank { "0" }.trimEnd('.'))
            val usdVal = if (isUsdMode) bd else bd.multiply(asset.priceUsdRaw)
            val irrVal = usdVal.multiply(currentIrrRate)
            return "${BalanceFormatter.formatNumberWithSeparator(irrVal)} تومان"
        } catch (e: Exception) { return "0 تومان" }
    }

    fun getBaseCryptoAmount(asset: AssetItem, amountText: String, isUsdMode: Boolean): BigDecimal {
        return try {
            val bd = BigDecimal(amountText.ifBlank { "0" }.trimEnd('.'))
            if (isUsdMode && asset.priceUsdRaw > BigDecimal.ZERO) {
                bd.divide(asset.priceUsdRaw, asset.decimals, RoundingMode.HALF_UP)
            } else if (!isUsdMode) {
                bd
            } else BigDecimal.ZERO
        } catch (e: Exception) { BigDecimal.ZERO }
    }

    fun formatCryptoFromRaw(asset: AssetItem, amount: BigDecimal): String {
        return BalanceFormatter.formatBalance(amount.coerceAtLeast(BigDecimal.ZERO), asset.decimals)
    }

    fun formatUsdFromRaw(asset: AssetItem, amount: BigDecimal): String {
        val usdVal = amount.coerceAtLeast(BigDecimal.ZERO).multiply(asset.priceUsdRaw)
        return "$${BalanceFormatter.formatUsdValue(usdVal, false)}"
    }

    fun formatIrrFromRaw(asset: AssetItem, amount: BigDecimal): String {
        val usdVal = amount.coerceAtLeast(BigDecimal.ZERO).multiply(asset.priceUsdRaw)
        val irrVal = usdVal.multiply(currentIrrRate)
        return "${BalanceFormatter.formatNumberWithSeparator(irrVal)} تومان"
    }


}



