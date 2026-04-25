package com.mtd.data.datasource

import com.mtd.domain.model.core.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.UtxoNetworkParametersResolver
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import okhttp3.OkHttpClient
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
    private val dataSourceCache = mutableMapOf<String, IChainDataSource>()

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
                    return network.networkType == NetworkType.BITCOIN ||
                        network.networkType == NetworkType.UTXO
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    val networkParams = UtxoNetworkParametersResolver.resolve(network.name)
                    return BitcoinDataSource(network, retrofitBuilder, networkParams,okHttpClient)
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
        val network = blockchainRegistry.getNetworkByChainId(chainId)
            ?: throw IllegalArgumentException("Network not found for chainId: $chainId")
        return create(network)
    }

    fun create(network: BlockchainNetwork): IChainDataSource {
        dataSourceCache[network.id]?.let { return it }

        val provider = providers.firstOrNull { it.supports(network) }
            ?: throw IllegalArgumentException("Unsupported network type: ${network.networkType}")

        val newDataSource = provider.create(network)
        dataSourceCache[network.id] = newDataSource
        return newDataSource
    }
}
