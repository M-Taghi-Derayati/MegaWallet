package com.mtd.core.registry

import android.content.Context
import com.mtd.core.model.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.core.utils.loadNetworkConfigs
import javax.inject.Inject


class BlockchainRegistry @Inject constructor() {

    private val networks = mutableMapOf<NetworkType, BlockchainNetwork>()

    fun registerNetwork(network: BlockchainNetwork) {
        networks[network.networkType] = network
    }

    fun getNetworkByType(type: NetworkType): BlockchainNetwork? {
        return networks[type]
    }

    fun getAllNetworks(): List<BlockchainNetwork> {
        return networks.values.toList()
    }

    fun loadNetworksFromAssets(context: Context, fileName: String = "networks.json") {
        val configs = loadNetworkConfigs(context, fileName)
        configs.forEach { config ->
            val networkType = try {
                NetworkType.valueOf(config.networkType)  // تبدیل رشته به enum
            } catch (e: Exception) {
                null
            }


            if (networkType != null) {
                // ساخت نسخه جدید با networkType enum
                val networkConfigWithEnum = config.copy(
                    networkType = networkType.toString()
                )
                val network = GenericEvmNetwork(networkConfigWithEnum)
                registerNetwork(network)
            }
        }
    }
}