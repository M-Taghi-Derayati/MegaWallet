package com.mtd.core.keymanager

import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.BlockchainRegistry
import javax.inject.Inject


class KeyManager @Inject constructor (
    private val registry: BlockchainRegistry
) {

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
}
