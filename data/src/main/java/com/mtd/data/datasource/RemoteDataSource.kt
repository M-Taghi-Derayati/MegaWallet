package com.mtd.data.datasource

import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.service.BSCscanApiService
import com.mtd.domain.model.EvmTransaction
import com.mtd.domain.model.IUserPreferencesRepository
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import retrofit2.Retrofit
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteDataSource @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val userPreferencesRepository: IUserPreferencesRepository,
    private val okHttpClient: OkHttpClient,
    private val retrofitBuilder: Retrofit.Builder,
) {

    // منبع ۳: لیستی از نودهای عمومی قابل اعتماد (مثل Infura) به عنوان Fallback نهایی
    private val publicFallbackRpcs = mapOf(
        1L to "https://mainnet.infura.io/v3/YOUR_INFURA_KEY",
        11155111L to "https://sepolia.infura.io/v3/YOUR_INFURA_KEY"
    )
    // نکته: این API Key فقط برای Etherscan و اکسپلوررهای مشابه آن کار می‌کند.
    private val etherscanApiKey = "YOUR_ETHERSCAN_API_KEY"

    internal suspend fun getBestRpcUrl(chainId: Long): String {
        // --- ۱. ساخت لیست کامل کاندیداها ---
        val candidateRpcs = buildCandidateRpcList(chainId)

        if (candidateRpcs.isEmpty()) {
            throw IllegalStateException("No RPC URL found for chainId: $chainId")
        }

        // --- ۲. اجرای Ping Race روی لیست نهایی ---
        val timings = mutableMapOf<String, Long>()
        candidateRpcs.forEach { url ->
            try {
                // ... (منطق Ping Race بدون تغییر)
            } catch (e: Exception) {
                timings[url] = Long.MAX_VALUE
            }
        }

        val fastestUrl = timings.minByOrNull { it.value }?.key

        return fastestUrl ?: throw IllegalStateException("All RPCs failed for chainId: $chainId. Please check your connection or RPC settings.")
    }

    internal suspend fun buildCandidateRpcList(chainId: Long): List<String> {
        // از تنظیمات کاربر، لیست RPC ها و ترتیب آنها را می‌خوانیم
        val userRpcList = userPreferencesRepository.getRpcListForChain(chainId)

        val defaultNetwork = blockchainRegistry.getNetworkByChainId(chainId)
        val defaultRpcs = defaultNetwork?.defaultRpcUrls ?: emptyList()
        val publicFallback = publicFallbackRpcs[chainId]

        if (userRpcList.isNotEmpty()) {
            // **سناریوی پیشرفته: کاربر تنظیمات را شخصی‌سازی کرده است**
            // لیست را بر اساس اولویت کاربر مرتب می‌کنیم و فقط URL ها را برمی‌گردانیم
            return userRpcList.sortedBy { it.priority }.map { it.url }
        } else {
            // **سناریوی پیش‌فرض: کاربر هیچ تغییری نداده است**
            // اولویت با RPC های فایل JSON است، سپس Fallback عمومی
            val combinedList = mutableListOf<String>()
            combinedList.addAll(defaultRpcs)
            publicFallback?.let { combinedList.add(it) }
            return combinedList
        }
    }

    private suspend fun getWeb3jInstance(chainId: Long): Web3j {
        val rpcUrl = getBestRpcUrl(chainId)
        val httpServices = HttpService(rpcUrl, okHttpClient, false)
        return Web3j.build(httpServices)
    }

    suspend fun getNonce(chainId: Long, address: String): BigInteger = withContext(Dispatchers.IO) {
        val web3j = getWeb3jInstance(chainId)
        val transactionCount =
            web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send()
        return@withContext transactionCount.transactionCount
    }

    suspend fun getGasPrice(chainId: Long): BigInteger = withContext(Dispatchers.IO) {
        val web3j = getWeb3jInstance(chainId)
        val gasPrice = web3j.ethGasPrice().send()
        return@withContext gasPrice.gasPrice
    }

    suspend fun sendRawTransaction(chainId: Long, signedTransactionHex: String): String =
        withContext(Dispatchers.IO) {
            val web3j = getWeb3jInstance(chainId)
            val transactionReceipt = web3j.ethSendRawTransaction(signedTransactionHex).send()
            if (transactionReceipt.hasError()) {
                throw RuntimeException("Error sending transaction: ${transactionReceipt.error.message}")
            }
            return@withContext transactionReceipt.transactionHash
        }


    suspend fun getNativeTokenBalance(chainId: Long, address: String): BigInteger =
        withContext(Dispatchers.IO) {
            val web3j = getWeb3jInstance(chainId)
            val balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()
            return@withContext balance.balance
        }

    suspend fun getErc20TokenBalance(chainId: Long, contractAddress: String, userAddress: String): BigInteger =
        withContext(Dispatchers.IO) {
            // این بخش کمی پیچیده‌تر است چون نیاز به فراخوانی یک تابع در قرارداد هوشمند دارد.
            val web3j = getWeb3jInstance(chainId)

            val function = FunctionEncoder.encode(
                Function(
                    "balanceOf",
                    listOf(Address(userAddress)),
                    listOf(object : TypeReference<Uint256>() {})
                )
            )

            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(userAddress, contractAddress, function),
                DefaultBlockParameterName.LATEST
            ).send()

            if (response.hasError() || response.value.isNullOrBlank() || response.value == "0x") {
                return@withContext BigInteger.ZERO
            }

            val result = FunctionReturnDecoder.decode(
                response.value,
                Function(
                    "balanceOf",
                    emptyList(),
                    listOf(object : TypeReference<Uint256>() {})
                ).outputParameters
            )

            return@withContext if (result.isNotEmpty()) result[0].value as BigInteger else BigInteger.ZERO
        }


    suspend fun getEvmTransactionHistory(chainId: Long, address: String): List<TransactionRecord> {
        val network = blockchainRegistry.getNetworkByChainId(chainId)
            ?: throw IllegalArgumentException("Network not found for id: $chainId")

        // لیست URL های API اکسپلوررها را از کانفیگ شبکه می‌گیریم.
        val candidateApiUrls = network.explorers

        if (candidateApiUrls.isEmpty()) {
            return emptyList() // اگر هیچ اکسپلورری تعریف نشده باشد، لیست خالی برمی‌گردانیم.
        }

        // به ترتیب، اولین اکسپلورری که پاسخ صحیح بدهد را امتحان می‌کنیم.
        for (apiUrl in candidateApiUrls) {
            try {
                // برای هر URL، یک نمونه جدید از سرویس با Base URL صحیح می‌سازیم.
                val apiService = retrofitBuilder
                    .baseUrl(apiUrl)
                    .build()
                    .create(BSCscanApiService::class.java)

                // فرض می‌کنیم همه از یک کلید API استفاده می‌کنند.
                // می‌توان این منطق را پیچیده‌تر کرد تا برای هر URL کلید متفاوتی در نظر گرفت.
                val response = apiService.getTransactions(address = address, apiKey = etherscanApiKey)

                if (response.isSuccessful && response.body()?.status == "1") {
                    // موفقیت! پاسخ را پردازش کرده و نتیجه را برمی‌گردانیم.
                    return response.body()!!.result.map { dto ->
                        val fee = (dto.gasUsed.toBigIntegerOrNull() ?: BigInteger.ZERO) *
                                (dto.gasPrice.toBigIntegerOrNull() ?: BigInteger.ZERO)
                        EvmTransaction(
                            hash = dto.hash,
                            fromAddress = dto.from,
                            toAddress = dto.to,
                            amount = dto.value,
                            fee = fee,
                            timestamp = dto.timeStamp.toLongOrNull() ?: 0L,
                            isOutgoing = dto.from.equals(address, ignoreCase = true),
                            status = if (dto.isError == "0") TransactionStatus.CONFIRMED else TransactionStatus.FAILED
                        )
                    }
                }
            } catch (e: Exception) {
                // اگر خطای شبکه یا اتصال رخ داد، این اکسپلورر را نادیده گرفته و به سراغ بعدی می‌رویم.
                println("Failed to fetch from $apiUrl: ${e.message}")
            }
        }

        // اگر تمام اکسپلوررها با خطا مواجه شدند، لیست خالی برمی‌گردانیم.
        return emptyList()
    }


}