package com.mtd.core.registry

import android.content.Context
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.BitcoinNetwork
import com.mtd.core.network.bitcoin.UtxoNetworkParametersResolver
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.core.network.tron.TronNetwork
import com.mtd.core.utils.AddressRegexUtils
import com.mtd.core.utils.loadNetworkConfigs
import com.mtd.domain.model.core.NetworkConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import fr.acinq.bitcoin.Base58
import org.bitcoinj.base.Address
import org.web3j.crypto.WalletUtils
import javax.inject.Inject


/**
 * Strategy-style factory for creating [BlockchainNetwork] instances from [com.mtd.domain.model.core.NetworkConfig].
 * این اینترفیس کمک می‌کند از whenهای بزرگ وابسته به [com.mtd.domain.model.core.NetworkType] دور شویم
 * و منطق ساخت هر شبکه را در یک کلاس جداگانه نگه داریم.
 */
interface NetworkFactory {
    fun supports(networkType: NetworkType, config: NetworkConfig): Boolean
    fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork
}

class EvmNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.EVM
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        return GenericEvmNetwork(config)
    }
}

class BitcoinNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.BITCOIN
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        val networkName = NetworkName.valueOf(config.name.uppercase())
        val params = UtxoNetworkParametersResolver.resolve(networkName)
        return BitcoinNetwork(config, params)
    }
}

class UtxoNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.UTXO
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        val networkName = NetworkName.valueOf(config.name.uppercase())
        val params = UtxoNetworkParametersResolver.resolve(networkName)
        return BitcoinNetwork(config, params)
    }
}

class TronNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.TVM
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        return TronNetwork(config)
    }
}


class BlockchainRegistry @Inject constructor() {


    private val networks = mutableMapOf<NetworkType, MutableMap<Long, BlockchainNetwork>>()

    private val networksById = mutableMapOf<String, BlockchainNetwork>()
    private val networksByType = mutableMapOf<NetworkType, BlockchainNetwork>()
    private val networksByChainId = mutableMapOf<Long, BlockchainNetwork>()
    private val addressRegexByNetworkId = mutableMapOf<String, Regex>()
    private val addressRegexByNetworkType = mutableMapOf<NetworkType, MutableList<Regex>>()

    /**
     * مجموعهٔ factoryهای موجود برای ساخت شبکه‌ها.
     * در حال حاضر به‌صورت داخلی مقداردهی می‌شود، اما در آینده می‌تواند از DI تزریق شود.
     */
    private val networkFactories: List<NetworkFactory> = listOf(
        EvmNetworkFactory(),
        BitcoinNetworkFactory(),
        UtxoNetworkFactory(),
        TronNetworkFactory()
    )


    fun registerNetwork(network: BlockchainNetwork) {
        // Register by ID (universal)
        networksById[network.id] = network

        val chainId = network.chainId
        if (chainId != null) {
            // Register in the nested map and chainId map for Ethereum-like networks
            networks.getOrPut(network.networkType) { mutableMapOf() }[chainId] = network
            networksByChainId[chainId] = network
        }

        // Register as default for type
        if (!networksByType.containsKey(network.networkType) || !network.isTestnet) {
            networksByType[network.networkType] = network
        }
    }


    fun getNetworkByName(name: NetworkName): BlockchainNetwork? {
        return networks.values.flatMap { it.values }.find { it.name==name }
    }

    fun getNetworkById(id: String): BlockchainNetwork? {
        return networksById[id]
    }


    fun getNetworkByChainId(chainId: Long): BlockchainNetwork? {
        return networksByChainId[chainId]
    }


    fun getAllNetworks(): List<BlockchainNetwork> {
        return networksById.values.toList()
    }

    fun getNetworkByType(type: NetworkType): BlockchainNetwork? {
        return networksByType[type]
    }

    fun getDefaultNetworkByType(type: NetworkType): BlockchainNetwork? {
        return getAllNetworks().firstOrNull { it.networkType == type }
    }


    private fun clearAll() {
        networks.clear()
        networksById.clear()
        networksByType.clear()
        networksByChainId.clear()
        addressRegexByNetworkId.clear()
        addressRegexByNetworkType.clear()
    }


    fun getNetworkTypeForAddress(address: String): NetworkType? {
        val normalized = address.trim()
        if (normalized.isBlank()) return null

        NetworkType.values().forEach { type ->
            val match = addressRegexByNetworkType[type]?.any { regex ->
                regex.matches(normalized)
            } == true
            if (match) return type
        }

        if (WalletUtils.isValidAddress(normalized)) {
            return NetworkType.EVM
        }

        // TRON addresses are Base58 too, so detect before generic Base58 checks.
        if (normalized.startsWith("T") && normalized.length == 34) {
            try {
                Base58.decode(normalized)
                return NetworkType.TVM
            } catch (_: Exception) {
                // ignore
            }
        }

        if (normalized.startsWith("bc1", true) || normalized.startsWith("tb1", true)) {
            return NetworkType.BITCOIN
        }

        val utxoCandidates = listOf(
            NetworkName.BITCOIN to NetworkType.BITCOIN,
            NetworkName.BITCOINTESTNET to NetworkType.BITCOIN,
            NetworkName.LITECOIN to NetworkType.UTXO,
            NetworkName.LTCTESTNET to NetworkType.UTXO,
            NetworkName.DOGE to NetworkType.UTXO,
            NetworkName.DOGETESTNET to NetworkType.UTXO
        )
        utxoCandidates.forEach { (networkName, type) ->
            runCatching {
                Address.fromString(UtxoNetworkParametersResolver.resolve(networkName), normalized)
            }.onSuccess {
                return type
            }
        }

        return null
    }

    fun getNetworkType(address: String? = null, networkId: String? = null): NetworkType? {
        address?.let { getNetworkTypeForAddress(it) }?.let { return it }

        val normalizedNetworkId = networkId?.trim()?.lowercase().orEmpty()
        if (normalizedNetworkId.isBlank()) return null

        return getNetworkById(normalizedNetworkId)?.networkType
            ?: inferNetworkTypeFromNetworkId(normalizedNetworkId)
    }

    fun isValidAddressForNetworkId(address: String, networkId: String): Boolean {
        val normalizedAddress = address.trim()
        val normalizedNetworkId = networkId.trim().lowercase()
        if (normalizedAddress.isBlank() || normalizedNetworkId.isBlank()) return false

        addressRegexByNetworkId[normalizedNetworkId]?.let { regex ->
            return regex.matches(normalizedAddress)
        }

        val targetType = getNetworkType(networkId = normalizedNetworkId) ?: return false
        return getNetworkTypeForAddress(normalizedAddress) == targetType
    }

    private fun inferNetworkTypeFromNetworkId(networkId: String): NetworkType? {
        if (
            networkId.contains("tron") ||
            networkId.contains("shasta") ||
            networkId.contains("nile") ||
            networkId.contains("tvm")
        ) return NetworkType.TVM

        if (
            networkId.contains("bitcoin") ||
            networkId == "btc" ||
            networkId.startsWith("btc_")
        ) return NetworkType.BITCOIN

        if (
            networkId.contains("doge") ||
            networkId.contains("dogecoin") ||
            networkId.contains("litecoin") ||
            networkId == "ltc" ||
            networkId.startsWith("ltc_")
        ) return NetworkType.UTXO

        val evmHints = listOf(
            "ethereum", "sepolia", "evm", "bsc", "binance", "polygon", "matic",
            "arbitrum", "optimism", "base", "avalanche", "avax", "fantom", "linea",
            "zksync", "scroll", "opbnb"
        )
        if (evmHints.any { networkId.contains(it) }) return NetworkType.EVM

        return null
    }


    fun loadNetworksFromAssets(
        context: Context,
        fileName: String = "networks.json",
    ) {

        clearAll()
        val configs = loadNetworkConfigs(context, fileName)
        indexAddressRegex(configs)

        configs
            .filter { config -> config.isTestnet == true }
            .forEach { config ->
                val networkType =
                    runCatching { NetworkType.valueOf(config.networkType.uppercase()) }.getOrNull()
                        ?: return@forEach
            val factory = networkFactories.firstOrNull { it.supports(networkType, config) }
            val network = factory?.create(networkType, config)
            network?.let { registerNetwork(it) }
            }
    }

    private fun indexAddressRegex(configs: List<NetworkConfig>) {
        configs.forEach { config ->
            val normalizedId = config.id.trim().lowercase()
            if (normalizedId.isBlank()) return@forEach

            val networkType = runCatching {
                NetworkType.valueOf(config.networkType.uppercase())
            }.getOrNull() ?: return@forEach

            val compiledRegex =
                AddressRegexUtils.compileAddressRegex(config.regex) ?: return@forEach

            addressRegexByNetworkId[normalizedId] = compiledRegex
            addressRegexByNetworkType.getOrPut(networkType) { mutableListOf() }.add(compiledRegex)
        }
    }
}







