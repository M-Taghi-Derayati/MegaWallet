package com.mtd.data.datasource


import com.mtd.core.model.NetworkName.*
import com.mtd.core.network.BlockchainNetwork
import com.mtd.data.repository.TransactionParams
import com.mtd.data.service.BSCscanApiService
import com.mtd.data.service.BlockscoutApiService
import com.mtd.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
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
import java.math.BigInteger
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.to

class EvmDataSource(
    private val network: BlockchainNetwork,
    private val web3j: Web3j,
    private val retrofitBuilder: Retrofit.Builder
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

    private suspend fun getGasPrice(): BigInteger {
        val gasPrice = web3j.ethGasPrice().sendAsync().await()
        return gasPrice.gasPrice
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
            val gasPrice = getGasPrice()

            // TODO: Gas Limit باید به صورت داینامیک با eth_estimateGas تخمین زده شود. فعلاً هاردکد می‌کنیم.
            val gasLimit =
                if (params.data != null) BigInteger.valueOf(100_000) else BigInteger.valueOf(21_000)

            val rawTransaction = RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
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
                // TODO: لیست توکن‌ها باید از کانفیگ شبکه خوانده شود.
                // در آینده، NetworkConfig می‌تواند لیستی از توکن‌های معروف آن شبکه را هم داشته باشد.
                // فعلاً برای تست، از لیست هاردکد شده استفاده می‌کنیم.
                val tokenList = getHardcodedTokenListFor(network.chainId)

                // ما تمام درخواست‌های موجودی را به صورت همزمان (concurrently) ارسال می‌کنیم تا سرعت بالا برود.
                val assetDeferreds = tokenList.map { tokenInfo ->
                    async { // هر درخواست در یک coroutine جداگانه اجرا می‌شود
                        val balance = if (tokenInfo.contractAddress == null) {
                            // ۱. خواندن موجودی توکن اصلی (Native)
                            val balanceResult =
                                web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                                    .send()
                            balanceResult.balance
                        } else {
                            // ۲. خواندن موجودی توکن ERC20
                            val function = Function(
                                "balanceOf",
                                listOf(Address(address)),
                                listOf(object : TypeReference<Uint256>() {})
                            )
                            val encodedFunction = FunctionEncoder.encode(function)
                            val response = web3j.ethCall(
                                Transaction.createEthCallTransaction(
                                    address,
                                    tokenInfo.contractAddress,
                                    encodedFunction
                                ),
                                DefaultBlockParameterName.LATEST
                            ).send()

                            if (response.hasError() || response.value.isNullOrBlank() || response.value == "0x") {
                                BigInteger.ZERO
                            } else {
                                val result = FunctionReturnDecoder.decode(
                                    response.value,
                                    function.outputParameters
                                )
                                if (result.isNotEmpty()) result[0].value as BigInteger else BigInteger.ZERO
                            }
                        }
                        tokenInfo.copy(balance = balance)
                    }
                }

                // منتظر می‌مانیم تا تمام درخواست‌ها تمام شوند و نتایج را جمع‌آوری می‌کنیم.
                val assets = assetDeferreds.awaitAll()
                ResultResponse.Success(assets)

            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
        }
    }

    // یک تابع کمکی موقت برای لیست توکن‌ها
    fun getHardcodedTokenListFor(chainId: Long?): List<Asset> {
        return when (chainId) {
            11155111L -> listOf( // Sepolia Testnet
                Asset("Sepolia ETH", network.currencySymbol, 18, null, BigInteger.ZERO),
                // آدرس قرارداد تستی برای یک توکن ERC20 در Sepolia (مثلاً یک نسخه از USDC)
                Asset(
                    "USD Coin",
                    "USDC",
                    6,
                    "0x94a9D9AC8a22534E3FaCa422de466b95853443aD",
                    BigInteger.ZERO
                )
            )

            97L -> listOf( // BSC Testnet
                Asset("Test BNB", network.currencySymbol, 18, null, BigInteger.ZERO)
                // می‌توانید آدرس یک توکن BEP20 تستی را هم اینجا اضافه کنید
            )

            else -> emptyList()
        }
    }

    override suspend fun getBalance(address: String): ResultResponse<BigInteger> {
        TODO("Not yet implemented")
    }

    override suspend fun estimateFee(): ResultResponse<BigInteger> {
        TODO("Not yet implemented")
    }

}