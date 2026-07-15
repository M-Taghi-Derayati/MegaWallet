package com.mtd.data.repository.gasless

import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IGaslessChainReader
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

/**
 * DIRECT-mode implementation of [IGaslessChainReader].
 *
 * Holds the on-chain read logic that previously lived (duplicated, with a leaky `RpcUrls`
 * vs `RpcUrlsEvm` inconsistency) inside [EvmGaslessRepositoryImpl] / [TronGaslessRepositoryImpl]:
 * an `eth_call` for token `allowance(owner,spender)` and the relayer contract `treasury()`,
 * with per-node failover over [BlockchainNetwork.RpcUrlsEvm] and a cached [Web3j] per RPC URL.
 *
 * Works purely in EVM-hex address space (Tron callers convert at the boundary). The future
 * PROXY-mode counterpart will implement this same contract against the relayer proxy.
 */
@Singleton
class DirectGaslessChainReader @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val okHttpClient: OkHttpClient
) : IGaslessChainReader {

    override suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger> = safeApiCall {
        val network = requireNetwork(networkId)
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

    override suspend fun getRelayerTreasury(
        networkId: String,
        relayerContractAddress: String
    ): ResultResponse<String> = safeApiCall {
        val network = requireNetwork(networkId)
        executeWithRpcFailover(network.RpcUrlsEvm) { web3j ->
            val function = Function("treasury", emptyList(), emptyList())
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

    private fun requireNetwork(networkId: String): BlockchainNetwork {
        return blockchainRegistry.getNetworkById(networkId)
            ?: throw IllegalStateException("Network not found for id: $networkId")
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

    private suspend fun <T> executeWithRpcFailover(
        rpcUrls: List<String>,
        block: suspend (Web3j) -> T
    ): T {
        var lastError: Exception? = null
        for (rpc in rpcUrls) {
            try {
                return withTimeout(RPC_TIMEOUT_MS) {
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

    private companion object {
        // Per-node failover cap for read-only eth_call. Unifies the previous
        // EvmGasless (6s) / TronGasless (20s) values to the more lenient 20s.
        private const val RPC_TIMEOUT_MS = 20_000L
    }
}
