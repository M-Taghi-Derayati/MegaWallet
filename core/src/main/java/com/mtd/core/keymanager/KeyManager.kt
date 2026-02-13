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
     * کلیدهای خصوصی را از روی سکرت تولید کرده و در کش داخلی (Private Cache) بارگذاری می‌کند.
     * این متد هرگز نباید در لایه‌های UI صدا زده شود.
     */
    fun loadKeysIntoCache(secret: String, isMnemonic: Boolean) {
        credentialsCache.clear()
        val networks = registry.getAllNetworks()
        
        networks.forEach { network ->
            val chainId = network.chainId
            if (chainId != null) {
                // ما اینجا از تابع‌های رجیستری برای استخراج مستقیم پرایوت کی استفاده می‌کنیم
                // توجه: آبجکت WalletKey که اینجا برمی‌گردد دیگر شامل پرایوت کی نیست،
                // اما منطق داخلی هر شبکه (BlockchainNetwork) می‌تواند پرایوت کی را در لحظه تولید کند.
                // برای این کار، ممکن است نیاز باشد متدهای BlockchainNetwork را هم کمی تغییر دهیم یا
                // مستقیماً از helperها استفاده کنیم.
                
                // فعلاً فرض می‌کنیم متد کمکی برای استخراج PrivateKey داریم یا BlockchainNetwork آن را برمی‌گرداند.
                // برای سادگی فعلاً از یک متد داخلی استفاده می‌کنیم:
                val privateKey = if (isMnemonic) {
                    derivePrivateKeyHexFromMnemonic(secret, network)
                } else {
                    secret // در حالت پرایوت کی، خود سیکرت همان کلید است
                }
                
                if (privateKey != null) {
                    credentialsCache[chainId] = Credentials.create(privateKey)
                }
            }
        }
    }

    private fun derivePrivateKeyHexFromMnemonic(mnemonic: String, network: com.mtd.core.network.BlockchainNetwork): String? {
        return try {
             network.getPrivateKeyFromMnemonic(mnemonic)
        } catch (e: Exception) {
            null
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
