package com.mtd.data.repository.transfer

import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.EvmAbiEncoder
import com.mtd.data.repository.gasless.EvmGaslessCoordinator
import com.mtd.data.repository.gasless.PendingGaslessTxStore
import com.mtd.data.repository.gasless.TronGaslessCoordinator
import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.interfaceRepository.IUnifiedTransferCoordinator
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.EvmApproveQuoteResult
import com.mtd.domain.model.EvmGaslessTransferRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmSponsorMode
import com.mtd.domain.model.GaslessDisplayPreview
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessFinalResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSubmission
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.PendingGaslessTx
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronGaslessTransferRequest
import com.mtd.domain.model.TronSponsorApproveResult
import com.mtd.domain.model.TronSponsorMode
import com.mtd.domain.model.UnifiedGaslessSession
import com.mtd.domain.model.UnifiedTransferRequest
import com.mtd.domain.model.core.NetworkType
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedTransferCoordinator @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val blockchainRegistry: BlockchainRegistry,
    private val evmGaslessCoordinator: EvmGaslessCoordinator,
    private val tronGaslessCoordinator: TronGaslessCoordinator,
    private val pendingGaslessTxStore: PendingGaslessTxStore,
    // PROXY routing inputs (both Hilt-bound in DataModule: IAssetCatalog→MergedAssetCatalog — the
    // signed bundle merged with the user's own token list — and the connection-mode provider).
    private val connectionModeProvider: IBlockchainConnectionModeProvider,
    private val assetCatalog: IAssetCatalog
) : IUnifiedTransferCoordinator {


    override suspend fun sendNormal(request: UnifiedTransferRequest): ResultResponse<String> {
        return try {
            validateRequest(request)
            val network = blockchainRegistry.getNetworkById(request.networkId)
                ?: throw IllegalStateException("Network not found: ${request.networkId}")

            when (network.networkType) {
                NetworkType.EVM -> {
                    if (connectionModeProvider.currentMode() == BlockchainConnectionMode.PROXY) {
                        // PROXY: forward raw {recipient, amount, assetId}; the relayer builds the
                        // unsigned tx (native-vs-token from the registry) and the client signs it.
                        sendEvmViaProxy(request, network.id)
                    } else {
                    val gasPrice = request.gasPrice
                        ?: throw IllegalStateException("gasPrice is required for EVM normal transfer")
                    val gasLimit = request.gasLimit
                        ?: throw IllegalStateException("gasLimit is required for EVM normal transfer")

                    val params = if (request.tokenAddress.isNullOrBlank()) {
                        TransactionParams.Evm(
                            networkId = network.id,
                            to = request.toAddress,
                            amount = request.amount,
                            data = request.data,
                            assetId = request.assetId,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit
                        )
                    } else {
                        val transferData = request.data ?: EvmAbiEncoder.encodeTransfer(
                            toAddress = request.toAddress,
                            amount = request.amount
                        )
                        TransactionParams.Evm(
                            networkId = network.id,
                            to = request.tokenAddress!!,
                            amount = BigInteger.ZERO,
                            data = transferData,
                            assetId = request.assetId,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit
                        )
                    }

                    walletRepository.sendTransaction(params)
                    }
                }

                NetworkType.TVM -> {
                    if (connectionModeProvider.currentMode() == BlockchainConnectionMode.PROXY) {
                        // PROXY: forward raw {recipient, amount, assetId}; the relayer builds the
                        // unsigned tx (native-vs-TRC20 from the registry) and the client signs it.
                        sendTvmViaProxy(request, network.id)
                    } else {
                    val params = TransactionParams.Tvm(
                        networkId = network.id,
                        toAddress = request.toAddress,
                        amount = request.amount,
                        assetId = request.assetId,
                        contractAddress = request.tokenAddress,
                        feeLimit = request.feeLimit ?: 10_000_000L,
                        contractFunction = request.contractFunction,
                        contractParameter = request.contractParameter
                    )
                    walletRepository.sendTransaction(params)
                    }
                }

                NetworkType.BITCOIN,
                NetworkType.UTXO -> {
                    if (!request.tokenAddress.isNullOrBlank()) {
                        throw IllegalStateException("Token transfer is not supported for UTXO networks")
                    }

                    val chainId = network.chainId
                        ?: throw IllegalStateException("chainId is required for UTXO normal transfer")

                    val amountInSatoshi = request.amount.toPositiveLongOrThrow("amount")
                    val feeRate = request.utxoFeeRateInSatsPerByte
                        ?: defaultUtxoFeeRateInSatsPerByte(request.networkId)

                    val params = TransactionParams.Utxo(
                        chainId = chainId,
                        toAddress = request.toAddress,
                        amountInSatoshi = amountInSatoshi,
                        feeRateInSatsPerByte = feeRate,
                        // PROXY needs the registry assetId; DIRECT ignores it (native-only UTXO).
                        assetId = if (connectionModeProvider.currentMode() == BlockchainConnectionMode.PROXY) {
                            resolveProxyAssetId(request)
                        } else {
                            null
                        }
                    )
                    walletRepository.sendTransaction(params)
                }

                else -> ResultResponse.Error(
                    IllegalStateException("Network type ${network.networkType} is not supported by unified transfer")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun sendPreparedTransaction(params: TransactionParams): ResultResponse<String> {
        return try {
            walletRepository.sendTransaction(params)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun getSupportedGaslessTokens(networkId: String): ResultResponse<List<GaslessSupportedToken>> {
        return try {
            val network = blockchainRegistry.getNetworkById(networkId)
                ?: throw IllegalStateException("Network not found: $networkId")

            when (network.networkType) {
                NetworkType.EVM -> evmGaslessCoordinator.getSupportedTokens(networkId)
                NetworkType.TVM -> tronGaslessCoordinator.getSupportedTokens(networkId)
                else -> ResultResponse.Error(
                    IllegalStateException("Gasless token list is not supported for network type ${network.networkType}")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun checkGaslessEligibility(
        networkId: String,
        tokenAddress: String,
        service: GaslessServiceType
    ): ResultResponse<GaslessEligibilityResult> {
        return try {
            val network = blockchainRegistry.getNetworkById(networkId)
                ?: throw IllegalStateException("Network not found: $networkId")
            val userAddress = walletRepository.getActiveAddressForNetwork(networkId)
                ?: throw IllegalStateException("Active wallet address not found for $networkId")

            when (network.networkType) {
                NetworkType.EVM -> evmGaslessCoordinator.checkEligibility(
                    service = service,
                    userAddress = userAddress,
                    tokenAddress = tokenAddress,
                    networkId = networkId
                )
                NetworkType.TVM -> tronGaslessCoordinator.checkEligibility(
                    service = service,
                    userAddress = userAddress,
                    tokenAddress = tokenAddress,
                    networkId = networkId
                )
                else -> ResultResponse.Error(
                    IllegalStateException("Gasless eligibility is not supported for network type ${network.networkType}")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun prepareGasless(request: UnifiedTransferRequest): ResultResponse<UnifiedGaslessSession> {
        return try {
            validateRequest(request)
            val network = blockchainRegistry.getNetworkById(request.networkId)
                ?: throw IllegalStateException("Network not found: ${request.networkId}")

            when (network.networkType) {
                NetworkType.EVM -> {
                    // Phase 4: per-network gasless availability (incl. BSC) is decided by the
                    // backend capability via FeatureAvailabilityResolver — no hardcoded chainId block.
                    val permit2 = request.permit2Address
                        ?: throw IllegalStateException("permit2Address is required for EVM gasless")
                    when (
                        val prepared = evmGaslessCoordinator.prepareSession(
                            EvmGaslessTransferRequest(
                                networkId = request.networkId,
                                tokenAddress = request.tokenAddress
                                    ?: throw IllegalStateException("tokenAddress is required for EVM gasless"),
                                targetAddress = request.toAddress,
                                amount = request.amount,
                                assetId = request.assetId,
                                permit2Address = permit2,
                                feeAmount = request.feeAmount,
                                feeFundingSource = request.feeFundingSource,
                                deadlineEpochSeconds = request.deadlineEpochSeconds
                            )
                        )
                    ) {
                        is ResultResponse.Success -> ResultResponse.Success(
                            UnifiedGaslessSession.Evm(prepared.data)
                        )
                        is ResultResponse.Error -> prepared
                    }
                }

                NetworkType.TVM -> {
                    when (
                        val prepared = tronGaslessCoordinator.prepareSession(
                            TronGaslessTransferRequest(
                                networkId = request.networkId,
                                tokenAddress = request.tokenAddress
                                    ?: throw IllegalStateException("tokenAddress is required for TRON gasless"),
                                targetAddress = request.toAddress,
                                amount = request.amount,
                                assetId = request.assetId,
                                feeAmount = request.feeAmount,
                                feeFundingSource = request.feeFundingSource,
                                deadlineEpochSeconds = request.deadlineEpochSeconds
                            )
                        )
                    ) {
                        is ResultResponse.Success -> ResultResponse.Success(
                            UnifiedGaslessSession.Tron(prepared.data)
                        )
                        is ResultResponse.Error -> prepared
                    }
                }

                else -> ResultResponse.Error(
                    IllegalStateException("Gasless is not supported for network type ${network.networkType}")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun previewGaslessDisplayPolicy(
        request: UnifiedTransferRequest
    ): ResultResponse<GaslessDisplayPreview> {
        return when (val prepared = prepareGasless(request)) {
            is ResultResponse.Success -> {
                val session = prepared.data
                when (session) {
                    is UnifiedGaslessSession.Evm -> {
                        when (val quote = evmGaslessCoordinator.previewQuote(session.value)) {
                            is ResultResponse.Success -> ResultResponse.Success(
                                GaslessDisplayPreview(
                                    displayPolicy = quote.data.displayPolicy,
                                    gaslessFeeAmount = quote.data.canonicalParams.feeAmount,
                                    needsApprove = session.value.needsApprove,
                                    smartFee = quote.data.smartFee,
                                    feeFundingSource = quote.data.feeFundingSource,
                                    gasCreditApplied = quote.data.gasCreditApplied,
                                    totalFee = quote.data.totalFee,
                                    finalFee = quote.data.finalFee
                                )
                            )
                            is ResultResponse.Error -> quote
                        }
                    }

                    is UnifiedGaslessSession.Tron -> {
                        when (val quote = tronGaslessCoordinator.previewQuote(session.value)) {
                            is ResultResponse.Success -> ResultResponse.Success(
                                GaslessDisplayPreview(
                                    displayPolicy = quote.data.displayPolicy,
                                    gaslessFeeAmount = quote.data.canonicalParams.feeAmount,
                                    needsApprove = session.value.needsApprove,
                                    smartFee = quote.data.smartFee,
                                    feeFundingSource = quote.data.feeFundingSource,
                                    gasCreditApplied = quote.data.gasCreditApplied,
                                    totalFee = quote.data.totalFee,
                                    finalFee = quote.data.finalFee
                                )
                            )
                            is ResultResponse.Error -> quote
                        }
                    }
                }
            }

            is ResultResponse.Error -> prepared
        }
    }

    override fun buildApproveTransaction(
        session: UnifiedGaslessSession,
        gasPrice: BigInteger?,
        gasLimit: BigInteger?,
        tronFeeLimit: Long,
        approveAmount: BigInteger?
    ): ResultResponse<TransactionParams> {
        return try {
            when (session) {
                is UnifiedGaslessSession.Evm -> {
                    val gp = gasPrice ?: throw IllegalStateException("gasPrice is required for EVM approve")
                    val gl = gasLimit ?: throw IllegalStateException("gasLimit is required for EVM approve")
                    val tx = evmGaslessCoordinator.buildApproveTransaction(
                        session = session.value,
                        gasPrice = gp,
                        gasLimit = gl,
                        approveAmount = approveAmount ?: session.value.request.amount
                    )
                    ResultResponse.Success(tx)
                }

                is UnifiedGaslessSession.Tron -> {
                    val tx = tronGaslessCoordinator.buildApproveTransaction(
                        session = session.value,
                        feeLimit = tronFeeLimit,
                        approveAmount = approveAmount ?: session.value.request.amount
                    )
                    ResultResponse.Success(tx)
                }
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun requestTronSponsorForApprove(
        session: UnifiedGaslessSession,
        mode: TronSponsorMode
    ): ResultResponse<TronSponsorApproveResult> {
        return when (session) {
            is UnifiedGaslessSession.Tron -> tronGaslessCoordinator.requestSponsorForApprove(session.value, mode)
            is UnifiedGaslessSession.Evm -> ResultResponse.Error(
                IllegalStateException("Sponsor approve flow is only available for TRON gasless")
            )
        }
    }

    override suspend fun quoteTronApproveRequirement(
        session: UnifiedGaslessSession
    ): ResultResponse<TronApproveQuoteResult> {
        return when (session) {
            is UnifiedGaslessSession.Tron -> tronGaslessCoordinator.quoteApproveRequirement(session.value)
            is UnifiedGaslessSession.Evm -> ResultResponse.Error(
                IllegalStateException("TRON approve quote is only available for TRON gasless")
            )
        }
    }

    override suspend fun quoteEvmApproveRequirement(
        session: UnifiedGaslessSession
    ): ResultResponse<EvmApproveQuoteResult> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> evmGaslessCoordinator.quoteApproveRequirement(session.value)
            is UnifiedGaslessSession.Tron -> ResultResponse.Error(
                IllegalStateException("EVM approve quote is only available for EVM gasless")
            )
        }
    }

    override suspend fun requestEvmSponsorForApprove(
        session: UnifiedGaslessSession,
        mode: EvmSponsorMode
    ): ResultResponse<EvmSponsorApproveResult> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> evmGaslessCoordinator.requestSponsorForApprove(session.value, mode)
            is UnifiedGaslessSession.Tron -> ResultResponse.Error(
                IllegalStateException("Sponsor approve flow is only available for EVM gasless")
            )
        }
    }

    override suspend fun submitGasless(session: UnifiedGaslessSession): ResultResponse<GaslessSubmission> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> {
                when (val queued = evmGaslessCoordinator.signAndSubmit(session.value)) {
                    is ResultResponse.Success -> {
                        pendingGaslessTxStore.put(
                            PendingGaslessTx(
                                queueId = queued.data.id,
                                networkId = session.value.request.networkId,
                                walletId = walletRepository.getActiveWalletId()
                            )
                        )
                        ResultResponse.Success(
                            GaslessSubmission(
                                queueId = queued.data.id,
                                stage = queued.data.stage,
                                idempotent = queued.data.idempotent
                            )
                        )
                    }
                    is ResultResponse.Error -> queued
                }
            }

            is UnifiedGaslessSession.Tron -> {
                when (val queued = tronGaslessCoordinator.signAndSubmit(session.value)) {
                    is ResultResponse.Success -> {
                        pendingGaslessTxStore.put(
                            PendingGaslessTx(
                                queueId = queued.data.id,
                                networkId = session.value.request.networkId,
                                walletId = walletRepository.getActiveWalletId()
                            )
                        )
                        ResultResponse.Success(
                            GaslessSubmission(
                                queueId = queued.data.id,
                                stage = queued.data.stage,
                                idempotent = queued.data.idempotent
                            )
                        )
                    }
                    is ResultResponse.Error -> queued
                }
            }
        }
    }

    override suspend fun pollGaslessUntilFinal(
        session: UnifiedGaslessSession,
        queueId: String,
        pollIntervalMs: Long,
        timeoutMs: Long
    ): ResultResponse<GaslessFinalResult> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> {
                when (
                    val status = evmGaslessCoordinator.pollUntilFinal(
                        txId = queueId,
                        networkId = session.value.request.networkId,
                        pollIntervalMs = pollIntervalMs,
                        timeoutMs = timeoutMs
                    )
                ) {
                    is ResultResponse.Success -> {
                        if (status.data.isFinal) {
                            pendingGaslessTxStore.remove(session.value.request.networkId, queueId)
                        }
                        ResultResponse.Success(GaslessFinalResult(queueId, status.data))
                    }
                    is ResultResponse.Error -> status
                }
            }

            is UnifiedGaslessSession.Tron -> {
                when (
                    val status = tronGaslessCoordinator.pollUntilFinal(
                        txId = queueId,
                        networkId = session.value.request.networkId,
                        pollIntervalMs = pollIntervalMs,
                        timeoutMs = timeoutMs
                    )
                ) {
                    is ResultResponse.Success -> {
                        if (status.data.isFinal) {
                            pendingGaslessTxStore.remove(session.value.request.networkId, queueId)
                        }
                        ResultResponse.Success(GaslessFinalResult(queueId, status.data))
                    }
                    is ResultResponse.Error -> status
                }
            }
        }
    }

    override fun getPendingGaslessTransactions(): List<PendingGaslessTx> {
        return pendingGaslessTxStore.getAll()
    }

    override fun clearPendingGaslessTransactions() {
        pendingGaslessTxStore.clear()
    }

    /**
     * PROXY EVM send: resolve the registry assetId from (networkId, tokenAddress|native), then hand
     * raw {recipient, amount, assetId} to [walletRepository.sendTransaction]. The ProxyChainDataSource
     * calls /prepare (server builds the unsigned tx), signs locally, and broadcasts. No ABI encoding
     * here — that is the DIRECT path's job and is left untouched.
     */
    private suspend fun sendEvmViaProxy(
        request: UnifiedTransferRequest,
        networkId: String
    ): ResultResponse<String> {
        val params = TransactionParams.Evm(
            networkId = networkId,
            to = request.toAddress,
            amount = request.amount,
            data = null,
            // Unused in PROXY mode — the relayer supplies gas in /prepare (scaled by feeLevel).
            gasPrice = BigInteger.ZERO,
            gasLimit = BigInteger.ZERO,
            assetId = resolveProxyAssetId(request),
            feeLevel = request.feeLevel
        )
        return walletRepository.sendTransaction(params)
    }

    /**
     * PROXY TVM send: same shape as EVM — forward raw {recipient, amount, assetId}. The relayer builds
     * the unsigned Tron tx (native-vs-TRC20 from the registry) and the client signs it. No ABI/contract
     * encoding here — that is the DIRECT path's job and is left untouched.
     */
    private suspend fun sendTvmViaProxy(
        request: UnifiedTransferRequest,
        networkId: String
    ): ResultResponse<String> {
        val params = TransactionParams.Tvm(
            networkId = networkId,
            toAddress = request.toAddress,
            amount = request.amount,
            assetId = resolveProxyAssetId(request),
            feeLevel = request.feeLevel
        )
        return walletRepository.sendTransaction(params)
    }

    /** Resolve the registry assetId from (networkId, tokenAddress|native) for a PROXY send. */
    private fun resolveProxyAssetId(request: UnifiedTransferRequest): String {
        return assetCatalog.getAssetConfigsForNetwork(request.networkId)
            .firstOrNull { cfg ->
                if (request.tokenAddress.isNullOrBlank()) {
                    cfg.contractAddress.isNullOrBlank()
                } else {
                    cfg.contractAddress?.equals(request.tokenAddress, ignoreCase = true) == true
                }
            }?.id
            ?: throw IllegalStateException(
                "No registry assetId for ${request.networkId}/${request.tokenAddress ?: "native"}"
            )
    }

    private fun validateRequest(request: UnifiedTransferRequest) {
        if (request.networkId.isBlank()) {
            throw IllegalArgumentException("networkId is required")
        }
        if (request.toAddress.isBlank()) {
            throw IllegalArgumentException("toAddress is required")
        }
        if (request.amount <= BigInteger.ZERO) {
            throw IllegalArgumentException("amount must be greater than zero")
        }
    }

    private fun defaultUtxoFeeRateInSatsPerByte(networkId: String): Long {
        return when (networkId) {
            "doge_mainnet",
            "doge_testnet" -> 1_500L

            else -> 8L
        }
    }

    private fun BigInteger.toPositiveLongOrThrow(fieldName: String): Long {
        if (this > BigInteger.valueOf(Long.MAX_VALUE)) {
            throw IllegalArgumentException("$fieldName exceeds Long.MAX_VALUE")
        }
        return this.toLong()
    }
}
