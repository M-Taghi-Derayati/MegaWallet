package com.mtd.data.repository.transfer

import com.mtd.domain.model.core.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.EvmAbiEncoder
import com.mtd.data.repository.gasless.EvmGaslessCoordinator
import com.mtd.data.repository.gasless.PendingGaslessTxStore
import com.mtd.data.repository.gasless.TronGaslessCoordinator
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.EvmGaslessTransferRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmSponsorMode
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessDisplayPolicyBundle
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
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedTransferCoordinator @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val blockchainRegistry: BlockchainRegistry,
    private val evmGaslessCoordinator: EvmGaslessCoordinator,
    private val tronGaslessCoordinator: TronGaslessCoordinator,
    private val pendingGaslessTxStore: PendingGaslessTxStore
) {

    suspend fun sendNormal(request: UnifiedTransferRequest): ResultResponse<String> {
        return try {
            validateRequest(request)
            val network = blockchainRegistry.getNetworkById(request.networkId)
                ?: throw IllegalStateException("Network not found: ${request.networkId}")

            when (network.networkType) {
                NetworkType.EVM -> {
                    val gasPrice = request.gasPrice
                        ?: throw IllegalStateException("gasPrice is required for EVM normal transfer")
                    val gasLimit = request.gasLimit
                        ?: throw IllegalStateException("gasLimit is required for EVM normal transfer")

                    val params = if (request.tokenAddress.isNullOrBlank()) {
                        TransactionParams.Evm(
                            networkName = network.name,
                            to = request.toAddress,
                            amount = request.amount,
                            data = request.data,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit
                        )
                    } else {
                        val transferData = request.data ?: EvmAbiEncoder.encodeTransfer(
                            toAddress = request.toAddress,
                            amount = request.amount
                        )
                        TransactionParams.Evm(
                            networkName = network.name,
                            to = request.tokenAddress!!,
                            amount = BigInteger.ZERO,
                            data = transferData,
                            gasPrice = gasPrice,
                            gasLimit = gasLimit
                        )
                    }

                    walletRepository.sendTransaction(params)
                }

                NetworkType.TVM -> {
                    val params = TransactionParams.Tvm(
                        networkName = network.name,
                        toAddress = request.toAddress,
                        amount = request.amount,
                        contractAddress = request.tokenAddress,
                        feeLimit = request.feeLimit ?: 10_000_000L,
                        contractFunction = request.contractFunction,
                        contractParameter = request.contractParameter
                    )
                    walletRepository.sendTransaction(params)
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
                        feeRateInSatsPerByte = feeRate
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

    suspend fun sendPreparedTransaction(params: TransactionParams): ResultResponse<String> {
        return try {
            walletRepository.sendTransaction(params)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    suspend fun getSupportedGaslessTokens(networkId: String): ResultResponse<List<GaslessSupportedToken>> {
        return try {
            val network = blockchainRegistry.getNetworkById(networkId)
                ?: throw IllegalStateException("Network not found: $networkId")

            when (network.networkType) {
                NetworkType.EVM -> evmGaslessCoordinator.getSupportedTokens()
                NetworkType.TVM -> tronGaslessCoordinator.getSupportedTokens()
                else -> ResultResponse.Error(
                    IllegalStateException("Gasless token list is not supported for network type ${network.networkType}")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    suspend fun checkGaslessEligibility(
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
                    tokenAddress = tokenAddress
                )
                NetworkType.TVM -> tronGaslessCoordinator.checkEligibility(
                    service = service,
                    userAddress = userAddress,
                    tokenAddress = tokenAddress
                )
                else -> ResultResponse.Error(
                    IllegalStateException("Gasless eligibility is not supported for network type ${network.networkType}")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    suspend fun prepareGasless(request: UnifiedTransferRequest): ResultResponse<UnifiedGaslessSession> {
        return try {
            validateRequest(request)
            val network = blockchainRegistry.getNetworkById(request.networkId)
                ?: throw IllegalStateException("Network not found: ${request.networkId}")

            when (network.networkType) {
                NetworkType.EVM -> {
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
                                permit2Address = permit2,
                                feeAmount = request.feeAmount,
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
                                feeAmount = request.feeAmount,
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

    suspend fun previewGaslessDisplayPolicy(
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
                                    needsApprove = session.value.needsApprove
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
                                    needsApprove = session.value.needsApprove
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

    fun buildApproveTransaction(
        session: UnifiedGaslessSession,
        gasPrice: BigInteger? = null,
        gasLimit: BigInteger? = null,
        tronFeeLimit: Long = 15_000_000L,
        approveAmount: BigInteger? = null
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

    suspend fun requestTronSponsorForApprove(
        session: UnifiedGaslessSession,
        mode: TronSponsorMode = TronSponsorMode.GIFT
    ): ResultResponse<TronSponsorApproveResult> {
        return when (session) {
            is UnifiedGaslessSession.Tron -> tronGaslessCoordinator.requestSponsorForApprove(session.value, mode)
            is UnifiedGaslessSession.Evm -> ResultResponse.Error(
                IllegalStateException("Sponsor approve flow is only available for TRON gasless")
            )
        }
    }

    suspend fun quoteTronApproveRequirement(
        session: UnifiedGaslessSession
    ): ResultResponse<TronApproveQuoteResult> {
        return when (session) {
            is UnifiedGaslessSession.Tron -> tronGaslessCoordinator.quoteApproveRequirement(session.value)
            is UnifiedGaslessSession.Evm -> ResultResponse.Error(
                IllegalStateException("TRON approve quote is only available for TRON gasless")
            )
        }
    }

    suspend fun requestEvmSponsorForApprove(
        session: UnifiedGaslessSession,
        mode: EvmSponsorMode = EvmSponsorMode.GIFT
    ): ResultResponse<EvmSponsorApproveResult> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> evmGaslessCoordinator.requestSponsorForApprove(session.value, mode)
            is UnifiedGaslessSession.Tron -> ResultResponse.Error(
                IllegalStateException("Sponsor approve flow is only available for EVM gasless")
            )
        }
    }

    suspend fun submitGasless(session: UnifiedGaslessSession): ResultResponse<GaslessSubmission> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> {
                when (val queued = evmGaslessCoordinator.signAndSubmit(session.value)) {
                    is ResultResponse.Success -> {
                        pendingGaslessTxStore.put(
                            PendingGaslessTx(
                                chain = GaslessChain.EVM,
                                queueId = queued.data.id,
                                networkId = session.value.request.networkId,
                                walletId = walletRepository.getActiveWalletId()
                            )
                        )
                        ResultResponse.Success(
                            GaslessSubmission(
                                queueId = queued.data.id,
                                stage = queued.data.stage
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
                                chain = GaslessChain.TRON,
                                queueId = queued.data.id,
                                networkId = session.value.request.networkId,
                                walletId = walletRepository.getActiveWalletId()
                            )
                        )
                        ResultResponse.Success(
                            GaslessSubmission(
                                queueId = queued.data.id,
                                stage = queued.data.stage
                            )
                        )
                    }
                    is ResultResponse.Error -> queued
                }
            }
        }
    }

    suspend fun pollGaslessUntilFinal(
        session: UnifiedGaslessSession,
        queueId: String,
        pollIntervalMs: Long = 4_000L,
        timeoutMs: Long = 5 * 60_000L
    ): ResultResponse<GaslessFinalResult> {
        return when (session) {
            is UnifiedGaslessSession.Evm -> {
                when (
                    val status = evmGaslessCoordinator.pollUntilFinal(
                        txId = queueId,
                        pollIntervalMs = pollIntervalMs,
                        timeoutMs = timeoutMs
                    )
                ) {
                    is ResultResponse.Success -> {
                        if (status.data.isFinal) {
                            pendingGaslessTxStore.remove(GaslessChain.EVM, queueId)
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
                        pollIntervalMs = pollIntervalMs,
                        timeoutMs = timeoutMs
                    )
                ) {
                    is ResultResponse.Success -> {
                        if (status.data.isFinal) {
                            pendingGaslessTxStore.remove(GaslessChain.TRON, queueId)
                        }
                        ResultResponse.Success(GaslessFinalResult(queueId, status.data))
                    }
                    is ResultResponse.Error -> status
                }
            }
        }
    }

    fun getPendingGaslessTransactions(): List<PendingGaslessTx> {
        return pendingGaslessTxStore.getAll()
    }

    fun clearPendingGaslessTransactions() {
        pendingGaslessTxStore.clear()
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

    data class GaslessDisplayPreview(
        val displayPolicy: GaslessDisplayPolicyBundle?,
        val needsApprove: Boolean
    )
}
