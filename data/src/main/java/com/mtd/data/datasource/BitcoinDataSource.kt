package com.mtd.data.datasource

import com.fasterxml.jackson.databind.JsonNode
import com.mtd.domain.model.core.NetworkName
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.datasource.IChainDataSource.FeeData
import com.mtd.data.dto.PushTxRequest
import com.mtd.data.service.BTCApiService
import com.mtd.data.service.UtxoApiService
import com.mtd.data.utils.AssetNormalizer.normalize
import com.mtd.domain.model.Asset
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoinj.base.Address
import org.bitcoinj.base.Coin
import org.bitcoinj.base.ScriptType
import org.bitcoinj.base.Sha256Hash
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionWitness
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.script.Script.parse
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptBuilder.createOutputScript
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.http.HttpService
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class BitcoinDataSource(
    private val network: BlockchainNetwork,
    private val retrofitBuilder: Retrofit.Builder,
    private val networkParameters: NetworkParameters,
    private val okHttpClient: OkHttpClient
) : IChainDataSource {

    private enum class UtxoBackend {
        MEMPOOL,
        BLOCKCYPHER
    }

    private data class SpendableUtxo(
        val txid: String,
        val vout: Int,
        val value: Long
    )

    private data class BlockCypherRoute(
        val coin: String,
        val chain: String
    )

    private val backend: UtxoBackend by lazy {
        when (network.name) {
            NetworkName.BITCOIN,
            NetworkName.BITCOINTESTNET -> UtxoBackend.MEMPOOL

            else -> UtxoBackend.BLOCKCYPHER
        }
    }

    private class BitcoinRpcResponse : Response<JsonNode>()

    private val currentRpcIndex = AtomicInteger(0)
    private var currentWeb3j: Web3j? = null

    private object BitcoinRpcFactory {
        private val cache = ConcurrentHashMap<String, HttpService>()

        fun getOrCreate(rpcUrl: String, okHttpClient: OkHttpClient): HttpService {
            val normalizedUrl = if (rpcUrl.endsWith("/")) rpcUrl else "$rpcUrl/"
            return cache.getOrPut(normalizedUrl) {
                HttpService(normalizedUrl, okHttpClient, false)
            }
        }
    }

    private suspend fun <T> withMempoolRpcFallback(
        operation: String,
        primary: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        return try {
            primary()

        } catch (e: Exception) {
            if (!canUseRpcFallback()) throw e
            Timber.w(
                e,
                "Mempool failed for $operation on ${network.name}; falling back to BTC RPC."
            )
            fallback()
        }
    }


    private fun canUseRpcFallback(): Boolean {
        return backend == UtxoBackend.MEMPOOL && network.RpcUrlsEvm.isNotEmpty()
    }

    private suspend fun callBitcoinRpc(
        method: String,
        params: List<Any?> = emptyList(),
        timeoutMs: Long = 20_000L
    ): Any {
        if (network.RpcUrlsEvm.isEmpty()) {
            throw IllegalStateException("No BTC RPC URLs configured for ${network.id}")
        }

        var lastError: Exception? = null
        for (rpcUrl in network.RpcUrlsEvm) {
            try {
                val service = BitcoinRpcFactory.getOrCreate(rpcUrl, okHttpClient)
                val request = Request<Any, BitcoinRpcResponse>(
                    method,
                    params,
                    service,
                    BitcoinRpcResponse::class.java
                )
                val response = withTimeout(timeoutMs) { request.send() }
                response.error?.let { error ->
                    if (error.code == -32601) {
                        throw UnsupportedOperationException(
                            "Bitcoin RPC method not supported on $rpcUrl: $method"
                        )
                    }
                    throw IllegalStateException(
                        "Bitcoin RPC error on $rpcUrl [$method]: ${error.code} ${error.message}"
                    )
                }
                val resultNode = response.result ?: throw IllegalStateException(
                    "Bitcoin RPC returned null result on $rpcUrl [$method]"
                )
                return jsonNodeToValue(resultNode) ?: throw IllegalStateException(
                    "Bitcoin RPC returned empty result on $rpcUrl [$method]"
                )
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("All Bitcoin RPC URLs failed for method $method")
    }

    private fun jsonNodeToValue(node: JsonNode): Any? {
        return when {
            node.isNull -> null
            node.isObject -> node.fields().asSequence().associate { (key, value) ->
                key to jsonNodeToValue(value)
            }
            node.isArray -> node.map { child -> jsonNodeToValue(child) }
            node.isTextual -> node.asText()
            node.isIntegralNumber -> node.asLong()
            node.isFloatingPointNumber -> node.decimalValue()
            node.isBoolean -> node.asBoolean()
            else -> node.asText()
        }
    }


    private suspend fun scanTxOutSet(address: String): Map<*, *> {
        repeat(3) { attempt ->
            try {
                val result = callBitcoinRpc(
                    method = "scantxoutset",
                    params = listOf("start", listOf("addr($address)")),
                    timeoutMs = 120_000L
                ) as? Map<*, *>
                return result ?: throw IllegalStateException("Invalid scantxoutset response")
            } catch (e: Exception) {
                val busyScan =
                    e.message.orEmpty().contains("Scan already in progress", ignoreCase = true)
                if (!busyScan || attempt == 2) throw e
                Timber.w(e, "scantxoutset busy; retrying for $address")
                delay(2_000L)
            }
        }
        throw IllegalStateException("scantxoutset failed for $address")
    }

    private suspend fun broadcastViaRpc(txHex: String): String {
        return try {
            val result = callBitcoinRpc("sendrawtransaction", listOf(txHex))
            result.toString()
        } catch (e: Exception) {
            if (!e.message.orEmpty().contains("Method not supported", ignoreCase = true)) {
                throw e
            }
            broadcastViaMempool(txHex)
        }
    }


    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val rawBalance = when (backend) {
                    UtxoBackend.MEMPOOL -> withMempoolRpcFallback(
                        operation = "getBalance",
                        primary = { getBalanceFromMempool(address) },
                        fallback = { getBalanceFromRpc(address) }
                    )

                    UtxoBackend.BLOCKCYPHER -> getBalanceFromBlockCypher(address)
                }
                normalize(rawBalance, network.decimals, network.name)
            }.fold(
                onSuccess = { ResultResponse.Success(it) },
                onFailure = { ResultResponse.Error(it) }
            )
        }
    }


    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                when (backend) {
                    UtxoBackend.MEMPOOL -> withMempoolRpcFallback(
                        operation = "getTransactionHistory",
                        primary = { getHistoryFromMempool(address) },
                        fallback = { getHistoryFromRpc(address) }
                    )

                    UtxoBackend.BLOCKCYPHER -> getHistoryFromBlockCypher(address)
                }
            }.fold(
                onSuccess = { ResultResponse.Success(it) },
                onFailure = { ResultResponse.Error(it) }
            )
        }
    }

    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> = withContext(Dispatchers.IO) {
        if (params !is TransactionParams.Utxo) {
            return@withContext ResultResponse.Error(
                IllegalArgumentException("Invalid params type for UTXO data source")
            )
        }

        runCatching {
            val key = ECKey.fromPrivate(privateKeyHex.hexToBytes())
            val inputScriptType = resolveInputScriptType()
            val fromAddress = key.toAddress(inputScriptType, networkParameters.network())

            val spendableUtxos = when (backend) {
                UtxoBackend.MEMPOOL -> {
                    val mempoolUtxos = runCatching {
                        loadUtxosFromMempool(fromAddress.toString())
                    }.getOrElse { error ->
                        if (!canUseRpcFallback()) throw error
                        Timber.w(error, "Mempool UTXO loading failed. Falling back to BTC RPC.")
                        loadUtxosFromRpc(fromAddress.toString())

                    }
                    if (mempoolUtxos.isNotEmpty()) {
                        mempoolUtxos
                    } else if (canUseRpcFallback()) {
                        loadUtxosFromRpc(fromAddress.toString())
                    } else {
                        mempoolUtxos
                    }
                }

                UtxoBackend.BLOCKCYPHER -> loadUtxosFromBlockCypher(fromAddress.toString())
            }
            if (spendableUtxos.isEmpty()) {
                throw IllegalStateException("No confirmed UTXOs available to spend.")
            }

            val selectedInputs = mutableListOf<SpendableUtxo>()
            var totalInputValue = 0L
            for (utxo in spendableUtxos.sortedBy { it.value }) {
                totalInputValue += utxo.value
                selectedInputs.add(utxo)
                if (totalInputValue >= params.amountInSatoshi) break
            }

            if (totalInputValue < params.amountInSatoshi) {
                throw IllegalStateException("Insufficient funds to cover amount.")
            }

            val finalFee = estimateTxFeeBasedOnRate(
                inputs = selectedInputs.size,
                outputs = 2,
                feeRate = params.feeRateInSatsPerByte,
                scriptType = inputScriptType
            )
            val requiredTotal = params.amountInSatoshi + finalFee
            if (totalInputValue < requiredTotal) {
                throw IllegalStateException(
                    "Insufficient funds to cover transaction fee. Required: $requiredTotal, Available: $totalInputValue"
                )
            }

            val tx = Transaction(networkParameters)
            selectedInputs.forEach { utxo ->
                tx.addInput(Sha256Hash.wrap(utxo.txid), utxo.vout.toLong(), parse(ByteArray(0)))
            }

            val toAddress = Address.fromString(networkParameters, params.toAddress)
            tx.addOutput(Coin.valueOf(params.amountInSatoshi), toAddress)

            val changeAmount = totalInputValue - params.amountInSatoshi - finalFee
            if (changeAmount >= dustThreshold()) {
                tx.addOutput(Coin.valueOf(changeAmount), fromAddress)
            } else if (changeAmount > 0L) {
                Timber.d("Dust change ($changeAmount sats) is added to miner fee.")
            }

            val signedInputs = when (inputScriptType) {
                ScriptType.P2WPKH -> signSegwitInputs(tx, selectedInputs, key)
                ScriptType.P2PKH -> signLegacyInputs(tx, key, fromAddress)
                else -> throw IllegalStateException("Unsupported UTXO script type: $inputScriptType")
            }

            tx.clearInputs()
            signedInputs.forEach(tx::addInput)

            val txHex = tx.serialize().toHexString()
            when (backend) {
                UtxoBackend.MEMPOOL -> withMempoolRpcFallback(
                    operation = "broadcastTransaction",
                    primary = { broadcastViaMempool(txHex) },
                    fallback = { broadcastViaRpc(txHex) }
                )

                UtxoBackend.BLOCKCYPHER -> broadcastViaBlockCypher(txHex)
            }
        }.fold(
            onSuccess = { ResultResponse.Success(it) },
            onFailure = { ResultResponse.Error(it) }
        )
    }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?
    ): ResultResponse<List<FeeData>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                when (backend) {
                    UtxoBackend.MEMPOOL -> withMempoolRpcFallback(
                        operation = "getFeeOptions",
                        primary = { getFeeOptionsFromMempool() },
                        fallback = { getFeeOptionsFromRpc() }
                    )

                    UtxoBackend.BLOCKCYPHER -> getFeeOptionsFromBlockCypher()
                }
            }.fold(
                onSuccess = { ResultResponse.Success(it) },
                onFailure = { ResultResponse.Error(it) }
            )
        }
    }

    override fun getWeb3jInstance(): Web3j {
        throw UnsupportedOperationException("Web3j is not available for UTXO data source")
    }

    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> {
        return when (val balanceResult = getBalance(address)) {
            is ResultResponse.Error -> ResultResponse.Error(balanceResult.exception)
            is ResultResponse.Success -> {
                val (name, symbol) = nativeAssetMeta()
                ResultResponse.Success(
                    listOf(
                        Asset(
                            name = name,
                            symbol = symbol,
                            decimals = network.decimals,
                            contractAddress = null,
                            balance = balanceResult.data
                        )
                    )
                )
            }
        }
    }

    override suspend fun getBalancesForMultipleAddresses(addresses: List<String>): ResultResponse<Map<String, List<Asset>>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                addresses.map { address ->
                    async {
                        val assets = when (val result = getBalanceAssets(address)) {
                            is ResultResponse.Success -> result.data
                            is ResultResponse.Error -> emptyList()
                        }
                        address to assets
                    }
                }.awaitAll().toMap()
            }.fold(
                onSuccess = { ResultResponse.Success(it) },
                onFailure = { ResultResponse.Error(it) }
            )
        }
    }

    private suspend fun getBalanceFromMempool(address: String): Long {
        val api = mempoolApi()
        val response = api.getAddressDetails(address)
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("Mempool API error: ${response.code()}")
        }
        val body = response.body()!!
        return body.chain_stats.funded_txo_sum - body.chain_stats.spent_txo_sum
    }

    private suspend fun getBalanceFromRpc(address: String): Long {
        return runCatching {
            val scan = scanTxOutSet(address)
            val totalAmountBtc =
                scan["total_amount"]?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            btcToSats(totalAmountBtc)
        }.getOrElse { primaryError ->
            Timber.w(
                primaryError,
                "scantxoutset balance failed for $address, trying addressindex RPC."
            )
            val response = callBitcoinRpc(
                method = "getaddressbalance",
                params = listOf(mapOf("addresses" to listOf(address)))
            ) as? Map<*, *> ?: throw IllegalStateException("Invalid getaddressbalance response")
            response["balance"]?.toString()?.toLongOrNull()
                ?: throw IllegalStateException("Missing balance field in getaddressbalance response")
        }
    }

    private suspend fun loadUtxosFromRpc(address: String): List<SpendableUtxo> {
        return runCatching {
            val scan = scanTxOutSet(address)
            val unspents = scan["unspents"] as? List<*> ?: return@runCatching emptyList()
            unspents.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val txid = map["txid"]?.toString().orEmpty()
                val vout = map["vout"]?.toString()?.toIntOrNull()
                val amountBtc = map["amount"]?.toString()?.toBigDecimalOrNull()
                val value = amountBtc?.let { btcToSats(it) }
                if (txid.isBlank() || vout == null || value == null || value <= 0L) {
                    null
                } else {
                    SpendableUtxo(txid = txid, vout = vout, value = value)
                }
            }
        }.getOrElse { primaryError ->
            Timber.w(
                primaryError,
                "scantxoutset UTXO failed for $address, trying addressindex RPC."
            )
            val utxos = callBitcoinRpc(
                method = "getaddressutxos",
                params = listOf(mapOf("addresses" to listOf(address)))
            ) as? List<*> ?: throw IllegalStateException("Invalid getaddressutxos response")
            utxos.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val txid = map["txid"]?.toString().orEmpty()
                val vout = map["outputIndex"]?.toString()?.toIntOrNull()
                val value = map["satoshis"]?.toString()?.toLongOrNull()
                if (txid.isBlank() || vout == null || value == null || value <= 0L) {
                    null
                } else {
                    SpendableUtxo(txid = txid, vout = vout, value = value)
                }
            }
        }
    }

    private suspend fun getHistoryFromRpc(address: String): List<TransactionRecord> {
        return runCatching {
            val txIds = callBitcoinRpc(
                method = "getaddresstxids",
                params = listOf(mapOf("addresses" to listOf(address)))
            ) as? List<*> ?: return@runCatching emptyList()

            txIds.mapNotNull { it?.toString()?.takeIf { hash -> hash.isNotBlank() } }.map { hash ->
                BitcoinTransaction(
                    hash = hash,
                    amount = BigInteger.ZERO,
                    fromAddress = null,
                    toAddress = null,
                    fee = BigInteger.ZERO,
                    timestamp = 0L,
                    status = TransactionStatus.PENDING,
                    networkName = network.name,
                    isOutgoing = false
                )
            }
        }.getOrElse {
            // PublicNode BTC nodes usually do not expose address index methods.
            // Build minimal history from currently unspent outputs.
            loadUtxosFromRpc(address).map { utxo ->
                BitcoinTransaction(
                    hash = utxo.txid,
                    amount = utxo.value.toBigInteger(),
                    fromAddress = null,
                    toAddress = address,
                    fee = BigInteger.ZERO,
                    timestamp = 0L,
                    status = TransactionStatus.PENDING,
                    networkName = network.name,
                    isOutgoing = false
                )
            }
        }
    }

    private suspend fun getBalanceFromBlockCypher(address: String): Long {
        val route = blockCypherRoute()
        val api = blockCypherApi()
        val response = api.getAddressData(
            coin = route.coin,
            chain = route.chain,
            address = address,
            limit = 50
        )
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("BlockCypher API error: ${response.code()}")
        }
        val body = response.body()!!
        return body.finalBalance ?: body.balance ?: 0L
    }

    private suspend fun getHistoryFromMempool(address: String): List<TransactionRecord> {
        val api = mempoolApi()
        val response = api.getConfirmedTransactions(address)
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("Mempool API error: ${response.code()}")
        }

        return response.body()!!.map { tx ->
            val fromAddress = tx.vin.firstOrNull()?.prevout?.scriptpubkey_address
            val toOutput = tx.vout.firstOrNull { it.scriptpubkey_address != fromAddress }
            val toAddress = toOutput?.scriptpubkey_address
            val amount = toOutput?.value ?: 0L

            BitcoinTransaction(
                hash = tx.txid,
                amount = amount.toBigInteger(),
                fromAddress = fromAddress,
                toAddress = toAddress,
                fee = BigInteger.valueOf(tx.fee ?: 0L),
                timestamp = tx.status.block_time ?: 0L,
                status = if (tx.status.confirmed == true) {
                    TransactionStatus.CONFIRMED
                } else {
                    TransactionStatus.PENDING
                },
                networkName = network.name,
                isOutgoing = fromAddress == address
            )
        }
    }

    private suspend fun getHistoryFromBlockCypher(address: String): List<TransactionRecord> {
        val route = blockCypherRoute()
        val api = blockCypherApi()
        val response = api.getAddressData(
            coin = route.coin,
            chain = route.chain,
            address = address,
            limit = 50
        )
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("BlockCypher API error: ${response.code()}")
        }

        val refs =
            (response.body()!!.txRefs.orEmpty() + response.body()!!.unconfirmedTxRefs.orEmpty())
        if (refs.isEmpty()) return emptyList()

        val txDetailByHash = coroutineScope {
            refs.map { it.txHash }
                .distinct()
                .map { txHash ->
                    async {
                        txHash to runCatching {
                            fetchBlockCypherTransactionDetail(route, txHash)
                        }.getOrNull()
                    }
                }
                .awaitAll()
                .toMap()
        }

        return refs
            .groupBy { it.txHash }
            .map { (txHash, txRefs) ->
                val txDetail = txDetailByHash[txHash]
                val received = txRefs
                    .filter { (it.txOutputN ?: -1) >= 0 }
                    .sumOf { it.value ?: 0L }
                val sent = txRefs
                    .filter { (it.txInputN ?: -1) >= 0 }
                    .sumOf { it.value ?: 0L }
                val net = received - sent
                val isOutgoing = net < 0
                val amount = if (net < 0) -net else net
                val anyConfirmed =
                    txDetail?.confirmations ?: (txRefs.maxOfOrNull { it.confirmations ?: 0 } ?: 0)
                val timestamp = listOfNotNull(
                    parseIsoTimestamp(txDetail?.confirmed),
                    parseIsoTimestamp(txDetail?.received),
                    txRefs.mapNotNull { parseIsoTimestamp(it.confirmed) }.maxOrNull()
                ).maxOrNull()
                    ?: 0L
                val fee = BigInteger.valueOf((txDetail?.fees ?: 0L).coerceAtLeast(0L))

                BitcoinTransaction(
                    hash = txHash,
                    amount = amount.toBigInteger(),
                    fromAddress = if (isOutgoing) address else null,
                    toAddress = if (isOutgoing) null else address,
                    fee = fee,
                    timestamp = timestamp,
                    status = if (anyConfirmed > 0) {
                        TransactionStatus.CONFIRMED
                    } else {
                        TransactionStatus.PENDING
                    },
                    networkName = network.name,
                    isOutgoing = isOutgoing
                )
            }
            .sortedByDescending { it.timestamp }
    }

    private suspend fun fetchBlockCypherTransactionDetail(
        route: BlockCypherRoute,
        txHash: String
    ) = blockCypherApi().run {
        val response = getTransaction(
            coin = route.coin,
            chain = route.chain,
            txHash = txHash
        )
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("BlockCypher tx API error for $txHash: ${response.code()}")
        }
        response.body()!!
    }

    private fun resolveInputScriptType(): ScriptType {
        return when (network.name) {
            NetworkName.DOGE,
            NetworkName.DOGETESTNET -> ScriptType.P2PKH

            else -> ScriptType.P2WPKH
        }
    }

    private fun dustThreshold(): Long {
        return when (network.name) {
            NetworkName.DOGE,
            NetworkName.DOGETESTNET -> 1_000_000L // 0.01 DOGE
            else -> 546L
        }
    }

    private suspend fun loadUtxosFromMempool(address: String): List<SpendableUtxo> {
        val api = mempoolApi()
        val utxoResponse = api.getUtxos(address)
        if (!utxoResponse.isSuccessful) {
            throw IllegalStateException("Mempool UTXO API error: ${utxoResponse.code()}")
        }
        if (utxoResponse.body().isNullOrEmpty()) {
            return emptyList()
        }
        return utxoResponse.body()!!
            .filter { it.status.confirmed }
            .map { SpendableUtxo(it.txid, it.vout, it.value) }
    }

    private suspend fun loadUtxosFromBlockCypher(address: String): List<SpendableUtxo> {
        val route = blockCypherRoute()
        val api = blockCypherApi()
        val response = api.getAddressData(
            coin = route.coin,
            chain = route.chain,
            address = address,
            unspentOnly = true,
            includeScript = true,
            limit = 200
        )
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("BlockCypher API error: ${response.code()}")
        }
        return response.body()!!.txRefs.orEmpty()
            .filter {
                (it.txOutputN ?: -1) >= 0 && (it.confirmations ?: 0) > 0 && (it.value ?: 0L) > 0L
            }
            .map {
                SpendableUtxo(
                    txid = it.txHash,
                    vout = it.txOutputN ?: 0,
                    value = it.value ?: 0L
                )
            }
    }

    private fun signSegwitInputs(
        tx: Transaction,
        inputs: List<SpendableUtxo>,
        key: ECKey
    ): List<TransactionInput> {
        val signedInputs = mutableListOf<TransactionInput>()
        for (i in inputs.indices) {
            val utxoValue = Coin.valueOf(inputs[i].value)
            val scriptCode = ScriptBuilder.createP2PKHOutputScript(key).program
            val sighash = tx.hashForWitnessSignature(
                i,
                scriptCode,
                utxoValue,
                Transaction.SigHash.ALL,
                false
            )
            val signature = key.sign(sighash)
            val txSignature = TransactionSignature(signature, Transaction.SigHash.ALL, false)
            val witness = TransactionWitness.redeemP2WPKH(txSignature, key)
            signedInputs.add(tx.getInput(i.toLong()).withWitness(witness))
        }
        return signedInputs
    }

    private fun signLegacyInputs(
        tx: Transaction,
        key: ECKey,
        fromAddress: Address
    ): List<TransactionInput> {
        val signedInputs = mutableListOf<TransactionInput>()
        val connectedScript = createOutputScript(fromAddress)
        for (i in 0 until tx.inputs.size) {
            val txSignature = tx.calculateSignature(
                i,
                key,
                connectedScript,
                Transaction.SigHash.ALL,
                false
            )
            val scriptSig = ScriptBuilder.createInputScript(txSignature, key)
            signedInputs.add(tx.getInput(i.toLong()).withScriptSig(scriptSig))
        }
        return signedInputs
    }

    private suspend fun broadcastViaMempool(txHex: String): String {
        val api = retrofitBuilder
            .baseUrl(network.explorers.first())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(BTCApiService::class.java)

        val requestBody = txHex.toRequestBody("text/plain".toMediaType())
        val response = api.broadcastTransaction(requestBody)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        }
        throw IllegalStateException("Broadcast failed: ${response.errorBody()?.string()}")
    }

    private suspend fun broadcastViaBlockCypher(txHex: String): String {
        val route = blockCypherRoute()
        val api = blockCypherApi()
        val response = api.pushTransaction(
            coin = route.coin,
            chain = route.chain,
            body = PushTxRequest(txHex)
        )
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("Broadcast failed: ${response.errorBody()?.string()}")
        }
        val body = response.body()!!
        body.tx?.hash?.takeIf { it.isNotBlank() }?.let { return it }
        throw IllegalStateException(body.error ?: "Broadcast failed without tx hash")
    }

    private suspend fun getFeeOptionsFromMempool(): List<FeeData> {
        val api = mempoolApi()
        val response = api.getRecommendedFees()
        if (!response.isSuccessful || response.body() == null) {
            throw IllegalStateException("Mempool fee API error: ${response.code()}")
        }

        val feeInfo = response.body()!!
        val slowRate = kbToByteRate(feeInfo.minimumFee.toLong())
        val normalRate = kbToByteRate(feeInfo.economyFee.toLong())
        val fastRate = kbToByteRate(feeInfo.fastestFee.toLong())

        return buildFeeOptions(
            slowRate = slowRate,
            normalRate = normalRate,
            fastRate = fastRate
        )
    }

    private suspend fun getFeeOptionsFromRpc(): List<FeeData> {
        suspend fun estimate(targetBlocks: Int, fallbackRate: Long): Long {
            val response = try {
                callBitcoinRpc("estimatesmartfee", listOf(targetBlocks)) as? Map<*, *>
            } catch (_: Exception) {
                null
            }
            val feeRateBtcPerKb = response?.get("feerate")?.toString()?.toBigDecimalOrNull()
            if (feeRateBtcPerKb == null || feeRateBtcPerKb <= BigDecimal.ZERO) {
                return fallbackRate
            }
            return btcPerKbToSatPerByte(feeRateBtcPerKb)
        }

        val fallback = fallbackFeeRates()
        val slowRate = estimate(targetBlocks = 6, fallbackRate = fallback.first)
        val normalRate = estimate(targetBlocks = 3, fallbackRate = fallback.second)
        val fastRate = estimate(targetBlocks = 1, fallbackRate = fallback.third)
        return buildFeeOptions(slowRate, normalRate, fastRate)
    }

    private suspend fun getFeeOptionsFromBlockCypher(): List<FeeData> {
        val route = blockCypherRoute()
        val api = blockCypherApi()
        val response = api.getChainInfo(route.coin, route.chain)

        val fallback = fallbackFeeRates()
        if (!response.isSuccessful || response.body() == null) {
            return buildFeeOptions(fallback.first, fallback.second, fallback.third)
        }

        val body = response.body()!!
        val slowRate = kbToByteRate(body.lowFeePerKb ?: fallback.first * 1000L)
        val normalRate = kbToByteRate(body.mediumFeePerKb ?: fallback.second * 1000L)
        val fastRate = kbToByteRate(body.highFeePerKb ?: fallback.third * 1000L)
        return buildFeeOptions(slowRate, normalRate, fastRate)
    }

    private fun buildFeeOptions(
        slowRate: Long,
        normalRate: Long,
        fastRate: Long
    ): List<FeeData> {
        val scriptType = resolveInputScriptType()

        fun feeFor(rate: Long): BigDecimal {
            return BigDecimal.valueOf(estimateTxFeeBasedOnRate(1, 2, rate, scriptType))
        }

        return listOf(
            FeeData(
                level = "کند",
                feeInSmallestUnit = feeFor(slowRate),
                // اضافه کردن normalize برای یکپارچگی با UI
                feeInCoin = normalize(
                    feeFor(slowRate).toBigInteger(),
                    network.decimals,
                    network.name
                ),
                estimatedTime = "~ 30 دقیقه",
                feeRateInSatsPerByte = slowRate
            ),
            FeeData(
                level = "عادی",
                feeInSmallestUnit = feeFor(normalRate),
                feeInCoin = normalize(
                    feeFor(normalRate).toBigInteger(),
                    network.decimals,
                    network.name
                ),
                estimatedTime = "~ 10 دقیقه",
                feeRateInSatsPerByte = normalRate
            ),
            FeeData(
                level = "سریع",
                feeInSmallestUnit = feeFor(fastRate),
                feeInCoin = normalize(
                    feeFor(fastRate).toBigInteger(),
                    network.decimals,
                    network.name
                ),
                estimatedTime = "~ 2 دقیقه",
                feeRateInSatsPerByte = fastRate
            )
        )
    }

    private fun fallbackFeeRates(): Triple<Long, Long, Long> {
        return when (network.name) {
            NetworkName.DOGE,
            NetworkName.DOGETESTNET -> Triple(1_000L, 1_500L, 2_000L)

            else -> Triple(3L, 8L, 15L)
        }
    }

    private fun mempoolApi(): BTCApiService {
        return retrofitBuilder
            .baseUrl(network.explorers.first())
            .build()
            .create(BTCApiService::class.java)
    }

    private fun blockCypherApi(): UtxoApiService {
        return retrofitBuilder
            .baseUrl(network.explorers.first())
            .build()
            .create(UtxoApiService::class.java)
    }

    private fun blockCypherRoute(): BlockCypherRoute {
        return when (network.name) {
            NetworkName.BITCOIN -> BlockCypherRoute("btc", "main")
            NetworkName.BITCOINTESTNET -> BlockCypherRoute("btc", "test3")
            NetworkName.LITECOIN, NetworkName.LTCTESTNET -> BlockCypherRoute("ltc", "main")
            NetworkName.DOGE, NetworkName.DOGETESTNET -> BlockCypherRoute("doge", "main")
            else -> throw IllegalStateException("Unsupported BlockCypher network route: ${network.name}")
        }
    }

    private fun nativeAssetMeta(): Pair<String, String> {
        return when (network.name) {
            NetworkName.BITCOIN -> "Bitcoin" to "BTC"
            NetworkName.BITCOINTESTNET -> "Bitcoin Testnet" to "BTC"
            NetworkName.LITECOIN -> "Litecoin" to "LTC"
            NetworkName.LTCTESTNET -> "Litecoin Testnet" to "LTC"
            NetworkName.DOGE -> "Dogecoin" to "DOGE"
            NetworkName.DOGETESTNET -> "Dogecoin Testnet" to "DOGE"
            else -> network.name.name to network.currencySymbol
        }
    }

    private fun parseIsoTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).epochSecond }.getOrNull()
    }

    private fun kbToByteRate(feePerKb: Long): Long {
        return (feePerKb / 1000L).coerceAtLeast(1L)
    }

    private fun btcPerKbToSatPerByte(feeRateBtcPerKb: BigDecimal): Long {
        val satsPerKb = feeRateBtcPerKb.multiply(BigDecimal(100_000_000))
        return satsPerKb.divide(BigDecimal(1000), 0, java.math.RoundingMode.CEILING)
            .toLong()
            .coerceAtLeast(1L)
    }

    private fun btcToSats(valueBtc: BigDecimal): Long {
        return valueBtc.multiply(BigDecimal(100_000_000))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toLong()
    }

    private fun estimateTxFeeBasedOnRate(
        inputs: Int,
        outputs: Int,
        feeRate: Long,
        scriptType: ScriptType
    ): Long {
        val txVBytes = when (scriptType) {
            ScriptType.P2PKH -> (inputs * 148) + (outputs * 34) + 10
            ScriptType.P2WPKH -> (inputs * 68) + (outputs * 31) + 11
            else -> (inputs * 148) + (outputs * 34) + 10
        }

        val calculatedFee = txVBytes * feeRate

        // قانون 0.01 DOGE فقط برای شبکه‌های دوج‌کوین
        val minRelayFee = when (network.name) {
            NetworkName.DOGE,
            NetworkName.DOGETESTNET -> 1_000_000L

            else -> 0L
        }
        return if (minRelayFee > 0L && calculatedFee < minRelayFee) minRelayFee else calculatedFee
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
