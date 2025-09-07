package com.mtd.core.registry

import android.content.Context
import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.BitcoinNetwork
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.core.utils.loadNetworkConfigs
import fr.acinq.bitcoin.Base58
import org.bitcoinj.base.exceptions.AddressFormatException
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.web3j.crypto.WalletUtils
import javax.inject.Inject


class BlockchainRegistry @Inject constructor() {


    private val networks = mutableMapOf<NetworkType, MutableMap<Long, BlockchainNetwork>>()

    private val networksByType = mutableMapOf<NetworkType, BlockchainNetwork>()
    private val networksByChainId = mutableMapOf<Long, BlockchainNetwork>()


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

        // TODO: در آینده، بلاک‌های بررسی برای شبکه‌های دیگر (Solana, Tron, ...) اینجا اضافه می‌شوند.

        // ۳. اگر هیچکدام از موارد بالا نبود، آدرس ناشناخته است.
        return null
    }


    fun loadNetworksFromAssets(context: Context, fileName: String = "networks.json") {
        val configs = loadNetworkConfigs(context, fileName)

        configs.forEach { config ->
            val networkType=NetworkType.valueOf(config.networkType.uppercase())
            NetworkName.valueOf(config.name.uppercase())
            val network = when (networkType) {
                NetworkType.EVM -> GenericEvmNetwork(config)
                NetworkType.BITCOIN -> {
                    val params = if (config.isMainnet()) MainNetParams.get() else TestNet3Params.get()
                    BitcoinNetwork(config, params)
                }
                else -> null
            }
            network?.let { registerNetwork(it) }
        }
    }

    fun NetworkConfig.isMainnet(): Boolean {
        // یک قرارداد: chainId 0 برای بیت‌کوین mainnet، یا هر عدد دیگری برای testnet
        // برای EVM ها هم می‌توانیم chainId های معروف را چک کنیم.
        return this.chainId == 0L || this.chainId == 1L || this.chainId == 137L // مثال
    }

}