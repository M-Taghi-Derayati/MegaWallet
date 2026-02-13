package com.mtd.core.registry

import android.content.Context
import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.BitcoinNetwork
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.core.network.tron.TronNetwork
import com.mtd.core.utils.loadNetworkConfigs
import fr.acinq.bitcoin.Base58
import org.bitcoinj.base.exceptions.AddressFormatException
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.web3j.crypto.WalletUtils
import javax.inject.Inject


/**
 * Strategy-style factory for creating [BlockchainNetwork] instances from [NetworkConfig].
 * این اینترفیس کمک می‌کند از whenهای بزرگ وابسته به [NetworkType] دور شویم
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
        val params = if (!config.isTestnet) MainNetParams.get() else TestNet3Params.get()
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

    private val networksByType = mutableMapOf<NetworkType, BlockchainNetwork>()
    private val networksByChainId = mutableMapOf<Long, BlockchainNetwork>()

    /**
     * مجموعهٔ factoryهای موجود برای ساخت شبکه‌ها.
     * در حال حاضر به‌صورت داخلی مقداردهی می‌شود، اما در آینده می‌تواند از DI تزریق شود.
     */
    private val networkFactories: List<NetworkFactory> = listOf(
        EvmNetworkFactory(),
        BitcoinNetworkFactory(),
        TronNetworkFactory()
    )


    fun registerNetwork(network: BlockchainNetwork) {
        val chainId = network.chainId ?: return // فقط شبکه‌های با chainId را ثبت می‌کنیم
        // اگر این اولین شبکه از این نوع است، یک Map جدید برای آن بساز
        networks.getOrPut(network.networkType) { mutableMapOf() }

        // شبکه را در Map داخلی بر اساس chainId آن ثبت کن
        networks[network.networkType]!![chainId] = network
        networksByChainId[chainId] = network
        networksByType[network.networkType] = network
    }


    fun getNetworkByName(name: NetworkName): BlockchainNetwork? {
        return networks.values.flatMap { it.values }.find { it.name==name }
    }

    fun getNetworkById(id: String): BlockchainNetwork? {
        return networks.values.flatMap { it.values }.find { it.id==id }
    }


    fun getNetworkByChainId(chainId: Long): BlockchainNetwork? {
        return networksByChainId[chainId]
    }


    fun getAllNetworks(): List<BlockchainNetwork> {
        return networks.values.flatMap { it.values }
    }

    fun getNetworkByType(type: NetworkType): BlockchainNetwork? {
        return networksByType[type]
    }

    fun getDefaultNetworkByType(type: NetworkType): BlockchainNetwork? {
        return getAllNetworks().firstOrNull { it.networkType == type }
    }


    private fun clearAll() {
        networks.clear()
        networksByType.clear()
        networksByChainId.clear()
    }


    fun getNetworkTypeForAddress(address: String): NetworkType? {
        // ۱. بررسی آدرس‌های سازگار با EVM (مثل اتریوم)
        if (WalletUtils.isValidAddress(address)) {
            return NetworkType.EVM
        }

        // ۲. بررسی آدرس‌های بیت‌کوین (Base58 Legacy/SegWit-in-P2SH و Bech32 Native SegWit)
        try {
            // کتابخانه bitcoinj خودش هر دو فرمت mainnet و testnet را مدیریت می‌کند.
            // اگر آدرس قابل پارس کردن باشد، یعنی یک آدرس بیت‌کوین معتبر است.
            Base58.decode(address) // این یک چک سریع برای فرمت Base58 است.
            // یک اعتبارسنجی کامل‌تر می‌تواند شامل پارس کردن با NetworkParameters باشد.
            return NetworkType.BITCOIN
        } catch (e: AddressFormatException) {
            // این آدرس Base58 بیت‌کوین نیست.
        } catch (e: Exception) {
            // خطاهای دیگر
        }

        // Bech32 check for Native SegWit (bc1..., tb1...)
        if (address.startsWith("bc1", true) || address.startsWith("tb1", true)) {
            // TODO: افزودن یک کتابخانه اعتبارسنجی Bech32 برای دقت بیشتر
            return NetworkType.BITCOIN
        }

        // 3. Tron Addresses (Start with T, 34 chars, Base58)
        if (address.startsWith("T") && address.length == 34) {
             try {
                 Base58.decode(address)
                 return NetworkType.TVM
             } catch (e: Exception) {
                 // Invalid Base58
             }
        }

        return null
    }


    fun loadNetworksFromAssets(
        context: Context,
        fileName: String = "networks.json",
    ) {

        clearAll()
        val configs = loadNetworkConfigs(context, fileName)

        configs
            .filter { config -> config.isTestnet == true }
            .forEach { config ->
            val networkType = NetworkType.valueOf(config.networkType.uppercase())
            NetworkName.valueOf(config.name.uppercase())

            val factory = networkFactories.firstOrNull { it.supports(networkType, config) }
            val network = factory?.create(networkType, config)
            network?.let { registerNetwork(it) }

           /* val network = when (networkType) {
                NetworkType.EVM -> GenericEvmNetwork(config)
                NetworkType.BITCOIN -> {
                    val params = if (!config.isTestnet) MainNetParams.get() else TestNet3Params.get()
                    BitcoinNetwork(config, params)
                }
                NetworkType.TVM -> TronNetwork(config)
                else -> null
            }
            network?.let { registerNetwork(it) }*/
        }
    }
}




