package com.mtd.data.datasource

import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.UtxoNetworkParametersResolver
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.BuildConfig
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.core.NetworkType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strategy-style provider برای ساخت [IChainDataSource] براساس نوع شبکه.
 * این abstraction کمک می‌کند from whenهای بزرگ وابسته به [NetworkType] فاصله بگیریم.
 */
interface ChainDataSourceProvider {
    val mode: BlockchainConnectionMode
    fun supports(network: BlockchainNetwork): Boolean
    fun create(network: BlockchainNetwork): IChainDataSource
}

@Singleton
class ChainDataSourceFactory @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val modeProvider: IBlockchainConnectionModeProvider,
    private val retrofitBuilder: Retrofit.Builder,
    private val assetRegistry: AssetRegistry,
    private val okHttpClient: OkHttpClient
) {
    private val dataSourceCache = mutableMapOf<String, IChainDataSource>()

    // TASK-36 — shared so circuit-breaker state persists per (mode, network) across decorators.
    private val healthTracker = TransportHealthTracker()

    private companion object {
        // Kill switch: flip to false to restore single-transport behavior (no failover). TASK-36.
        const val FAILOVER_ENABLED = true
    }

    /**
     * مجموعهٔ providerهای موجود برای ساخت datasourceها.
     * فعلاً به‌صورت داخلی مقداردهی می‌شود تا سازگاری با تست‌ها و DI فعلی حفظ شود.
     */
    private val providers: List<ChainDataSourceProvider> by lazy {
        listOf(
            object : ChainDataSourceProvider {
                override val mode: BlockchainConnectionMode = BlockchainConnectionMode.DIRECT

                override fun supports(network: BlockchainNetwork): Boolean {
                    return network.networkType == NetworkType.EVM
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    // تغییر مهم: پاس دادن okHttpClient به جای ساختن Web3j در اینجا
                    return EvmDataSource(network, retrofitBuilder, assetRegistry, okHttpClient)
                }
            },
            object : ChainDataSourceProvider {
                override val mode: BlockchainConnectionMode = BlockchainConnectionMode.DIRECT

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
                override val mode: BlockchainConnectionMode = BlockchainConnectionMode.DIRECT

                override fun supports(network: BlockchainNetwork): Boolean {
                    return network.networkType == NetworkType.TVM
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    return TronDataSource(network,retrofitBuilder,assetRegistry, okHttpClient)
                }
            },
            object : ChainDataSourceProvider {
                // PROXY transport — selected when the connection mode is PROXY. (Previously mis-tagged
                // DIRECT, which left PROXY mode with NO matching provider, so create() threw before any
                // HTTP call and balances/history were never sent to the relayer.)
                override val mode: BlockchainConnectionMode = BlockchainConnectionMode.PROXY

                override fun supports(network: BlockchainNetwork): Boolean {
                    return network.networkType == NetworkType.EVM ||
                        network.networkType == NetworkType.TVM ||
                        network.networkType == NetworkType.BITCOIN ||
                        network.networkType == NetworkType.UTXO
                }

                override fun create(network: BlockchainNetwork): IChainDataSource {
                    // Built from the shared retrofitBuilder (which already carries the BigInteger-aware
                    // Gson + scalars converters); cached per (mode, network) by the factory.
                    val proxyService = retrofitBuilder
                        .baseUrl(BuildConfig.RELAYER_BASE_URL)
                        .build()
                        .create(MobileProxyApiService::class.java)
                    // UTXO send needs local bitcoinj assembly; resolve NetworkParameters the same way
                    // the DIRECT UTXO source does. Non-UTXO networks get null (EVM/TVM sign differently).
                    val utxoTxBuilder = if (
                        network.networkType == NetworkType.BITCOIN ||
                        network.networkType == NetworkType.UTXO
                    ) {
                        BitcoinjUtxoTxBuilder(network, UtxoNetworkParametersResolver.resolve(network.name))
                    } else {
                        null
                    }
                    return ProxyChainDataSource(network, proxyService, utxoTxBuilder = utxoTxBuilder)
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
        val mode = modeProvider.currentMode()
        val cacheKey = cacheKey(mode, network)
        dataSourceCache[cacheKey]?.let { return it }

        val provider = providers.firstOrNull { it.mode == mode && it.supports(network) }
            ?: throw unsupportedModeException(mode, network)
        val preferred = provider.create(network)

        // TASK-36 — wrap the mode-selected source so reads self-heal onto the other transport when
        // this one has a transport/server failure. The user's `mode` stays the preferred transport;
        // failover is a temporary, per-call override (broadcast is never failed over). Flag-gated.
        val alternateMode = if (mode == BlockchainConnectionMode.PROXY) {
            BlockchainConnectionMode.DIRECT
        } else {
            BlockchainConnectionMode.PROXY
        }
        val alternateProvider = providers.firstOrNull {
            it.mode == alternateMode && it.supports(network)
        }

        val newDataSource = if (FAILOVER_ENABLED && alternateProvider != null) {
            TransportFailoverChainDataSource(
                preferred = preferred,
                alternateFactory = { alternateProvider.create(network) },
                preferredMode = mode,
                networkId = network.id,
                health = healthTracker
            )
        } else {
            preferred
        }
        dataSourceCache[cacheKey] = newDataSource
        return newDataSource
    }

    private fun cacheKey(mode: BlockchainConnectionMode, network: BlockchainNetwork): String {
        return "${mode.name}:${network.id}"
    }

    private fun unsupportedModeException(
        mode: BlockchainConnectionMode,
        network: BlockchainNetwork
    ): IllegalArgumentException {
        return when (mode) {
            BlockchainConnectionMode.PROXY -> IllegalArgumentException(
                "Proxy chain data source is not configured yet for network: ${network.id}"
            )
            BlockchainConnectionMode.DIRECT -> IllegalArgumentException(
                "Unsupported network type: ${network.networkType}"
            )
        }
    }
}
