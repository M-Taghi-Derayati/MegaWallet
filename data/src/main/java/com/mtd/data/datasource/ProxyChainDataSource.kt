package com.mtd.data.datasource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mtd.core.network.BlockchainNetwork

import com.mtd.data.dto.BalancesRequestDto
import com.mtd.data.dto.BatchBalanceRequestDto
import com.mtd.data.dto.BatchBalanceWalletDto
import com.mtd.data.dto.BatchNetworkBalanceDto
import com.mtd.data.dto.BroadcastRequestDto
import com.mtd.data.dto.ContractCallParameterDto
import com.mtd.data.dto.HistoryAddressDto
import com.mtd.data.dto.HistoryRequestDto
import com.mtd.data.dto.PrepareContractCallRequestDto
import com.mtd.data.dto.PrepareTxRequestDto
import com.mtd.data.dto.ProxyBalanceDto
import com.mtd.data.mapper.toDomain
import com.mtd.data.network.proxyCall
import com.mtd.data.service.MobileProxyApiService
import com.mtd.data.utils.AssetNormalizer.normalize
import com.mtd.domain.model.Asset
import com.mtd.domain.model.FeeData
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.PreparedEvmTx
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.UtxoInput
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.ceil

/**
 * PROXY-mode [IChainDataSource] — routes reads + broadcast through the centralized Mobile
 * Blockchain Proxy (`/api/mobile/v1`) instead of hitting chain RPCs directly. Selected by
 * [ChainDataSourceFactory] when [com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider]
 * reports PROXY, and returns the **same domain types** as the DIRECT sources so the toggle is
 * transparent to ViewModels.
 *
 * Scope: balances, balance, multi-balances, fee options, tx fee details, and EVM + TVM send
 * (prepare → sign locally → broadcast). Non-custodial: private keys never leave the device; only
 * the signed payload is relayed. UTXO send is the next phase (returns a typed error meanwhile).
 */
class ProxyChainDataSource(
    private val network: BlockchainNetwork,
    private val proxyService: MobileProxyApiService,
    // Default = production web3j signers; tests inject fakes so no BouncyCastle is loaded.
    private val signer: EvmTxSigner = Web3jEvmTxSigner,
    private val tronSigner: TronTxSigner = Web3jTronTxSigner,
    // Built per-network by the factory (needs bitcoinj NetworkParameters); null for non-UTXO networks.
    private val utxoTxBuilder: UtxoTxBuilder? = null
) : IChainDataSource {

    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> {
        return proxyCall(
            call = { proxyService.balances(network.id, BalancesRequestDto(address = address)) },
            map = { dto ->
                val explicit = dto.assets
                if (!explicit.isNullOrEmpty()) {
                    // Live proxy shape: `assets` is the full list including the native coin
                    // (identified by a null/blank contractAddress). The separate `native` block is a
                    // summary of the same coin, so we do NOT add it again (avoids duplication).
                    explicit.map { it.toAsset(isNative = it.contractAddress.isNullOrBlank()) }
                } else {
                    // Legacy/contract shape: discrete `native` + tokens-only `tokens`.
                    buildList {
                        dto.native?.let { add(it.toAsset(isNative = true)) }
                        dto.tokens?.forEach { add(it.toAsset(isNative = false)) }
                    }
                }
            }
        )
    }

    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> {
        return proxyCall(
            call = { proxyService.balances(network.id, BalancesRequestDto(address = address)) },
            map = { dto -> dto.native?.toAsset(isNative = true)?.balance ?: BigDecimal.ZERO }
        )
    }

    override suspend fun getBalancesForMultipleAddresses(
        addresses: List<String>
    ): ResultResponse<Map<String, List<Asset>>> = coroutineScope {
        // TASK-10 — fan the per-address balance calls out concurrently (was a sequential N×RTT loop),
        // so wall-clock time is ~1 RTT for a multi-address wallet. Failure stays isolated per address
        // (an error → emptyList), matching the DIRECT sources' contract, and a cancellation of the
        // caller cancels the whole scope. Duplicate addresses collapse to one entry, as before.
        val deferred = addresses.distinct().map { address ->
            address to async {
                when (val r = getBalanceAssets(address)) {
                    is ResultResponse.Success -> r.data
                    is ResultResponse.Error -> emptyList()
                }
            }
        }
        val result = deferred.associate { (address, job) -> address to job.await() }
        ResultResponse.Success(result)
    }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?,
        amount: BigInteger?
    ): ResultResponse<List<FeeData>> {
        // Forward the draft tx context so the backend can do context-aware estimation (EVM L1+L2,
        // TRON energy). All params are optional — omitting them keeps the old blind estimate (back-compat).
        // For UTXO the per-vByte rate needs a size, so we estimate vbytes locally (1-in / 2-out).
        val isUtxo = network.networkType == NetworkType.UTXO || network.networkType == NetworkType.BITCOIN
        return proxyCall(
            call = {
                proxyService.feeOptions(
                    networkId = network.id,
                    sender = fromAddress,
                    recipient = toAddress,
                    tokenAddress = asset?.contractAddress?.takeIf { it.isNotBlank() },
                    amount = amount?.toString(),
                    vbytes = if (isUtxo) estimateUtxoVbytes() else null
                )
            },
            map = { dto ->
                val evmGasLimit = dto.gasLimit
                val isEvm = network.networkType == NetworkType.EVM
                // Backend returns a `tiers` object (slow/standard/fast machine keys), not an array.
                listOfNotNull(
                    dto.tiers?.slow?.let { it to "کند" },
                    dto.tiers?.standard?.let { it to "عادی" },
                    dto.tiers?.fast?.let { it to "سریع" }
                ).map { (tier, level) ->
                    // For EVM the node reserves gasLimit × maxFeePerGas (+ l1DataFee) up-front, NOT the
                    // gasPrice-based `estimatedCost`/`totalFee` (which is l2ExecutionFee = gasLimit × gasPrice
                    // + l1DataFee). Using the lower estimate here made a MAX native send deduct too little, so
                    // value + reserved exceeded the balance and broadcast was rejected ("insufficient funds").
                    // Reserve the ceiling — mirrors the DIRECT path (EvmDataSource.getFeeOptions) and still
                    // never underestimates the L2 total (ceiling ≥ totalFee), preserving the L2 fix.
                    val evmCeiling = if (isEvm && tier.maxFeePerGas != null && evmGasLimit != null) {
                        tier.maxFeePerGas * evmGasLimit + (tier.l1DataFee ?: BigInteger.ZERO)
                    } else null
                    // Non-EVM (or an older backend without maxFeePerGas) keeps the context-aware total.
                    val feeRaw = evmCeiling ?: tier.totalFee ?: dto.totalFee ?: tier.estimatedCost ?: BigInteger.ZERO
                    FeeData(
                        level = level,
                        feeInSmallestUnit = feeRaw.toBigDecimal(),
                        estimatedTime = estimatedTimeLabel(tier.estimatedSeconds, level),
                        gasPrice = tier.gasPrice,
                        gasLimit = evmGasLimit,
                        feeInCoin = normalize(feeRaw, network.decimals, network.networkType),
                        feeInUsd = null,
                        // The build uses an integer sat/vByte; round a fractional rate UP and keep a
                        // relayable floor of 1. Display uses `feeRaw` above, so accuracy is unaffected.
                        feeRateInSatsPerByte = tier.satPerVByte?.let {
                            maxOf(1L, ceil(it).toLong())
                        }
                    )
                }
            }
        )
    }

    /**
     * Rough vByte size for the fee preview, script-type-correct per network (matches
     * [BitcoinjUtxoTxBuilder]'s sizing). Coin selection happens at send time, so we assume a typical
     * 1-input / 2-output (recipient + change) tx; the builder recomputes the exact fee on send.
     */
    private fun estimateUtxoVbytes(inputs: Int = 1, outputs: Int = 2): Int {
        return when (network.name) {
            NetworkName.DOGE, NetworkName.DOGETESTNET -> (inputs * 148) + (outputs * 34) + 10
            else -> (inputs * 68) + (outputs * 31) + 11
        }
    }

    override suspend fun getTransactionFeeDetails(txId: String): ResultResponse<TransactionFeeDetails> {
        // On-demand full fee/energy — the server proxy of gettransactioninfobyid (TRON) /
        // eth_getTransactionReceipt (EVM). The history list is intentionally partial for TRON token
        // rows (feeRaw="0", energy=null on the carrier tx), so this fills the real values when the
        // user OPENS a tx. A PENDING receipt has feeRaw=null → surfaced as ZERO until it settles.
        return proxyCall(
            call = { proxyService.transactionDetail(network.id, txId) },
            map = { dto ->
                TransactionFeeDetails(
                    // Top-level feeRaw is null while PENDING; fall back to the TRON breakdown, then ZERO.
                    fee = dto.feeRaw ?: dto.tron?.feeBreakdown?.feeRaw ?: BigInteger.ZERO,
                    energyUsed = dto.tron?.energyUsed,
                    bandwidthUsed = dto.tron?.bandwidthUsed,
                    energyFee = dto.tron?.feeBreakdown?.energyFeeRaw,
                    networkFee = dto.tron?.feeBreakdown?.networkFeeRaw
                )
            }
        )
    }

    /**
     * Maps the UI's Persian fee-tier label (from [getFeeOptions]: «کند»/«عادی»/«سریع») to the
     * backend's machine key (`slow`/`standard`/`fast`) that the prepare endpoints expect. Passing the
     * raw Persian string made TRON native + EVM contract-call sends silently fall back to the server
     * default tier (TASK-16). Already-machine keys pass through; unknown/absent → `standard`.
     */
    private fun toBackendFeeLevel(feeLevel: String?): String = when (feeLevel) {
        "کند" -> "slow"
        "عادی" -> "standard"
        "سریع" -> "fast"
        "slow", "standard", "fast" -> feeLevel
        else -> "standard"
    }

    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> {
        return when (params) {
            is TransactionParams.Evm -> sendEvm(params, privateKeyHex)
            is TransactionParams.Tvm -> sendTron(params, privateKeyHex)
            is TransactionParams.TvmPrepared -> sendPreparedTron(params, privateKeyHex)
            is TransactionParams.Utxo -> sendUtxo(params, privateKeyHex)
        }
    }

    /**
     * پای TVMِ سوآپ/پل: تراکنش را سرور ساخته، پس این‌جا هیچ `prepare`ی صدا زده نمی‌شود — فقط امضا
     * و ارسال.
     *
     * بدنه به‌صورت `JsonObject` ساخته و بعد رشته می‌شود، نه از روی یک DTOی typed: پروکسی همان
     * payload را به نود می‌دهد و نود در برابرِ همین بایت‌ها اعتبارسنجی می‌کند، پس هر فیلدی که یک
     * لایهٔ بازتابی دور بیندازد بی‌سروصدا امضا را باطل می‌کند.
     */
    private suspend fun sendPreparedTron(
        params: TransactionParams.TvmPrepared,
        privateKeyHex: String
    ): ResultResponse<String> {
        return try {
            val rawData = JsonParser.parseString(params.rawDataJson).asJsonObject
            val signatureHex = tronSigner.signPreparedTronTx(
                rawDataHex = params.rawDataHex,
                expectedTxId = params.txId,
                privateKeyHex = privateKeyHex
            )

            val signedTx = JsonObject().apply {
                addProperty("txID", params.txId)
                add("raw_data", rawData)
                addProperty("raw_data_hex", params.rawDataHex)
                addProperty("visible", params.visible)
                add("signature", JsonArray().apply { add(signatureHex) })
            }

            proxyCall(
                call = { proxyService.broadcastTransaction(network.id, BroadcastRequestDto(signedTx.toString())) },
                map = { it.txHash?.takeIf { hash -> hash.isNotBlank() } ?: params.txId }
            )
        } catch (e: Exception) {
            ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
        }
    }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        // Unified history (POST /api/mobile/v1/history) is Phase 2 — different shape + cursor.
        return ResultResponse.Error(
            ApiException(
                apiError = ApiError.UnsupportedOperation,
                reasonFa = "PROXY history is implemented in Phase 2"
            )
        )
    }

    private suspend fun sendEvm(
        params: TransactionParams.Evm,
        privateKeyHex: String
    ): ResultResponse<String> {
        return try {
            val sender = signer.deriveAddress(privateKeyHex)
            val contractData = params.data?.takeIf { it.isNotBlank() && it != "0x" }
            if (contractData != null) {
                val prepared = when (
                    val prep = proxyCall(
                        call = {
                            proxyService.prepareContractCall(
                                network.id,
                                PrepareContractCallRequestDto(
                                    sender = sender,
                                    to = params.to,
                                    data = contractData,
                                    valueWei = params.amount,
                                    gasLimit = params.gasLimit.takeIf { it > BigInteger.ZERO },
                                    feeLevel = toBackendFeeLevel(params.feeLevel)
                                )
                            )
                        },
                        map = { it.transaction }
                    )
                ) {
                    is ResultResponse.Success -> prep.data
                        ?: return ResultResponse.Error(
                            ApiException(ApiError.ValidationError, reasonFa = "prepare returned no transaction")
                        )
                    is ResultResponse.Error -> return ResultResponse.Error(prep.exception)
                }

                val rawSignedTx = signer.signPreparedTransaction(privateKeyHex, prepared.toPreparedEvmTx())
                return proxyCall(
                    call = { proxyService.broadcastTransaction(network.id, BroadcastRequestDto(rawSignedTx)) },
                    map = { it.txHash.orEmpty() }
                )
            }

            val assetId = params.assetId?.takeIf { it.isNotBlank() }
                ?: return ResultResponse.Error(
                    ApiException(ApiError.ValidationError, reasonFa = "Missing assetId for proxy send")
                )

            // /prepare returns the server-built UNSIGNED tx (native-vs-token decided server-side from
            // the registry assetId). We sign it locally and relay the raw signed payload.
            val prepared = when (
                val prep = proxyCall(
                    call = {
                        proxyService.prepareTransaction(
                            network.id,
                            PrepareTxRequestDto(
                                sender = sender,
                                recipient = params.to,
                                assetId = assetId,
                                amountRaw = params.amount,
                                feeLevel = toBackendFeeLevel(params.feeLevel)
                            )
                        )
                    },
                    map = { it.transaction }
                )
            ) {
                is ResultResponse.Success -> prep.data
                    ?: return ResultResponse.Error(
                        ApiException(ApiError.ValidationError, reasonFa = "prepare returned no transaction")
                    )
                is ResultResponse.Error -> return ResultResponse.Error(prep.exception)
            }

            val rawSignedTx = signer.signPreparedTransaction(privateKeyHex, prepared.toPreparedEvmTx())

            // Canonical id across families is `txHash` — used for display and /status polling.
            proxyCall(
                call = { proxyService.broadcastTransaction(network.id, BroadcastRequestDto(rawSignedTx)) },
                map = { it.txHash.orEmpty() }
            )
        } catch (e: Exception) {
            ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
        }
    }

    private suspend fun sendTron(
        params: TransactionParams.Tvm,
        privateKeyHex: String
    ): ResultResponse<String> {
        return try {
            val sender = tronSigner.deriveTronAddress(privateKeyHex)
            if (!params.contractAddress.isNullOrBlank() && !params.contractFunction.isNullOrBlank()) {
                val unsignedTx = when (
                    val prep = proxyCall(
                        call = {
                            proxyService.prepareContractCall(
                                network.id,
                                PrepareContractCallRequestDto(
                                    sender = sender,
                                    contractAddress = params.contractAddress,
                                    functionSelector = params.contractFunction,
                                    parameters = listOf(
                                        ContractCallParameterDto(type = "address", value = params.toAddress),
                                        ContractCallParameterDto(type = "uint256", value = params.amount.toString())
                                    ),
                                    feeLimitSun = BigInteger.valueOf(params.feeLimit)
                                )
                            )
                        },
                        map = { it.transaction }
                    )
                ) {
                    is ResultResponse.Success -> prep.data
                        ?: return ResultResponse.Error(
                            ApiException(ApiError.ValidationError, reasonFa = "prepare returned no transaction")
                        )
                    is ResultResponse.Error -> return ResultResponse.Error(prep.exception)
                }

                val rawDataHex = unsignedTx.get("raw_data_hex")
                    ?.takeIf { !it.isJsonNull }?.asString
                    ?: return ResultResponse.Error(
                        ApiException(ApiError.ValidationError, reasonFa = "prepare returned no raw_data_hex")
                    )
                val signatureHex = tronSigner.signRawDataHex(rawDataHex, privateKeyHex)
                val signedTx = unsignedTx.deepCopy().apply {
                    add("signature", JsonArray().apply { add(signatureHex) })
                }
                return proxyCall(
                    call = { proxyService.broadcastTransaction(network.id, BroadcastRequestDto(signedTx.toString())) },
                    map = { it.txHash.orEmpty() }
                )
            }

            val assetId = params.assetId?.takeIf { it.isNotBlank() }
                ?: return ResultResponse.Error(
                    ApiException(ApiError.ValidationError, reasonFa = "Missing assetId for proxy send")
                )

            // /prepare returns the node-built UNSIGNED Tron tx (incl. raw_data_hex + txID). Native-vs-
            // TRC20 is decided server-side from the registry; fee_limit for TRC20 is baked in by the
            // server (its 100-TRX default), so we don't override it here.
            val unsignedTx = when (
                val prep = proxyCall(
                    call = {
                        proxyService.prepareTransaction(
                            network.id,
                            PrepareTxRequestDto(
                                sender = sender,
                                recipient = params.toAddress,
                                assetId = assetId,
                                amountRaw = params.amount,
                                feeLevel = toBackendFeeLevel(params.feeLevel)
                            )
                        )
                    },
                    map = { it.transaction }
                )
            ) {
                is ResultResponse.Success -> prep.data
                    ?: return ResultResponse.Error(
                        ApiException(ApiError.ValidationError, reasonFa = "prepare returned no transaction")
                    )
                is ResultResponse.Error -> return ResultResponse.Error(prep.exception)
            }

            val rawDataHex = unsignedTx.get("raw_data_hex")
                ?.takeIf { !it.isJsonNull }?.asString
                ?: return ResultResponse.Error(
                    ApiException(ApiError.ValidationError, reasonFa = "prepare returned no raw_data_hex")
                )

            val signatureHex = tronSigner.signRawDataHex(rawDataHex, privateKeyHex)

            // Reassemble the signed tx exactly as TRON expects: the verbatim unsigned object + the
            // `signature` array. Broadcast it as a JSON string (the server JSON.parses string bodies).
            val signedTx = unsignedTx.deepCopy().apply {
                add("signature", JsonArray().apply { add(signatureHex) })
            }

            proxyCall(
                call = { proxyService.broadcastTransaction(network.id, BroadcastRequestDto(signedTx.toString())) },
                map = { it.txHash.orEmpty() }
            )
        } catch (e: Exception) {
            ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
        }
    }

    private suspend fun sendUtxo(
        params: TransactionParams.Utxo,
        privateKeyHex: String
    ): ResultResponse<String> {
        return try {
            val builder = utxoTxBuilder
                ?: return ResultResponse.Error(
                    ApiException(
                        ApiError.UnsupportedOperation,
                        reasonFa = "UTXO proxy builder unavailable for ${network.id}"
                    )
                )
            val assetId = params.assetId?.takeIf { it.isNotBlank() }
                ?: return ResultResponse.Error(
                    ApiException(ApiError.ValidationError, reasonFa = "Missing assetId for proxy send")
                )
            val sender = builder.deriveAddress(privateKeyHex)

            // /prepare returns the spendable UTXO set + the current fee rate (no coin selection, no
            // scriptPubKey, no change, no absolute fee — the client does all of that locally).
            val prepared = when (
                val prep = proxyCall(
                    call = {
                        proxyService.prepareTransaction(
                            network.id,
                            PrepareTxRequestDto(
                                sender = sender,
                                recipient = params.toAddress,
                                assetId = assetId,
                                amountRaw = BigInteger.valueOf(params.amountInSatoshi)
                            )
                        )
                    },
                    map = { it }
                )
            ) {
                is ResultResponse.Success -> prep.data
                is ResultResponse.Error -> return ResultResponse.Error(prep.exception)
            }

            val utxos = prepared.utxos.orEmpty().mapNotNull { dto ->
                val txid = dto.txid?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val vout = dto.vout ?: return@mapNotNull null
                val valueSat = dto.value?.toLong() ?: return@mapNotNull null
                UtxoInput(txid = txid, vout = vout, valueSat = valueSat)
            }
            if (utxos.isEmpty()) {
                return ResultResponse.Error(
                    ApiException(ApiError.ValidationError, reasonFa = "prepare returned no spendable UTXOs")
                )
            }

            // Prefer the server's live fee oracle; fall back to the caller's chosen rate. The builder
            // takes an INTEGER sat/vByte, and on low-fee testnets the rate is fractional (e.g. 0.5).
            // A naive toLong() truncates 0.x → 0, producing a ZERO-fee tx (the wallet spends the whole
            // balance into recipient+change with nothing left for miners). Round UP and floor at 1,
            // mirroring the fee-preview path (getFeeOptions) so preview and send agree.
            val effectiveRate = prepared.feeRate ?: params.feeRateInSatsPerByte.toDouble()
            val feeRateSatPerVByte = maxOf(1L, kotlin.math.ceil(effectiveRate).toLong())
            val signedTxHex = builder.buildSignedTx(
                privateKeyHex = privateKeyHex,
                recipient = params.toAddress,
                amountSat = params.amountInSatoshi,
                feeRateSatPerVByte = feeRateSatPerVByte,
                utxos = utxos
            )

            proxyCall(
                call = { proxyService.broadcastTransaction(network.id, BroadcastRequestDto(signedTxHex)) },
                map = { it.txHash.orEmpty() }
            )
        } catch (e: Exception) {
            ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
        }
    }

    /** Parse the hex-quantity `/prepare` EVM transaction object into the signer's domain form. */
    private fun JsonObject.toPreparedEvmTx(): PreparedEvmTx {
        fun str(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString
        val resolvedChainId = get("chainId")?.takeIf { !it.isJsonNull }?.asLong ?: network.chainId
            ?: throw IllegalStateException("Missing chainId in prepared tx")
        return PreparedEvmTx(
            to = str("to") ?: throw IllegalStateException("Missing 'to' in prepared tx"),
            value = hexToBigInteger(str("value")) ?: BigInteger.ZERO,
            data = str("data") ?: "0x",
            nonce = hexToBigInteger(str("nonce"))
                ?: throw IllegalStateException("Missing nonce in prepared tx"),
            gasLimit = hexToBigInteger(str("gasLimit"))
                ?: throw IllegalStateException("Missing gasLimit in prepared tx"),
            chainId = resolvedChainId,
            type = get("type")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            gasPrice = hexToBigInteger(str("gasPrice")),
            maxFeePerGas = hexToBigInteger(str("maxFeePerGas")),
            maxPriorityFeePerGas = hexToBigInteger(str("maxPriorityFeePerGas"))
        )
    }

    private fun hexToBigInteger(hex: String?): BigInteger? {
        val raw = hex?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val clean = raw.removePrefix("0x").removePrefix("0X")
        if (clean.isEmpty()) return BigInteger.ZERO
        return try {
            BigInteger(clean, 16)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun ProxyBalanceDto.toAsset(isNative: Boolean): Asset {
        val decimals = this.decimals ?: network.decimals
        val raw = this.balanceRaw ?: BigInteger.ZERO
        return Asset(
            name = name ?: symbol.orEmpty(),
            symbol = symbol.orEmpty(),
            decimals = decimals,
            contractAddress = if (isNative) null else contractAddress,
            balance = normalize(raw, decimals, network.networkType)
        )
    }

    override suspend fun getHistory(
        addresses: List<HistoryAddressDto>,
        cursor: String?,
        limit: Int?
    ): ResultResponse<HistoryPage> {
        if (addresses.size > MAX_HISTORY_PAIRS) {
            return ResultResponse.Error(
                ApiException(
                    ApiError.ValidationError,
                    reasonFa = "Too many address pairs: ${addresses.size} (max $MAX_HISTORY_PAIRS)"
                )
            )
        }
        return try {
            val resp = proxyService.history(HistoryRequestDto(addresses, cursor, limit))
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                ResultResponse.Success(
                    HistoryPage(
                        items = body.items.map { it.toDomain() },
                        nextCursor = body.pageInfo.nextCursor,
                        hasMore = body.pageInfo.hasMore,
                        staleSources = body.staleSources?.map { src ->
                            listOfNotNull(src.networkId, src.address).joinToString(":")
                                .ifEmpty { src.error.orEmpty() }
                        }
                    )
                )
            } else {
                ResultResponse.Error(ApiException(ApiError.from(null, resp.code()), httpStatus = resp.code()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultResponse.Error(ApiException(ApiError.Unknown(null, e.message), cause = e))
        }
    }

    override suspend fun getBatchBalances(
        wallets: List<BatchBalanceWalletDto>
    ): ResultResponse<Map<String, ResultResponse<List<Asset>>>> {
        return proxyCall(
            call = { proxyService.batchBalances(BatchBalanceRequestDto(wallets)) },
            map = { data -> data.mapValues { (_, result) -> result.toResult() } }
        )
    }

    private fun BatchNetworkBalanceDto.toResult(): ResultResponse<List<Asset>> {
        return if (status.equals("ok", ignoreCase = true)) {
            ResultResponse.Success(balances.orEmpty().map { it.toBatchAsset() })
        } else {
            ResultResponse.Error(ApiException(ApiError.from(error?.code, 0), reasonFa = error?.message))
        }
    }

    private fun ProxyBalanceDto.toBatchAsset(): Asset {
        val d = decimals ?: 0
        val raw = balanceRaw ?: BigInteger.ZERO
        // networkType is irrelevant for a base-10 BigInteger input; reusing normalize() keeps the
        // formatting (strip-trailing-zeros, fixed scale) consistent with the single-network toAsset().
        return Asset(name ?: symbol.orEmpty(), symbol.orEmpty(), d, contractAddress, normalize(raw, d, network.networkType))
    }

    /**
     * برچسبِ زمانِ تقریبیِ یک ردهٔ کارمزد.
     *
     * ⚠️ هرگز رشتهٔ تهی برنمی‌گرداند. پیش‌تر `estimatedSeconds` نبودن یعنی `""` و صفحهٔ تأیید یک
     * `Text` خالی می‌کشید — یعنی زمانِ ارسال «گاهی» دیده می‌شد و گاهی نه، بسته به اینکه سرور آن
     * فیلد را فرستاده باشد یا نه. مسیرِ مستقیم (`EvmDataSource.getFeeOptions`) همیشه یک متن دارد،
     * و قرارِ پروژه این است که دو حالتِ DIRECT و PROXY رفتارِ یکسان داشته باشند.
     *
     * وقتی سرور عددی نداده، همان تخمین‌های مسیرِ مستقیم استفاده می‌شوند — حدس‌اند، ولی همان
     * حدسی که کاربر در حالتِ دیگر هم می‌بیند.
     */
    private fun estimatedTimeLabel(seconds: Long?, level: String): String {
        // ⚠️ این سه برچسب همان‌هایی‌اند که بالا به `tiers.slow/standard/fast` داده می‌شوند؛
        // اگر آن‌جا عوض شدند، این‌جا هم باید عوض شوند.
        val secs = seconds?.takeIf { it > 0L } ?: return when (level) {
            "کند" -> "~ ۲ دقیقه"
            "سریع" -> "~ ۱۵ ثانیه"
            else -> "~ ۳۰ ثانیه"
        }
        return if (secs < 60L) "~ $secs ثانیه" else "~ ${secs / 60L} دقیقه"
    }

    private companion object {
        const val MAX_HISTORY_PAIRS = 25
    }
}
