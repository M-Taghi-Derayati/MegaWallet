package com.mtd.data.datasource


import com.mtd.core.model.NetworkName.BSCTESTNET
import com.mtd.core.model.NetworkName.SEPOLIA
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.AssetRegistry
import com.mtd.data.datasource.IChainDataSource.FeeData
import com.mtd.data.repository.TransactionParams
import com.mtd.data.service.BSCscanApiService
import com.mtd.data.service.BlockscoutApiService
import com.mtd.domain.model.Asset
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import retrofit2.Retrofit
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.format.DateTimeFormatter

class EvmDataSource(
    private val network: BlockchainNetwork,
    private val web3j: Web3j,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry
) : IChainDataSource {

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> {

        for (explorer in network.explorers) {
            return try {
                when (network.name) {
                    SEPOLIA -> {
                        val api = retrofitBuilder.baseUrl(explorer).build()
                            .create(BlockscoutApiService::class.java)
                        val response = api.getTransactions(address)
                        if (response.isSuccessful && response.body() != null) {

                            val records = response.body()!!.items.map { dto ->
                                val fee = (dto.gasUsed?.toBigIntegerOrNull() ?: BigInteger.ZERO) *
                                        (dto.gasPrice?.toBigIntegerOrNull() ?: BigInteger.ZERO)

                                // تبدیل فرمت تاریخ ISO 8601 به Unix Timestamp
                                val timestamp =
                                    Instant.from(DateTimeFormatter.ISO_INSTANT.parse(dto.timestamp)).epochSecond

                                EvmTransaction(
                                    hash = dto.hash,
                                    fromAddress = dto.from.hash,
                                    toAddress = dto.to?.hash
                                        ?: "Contract Creation", // مدیریت حالت ساخت قرارداد
                                    amount = dto.value,
                                    fee = fee,
                                    timestamp = timestamp,
                                    isOutgoing = dto.from.hash.equals(address, ignoreCase = true),
                                    status = if (dto.status == "ok") TransactionStatus.CONFIRMED else TransactionStatus.FAILED
                                )
                            }
                            ResultResponse.Success(records)
                        } else {
                            ResultResponse.Error(Exception("Failed to fetch from ${explorer}:"))
                        }

                    }

                    BSCTESTNET -> {
                        val api = retrofitBuilder.baseUrl(explorer).build()
                            .create(BSCscanApiService::class.java)
                        val response = api.getTransactions(address = address)
                        if (response.isSuccessful && response.body()?.status == "1") {
                            val records = response.body()!!.result.map { dto ->
                                EvmTransaction(
                                    hash = dto.hash,
                                    fromAddress = dto.from,
                                    toAddress = dto.to,
                                    amount = dto.value,
                                    fee = (dto.gasUsed.toBigIntegerOrNull()
                                        ?: BigInteger.ZERO) * (dto.gasPrice.toBigIntegerOrNull()
                                        ?: BigInteger.ZERO),
                                    timestamp = dto.timeStamp.toLongOrNull() ?: 0L,
                                    isOutgoing = dto.from.equals(address, ignoreCase = true),
                                    status = if (dto.isError == "0") TransactionStatus.CONFIRMED else TransactionStatus.FAILED
                                )
                            }
                            return ResultResponse.Success(records)
                        } else {
                            ResultResponse.Error(Exception("Failed to fetch from ${explorer}:"))
                        }
                    }

                    else -> ResultResponse.Error(Exception("Failed to fetch from ${explorer}:"))
                }
            } catch (e: Exception) {
                ResultResponse.Error(Exception("Failed to fetch from ${explorer}: ${e.message}"))
            }
        }

        return ResultResponse.Error(Exception("Failed to fetch from"))
    }

    private suspend fun getNonce(address: String): BigInteger {
        val transactionCount =
            web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).sendAsync()
                .await()
        return transactionCount.transactionCount
    }


    private suspend fun sendRawTransaction(signedTxHex: String): String {
        val transactionReceipt = web3j.ethSendRawTransaction(signedTxHex).sendAsync().await()
        if (transactionReceipt.hasError()) {
            throw RuntimeException("Error sending transaction: ${transactionReceipt.error.message}")
        }
        return transactionReceipt.transactionHash
    }

    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> {
        // اطمینان از اینکه پارامترها از نوع صحیح هستند
        if (params !is TransactionParams.Evm) {
            return ResultResponse.Error(IllegalArgumentException("Invalid params type for EvmDataSource"))
        }

        return try {
            val credentials = Credentials.create(privateKeyHex)

            val nonce = getNonce(credentials.address)


            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                params.gasPrice,
                params.gasLimit,
                params.to,
                params.amount,
                params.data ?: ""
            )

            val signedTx =
                TransactionEncoder.signMessage(rawTransaction, network.chainId!!, credentials)
            val txHash = sendRawTransaction(Numeric.toHexString(signedTx))

            ResultResponse.Success(txHash)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun getBalanceEVM(address: String): ResultResponse<List<Asset>> {

        return withContext(Dispatchers.IO) {
            try {
                val supportedAssets = assetRegistry.getAssetsForNetwork(network.id)
                if (supportedAssets.isEmpty()) {
                    return@withContext ResultResponse.Success(emptyList())
                }

                val assetDeferreds = supportedAssets.map { assetConfig ->
                    async {
                        val balance = if (assetConfig.contractAddress == null) {
                            // الف. خواندن موجودی توکن اصلی (Native)
                            web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).sendAsync().await().balance
                        } else {
                            // ب. خواندن موجودی توکن ERC20 (بخش اصلاح شده)

                            // ۱. تعریف تابع با امضای کامل و صحیح (فقط یک بار)
                            val function = Function(
                                "balanceOf",
                                listOf(Address(address)),
                                emptyList() // ما به تعریف خروجی در اینجا نیازی نداریم
                            )

                            val encodedFunction = FunctionEncoder.encode(function)
                            val response = web3j.ethCall(
                                Transaction.createEthCallTransaction(address, assetConfig.contractAddress, encodedFunction),
                                DefaultBlockParameterName.LATEST
                            ).sendAsync().await()

                            if (response.hasError() || response.value.isNullOrBlank() || response.value == "0x") {
                                BigInteger.ZERO
                            } else {
                                // ۲. استفاده از همان آبجکت function برای دیکود کردن
                                try {
                                    BigInteger(response.value.substring(2), 16)
                                } catch (e: NumberFormatException) {
                                    BigInteger.ZERO
                                }
                            }
                        }

                        Asset(
                            name = assetConfig.name,
                            symbol = assetConfig.symbol,
                            decimals = assetConfig.decimals,
                            contractAddress = assetConfig.contractAddress,
                            balance = balance
                        )
                    }
                }

                val assets = assetDeferreds.awaitAll()
                ResultResponse.Success(assets)

            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
        }
    }


    override suspend fun getBalance(address: String): ResultResponse<BigInteger> {
        TODO("Not yet implemented")
    }

    private suspend fun getGasPrice(): BigInteger {
        val gasPrice = web3j.ethGasPrice().sendAsync().await()
        return gasPrice.gasPrice
    }


    private suspend fun estimateGasLimit(from: String, to: String, data: String? = null): BigInteger {
        val transaction = Transaction.createEthCallTransaction(from, to, data ?: "")
        return try {
            val estimate = web3j.ethEstimateGas(transaction).sendAsync().await()
            if (estimate.hasError()) {
                // اگر تخمین با خطا مواجه شد، یک مقدار پیش‌فرض بالا برمی‌گردانیم
                BigInteger("150000")
            } else {
                // کمی به مقدار تخمین زده شده اضافه می‌کنیم تا مطمئن باشیم کافی است
                (estimate.amountUsed.toBigDecimal() * BigDecimal("1.2")).toBigInteger()
            }
        } catch (e: Exception) {
            BigInteger("150000")
        }
    }

  /*  override suspend fun getFeeOptions(): ResultResponse<List<FeeData>> {
        return try {
            // ۱. دریافت قیمت پایه گاز (Gas Price) از شبکه
            val baseGasPrice = getGasPrice()

            // ۲. تعریف ضرایب برای سطوح مختلف کارمزد
            // این ضرایب رو می‌تونیم در آینده از یک منبع خارجی هم بگیریم
            val normalMultiplier = BigDecimal("1.0")
            val fastMultiplier = BigDecimal("1.2")
            val urgentMultiplier = BigDecimal("1.5")

            // ۳. تعریف Gas Limit استاندارد
            // ۲۱۰۰۰ برای انتقال ساده توکن اصلی (مثل ETH, BNB, MATIC).
            // برای توکن‌های ERC20، این مقدار بیشتره (معمولاً بین ۵۰۰۰۰ تا ۱۰۰۰۰۰).
            // TODO: این بخش باید هوشمندتر بشه و بر اساس نوع دارایی، Gas Limit رو تخمین بزنه (با web3j.ethEstimateGas).
            // فعلاً برای یک انتقال ساده، از ۲۱۰۰۰ استفاده می‌کنیم.
            val gasLimit = if (asset.contractAddress == null) {
                // انتقال توکن اصلی
                BigInteger("21000")
            } else {
                // انتقال توکن ERC20 - نیاز به data داریم
                val function = Function("transfer", listOf(Address(fromAddress), Uint256(BigInteger.ONE))), ...)
                val data = FunctionEncoder.encode(function)
                estimateGasLimit(fromAddress, asset.contractAddress, data)
            }

            // ۴. ساخت گزینه‌های کارمزد
            val options = listOf(
                FeeData(
                    level = "Normal 🐢",
                    gasPrice = (baseGasPrice.toBigDecimal() * normalMultiplier).toBigInteger(),
                    gasLimit = gasLimit,
                    feeInSmallestUnit = (baseGasPrice.toBigDecimal() * normalMultiplier).toBigInteger() * gasLimit,
                    estimatedTime = "~ 30 ثانیه"
                ),
                FeeData(
                    level = "Fast 🚀",
                    gasPrice = (baseGasPrice.toBigDecimal() * fastMultiplier).toBigInteger(),
                    gasLimit = gasLimit,
                    feeInSmallestUnit = (baseGasPrice.toBigDecimal() * fastMultiplier).toBigInteger() * gasLimit,
                    estimatedTime = "~ 15 ثانیه"
                ),
                FeeData(
                    level = "Urgent 🔥",
                    gasPrice = (baseGasPrice.toBigDecimal() * urgentMultiplier).toBigInteger(),
                    gasLimit = gasLimit,
                    feeInSmallestUnit = (baseGasPrice.toBigDecimal() * urgentMultiplier).toBigInteger() * gasLimit,
                    estimatedTime = "~ 5 ثانیه"
                )
            )
            ResultResponse.Success(options.reversed()) // Urgent رو اول نمایش میدیم

        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }*/

    override suspend fun getFeeOptions(
        fromAddress: String?, toAddress: String?, asset: Asset?
    ): ResultResponse<List<FeeData>> {
        return try {
            // ۱. دریافت gas price (برای سازگاری با شبکه‌های غیر EIP-1559)
            val baseGasPrice = getGasPrice().coerceAtLeast(BigInteger.ONE)



            // ۲. اگر شبکه EIP-1559 هست، ترجیحاً maxFeePerGas و priorityFee رو هم بگیریم
            val feeHistory = try {
                web3j.ethFeeHistory(
                    5,
                    DefaultBlockParameterName.LATEST,
                    listOf((25).toDouble(),( 50).toDouble(), (75).toDouble())
                ).send()
            } catch (_: Exception) { null }

            val priorityFee: BigInteger = try {
                feeHistory?.result?.reward?.let { rewardList ->
                    val rewards = rewardList.mapNotNull {  it.getOrNull(1)?.toLong()?:1 }
                    if (rewards.isEmpty()==false) rewards.average().toBigDecimal().toBigInteger()
                    else BigInteger("2000000000") // fallback: 2 gwei
                } ?: BigInteger("2000000000")
            } catch (_: Exception) {
                BigInteger("2000000000")
            }

           // val priorityFee = feeHistory?.result?.reward?.firstOrNull()?.getOrNull(1)?.toLong() ?: ("2000000000").toLong() // 2 gwei
            val maxFeePerGas = baseGasPrice.add(priorityFee)

            // ۳. Gas Limit تخمینی
            val gasLimit: BigInteger = if (asset?.contractAddress == null) {
                // انتقال توکن اصلی
                BigInteger.valueOf(21_000L)
            } else {
                // انتقال ERC20 → باید data بسازیم
                val function = Function(
                    "transfer",
                    listOf(Address(toAddress), Uint256(BigInteger.ONE)),
                    emptyList()
                )
                val data = FunctionEncoder.encode(function)
                estimateGasLimit(fromAddress?:"", asset.contractAddress!!, data)
            }

            // ۴. ضرایب (بجای ضرب مستقیم در gasPrice → از priorityFee کمک می‌گیریم)
            val normalGasPrice = maxFeePerGas
            val fastGasPrice = maxFeePerGas.add(priorityFee.divide((2).toBigInteger()))
            val urgentGasPrice = maxFeePerGas.add(priorityFee.multiply((2).toBigInteger()))

            // ۵. ساخت FeeData
            val options = listOf(
                FeeData(
                    level = "عادی 🐢",
                    gasPrice = normalGasPrice,
                    gasLimit = gasLimit,
                    feeInSmallestUnit = normalGasPrice * gasLimit,
                    feeInEth = (fastGasPrice * gasLimit).toEth(),
                    feeInUsd = null,
                    estimatedTime = "~ 30s - 60s"
                ),
                FeeData(
                    level = "سریع 🚀",
                    gasPrice = fastGasPrice,
                    gasLimit = gasLimit,
                    feeInSmallestUnit = fastGasPrice * gasLimit,
                    feeInEth = (fastGasPrice * gasLimit).toEth(),
                    feeInUsd = null,
                    estimatedTime = "~ 15s - 30s"
                ),
                FeeData(
                    level = "درلحظه 🔥",
                    gasPrice = urgentGasPrice,
                    gasLimit = gasLimit,
                    feeInSmallestUnit = urgentGasPrice * gasLimit,
                    feeInEth = (fastGasPrice * gasLimit).toEth(),
                    feeInUsd = null,
                    estimatedTime = "< 10s"
                )
            )

            ResultResponse.Success(options)

        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override fun getWeb3jInstance(): Web3j {
        return this.web3j
    }

    fun BigInteger.toEth(): BigDecimal {
        return this.toBigDecimal().divide(BigDecimal.TEN.pow(18))
    }

}