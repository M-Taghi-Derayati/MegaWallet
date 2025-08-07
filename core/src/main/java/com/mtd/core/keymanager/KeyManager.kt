package com.mtd.core.keymanager

import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.BlockchainRegistry
import org.web3j.crypto.Credentials
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject


class KeyManager @Inject constructor (
    private val registry: BlockchainRegistry
) {
    private val credentialsCache = ConcurrentHashMap<Long, Credentials>() // Key: chainId

    /**
     * ساخت کلیدها برای همه شبکه‌های ثبت‌شده با استفاده از mnemonic
     */
    fun generateWalletKeysFromMnemonic(mnemonic: String): List<WalletKey> {
        val result = mutableListOf<WalletKey>()
        val networks = registry.getAllNetworks()

        networks.forEach { network ->
            val walletKey = network.deriveKeyFromMnemonic(mnemonic)
            result.add(walletKey)
        }

        return result
    }

    /**
     * ساخت کلیدها برای همه شبکه‌ها از روی private key
     */
    fun generateWalletKeysFromPrivateKey(privateKey: String): List<WalletKey> {
        val result = mutableListOf<WalletKey>()
        val networks = registry.getAllNetworks()

        networks.forEach { network ->
            val walletKey = network.deriveKeyFromPrivateKey(privateKey)
            result.add(walletKey)
        }

        return result
    }

    /**
     * ساخت کلید فقط برای یک شبکه خاص
     */
    fun generateKeyForNetwork(
        mnemonic: String,
        networkType: NetworkType
    ): WalletKey? {
        val network = registry.getNetworkByType(networkType)
        return network?.deriveKeyFromMnemonic(mnemonic)
    }

    /**
     * کلیدهای تولید شده را در کش بارگذاری می‌کند تا برای امضا در دسترس باشند.
     * این متد باید بعد از باز شدن قفل کیف پول فراخوانی شود.
     */
    fun loadKeysIntoCache(keys: List<WalletKey>) {
        credentialsCache.clear()
        keys.forEach { key ->
            if (key.chainId != null) {
                credentialsCache[key.chainId] = Credentials.create(key.privateKeyHex)
            }
        }
    }


    /**
     * Credentials را برای یک chainId خاص از کش دریافت می‌کند.
     */
    fun getCredentialsForChain(chainId: Long): Credentials? {
        return credentialsCache[chainId]
    }

    /**
     * کش را پاک می‌کند (مثلاً هنگام قفل شدن کیف پول).
     */
    fun clearCache() {
        credentialsCache.clear()
    }

}
