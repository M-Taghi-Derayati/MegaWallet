package com.mtd.data.datasource


import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.datasource.IChainDataSource.FeeData
import com.mtd.data.repository.TransactionParams
import com.mtd.data.service.BlockcypherApiService
import com.mtd.data.service.MempoolUtxoDto
import com.mtd.domain.model.Asset
import com.mtd.domain.model.BitcoinTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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
import org.web3j.protocol.Web3j
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber
import java.math.BigInteger


class BitcoinDataSource(
    private val network: BlockchainNetwork,
    private val retrofitBuilder: Retrofit.Builder,
    private val networkParameters: NetworkParameters
) : IChainDataSource {


    override suspend fun getBalance(address: String): ResultResponse<BigInteger> {
        try {
            val api = retrofitBuilder.baseUrl(network.explorers.first()).build()
                .create(BlockcypherApiService::class.java)

            val response = api.getAddressDetails(address)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("API Error: ${response.code()}")
            }
            val balance =
                response.body()!!.chain_stats.funded_txo_sum - response.body()!!.chain_stats.spent_txo_sum
            return ResultResponse.Success(BigInteger.valueOf(balance))
        } catch (e: Exception) {
            return ResultResponse.Error(e)
        }
        return ResultResponse.Error(Exception("Failed to fetch from"))
    }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {
        try {
            val api = retrofitBuilder.baseUrl(network.explorers.first()).build()
                .create(BlockcypherApiService::class.java)
            val response = api.getConfirmedTransactions(address)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("API Error: ${response.code()}")
            }

            val txrefs = response.body()!!
            val result = txrefs.map { tx ->
                val fromAddress = tx.vin.firstOrNull()?.prevout?.scriptpubkey_address ?: ""
                val toOutput = tx.vout.firstOrNull { it.scriptpubkey_address != fromAddress }
                val toAddress = toOutput?.scriptpubkey_address ?: ""
                val amount = toOutput?.value ?: 0L


                BitcoinTransaction(
                    hash = tx.txid,
                    amount = amount.toBigInteger(),
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    fee = BigInteger.valueOf(tx.fee ?: 0L),
                    timestamp = tx.status.block_time ?: 0L,
                    status = when {
                        tx.status.confirmed == true -> TransactionStatus.CONFIRMED
                        else -> TransactionStatus.FAILED
                    },
                    isOutgoing = when {
                        fromAddress == address && toAddress == address -> true
                        fromAddress == address -> true
                        toAddress == address -> false
                        else -> true
                    }
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
                ecKeys.toAddress(ScriptType.P2WPKH, networkParameters.network())
            val retrofit = retrofitBuilder.baseUrl(network.explorers.first())
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()
            val api = retrofit.create(BlockcypherApiService::class.java)
            // 3. دریافت UTXOها از Blockstream API

            val utxoResponse = api.getUtxos(fromAddress.toString())
            if (!utxoResponse.isSuccessful || utxoResponse.body().isNullOrEmpty()) {
                return@withContext ResultResponse.Error(Exception("UTXO not available"))
            }

            val confirmedUtxos = utxoResponse.body()!!.filter { it.status.confirmed }
            if (confirmedUtxos.isEmpty()) {
                return@withContext ResultResponse.Error(Exception("No confirmed UTXOs available to spend."))
            }

            var totalInputValue = 0L
            val inputsToSpend = mutableListOf<MempoolUtxoDto>()
            // UTXO ها را تا زمانی که مبلغ اصلی پوشش داده شود، جمع کن
            for (utxo in confirmedUtxos.sortedBy { it.value }) {
                totalInputValue += utxo.value
                inputsToSpend.add(utxo)
                if (totalInputValue >= params.amountInSatoshi) {
                    break // به محض پوشش مبلغ، متوقف شو
                }
            }
            // اگر حتی مبلغ اصلی هم پوشش داده نشد، خطا بده
            if (totalInputValue < params.amountInSatoshi) {
                return@withContext ResultResponse.Error(Exception("Insufficient funds to cover amount."))
            }


            // مرحله ۲: محاسبه کارمزد نهایی بر اساس تعداد ورودی‌های واقعی
           // val finalFee = estimateTxFee(inputsToSpend.size, 2) // <-- محاسبه کارمزد با تعداد درست
            val finalFee = estimateTxFeeBasedOnRate(inputsToSpend.size, 2, params.feeRateInSatsPerByte)
            val requiredTotal = params.amountInSatoshi + finalFee

            // مرحله ۳: بررسی نهایی موجودی (آیا برای کارمزد هم پول داریم؟)
            if (totalInputValue < requiredTotal) {
                // اگر پول برای کارمزد کافی نیست، سعی کن UTXO بیشتری اضافه کنی
                // (این بخش پیشرفته است و فعلاً برای سادگی خطا برمی‌گردانیم)
                return@withContext ResultResponse.Error(Exception("Insufficient funds to cover transaction fee. Required: $requiredTotal, Available: $totalInputValue"))
            }
            val tx = Transaction(networkParameters)
            inputsToSpend.forEach { utxo ->
                tx.addInput(Sha256Hash.wrap(utxo.txid), utxo.vout.toLong(), parse(ByteArray(0)))
            }

            // ۳. اضافه کردن خروجی‌ها (Outputs)
            val toAddress = Address.fromString(networkParameters, params.toAddress)
            tx.addOutput(Coin.valueOf(params.amountInSatoshi), toAddress)

            val changeAmount = totalInputValue - params.amountInSatoshi - finalFee
            val DUST_THRESHOLD = 546L // آستانه استاندارد به صورت Long
            if (changeAmount >= DUST_THRESHOLD) {
                // اگر باقیمانده به اندازه کافی بزرگ است، یک خروجی باقیمانده می‌سازیم.
                tx.addOutput(Coin.valueOf(changeAmount), fromAddress)
                Timber.d("Change of $changeAmount sats will be sent back to $fromAddress")
            } else {
                // اگر باقیمانده Dust است، هیچ خروجی باقیمانده‌ای نمی‌سازیم.
                // این مقدار به صورت خودکار به کارمزد ماینر اضافه خواهد شد.
                Timber.d("Change amount ($changeAmount sats) is below dust threshold. Adding to miner fee.")
            }
           /* if (changeAmount >= Coin.SMALLEST_UNIT_EXPONENT) {
                tx.addOutput(Coin.valueOf(changeAmount), fromAddress)
            }*/

            // ۴. امضای هر ورودی به روش صحیح برای SegWit (P2WPKH)
            val signedInputs = mutableListOf<TransactionInput>()
            for (i in inputsToSpend.indices) {
                val utxo = inputsToSpend[i]
                val utxoValue = Coin.valueOf(utxo.value)

                val scriptCode = ScriptBuilder.createP2PKHOutputScript(ecKeys).program
                // ۲. محاسبه Sighash
                val sighash = tx.hashForWitnessSignature(i, scriptCode, utxoValue, Transaction.SigHash.ALL, false)
                // ۳. امضای هش
                val signature = ecKeys.sign(sighash)
                val transactionSignature = TransactionSignature(signature, Transaction.SigHash.ALL, false)
                val witness = TransactionWitness.redeemP2WPKH(transactionSignature, ecKeys)
                val originalInput = tx.getInput(i.toLong())
                val signedInput = originalInput.withWitness(witness)
                // ۶. اضافه کردن ورودی امضا شده به لیست موقت
                signedInputs.add(signedInput)
             /*   val scriptPubKey = ScriptBuilder.createP2WPKHOutputScript(ecKeys)
                val utxoValue = Coin.valueOf(utxo.value)
                val signature = tx.calculateWitnessSignature(i, ecKeys, scriptPubKey, utxoValue, Transaction.SigHash.ALL, false)

                // استفاده از متد static factory برای ایجاد witness
                val witness = TransactionWitness.redeemP2WPKH(signature, ecKeys)

                // جایگزینی input با input جدید که witness دارد
                val oldInput = tx.getInput(i.toLong())
                val newInput = oldInput.withWitness(witness)
                tx.clearInputs() // اگر این متد وجود ندارد، باید روش دیگری پیدا کنیم

                // اضافه کردن مجدد inputs
                for (j in 0 until i) {
                    tx.addInput(tx.getInput(j.toLong()))
                }
                tx.addInput(newInput)
                for (j in i + 1 until tx.inputs.size) {
                    tx.addInput(tx.getInput(j.toLong()))
                }*/
            }

            // ۷. پاک کردن تمام ورودی‌های بدون امضای قدیمی از تراکنش
            tx.clearInputs()

// ۸. اضافه کردن تمام ورودی‌های امضا شده جدید به تراکنش
            signedInputs.forEach { signedInput ->
                tx.addInput(signedInput)
            }
            // 5. برادکست تراکنش



            val serializedBytes = tx.serialize()
            val txHex = serializedBytes.joinToString("") { "%02x".format(it) }

            println("========================= DEBUG TRANSACTION =========================")
            println("TRANSACTION OBJECT: $tx")
            println("RAW HEX TO BROADCAST: $txHex")
            println("=====================================================================")

            val requestBody = txHex.toRequestBody("text/plain".toMediaType())
            val response = api.broadcastTransaction(requestBody) // تابعی که تراکنش رو ارسال کنه

            return@withContext if (response.isSuccessful && response.body() != null) {
                ResultResponse.Success(response.body()!!)
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

    override suspend fun getFeeOptions(fromAddress: String?, toAddress: String?, asset: Asset?): ResultResponse<List<FeeData>> {
        return try {
            // ۱. ساخت سرویس API
            val api = retrofitBuilder.baseUrl(network.explorers.first()).build()
                .create(BlockcypherApiService::class.java)

            // ۲. فراخوانی endpoint برای گرفتن تمام سطوح کارمزد
            val response = api.getRecommendedFees()

            if (response.isSuccessful && response.body() != null) {
                val feeInfo = response.body()!!

                // ۳. تابع کمکی داخلی برای تبدیل sats/kb به sats/byte
                fun kbToByteRate(feePerKb: Long): Long {
                    return (feePerKb / 1024L).coerceAtLeast(1L)
                }

                // ۴. محاسبه کارمزد کل برای یک تراکنش استاندارد (۱ ورودی، ۲ خروجی)
                // ما از منطق estimateTxFee خود شما استفاده می‌کنیم
                fun calculateTotalFee(feeRateInSatsPerByte: Long): BigInteger {
                    val inputs = 1 // فرض برای یک ورودی
                    val outputs = 2 // فرض برای دو خروجی (گیرنده و باقیمانده)
                    val txSize = inputs * 148 + outputs * 34 + 10
                    return BigInteger.valueOf(txSize * feeRateInSatsPerByte)
                }

                // ۵. ساخت گزینه‌های کارمزد
                val options = listOf(

                    FeeData(
                        level = "کند",
                        feeInSmallestUnit = calculateTotalFee(kbToByteRate(feeInfo.minimumFee.toLong())),
                        estimatedTime = "~ 30 دقیقه",
                        feeRateInSatsPerByte = kbToByteRate(feeInfo.minimumFee.toLong()) // <-- پر کردن فیلد جدید
                    ),
                    FeeData(
                        level = "عادی",
                        feeInSmallestUnit = calculateTotalFee(kbToByteRate(feeInfo.economyFee.toLong())),
                        estimatedTime = "~ 10 دقیقه",
                        feeRateInSatsPerByte = kbToByteRate(feeInfo.economyFee.toLong())
                    ),
                    FeeData(
                        level = "سریع",
                        feeInSmallestUnit = calculateTotalFee(kbToByteRate(feeInfo.fastestFee.toLong())),
                        estimatedTime = "~ 2 دقیقه",
                        feeRateInSatsPerByte = kbToByteRate(feeInfo.fastestFee.toLong())
                    )
                )
                ResultResponse.Success(options)
            } else {
                ResultResponse.Error(Exception("API Error for fee options: ${response.code()}"))
            }
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override fun getWeb3jInstance(): Web3j {
        throw UnsupportedOperationException("Web3j is not available for BitcoinDataSource")
    }


    override suspend fun getBalanceEVM(address: String): ResultResponse<List<Asset>> {
        TODO("Not yet implemented")
    }


    // توابع کمکی که باید در یک فایل Utils قرار گیرند
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun estimateTxFeeBasedOnRate(inputs: Int, outputs: Int, feeRate: Long): Long {
        val txSize = inputs * 148 + outputs * 34 + 10
        return txSize * feeRate
    }
}