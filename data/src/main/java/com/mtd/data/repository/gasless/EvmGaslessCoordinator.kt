package com.mtd.data.repository.gasless

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.model.core.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.EvmAbiEncoder
import com.mtd.core.utils.TypedDataSigner
import com.mtd.domain.interfaceRepository.IGaslessEvmRepository
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.EvmGaslessSession
import com.mtd.domain.model.EvmGaslessTransferRequest
import com.mtd.domain.model.EvmQuoteRequest
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.EvmRelayParams
import com.mtd.domain.model.EvmRelayPayload
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmSponsorMode
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessCoordinatorState
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvmGaslessCoordinator @Inject constructor(
    private val gaslessRepository: IGaslessEvmRepository,
    private val walletRepository: IWalletRepository,
    private val keyManager: KeyManager,
    private val blockchainRegistry: BlockchainRegistry
) {

    companion object {
        private const val DEFAULT_DEADLINE_SECONDS: Long = 20 * 60
    }

    private val _state = MutableStateFlow(GaslessCoordinatorState.INIT)
    val state: StateFlow<GaslessCoordinatorState> = _state.asStateFlow()

    suspend fun getSupportedTokens(): ResultResponse<List<GaslessSupportedToken>> {
        return gaslessRepository.getSupportedTokens()
    }

    suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String
    ): ResultResponse<GaslessEligibilityResult> {
        return gaslessRepository.checkEligibility(
            service = service,
            userAddress = userAddress,
            tokenAddress = tokenAddress
        )
    }

    suspend fun previewQuote(session: EvmGaslessSession): ResultResponse<GaslessQuoteData> {
        return gaslessRepository.quote(
            EvmQuoteRequest(
                prepareToken = session.prepareToken,
                user = session.userAddress,
                token = session.request.tokenAddress,
                target = session.request.targetAddress,
                amount = session.request.amount,
                clientFeeAmount = session.request.feeAmount
            )
        )
    }

    suspend fun prepareSession(request: EvmGaslessTransferRequest): ResultResponse<EvmGaslessSession> {
        return try {
            _state.value = GaslessCoordinatorState.PREPARING

            val network = blockchainRegistry.getNetworkById(request.networkId)
                ?: throw IllegalStateException("Network not found: ${request.networkId}")
            if (network.networkType != NetworkType.EVM) {
                throw IllegalStateException("Selected network is not EVM")
            }

            val userAddress = walletRepository.getActiveAddressForNetwork(request.networkId)
                ?: throw IllegalStateException("Active wallet address not found for ${request.networkId}")

            val prepareData = when (val response = gaslessRepository.prepare(userAddress)) {
                is ResultResponse.Success -> response.data
                is ResultResponse.Error -> throw response.exception
            }
            if (network.chainId != null && network.chainId != prepareData.chainId) {
                throw IllegalStateException(
                    "Chain mismatch. App network=${network.chainId}, backend=${prepareData.chainId}"
                )
            }

            _state.value = GaslessCoordinatorState.CHECKING_ALLOWANCE
            val allowance = when (
                val response = gaslessRepository.getAllowance(
                    networkId = request.networkId,
                    tokenAddress = request.tokenAddress,
                    ownerAddress = userAddress,
                    spenderAddress = request.permit2Address
                )
            ) {
                is ResultResponse.Success -> response.data
                is ResultResponse.Error -> throw response.exception
            }

            val treasury = prepareData.treasuryAddress ?: when (
                val response = gaslessRepository.getRelayerTreasury(
                    networkId = request.networkId,
                    relayerContractAddress = prepareData.relayerContract
                )
            ) {
                is ResultResponse.Success -> response.data
                is ResultResponse.Error -> throw response.exception
            }

            val session = EvmGaslessSession(
                request = request,
                networkName = network.name,
                userAddress = userAddress,
                chainId = prepareData.chainId,
                relayerContract = prepareData.relayerContract,
                treasuryAddress = treasury,
                nonce = prepareData.nonce,
                allowance = allowance,
                prepareToken = prepareData.prepareToken,
                idempotencyKey = UUID.randomUUID().toString()
            )

            _state.value = if (session.needsApprove) {
                GaslessCoordinatorState.NEEDS_APPROVE
            } else {
                GaslessCoordinatorState.SIGNING_GASLESS
            }

            ResultResponse.Success(session)
        } catch (e: Exception) {
            _state.value = GaslessCoordinatorState.FAILED
            ResultResponse.Error(e)
        }
    }

    fun buildApproveTransaction(
        session: EvmGaslessSession,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        approveAmount: BigInteger = session.request.amount
    ): TransactionParams.Evm {
        _state.value = GaslessCoordinatorState.AWAITING_APPROVE_CONFIRMATION

        return TransactionParams.Evm(
            networkName = session.networkName,
            to = session.request.tokenAddress,
            amount = BigInteger.ZERO,
            data = EvmAbiEncoder.encodeApprove(session.request.permit2Address, approveAmount),
            gasPrice = gasPrice,
            gasLimit = gasLimit
        )
    }

    suspend fun requestSponsorForApprove(
        session: EvmGaslessSession,
        mode: EvmSponsorMode = EvmSponsorMode.GIFT
    ): ResultResponse<EvmSponsorApproveResult> {
        return try {
            if (!session.needsApprove) {
                _state.value = GaslessCoordinatorState.SIGNING_GASLESS
                return ResultResponse.Success(
                    EvmSponsorApproveResult(
                        funded = true,
                        mode = mode,
                        amount = null,
                        reason = "ALLOWANCE_ALREADY_OK",
                        txHash = null
                    )
                )
            }

            val response = gaslessRepository.sponsorApprove(
                EvmSponsorApproveRequest(
                    userAddress = session.userAddress,
                    tokenAddress = session.request.tokenAddress,
                    mode = mode
                )
            )
            when (response) {
                is ResultResponse.Success -> {
                    _state.value = if (response.data.funded) {
                        GaslessCoordinatorState.AWAITING_APPROVE_CONFIRMATION
                    } else {
                        GaslessCoordinatorState.NEEDS_APPROVE
                    }
                    response
                }
                is ResultResponse.Error -> {
                    _state.value = GaslessCoordinatorState.NEEDS_APPROVE
                    response
                }
            }
        } catch (e: Exception) {
            _state.value = GaslessCoordinatorState.NEEDS_APPROVE
            ResultResponse.Error(e)
        }
    }

    suspend fun signAndSubmit(session: EvmGaslessSession): ResultResponse<com.mtd.domain.model.EvmQueuedTx> {
        return try {
            if (session.needsApprove) {
                throw IllegalStateException("Insufficient allowance. Approve flow must be completed first.")
            }

            _state.value = GaslessCoordinatorState.SIGNING_GASLESS
            val credentials = keyManager.getCredentialsForChain(session.chainId)
                ?: throw IllegalStateException("Wallet is locked. Credentials unavailable for chain ${session.chainId}")

            val quote = when (
                val response = gaslessRepository.quote(
                    EvmQuoteRequest(
                        prepareToken = session.prepareToken,
                        user = session.userAddress,
                        token = session.request.tokenAddress,
                        target = session.request.targetAddress,
                        amount = session.request.amount,
                        clientFeeAmount = session.request.feeAmount
                    )
                )
            ) {
                is ResultResponse.Success -> response.data
                is ResultResponse.Error -> throw response.exception
            }
            val canonical = quote.canonicalParams
            val deadline = canonical.deadline
            val nonce = canonical.nonce
            val feeAmount = canonical.feeAmount
            val treasury = canonical.treasury

            val permitSignature = TypedDataSigner.signTypedDataHex(
                credentials = credentials,
                primaryType = "PermitTransferFrom",
                types = permit2Types(),
                domain = mapOf(
                    "name" to "Permit2",
                    "chainId" to session.chainId,
                    "verifyingContract" to session.request.permit2Address
                ),
                message = mapOf(
                    "permitted" to mapOf(
                        "token" to session.request.tokenAddress,
                        "amount" to session.request.amount.toString()
                    ),
                    "spender" to session.relayerContract,
                    "nonce" to nonce.toString(),
                    "deadline" to deadline.toString()
                )
            )

            val megaSignature = TypedDataSigner.signTypedDataHex(
                credentials = credentials,
                primaryType = "MegaTransfer",
                types = megaTransferTypes(),
                domain = mapOf(
                    "name" to "MegaRelayer",
                    "version" to "1.0.0",
                    "chainId" to session.chainId,
                    "verifyingContract" to session.relayerContract
                ),
                message = mapOf(
                    "user" to session.userAddress,
                    "token" to session.request.tokenAddress,
                    "amount" to session.request.amount.toString(),
                    "feeAmount" to feeAmount.toString(),
                    "target" to session.request.targetAddress,
                    "treasury" to treasury,
                    "nonce" to nonce.toString(),
                    "deadline" to deadline.toString()
                )
            )

            _state.value = GaslessCoordinatorState.SUBMITTING_RELAY
            val relayPayload = EvmRelayPayload(
                chain = GaslessChain.EVM,
                quoteToken = quote.quoteToken,
                params = EvmRelayParams(
                    user = session.userAddress,
                    token = session.request.tokenAddress,
                    target = session.request.targetAddress,
                    amount = session.request.amount,
                    feeAmount = feeAmount,
                    nonce = nonce,
                    deadline = deadline
                ),
                permitSignature = permitSignature,
                megaSignature = megaSignature
            )

            when (
                val response = gaslessRepository.submitRelay(
                    payload = relayPayload,
                    idempotencyKey = session.idempotencyKey
                )
            ) {
                is ResultResponse.Success -> {
                    _state.value = GaslessCoordinatorState.QUEUED
                    response
                }
                is ResultResponse.Error -> throw response.exception
            }
        } catch (e: Exception) {
            _state.value = GaslessCoordinatorState.FAILED
            ResultResponse.Error(e)
        }
    }

    suspend fun pollUntilFinal(
        txId: String,
        pollIntervalMs: Long = 4_000L,
        timeoutMs: Long = 5 * 60_000L
    ): ResultResponse<com.mtd.domain.model.EvmTxStatus> {
        val startedAt = System.currentTimeMillis()
        _state.value = GaslessCoordinatorState.PROCESSING

        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            when (val response = gaslessRepository.getTxStatus(txId)) {
                is ResultResponse.Success -> {
                    val status = response.data
                    when {
                        status.status.equals("SUCCESS", ignoreCase = true) -> {
                            _state.value = GaslessCoordinatorState.SUCCESS
                            return ResultResponse.Success(status)
                        }

                        status.status.equals("FAILED", ignoreCase = true) -> {
                            _state.value = GaslessCoordinatorState.FAILED
                            return ResultResponse.Success(status)
                        }
                        status.status.equals("TIMEOUT", ignoreCase = true) -> {
                            _state.value = GaslessCoordinatorState.FAILED
                            return ResultResponse.Success(status)
                        }

                        status.status.equals("QUEUED", ignoreCase = true) -> {
                            _state.value = GaslessCoordinatorState.QUEUED
                        }

                        else -> {
                            _state.value = GaslessCoordinatorState.PROCESSING
                        }
                    }
                }

                is ResultResponse.Error -> {
                    _state.value = GaslessCoordinatorState.FAILED
                    return response
                }
            }
            delay(pollIntervalMs)
        }

        _state.value = GaslessCoordinatorState.FAILED
        return ResultResponse.Error(IllegalStateException("Timeout while waiting for tx status"))
    }

    private fun permit2Types(): Map<String, List<Map<String, String>>> {
        return mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "TokenPermissions" to listOf(
                mapOf("name" to "token", "type" to "address"),
                mapOf("name" to "amount", "type" to "uint256")
            ),
            "PermitTransferFrom" to listOf(
                mapOf("name" to "permitted", "type" to "TokenPermissions"),
                mapOf("name" to "spender", "type" to "address"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "deadline", "type" to "uint256")
            )
        )
    }

    private fun megaTransferTypes(): Map<String, List<Map<String, String>>> {
        return mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "version", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "MegaTransfer" to listOf(
                mapOf("name" to "user", "type" to "address"),
                mapOf("name" to "token", "type" to "address"),
                mapOf("name" to "amount", "type" to "uint256"),
                mapOf("name" to "feeAmount", "type" to "uint256"),
                mapOf("name" to "target", "type" to "address"),
                mapOf("name" to "treasury", "type" to "address"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "deadline", "type" to "uint256")
            )
        )
    }
}
