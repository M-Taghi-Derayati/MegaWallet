package com.mtd.data.datasource

import com.mtd.core.model.NetworkName.*
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.repository.TransactionParams
import com.mtd.data.service.AddressFullDto
import com.mtd.data.service.BlockcypherApiService
import com.mtd.data.service.PushTxDto
import com.mtd.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitcoinj.base.Address
import org.bitcoinj.base.Coin
import org.bitcoinj.base.ScriptType
import org.bitcoinj.base.Sha256Hash
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.crypto.TransactionSignature


import retrofit2.Retrofit
import java.math.BigInteger
import java.time.Instant
import java.time.format.DateTimeFormatter


class BitcoinDataSource(
    private val network: BlockchainNetwork,
    private val retrofitBuilder: Retrofit.Builder,
    private val networkParameters: NetworkParameters
) : IChainDataSource {
    private val chainName = if (network.name == BITCOIN) "btc/main" else "btc/test3"


    override suspend fun getBalance(address: String): ResultResponse<BigInteger> {
        try {
            val api = retrofitBuilder.baseUrl(network.explorers.get(0)).build()
                .create(BlockcypherApiService::class.java)

            val response = api.getTransactionHistory(address)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("API Error: ${response.code()}")
            }
            ResultResponse.Success(response.body()!!.finalBalance)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
        return ResultResponse.Error(Exception("Failed to fetch from"))
    }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        try {
            val api = retrofitBuilder.baseUrl(network.explorers.first()).build()
                .create(BlockcypherApiService::class.java)
            val response = api.getTransactionHistory(address)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("API Error: ${response.code()}")
            }

            val txrefs = response.body()!!.txs ?: emptyList()
            val result = txrefs.map { tx ->
                val inputAddresses = tx.inputs?.flatMap { it.addresses ?: emptyList() } ?: emptyList()
                val outputAddresses = tx.outputs?.flatMap { it.addresses ?: emptyList() } ?: emptyList()

                val isSend = inputAddresses.contains(address)
                val fromAddress = inputAddresses.firstOrNull()
                val toAddress = outputAddresses.firstOrNull { it != address }

                val receivedAmount = tx.outputs?.filter {
                    it.addresses?.contains(address) == true
                }?.sumOf { it.value?.toLong() ?: 0L } ?: 0L

                val sentAmount = tx.inputs?.filter {
                    it.addresses?.contains(address) == true
                }?.sumOf { it.outputValue ?: 0L } ?: 0L


                BitcoinTransaction(
                    hash = tx.hash,
                    amount = BigInteger.valueOf(if (isSend) sentAmount else receivedAmount),
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    fee = tx.fees?.toBigInteger()?: BigInteger.ZERO,
                    status =if (isSend) TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
                    timestamp =Instant.parse(tx.confirmed).toEpochMilli()
                )
            }
            return ResultResponse.Success(result)

            /* for (explorer in network.explorers) {
                 when (network.name) {
                     BITCOINTESTNET -> {
                         val api = retrofitBuilder.baseUrl(explorer).build()
                             .create(BlockcypherApiService::class.java)
                         val response = api.getTransactionHistory(address = address)

                         if (!response.isSuccessful || response.body() == null) {
                             throw Exception("API Error: ${response.code()}")
                         }

                         val records = response.body()?.txs?.map { dto ->
                             // منطق پیچیده تبدیل DTO به مدل Domain
                             var netAmountChange = 0L
                             var isOutgoing = false

                             // محاسبه ورودی‌ها از آدرس ما
                             val totalInputFromAddress = dto.vin
                                 .filter { it.prevout?.address == address }
                                 .sumOf { it.prevout?.value ?: 0L }

                             // محاسبه خروجی‌ها به آدرس ما
                             val totalOutputToAddress = dto.vout
                                 .filter { it.address == address }
                                 .sumOf { it.value }

                             if (totalInputFromAddress > 0) {
                                 isOutgoing = true
                             }

                             netAmountChange = totalOutputToAddress - totalInputFromAddress

                             UtxoTransaction(
                                 hash = dto.txid,
                                 timestamp = dto.status.blockTime
                                     ?: (System.currentTimeMillis() / 1000),
                                 fee = BigInteger.valueOf(dto.fee),
                                 status = if (dto.status.confirmed) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
                                 inputs = dto.vin.mapNotNull { vin ->
                                     vin.prevout?.let {
                                         TransactionInput(
                                             it.address,
                                             BigInteger.valueOf(it.value)
                                         )
                                     }
                                 },
                                 outputs = dto.vout.map { vout ->
                                     TransactionOutput(
                                         vout.address,
                                         BigInteger.valueOf(vout.value)
                                     )
                                 },
                                 netAmountChange = BigInteger.valueOf(netAmountChange),
                                 isOutgoing = isOutgoing
                             )
                         }
                         return ResultResponse.Success(records!!)
                     }

                     else -> return ResultResponse.Error(Exception(""))
                 }
             }*/
        } catch (e: Exception) {
            return ResultResponse.Error(e)
        }
        return ResultResponse.Error(Exception("Failed to fetch from"))
    }


    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> = withContext(Dispatchers.IO) {

        if (params !is TransactionParams.Utxo) {
            return@withContext ResultResponse.Error(IllegalArgumentException("Invalid params type for BitcoinDataSource"))
        }

        try {

            val ecKeys = ECKey.fromPrivate(privateKeyHex.hexToBytes())
            val fromAddress =
                ecKeys.toAddress(ScriptType.P2WPKH, networkParameters.network()).toString()
            val toAddress = Address.fromString(networkParameters, params.toAddress)
            val retrofit = retrofitBuilder.baseUrl(network.explorers.first()).build()
            val api = retrofit.create(BlockcypherApiService::class.java)
            // 3. دریافت UTXOها از Blockstream API

            val utxoResponse = api.getUtxos(fromAddress)
            if (!utxoResponse.isSuccessful || utxoResponse.body()?.txs == null) {
                return@withContext ResultResponse.Error(Exception("UTXO not available"))
            }

            val utxos = utxoResponse.body()!!.txrefs!!
            val tx = Transaction(networkParameters)
            var totalInput = 0L
            val scriptMap = mutableMapOf<Pair<String, Int>, ByteArray>()
            for (utxo in utxos) {
                val outPoint =
                    TransactionOutPoint(utxo.txOutputN.toLong(), Sha256Hash.wrap(utxo.txHash))
                val input = TransactionInput(null, byteArrayOf(), outPoint)
                tx.addInput(input)
                totalInput += utxo.value

                scriptMap[utxo.txHash to utxo.txOutputN] = utxo.script.hexToBytes()

                if (totalInput >= params.amountInSatoshi + 200) break
            }

            // 4. اضافه کردن خروجی به گیرنده
            tx.addOutput(Coin.valueOf(params.amountInSatoshi), toAddress)
            val fee = estimateTxFee(tx.inputs.size, tx.outputs.size, params.feeRateInSatsPerByte)
            val change = totalInput - params.amountInSatoshi - fee
            if (change > 546) { // dust threshold
                tx.addOutput(
                    Coin.valueOf(change),
                    ecKeys.toAddress(ScriptType.P2WPKH, networkParameters.network())
                )
            }
            // 4. امضا تراکنش
            for (i in tx.inputs.indices) {
                val input = tx.getInput(i.toLong())
                val utxo = utxos[i]
                val scriptPubKey = scriptMap[utxo.txHash to utxo.txOutputN]
                    ?: throw IllegalStateException("Missing scriptPubKey for input $i")

                val hash = tx.hashForSignature(i, scriptPubKey, Transaction.SigHash.ALL, false)
                val signature = ecKeys.sign(hash)
                val txSignature = TransactionSignature(signature, Transaction.SigHash.ALL, false)
                val scriptSig = ScriptBuilder.createInputScript(txSignature, ecKeys)
                input.withScriptSig(scriptSig)
            }


            // 5. برادکست تراکنش
            val txHex = tx.serialize().joinToString(separator = "") { byte -> "%02x".format(byte) }
            val response =
                api.broadcastTransaction(PushTxDto(txHex)) // تابعی که تراکنش رو ارسال کنه

            return@withContext if (response.isSuccessful && response.body() != null) {
                ResultResponse.Success(response.body()!!.tx.hash)
            } else {
                ResultResponse.Error(
                    Exception(
                        "Broadcast failed: ${
                            response.errorBody()?.string()
                        }"
                    )
                )
            }

        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    private fun estimateTxFee(inputs: Int, outputs: Int, feeRate: Long): Long {
        val txSize = inputs * 148 + outputs * 34 + 10
        return txSize * feeRate
    }

    override suspend fun estimateFee(): ResultResponse<BigInteger> {
        // TODO: Implement fee estimation from a service like mempool.space
        return ResultResponse.Success(BigInteger.valueOf(10)) // 10 sats/byte as a placeholder
    }


    override suspend fun getBalanceEVM(address: String): ResultResponse<List<Asset>> {
        TODO("Not yet implemented")
    }


    // توابع کمکی که باید در یک فایل Utils قرار گیرند
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}