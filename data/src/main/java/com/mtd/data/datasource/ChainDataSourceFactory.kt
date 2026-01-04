package com.mtd.data.datasource

import com.mtd.core.model.NetworkName.BITCOINTESTNET
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import okhttp3.OkHttpClient
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChainDataSourceFactory @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry,
    private val okHttpClient: OkHttpClient
) {
    private val dataSourceCache = mutableMapOf<Long, IChainDataSource>()

    fun create(chainId: Long): IChainDataSource {
        if (dataSourceCache.containsKey(chainId)) {
            return dataSourceCache[chainId]!!
        }

        val network = blockchainRegistry.getNetworkByChainId(chainId)
            ?: throw IllegalArgumentException("Network not found for id: $chainId")

        val newDataSource = when (network.networkType) {
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
            else -> throw IllegalArgumentException("Unsupported network type: ${network.networkType}")
        }

        dataSourceCache[chainId] = newDataSource
        return newDataSource
    }
}