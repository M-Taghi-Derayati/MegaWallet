package com.mtd.data.repository.gasless

import com.mtd.domain.model.core.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IGaslessEvmRepository
import com.mtd.domain.model.EvmPrepareData
import com.mtd.domain.model.EvmQueuedTx
import com.mtd.domain.model.EvmQuoteData
import com.mtd.domain.model.EvmQuoteRequest
import com.mtd.domain.model.EvmRelayPayload
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmTxStatus
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvmGaslessRepositoryImpl @Inject constructor(
    private val gaslessApiGateway: GaslessApiGateway,
    private val blockchainRegistry: BlockchainRegistry,
    private val okHttpClient: OkHttpClient
) : IGaslessEvmRepository {

    override suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger?
    ): ResultResponse<EvmPrepareData> {
        return gaslessApiGateway.prepare(
            chain = GaslessChain.EVM,
            userAddress = userAddress,
            startNonce = startNonce?.toString()
        )
    }

    override suspend fun quote(request: EvmQuoteRequest): ResultResponse<EvmQuoteData> {
        return gaslessApiGateway.quote(chain = GaslessChain.EVM, request = request)
    }

    override suspend fun submitRelay(
        payload: EvmRelayPayload,
        idempotencyKey: String
    ): ResultResponse<EvmQueuedTx> {
        return gaslessApiGateway.relay(
            payload = payload.copy(chain = GaslessChain.EVM),
            idempotencyKey = idempotencyKey
        )
    }

    override suspend fun getTxStatus(txId: String): ResultResponse<EvmTxStatus> {
        return gaslessApiGateway.getTxStatus(GaslessChain.EVM, txId)
    }

    override suspend fun sponsorApprove(request: EvmSponsorApproveRequest): ResultResponse<EvmSponsorApproveResult> {
        return gaslessApiGateway.sponsorEvmApprove(request)
    }

    override suspend fun getSupportedTokens(): ResultResponse<List<GaslessSupportedToken>> {
        return gaslessApiGateway.getSupportedTokens(GaslessChain.EVM)
    }

    override suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String
    ): ResultResponse<GaslessEligibilityResult> {
        return gaslessApiGateway.checkEligibility(
            chain = GaslessChain.EVM,
            service = service,
            userAddress = userAddress,
            tokenAddress = tokenAddress
        )
    }

    override suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger> {
        return safeApiCall {
            val network = requireEvmNetwork(networkId)
            executeWithRpcFailover(network.RpcUrlsEvm) { web3j ->
                val function = Function(
                    "allowance",
                    listOf(Address(ownerAddress), Address(spenderAddress)),
                    emptyList()
                )
                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                        ownerAddress,
                        tokenAddress,
                        FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
                ).sendAsync().await()

                if (response.hasError()) {
                    throw IllegalStateException("allowance call failed: ${response.error.message}")
                }

                decodeUint256Result(response.value)
            }
        }
    }

    override suspend fun getRelayerTreasury(
        networkId: String,
        relayerContractAddress: String
    ): ResultResponse<String> {
        return safeApiCall {
            val network = requireEvmNetwork(networkId)
            executeWithRpcFailover(network.RpcUrls) { web3j ->
                val function = Function(
                    "treasury",
                    emptyList(),
                    emptyList()
                )
                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                        null,
                        relayerContractAddress,
                        FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
                ).sendAsync().await()

                if (response.hasError()) {
                    throw IllegalStateException("treasury call failed: ${response.error.message}")
                }

                decodeAddressResult(response.value)
            }
        }
    }

    private fun decodeUint256Result(rawResult: String?): BigInteger {
        val clean = Numeric.cleanHexPrefix(rawResult ?: "")
        if (clean.isBlank()) return BigInteger.ZERO
        val word = if (clean.length >= 64) clean.takeLast(64) else clean.padStart(64, '0')
        return BigInteger(word, 16)
    }

    private fun decodeAddressResult(rawResult: String?): String {
        val clean = Numeric.cleanHexPrefix(rawResult ?: "")
        if (clean.isBlank()) {
            throw IllegalStateException("Invalid address result: empty response")
        }
        val word = if (clean.length >= 64) clean.takeLast(64) else clean.padStart(64, '0')
        return "0x${word.takeLast(40)}"
    }

    private fun requireEvmNetwork(networkId: String): BlockchainNetwork {
        val network = blockchainRegistry.getNetworkById(networkId)
            ?: throw IllegalStateException("Network not found for id: $networkId")
        if (network.networkType != NetworkType.EVM) {
            throw IllegalStateException("Network $networkId is not EVM")
        }
        return network
    }

    private suspend fun <T> executeWithRpcFailover(
        rpcUrls: List<String>,
        block: suspend (Web3j) -> T
    ): T {
        var lastError: Exception? = null
        for (rpc in rpcUrls) {
            try {
                return withTimeout(6_000L) {
                    val web3j = Web3jCache.getOrCreate(rpc, okHttpClient)
                    block(web3j)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All RPC URLs failed")
    }

    private object Web3jCache {
        private val cache = ConcurrentHashMap<String, Web3j>()

        fun getOrCreate(rpcUrl: String, okHttpClient: OkHttpClient): Web3j {
            return cache.getOrPut(rpcUrl) {
                Web3j.build(HttpService(rpcUrl, okHttpClient, false))
            }
        }
    }
}
