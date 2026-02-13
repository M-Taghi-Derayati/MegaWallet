package com.mtd.data.datasource

import com.mtd.core.model.NetworkName.BITCOINTESTNET
import com.mtd.core.model.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import okhttp3.OkHttpClient
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strategy-style provider برای ساخت [IChainDataSource] براساس نوع شبکه.
 * این abstraction کمک می‌کند from whenهای بزرگ وابسته به [NetworkType] فاصله بگیریم.
 */
interface ChainDataSourceProvider {
    fun supports(network: BlockchainNetwork): Boolean
    fun create(network: BlockchainNetwork): IChainDataSource
}

@Singleton
class ChainDataSourceFactory @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry,
    private val okHttpClient: OkHttpClient
) {
    private val dataSourceCache = mutableMapOf<Long, IChainDataSource>()

    /**
     * مجموعهٔ providerهای موجود برای ساخت datasourceها.
     * فعلاً به‌صورت داخلی مقداردهی می‌شود تا سازگاری با تست‌ها و DI فعلی حفظ شود.
     */
    private val providers: List<ChainDataSourceProvider> by lazy {
        listOf(
            object : ChainDataSourceProvider {
                override fun supports(network: BlockchainNetwork): Boolean {
                    return network.networkType == NetworkType.EVM
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    // تغییر مهم: پاس دادن okHttpClient به جای ساختن Web3j در اینجا
                    return EvmDataSource(network, retrofitBuilder, assetRegistry, okHttpClient)
                }
            },
            object : ChainDataSourceProvider {
                override fun supports(network: BlockchainNetwork): Boolean {
                    return network.networkType == NetworkType.BITCOIN
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    val networkParams = if (network.name == BITCOINTESTNET) {
                        TestNet3Params.get()
                    } else {
                        MainNetParams.get()
                    }
                    return BitcoinDataSource(network, retrofitBuilder, networkParams)
                }
            },
            object : ChainDataSourceProvider {
                override fun supports(network: BlockchainNetwork): Boolean {
                    return network.networkType == NetworkType.TVM
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    return TronDataSource(network,retrofitBuilder,assetRegistry, okHttpClient)
                }
            }
        )
    }

    fun create(chainId: Long): IChainDataSource {

        dataSourceCache[chainId]?.let { return it }

        val network = blockchainRegistry.getNetworkByChainId(chainId)
            ?: throw IllegalArgumentException("Network not found for id: $chainId")


       /* val newDataSource = when (network.networkType) {
            NetworkType.EVM -> {
                // تغییر مهم: پاس دادن okHttpClient به جای ساختن Web3j در اینجا
                EvmDataSource(network, retrofitBuilder, assetRegistry, okHttpClient)
            }
            NetworkType.BITCOIN -> {
                val networkParams= if (network.name==BITCOINTESTNET){
                    TestNet3Params.get()
                }else{
                    MainNetParams.get()
                }
                BitcoinDataSource(network, retrofitBuilder,networkParams)
            }
            NetworkType.TVM -> {
                TronDataSource(network, retrofitBuilder, okHttpClient, assetRegistry)
            }
            else -> throw IllegalArgumentException("Unsupported network type: ${network.networkType}")
        }*/

        val provider = providers.firstOrNull { it.supports(network) }
            ?: throw IllegalArgumentException("Unsupported network type: ${network.networkType}")

        val newDataSource = provider.create(network)


        dataSourceCache[chainId] = newDataSource
        return newDataSource
    }
}