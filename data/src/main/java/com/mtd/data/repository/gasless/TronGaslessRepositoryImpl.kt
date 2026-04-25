package com.mtd.data.repository.gasless

import com.mtd.domain.model.core.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.TronAddressConverter
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IGaslessTronRepository
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessQuoteRequest
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TronApproveQuoteRequest
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronSponsorApproveRequest
import com.mtd.domain.model.TronSponsorApproveResult
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
class TronGaslessRepositoryImpl @Inject constructor(
    private val gaslessApiGateway: GaslessApiGateway,
    private val blockchainRegistry: BlockchainRegistry,
    private val okHttpClient: OkHttpClient
) : IGaslessTronRepository {

    override suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger?
    ): ResultResponse<GaslessPrepareData> {
        return gaslessApiGateway.prepare(
            chain = GaslessChain.TRON,
            userAddress = userAddress,
            startNonce = startNonce?.toString()
        )
    }

    override suspend fun quote(request: GaslessQuoteRequest): ResultResponse<GaslessQuoteData> {
        return gaslessApiGateway.quote(chain = GaslessChain.TRON, request = request)
    }

    override suspend fun submitRelay(
        payload: GaslessRelayPayload,
        idempotencyKey: String
    ): ResultResponse<GaslessQueuedTx> {
        return gaslessApiGateway.relay(
            payload = payload.copy(chain = GaslessChain.TRON),
            idempotencyKey = idempotencyKey
        )
    }

    override suspend fun getTxStatus(txId: String): ResultResponse<GaslessTxStatus> {
        return gaslessApiGateway.getTxStatus(GaslessChain.TRON, txId)
    }

    override suspend fun getSupportedTokens(): ResultResponse<List<GaslessSupportedToken>> {
        return gaslessApiGateway.getSupportedTokens(GaslessChain.TRON)
    }

    override suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String
    ): ResultResponse<GaslessEligibilityResult> {
        return gaslessApiGateway.checkEligibility(
            chain = GaslessChain.TRON,
            service = service,
            userAddress = userAddress,
            tokenAddress = tokenAddress
        )
    }

    override suspend fun quoteApprove(request: TronApproveQuoteRequest): ResultResponse<TronApproveQuoteResult> {
        return gaslessApiGateway.quoteTronApprove(GaslessChain.TRON,request)
    }

    override suspend fun sponsorApprove(request: TronSponsorApproveRequest): ResultResponse<TronSponsorApproveResult> {
        return gaslessApiGateway.sponsorTronApprove(request)
    }

    override suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger> {
        return safeApiCall {
            val network = requireTronNetwork(networkId)
            val tokenEvm = TronAddressConverter.tronToEvm(tokenAddress)
            val ownerEvm = TronAddressConverter.tronToEvm(ownerAddress)
            val spenderEvm = TronAddressConverter.tronToEvm(spenderAddress)

            executeWithRpcFailover(network.RpcUrlsEvm) { web3j ->
                val function = Function(
                    "allowance",
                    listOf(Address(ownerEvm), Address(spenderEvm)),
                    emptyList()
                )
                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                        ownerEvm,
                        tokenEvm,
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
            val network = requireTronNetwork(networkId)
            val relayerEvm = TronAddressConverter.tronToEvm(relayerContractAddress)

            executeWithRpcFailover(network.RpcUrlsEvm) { web3j ->
                val function = Function(
                    "treasury",
                    emptyList(),
                    emptyList()
                )
                val response = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                        null,
                        relayerEvm,
                        FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
                ).sendAsync().await()

                if (response.hasError()) {
                    throw IllegalStateException("treasury call failed: ${response.error.message}")
                }

                val treasuryEvm = decodeAddressResult(response.value)
                TronAddressConverter.evmToTron(treasuryEvm)
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

    private fun requireTronNetwork(networkId: String): BlockchainNetwork {
        val network = blockchainRegistry.getNetworkById(networkId)
            ?: throw IllegalStateException("Network not found for id: $networkId")
        if (network.networkType != NetworkType.TVM) {
            throw IllegalStateException("Network $networkId is not TRON/TVM")
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
                return withTimeout(20_000L) {
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
