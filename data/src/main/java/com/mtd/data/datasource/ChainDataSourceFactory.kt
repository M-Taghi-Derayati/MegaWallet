package com.mtd.data.datasource


import com.mtd.core.model.NetworkName.BITCOINTESTNET
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import okhttp3.OkHttpClient
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChainDataSourceFactory @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val retrofitBuilder: Retrofit.Builder,
    private val okHttpClient: OkHttpClient
) {
    // یک کش ساده برای جلوگیری از ساخت مکرر DataSource ها
    private val dataSourceCache = mutableMapOf<Long, IChainDataSource>()

    fun create(chainId: Long): IChainDataSource {
        // اگر قبلاً ساخته شده، از کش برگردان
        if (dataSourceCache.containsKey(chainId)) {
            return dataSourceCache[chainId]!!
        }

        val network = blockchainRegistry.getNetworkByChainId(chainId)
            ?: throw IllegalArgumentException("Network not found for id: $chainId")

        val newDataSource = when (network.networkType) {
            NetworkType.EVM -> {
                // ۱. انتخاب هوشمند RPC URL برای این شبکه
                // TODO: منطق کامل سه‌لایه انتخاب RPC باید در یک کلاس جداگانه پیاده‌سازی شود
                // و در اینجا فراخوانی شود. فعلاً از اولین RPC استفاده می‌کنیم.
                val rpcUrl = network.defaultRpcUrls.firstOrNull()
                    ?: throw IllegalStateException("No RPC URL configured for network: $chainId")

                // ۲. ساخت یک نمونه Web3j جدید و مشخص برای این شبکه
                val httpService = HttpService(rpcUrl, okHttpClient, false)
                val web3j = Web3j.build(httpService)

                // ۳. ساخت EvmDataSource با تمام وابستگی‌هایش
                EvmDataSource(network , web3j,retrofitBuilder)
            }
            NetworkType.BITCOIN -> {
                val networkParams= if (network.name==BITCOINTESTNET){
                    TestNet3Params.get()
                }else{
                    MainNetParams.get()
                }
                BitcoinDataSource(network, retrofitBuilder,networkParams)
            }
            // ... (سایر انواع شبکه)
            else -> throw IllegalArgumentException("Unsupported network type: ${network.networkType}")
        }

        // DataSource جدید را در کش ذخیره کن
        dataSourceCache[chainId] = newDataSource
        return newDataSource
    }
}