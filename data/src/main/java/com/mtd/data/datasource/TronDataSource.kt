package com.mtd.data.datasource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.tron.TronUtils
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.utils.AddressRegexUtils
import com.mtd.core.utils.TronAddressConverter
import com.mtd.data.dto.AccountRequest
import com.mtd.data.dto.CreateTxRequest
import com.mtd.data.dto.TriggerConstantRequest
import com.mtd.data.dto.TriggerSmartContractRequest
import com.mtd.data.dto.TrongridTokenData
import com.mtd.data.service.TronExplorerService
import com.mtd.data.service.TronNativeService
import com.mtd.data.utils.AssetNormalizer.normalize
import com.mtd.domain.model.Asset
import com.mtd.domain.model.FeeData
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TokenTransferDetails
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import com.mtd.domain.model.TronTransaction
import com.mtd.domain.model.assets.AssetConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import retrofit2.Retrofit
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest

class TronDataSource(
    private val network: BlockchainNetwork,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry,
    private val okHttpClient: OkHttpClient
) : IChainDataSource {

    companion object {
        private const val RPC_FAILOVER_TIMEOUT_MS = 12_000L
        private const val NATIVE_API_FAILOVER_TIMEOUT_MS = 15_000L
        private const val TRON_TRANSFER_CONTRACT = "TransferContract"
    }

    object Web3jFactory {
        // TASK-20: ConcurrentHashMap (was a plain mutableMapOf) — data sources are hit from concurrent
        // coroutines, so a non-thread-safe map here could corrupt/throw on parallel getOrCreate.
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Web3j>()

        fun getOrCreate(rpcUrl: String, okHttpClient: OkHttpClient): Web3j {
            return cache.getOrPut(rpcUrl) {
                Web3j.build(HttpService(rpcUrl, okHttpClient, false))
            }
        }
    }

    private suspend fun <T> executeWithFailover(block: suspend (Web3j) -> T): T {
        var lastException: Exception? = null

        for ((index, rpcUrl) in network.RpcUrlsEvm.withIndex()) {
            try {
                return withTimeout(RPC_FAILOVER_TIMEOUT_MS) {
                    val web3j = Web3jFactory.getOrCreate(rpcUrl, okHttpClient)
                    block(web3j)
                }
            } catch (e: Exception) {
                Timber.e(e, "TRON RPC failed [${index + 1}/${network.RpcUrls.size}] $rpcUrl")
                lastException = e
            }
        }

        throw lastException ?: IllegalStateException("All TRON RPCs failed for ${network.id}")
    }

    private suspend fun <T> executeNativeApiWithFailover(block: suspend (TronNativeService) -> T): T {
        var lastException: Exception? = null

        for ((index, rpc) in network.RpcUrls.withIndex()) {
            try {
                return withTimeout(NATIVE_API_FAILOVER_TIMEOUT_MS) {
                    val baseUrl = if (rpc.endsWith("/")) rpc else "$rpc/"
                    val api = retrofitBuilder.baseUrl(baseUrl).build().create(TronNativeService::class.java)
                    block(api)
                }
            } catch (e: Exception) {
                lastException = e
                Timber.e(e, "TRON native API failed [${index + 1}/${network.RpcUrls.size}] on RPC: $rpc")
            }
        }

        throw lastException ?: IllegalStateException("All TRON RPCs failed")
    }

    private suspend fun <T> executeExplorerWithFailover(block: suspend (TronExplorerService) -> T): T {
        var lastException: Exception? = null

        for ((index, explorer) in network.explorers.withIndex()) {
            try {
                return withTimeout(NATIVE_API_FAILOVER_TIMEOUT_MS) {
                    val baseUrl = if (explorer.endsWith("/")) explorer else "$explorer/"
                    val api = retrofitBuilder.baseUrl(baseUrl).build().create(TronExplorerService::class.java)
                    block(api)
                }
            } catch (e: Exception) {
                lastException = e
                Timber.e(e, "TRON explorer failed [${index + 1}/${network.explorers.size}] $explorer")
            }
        }

        throw lastException ?: IllegalStateException("All TRON explorers failed for ${network.id}")
    }

    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> {
        return ResultResponse.Success(BigDecimal.ZERO)
    }

    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> {
        return withContext(Dispatchers.IO) {
            try {
                val supportedAssets = assetRegistry.getAssetsForNetwork(network.id)
                if (supportedAssets.isEmpty()) return@withContext ResultResponse.Success(emptyList())
                val tronAddress = toEvmAddressOrThrow(address)
                val assetDeferreds = supportedAssets.map { assetConfig ->
                    async {
                        executeWithFailover { web3j ->
                            val balance = if (assetConfig.contractAddress == null) {
                                web3j.ethGetBalance(tronAddress, DefaultBlockParameterName.LATEST).sendAsync().await().balance
                            } else {
                                val contractAddress = toEvmAddressOrThrow(assetConfig.contractAddress ?: "")
                                val function =
                                    Function("balanceOf", listOf(Address(tronAddress)), emptyList())
                                val response = web3j.ethCall(
                                    Transaction.createEthCallTransaction(tronAddress, contractAddress, org.web3j.abi.FunctionEncoder.encode(function)),
                                    DefaultBlockParameterName.LATEST
                                ).sendAsync().await()
                               response.result.toString()
                            }
                            Asset(assetConfig.name, assetConfig.symbol, assetConfig.decimals, assetConfig.contractAddress,  normalize (balance,assetConfig.decimals,network.networkType))
                        }
                    }
                }
                ResultResponse.Success(assetDeferreds.awaitAll())
            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
        }
    }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        return try {
            val records = mutableListOf<TransactionRecord>()
            // Fetch native + token history on the first healthy explorer, failing over across the
            // configured explorers (mirrors the RPC failover) so one dead explorer no longer blanks
            // the entire history.
            val (normalTxs, tokenTxs) = executeExplorerWithFailover { api ->
                api.getTrxHistory(address, limit = 20, start = 0) to
                    api.getTokenHistory(address, limit = 20, start = 0)
            }
            // ۱. تراکنش‌های بومی (TRX)
            normalTxs.data.forEach { tx ->
                val contract = tx.rawData.contract.firstOrNull { it.type == TRON_TRANSFER_CONTRACT }
                    ?: return@forEach
                val value = contract.parameter.value
                val amount = value.amount ?: return@forEach
                if (amount <= BigInteger.ZERO) return@forEach
                
                val fromHex = value.ownerAddress
                val toHex = value.toAddress
                
                val fromAddress = if (fromHex != null) TronAddressConverter.evmToTron(fromHex) else ""
                val toAddress = if (toHex != null) TronAddressConverter.evmToTron(toHex) else ""
                if (!isAddressInvolved(address, fromAddress, toAddress)) return@forEach
                
                records.add(
                    TronTransaction(
                        hash = tx.txID,
                        timestamp = tx.blockTimestamp / 1000, // Convert ms to s
                        fee = tx.ret?.sumOf { it.fee ?: BigInteger.ZERO } ?: BigInteger.ZERO,
                        status = if (tx.ret?.all { it.contractRet == "SUCCESS" } == true) TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
                        fromAddress = fromAddress,
                        toAddress = toAddress,
                        amount = amount,
                        isOutgoing = fromAddress.equals(address, ignoreCase = true),
                        contractAddress = null, // Empty for TRX
                        bandwidthUsed = tx.bandwidth,
                        energyUsed = tx.energy,
                        tokenTransferDetails = null,
                        networkId = network.id,
                        networkName = network.name
                    )
                )
            }

            // ۲. تراکنش‌های توکن (TRC20 - مثل USDT)
            tokenTxs.data.forEach { tx ->
                if (!tx.isTransferEvent()) return@forEach
                if (tx.value <= BigInteger.ZERO) return@forEach
                if (!isAddressInvolved(address, tx.from, tx.to)) return@forEach

                records.add(
                    TronTransaction(
                        hash = tx.transactionId,
                        timestamp = tx.blockTimestamp / 1000,
                        fee = BigInteger.ZERO, // Trongrid TRC20 endpoint doesn't always show the fee
                        status = TransactionStatus.CONFIRMED,
                        fromAddress = tx.from,
                        toAddress = tx.to,
                        amount = tx.value,
                        isOutgoing = tx.from.equals(address, ignoreCase = true),
                        contractAddress = tx.tokenInfo?.address,
                        bandwidthUsed = null,
                        energyUsed = null,
                        networkId = network.id,
                        networkName = network.name,
                        tokenTransferDetails = TokenTransferDetails(
                            from = tx.from,
                            to = tx.to,
                            amount = tx.value,
                            tokenSymbol = tx.tokenInfo?.symbol ?: "TOKEN",
                            tokenDecimals = tx.tokenInfo?.decimals ?: 18,
                            contractAddress = tx.tokenInfo?.address.orEmpty()
                        )
                    )
                )
            }

            // ۳. مرتب‌سازی بر اساس زمان (جدیدترین به قدیمی‌ترین)
            val sortedRecords = records.sortedByDescending { it.timestamp }

            ResultResponse.Success(sortedRecords)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    } //TODO اینجا هزینه پرداختی هر تراکنش نیست باید https://apilist.tronscan.org/api/transaction-info?hash={transaction_id} رو استفاده کنیم وقتی جزئیات رو خواست ببینه

    override suspend fun getTransactionFeeDetails(txId: String): ResultResponse<TransactionFeeDetails> {
        return try {
            executeNativeApiWithFailover { api ->
                val requestBody = com.mtd.data.dto.TransactionInfoRequest(value = txId)
                val response = api.getTransactionInfoById(requestBody)

                val fee = response.fee?.toBigInteger() ?: BigInteger.ZERO
                val receipt = response.receipt
                val energyFee = receipt?.energyFee?.toBigInteger()
                val netFee = receipt?.netFee?.toBigInteger()
                val energyUsed = receipt?.energyUsageTotal
                val bandwidthUsed = receipt?.netUsage

                ResultResponse.Success(
                    TransactionFeeDetails(
                        fee = fee,
                        energyUsed = energyUsed,
                        bandwidthUsed = bandwidthUsed,
                        energyFee = energyFee,
                        networkFee = netFee
                    )
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> {
        if (params !is TransactionParams.Tvm) {
            return ResultResponse.Error(IllegalArgumentException("Invalid params"))
        }
        if (params.amount < BigInteger.ZERO) {
            return ResultResponse.Error(IllegalArgumentException("Amount cannot be negative"))
        }
        if (params.amount == BigInteger.ZERO && params.contractAddress.isNullOrBlank()) {
            return ResultResponse.Error(IllegalArgumentException("Amount must be greater than zero for native TRX transfer"))
        }
        if (params.toAddress.isBlank()) {
            return ResultResponse.Error(IllegalArgumentException("Recipient address is required"))
        }
        val normalizedToAddress = params.toAddress.trim()
        if (!AddressRegexUtils.matchesAddress(network.regex, normalizedToAddress)) {
            return ResultResponse.Error(IllegalArgumentException("Invalid recipient address"))
        }

        return try {
            val normalizedKey = privateKeyHex.removePrefix("0x")
            val credentials = Credentials.create(normalizedKey)
            val fromAddress = TronUtils.getAddressFromPublicKey(credentials.ecKeyPair.publicKey)
            val signedTx = buildSignedTransaction(
                params = params.copy(toAddress = normalizedToAddress),
                fromAddress = fromAddress,
                privateKeyHex = normalizedKey
            )
            val broadcast = broadcastSignedTransaction(signedTx)

            if (broadcast["result"]?.asBoolean == true) {
                val txId = broadcast["txid"]?.asString
                    ?: signedTx["txID"]?.asString
                    ?: throw IllegalStateException("Broadcast succeeded but txid missing")
                ResultResponse.Success(txId)
            } else {
                ResultResponse.Error(
                    IllegalStateException("Broadcast failed: ${broadcast.toString()}")
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?,
        amount: BigInteger?
    ): ResultResponse<List<FeeData>> {
        return try {
            if (fromAddress == null || toAddress == null) throw Exception("Addresses required")
            executeNativeApiWithFailover { api ->
                val params = api.getChainParameters().chainParameter.associate { it.key to (it.value ?: 0L) }
                val energyFeeSun = params["getEnergyFee"] ?: 420L
                val bandwidthFeeSun = params["getTransactionFee"] ?: 1000L
                val createAccountFeeSun = params["getCreateNewAccountFeeInSystem"] ?: 1100000L
                val totalFeeInSun: Long

                if (asset?.contractAddress == null) {
                    // --- بخش اول: انتقال TRX (Native) ---
                    val fromHex = TronAddressConverter.base58ToHex(fromAddress)
                    val toHex = TronAddressConverter.base58ToHex(toAddress)

                    val fromAccount = try {
                        api.getAccount(AccountRequest(address = fromHex, visible = false))
                    } catch (_: Exception) { null }
                    val accountResources = try {
                        api.getAccountResource(AccountRequest(address = fromHex, visible = false))
                    } catch (_: Exception) { null }

                    val txSize = try {
                        val tempTx = api.createTransaction(CreateTxRequest(fromHex, toHex, 1L, visible = false))
                        if (tempTx.raw_data_hex != null) (tempTx.raw_data_hex.length / 2) + 69L else 270L
                    } catch (_: Exception) { 270L }

                    val totalAvailableBW = accountResources?.availableBandwidth
                        ?: fromAccount?.availableBandwidth
                        ?: 600L

                    val isNewAccount = try {
                        val res = api.getAccount(AccountRequest(address = toHex, visible = false))
                        res.address.isNullOrEmpty()
                    } catch (_: Exception) { true }

                    val activationCost = if (isNewAccount) createAccountFeeSun else 0L

                    val bandwidthBurnCost = if (totalAvailableBW < txSize) {
                        (txSize - totalAvailableBW) * bandwidthFeeSun
                    } else 0L

                    totalFeeInSun = bandwidthBurnCost + activationCost

                } else {
                    // --- بخش دوم: انتقال توکن (TRC20) ---
                    val fromHex = TronAddressConverter.base58ToHex(fromAddress)
                    val contractHex = TronAddressConverter.base58ToHex(asset.contractAddress!!)
                    val toHex = TronAddressConverter.base58ToHex(toAddress)

                    val fromAccount = try {
                        api.getAccount(AccountRequest(address = fromHex, visible = false))
                    } catch (_: Exception) { null }
                    val accountResources = try {
                        api.getAccountResource(AccountRequest(address = fromHex, visible = false))
                    } catch (_: Exception) { null }

                    // Estimate energy against the real send amount (raw units). Falls back to a nominal
                    // 1 unit for a blind pre-quote (amount == null). `asset.balance` was wrong here — it's
                    // the DISPLAY balance (whole coins), not the raw transfer amount.
                    val estimateAmount = amount ?: BigInteger.ONE
                    val parameter = encodeTransferParams(toAddress, estimateAmount)

                    // ۱. بررسی داینامیک وجود توکن در مقصد (برای تخمین جریمه Storage)
                    val destinationHasToken = try {
                        val balanceReq = TriggerConstantRequest(
                            owner_address = fromHex,
                            contract_address = contractHex,
                            function_selector = "balanceOf(address)",
                            parameter = toHex.padStart(64, '0'),
                            visible = false
                        )
                        val res = api.triggerConstantContract(balanceReq)
                        val hexResult = res.constant_result?.firstOrNull() ?: ""
                        hexResult.isNotEmpty() && hexResult.replace("0", "").isNotEmpty()
                    } catch (_: Exception) { false }

                    // ۲. تخمین انرژی پایه از طریق TriggerConstant
                    val request = TriggerConstantRequest(
                        owner_address = fromHex,
                        contract_address = contractHex,
                        function_selector = "transfer(address,uint256)",
                        parameter = parameter,
                        visible = false
                    )

                    val rawEnergyUsed = try {
                        api.triggerConstantContract(request).energy_used ?: 31895L
                    } catch (_: Exception) { 31895L }

                    // ۳. اعمال منطق واقعی: ضریب کم (۱.۱) + جریمه ۳۲۰۰۰ واحدی برای اکانت جدید
                    val energyMultiplier = 1.1
                    val actualEnergyNeeded = if (!destinationHasToken) {
                        // اگر مقصد توکن ندارد، هزینه ایجاد Slot جدید در قرارداد اضافه می‌شود
                        ((rawEnergyUsed + 32000L) * energyMultiplier).toLong()
                    } else {
                        (rawEnergyUsed * energyMultiplier).toLong()
                    }

                    val bandwidthUsage = 350L
                    val totalAvailableEnergy = accountResources?.availableEnergy
                        ?: fromAccount?.availableEnergy
                        ?: 0L
                    val totalAvailableBW = accountResources?.availableBandwidth
                        ?: fromAccount?.availableBandwidth
                        ?: 600L

                    val energyToBurn = if (totalAvailableEnergy < actualEnergyNeeded) {
                        actualEnergyNeeded - totalAvailableEnergy
                    } else 0L

                    val bandwidthToBurn = if (totalAvailableBW < bandwidthUsage) {
                        bandwidthUsage - totalAvailableBW
                    } else 0L

                    totalFeeInSun = (energyToBurn * energyFeeSun) + (bandwidthToBurn * bandwidthFeeSun)
                }

                val feeInTrx = normalize(totalFeeInSun, 6, network.networkType)
                ResultResponse.Success(
                    listOf(
                        FeeData(
                            level = "عادی",
                            feeInSmallestUnit = totalFeeInSun.toBigDecimal(),
                            feeInCoin = feeInTrx,
                            estimatedTime = " ~ 1 دقیقه"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }


    override suspend fun getBalancesForMultipleAddresses(addresses: List<String>): ResultResponse<Map<String, List<Asset>>> {
        return withContext(Dispatchers.IO) {

            executeWithFailover { web3j ->
                try {
                    val supportedAssets = assetRegistry.getAssetsForNetwork(network.id)
                    if (supportedAssets.isEmpty()) return@executeWithFailover ResultResponse.Success(
                        emptyMap()
                    )

                    // 1. آماده‌سازی تمام درخواست‌ها به صورت تخت
                    val allRequests =
                        mutableListOf<Triple<String, AssetConfig, org.web3j.protocol.core.Request<*, *>>>()

                    addresses.forEach { address ->
                        val normalizedAddress = toEvmAddressOrThrow(address)
                        supportedAssets.forEach { assetConfig ->
                            if (assetConfig.contractAddress == null) {
                                // Native Coin
                                val request =
                                    web3j.ethGetBalance(normalizedAddress, DefaultBlockParameterName.LATEST)
                                allRequests.add(Triple(normalizedAddress, assetConfig, request))
                            } else {
                                // ERC-20 Token
                                val function =
                                    Function("balanceOf", listOf(Address(normalizedAddress)), emptyList())
                                val encodedFunction = org.web3j.abi.FunctionEncoder.encode(function)
                                val contractAddress = toEvmAddressOrThrow(assetConfig.contractAddress ?: "")
                                val transaction = Transaction.createEthCallTransaction(
                                    normalizedAddress,
                                    contractAddress,
                                    encodedFunction
                                )
                                val request =
                                    web3j.ethCall(transaction, DefaultBlockParameterName.LATEST)
                                allRequests.add(Triple(normalizedAddress, assetConfig, request))
                            }
                        }
                    }

                    if (allRequests.isEmpty()) return@executeWithFailover ResultResponse.Success(
                        emptyMap()
                    )

                    // 2. اعمال محدودیت اکید: Max 3 Requests per Batch
                    val REQUESTS_LIMIT_PER_BATCH = 3
                    val requestChunks = allRequests.chunked(REQUESTS_LIMIT_PER_BATCH)

                    // 3. ارسال موازی دسته‌ها
                    val chunkDeferreds = requestChunks.map { chunk ->
                        async {
                            try {
                                val batch = web3j.newBatch()
                                chunk.forEach { (_, _, request) ->
                                    batch.add(request)
                                }

                                val batchResponse = batch.sendAsync().await()
                                val responses = batchResponse.responses

                                val chunkResults = mutableListOf<Pair<String, Asset>>()

                                responses.forEachIndexed { index, response ->
                                    if (index < chunk.size) {
                                        val (address, assetConfig, _) = chunk[index]
                                        val balance =  normalize (response.result.toString(),assetConfig.decimals,network.networkType)
                                        val asset = Asset(
                                            assetConfig.name,
                                            assetConfig.symbol,
                                            assetConfig.decimals,
                                            assetConfig.contractAddress,
                                            balance
                                        )
                                        chunkResults.add(address to asset)
                                    }
                                }
                                chunkResults
                            } catch (e: Exception) {
                                Timber.e(e, "Error processing chunk")
                                emptyList<Pair<String, Asset>>()
                            }
                        }
                    }

                    // 4. تجمیع نتایج
                    val allChunkResults = chunkDeferreds.awaitAll().flatten()

                    val finalResults = mutableMapOf<String, MutableList<Asset>>()
                    allChunkResults.forEach { (address, asset) ->
                        finalResults.getOrPut(address) { mutableListOf() }.add(asset)
                    }

                    ResultResponse.Success(finalResults)

                } catch (e: Exception) {
                    Timber.e(e, "Error in batched balance fetching")
                    ResultResponse.Error(e)
                }
            }
        }
    }

    private fun encodeTransferParams(toAddress: String, amount: BigInteger): String {
        // تبدیل آدرس به Hex (بدون پیشوند 41) و پد کردن تا 64 کاراکتر
        val addressHex = TronUtils.toHex(toAddress).substring(2).padStart(64, '0')
        val amountHex = amount.toString(16).padStart(64, '0')
        return addressHex + amountHex
    }

    private suspend fun buildSignedTransaction(
        params: TransactionParams.Tvm,
        fromAddress: String,
        privateKeyHex: String
    ): JsonObject {
        val unsignedTx = createUnsignedTransaction(params, fromAddress)
        val rawDataHex = unsignedTx["raw_data_hex"]?.asString
            ?: throw IllegalStateException("Missing raw_data_hex in unsigned transaction")
        val txHash = sha256Hex(rawDataHex)
        val signatureHex = signTransactionHash(txHash, privateKeyHex)

        val signatures = JsonArray().apply { add(signatureHex) }
        unsignedTx.add("signature", signatures)
        return unsignedTx
    }

    private suspend fun createUnsignedTransaction(
        params: TransactionParams.Tvm,
        fromAddress: String
    ): JsonObject {
        return executeNativeApiWithFailover { api ->
            if (params.contractAddress.isNullOrBlank()) {
                val amountAsLong = try {
                    params.amount.toLongExactCompat()
                } catch (e: ArithmeticException) {
                    throw IllegalArgumentException("TRX amount is out of range for native transaction")
                }
                api.createTransactionRaw(
                    CreateTxRequest(
                        owner_address = fromAddress,
                        to_address = params.toAddress,
                        amount = amountAsLong,
                        visible = true
                    )
                )
            } else {
                val functionSelector = params.contractFunction ?: "transfer(address,uint256)"
                val functionParameter = params.contractParameter
                    ?: encodeTransferParams(params.toAddress, params.amount)
                val trigger = TriggerSmartContractRequest(
                    owner_address = fromAddress,
                    contract_address = params.contractAddress?:"",
                    function_selector = functionSelector,
                    parameter = functionParameter,
                    call_value = 0L,
                    fee_limit = params.feeLimit,
                    visible = true
                )
                val response = api.triggerSmartContractRaw(trigger)
                response.getAsJsonObject("transaction")
                    ?: throw IllegalStateException("TRON triggerSmartContract returned no transaction")
            }
        }
    }

    private suspend fun broadcastSignedTransaction(signedTx: JsonObject): JsonObject {
        return executeNativeApiWithFailover { api ->
            api.broadcastTransaction(signedTx)
        }
    }



    private fun sha256Hex(rawDataHex: String): ByteArray {
        val data = Numeric.hexStringToByteArray(rawDataHex)
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun signTransactionHash(hash: ByteArray, privateKeyHex: String): String {
        val keyPair = Credentials.create(privateKeyHex).ecKeyPair
        val signatureData = Sign.signMessage(hash, keyPair, false)

        val r = Numeric.toHexStringNoPrefix(signatureData.r).padStart(64, '0')
        val s = Numeric.toHexStringNoPrefix(signatureData.s).padStart(64, '0')
        val recoveryId = ((signatureData.v.firstOrNull()?.toInt() ?: 27) - 27).coerceIn(0, 1)
        val v = recoveryId.toString(16).padStart(2, '0')
        return r + s + v
    }

    private fun BigInteger.toLongExactCompat(): Long {
        if (this < BigInteger.valueOf(Long.MIN_VALUE) || this > BigInteger.valueOf(Long.MAX_VALUE)) {
            throw ArithmeticException("BigInteger out of Long range")
        }
        return toLong()
    }

    private fun isAddressInvolved(
        targetAddress: String,
        fromAddress: String,
        toAddress: String
    ): Boolean {
        return fromAddress.equals(targetAddress, ignoreCase = true) ||
            toAddress.equals(targetAddress, ignoreCase = true)
    }

    private fun TrongridTokenData.isTransferEvent(): Boolean {
        val explicitEventNames = listOfNotNull(type, eventType, eventName)
            .filter { it.isNotBlank() }

        if (explicitEventNames.isEmpty()) {
            return true
        }

        return explicitEventNames.all { eventName ->
            eventName.equals("Transfer", ignoreCase = true) ||
                eventName.equals("TRC20Transfer", ignoreCase = true) ||
                eventName.equals("trc20_transfer", ignoreCase = true)
        }
    }

    private fun toEvmAddressOrThrow(tronAddress: String): String {
        return TronAddressConverter.tronToEvm(tronAddress)
    }



}
