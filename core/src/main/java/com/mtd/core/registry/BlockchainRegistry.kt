package com.mtd.core.registry

import android.content.Context
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.BitcoinNetwork
import com.mtd.core.network.bitcoin.UtxoNetworkParametersResolver
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.core.network.tron.TronNetwork
import com.mtd.core.utils.AddressRegexUtils
import com.mtd.core.utils.loadNetworkConfigs
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.core.NetworkConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import fr.acinq.bitcoin.Base58
import org.bitcoinj.base.Address
import org.web3j.crypto.WalletUtils
import timber.log.Timber
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

        val networkName = NetworkName.fromConfigName(config.name)
            ?: throw IllegalArgumentException(
                "UTXO network '${config.id}' has no known NetworkName alias; " +
                    "a new UTXO chain still needs bitcoinj parameters in code."
            )
        val params = UtxoNetworkParametersResolver.resolve(networkName)
        return BitcoinNetwork(config, params)
    }
}

class UtxoNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.UTXO
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {

        val networkName = NetworkName.fromConfigName(config.name)
            ?: throw IllegalArgumentException(
                "UTXO network '${config.id}' has no known NetworkName alias; " +
                    "a new UTXO chain still needs bitcoinj parameters in code."
            )
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


class BlockchainRegistry @Inject constructor(
    private val testnetVisibility: ITestnetVisibilityProvider
) : INetworkCatalog {



    private class Catalog(
        val byId: Map<String, BlockchainNetwork> = emptyMap(),
        val byChainId: Map<Long, BlockchainNetwork> = emptyMap(),
        val defaultByType: Map<NetworkType, BlockchainNetwork> = emptyMap(),
        val regexById: Map<String, Regex> = emptyMap(),
        val regexByType: Map<NetworkType, List<Regex>> = emptyMap()
    )

    @Volatile
    private var catalog = Catalog()
    private val writeLock = Any()

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
        synchronized(writeLock) {
            catalog = catalog.withNetwork(network)
        }
    }


    private fun Catalog.withNetwork(network: BlockchainNetwork): Catalog {
        val newById = byId + (network.id to network)

        var newByChainId = byChainId
        val chainId = network.chainId
        if (chainId != null) {

            val existing = byChainId[chainId]
            if (existing != null && existing.id != network.id) {
                Timber.e(
                    "chainId collision: '%s' and '%s' both declare chainId=%d. Keeping '%s' for " +
                        "chainId-keyed lookups; both remain resolvable by networkId. Fix the " +
                        "catalog — chainId-routed sends for these networks are ambiguous.",
                    existing.id, network.id, chainId, existing.id
                )
            } else {
                newByChainId = byChainId + (chainId to network)
            }
        }

        // Register as default for type
        val newDefaultByType =
            if (!defaultByType.containsKey(network.networkType) || !network.isTestnet) {
                defaultByType + (network.networkType to network)
            } else {
                defaultByType
            }

        return Catalog(newById, newByChainId, newDefaultByType, regexById, regexByType)
    }


    fun getNetworkByName(name: NetworkName): BlockchainNetwork? {
        return catalog.byId.values.find { it.name == name }
    }

    fun getNetworkById(id: String): BlockchainNetwork? {
        return catalog.byId[id]
    }


    fun getNetworkByChainId(chainId: Long): BlockchainNetwork? {
        return catalog.byChainId[chainId]
    }


    fun getAllNetworks(): List<BlockchainNetwork> {
        return catalog.byId.values.toList()
    }

    val showTestnets =false //testnetVisibility.showTestnets()
    override fun getAllNetworkInfos(): List<NetworkInfo> {

        return getAllNetworks()
            .filter { it.isTestnet==showTestnets }
            .map { it.toNetworkInfo() }
    }

    override fun getNetworkInfoByName(name: NetworkName): NetworkInfo? {
        return getNetworkByName(name)?.toNetworkInfo()
    }

    override fun getNetworkInfoById(id: String): NetworkInfo? {
        return getNetworkById(id)?.toNetworkInfo()
    }

    fun getNetworkByType(type: NetworkType): BlockchainNetwork? {
        return catalog.defaultByType[type]
    }

    fun getDefaultNetworkByType(type: NetworkType): BlockchainNetwork? {
        return getAllNetworks().firstOrNull { it.networkType == type }
    }


    /**
     * @param configs شبکه‌های باندل.
     * @param trustedBaseline شبکه‌های seed محلی که هویتشان مرجع است.
     * @return تعداد شبکه‌های ثبت‌شده.
     */
    fun applyConfig(
        configs: List<NetworkConfig>,
        trustedBaseline: List<NetworkConfig> = emptyList()
    ): Int {
        val baselineById = trustedBaseline.associateBy { it.id.trim().lowercase() }

        val accepted = configs
            .filter { it.isTestnet==showTestnets }
            .map { config ->
            val baseline = baselineById[config.id.trim().lowercase()]
                ?: return@map config // شبکهٔ تازه — همین است که می‌خواهیم

            val violations = buildList {
                if (baseline.chainId != config.chainId) add("chainId ${baseline.chainId}->${config.chainId}")
                if (baseline.derivationPath != config.derivationPath) add("derivationPath")
                if (baseline.regex != config.regex) add("addressRegex")
            }
            if (violations.isEmpty()) {
                config
            } else {
                Timber.e(
                    "Rejected bundle override of security-critical fields for '%s' (%s); " +
                        "keeping the locally shipped definition.",
                    config.id, violations.joinToString()
                )
                baseline
            }
        }

        val rebuilt = buildCatalog(accepted)
        synchronized(writeLock) { catalog = rebuilt }

        Timber.i("Catalog applied: %d/%d networks registered", rebuilt.byId.size, configs.size)
        return rebuilt.byId.size
    }

    private fun buildCatalog(configs: List<NetworkConfig>): Catalog {
        val (regexById, regexByType) = buildAddressRegexIndex(configs)
        var built = Catalog(regexById = regexById, regexByType = regexByType)
        configs.forEach { config ->
            buildNetwork(config)?.let { built = built.withNetwork(it) }
        }
        return built
    }


    override fun getNetworkTypeForAddress(address: String): NetworkType? {
        try {
            val normalized = address.trim()
            if (normalized.isBlank()) return null

            val snapshot = catalog
            NetworkType.values().forEach { type ->
                val match = snapshot.regexByType[type]?.any { regex ->
                    regex.matches(normalized)
                } == true
                if (match) return type
            }

            if (WalletUtils.isValidAddress(normalized)) {
                return NetworkType.EVM
            }

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
        } catch (e: Exception) {
            return null
        }
    }

    override fun getNetworkTypeForNetworkId(networkId: String): NetworkType? {
        return getNetworkType(networkId = networkId)
    }

    fun getNetworkType(address: String? = null, networkId: String? = null): NetworkType? {
        address?.let { getNetworkTypeForAddress(it) }?.let { return it }

        val normalizedNetworkId = networkId?.trim()?.lowercase().orEmpty()
        if (normalizedNetworkId.isBlank()) return null

        return getNetworkById(normalizedNetworkId)?.networkType
            ?: inferNetworkTypeFromNetworkId(normalizedNetworkId)
    }

    override fun isValidAddressForNetworkId(address: String, networkId: String): Boolean {
        val normalizedAddress = address.trim()
        val normalizedNetworkId = networkId.trim().lowercase()
        if (normalizedAddress.isBlank() || normalizedNetworkId.isBlank()) return false

        catalog.regexById[normalizedNetworkId]?.let { regex ->
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


        applyConfig(loadNetworkConfigs(context, fileName))
    }

    private fun buildNetwork(config: NetworkConfig): BlockchainNetwork? {
        val networkType =
            runCatching { NetworkType.valueOf(config.networkType.uppercase()) }.getOrNull()
                ?: run {
                    Timber.w("Network '%s' skipped: unknown type '%s'", config.id, config.networkType)
                    return null
                }
        val factory = networkFactories.firstOrNull { it.supports(networkType, config) }
            ?: run {
                Timber.w("Network '%s' skipped: no factory for %s", config.id, networkType)
                return null
            }
        return runCatching { factory.create(networkType, config) }
            .onFailure { Timber.w(it, "Network '%s' skipped: factory could not build it", config.id) }
            .getOrNull()
    }

    private fun buildAddressRegexIndex(
        configs: List<NetworkConfig>
    ): Pair<Map<String, Regex>, Map<NetworkType, List<Regex>>> {
        val byId = mutableMapOf<String, Regex>()
        val byType = mutableMapOf<NetworkType, MutableList<Regex>>()

        configs.forEach { config ->
            val normalizedId = config.id.trim().lowercase()
            if (normalizedId.isBlank()) return@forEach

            val networkType = runCatching {
                NetworkType.valueOf(config.networkType.uppercase())
            }.getOrNull() ?: return@forEach

            val compiledRegex =
                AddressRegexUtils.compileAddressRegex(config.regex) ?: return@forEach

            byId[normalizedId] = compiledRegex
            byType.getOrPut(networkType) { mutableListOf() }.add(compiledRegex)
        }

        return byId.toMap() to byType.mapValues { (_, v) -> v.toList() }
    }

    private fun BlockchainNetwork.toNetworkInfo(): NetworkInfo {
        return NetworkInfo(
            id = id,
            networkType = networkType,
            name = name,
            currencySymbol = currencySymbol,
            iconUrl = iconUrl,
            faName = faName,
            decimals = decimals,
            explorers = explorers,
            explorerTxUrl = explorerTxUrl,
            color = color
        )
    }
}







