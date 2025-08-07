package com.mtd.core.network.bitcoin


import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.WalletKey
import com.mtd.core.network.BlockchainNetwork
import org.bitcoinj.base.LegacyAddress
import org.bitcoinj.base.ScriptType
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.HDPath
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed

abstract class AbstractUtxoNetwork(
    protected val config: NetworkConfig,
    protected val params: NetworkParameters
) : BlockchainNetwork {

    override val id = config.id
    override val chainId: Long? = null // شبکه‌های UTXO معمولاً chainId ندارند
    override val defaultRpcUrls = config.rpcUrls
    override val currencySymbol = config.currencySymbol
    override val blockExplorerUrl = config.blockExplorerUrl
    override val explorers = config.explorers

    override fun deriveKeyFromMnemonic(mnemonic: String): WalletKey {
        // ۱. ساخت Seed از Mnemonic
        val seed = DeterministicSeed(mnemonic, null, "", System.currentTimeMillis() / 1000)

        // ۲. ساخت زنجیره کلید
        val keyChain = DeterministicKeyChain.builder().seed(seed).build()

        // ۳. پارس کردن مسیر استخراج از کانفیگ
        val keyPath = HDPath.parsePath(config.derivationPath)

        // ۴. استخراج کلید نهایی
        val deterministicKey = keyChain.getKeyByPath(keyPath, true)

        val privateKeyHex = deterministicKey.privKeyBytes.toHexString()
        val publicKeyHex = deterministicKey.pubKey.toHexString()
        val address = LegacyAddress.fromKey(params, deterministicKey).toString()

        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = config.derivationPath,
            address = address,
            privateKeyHex = privateKeyHex,
            publicKeyHex = publicKeyHex
        )
    }

    override fun deriveKeyFromPrivateKey(privateKey: String): WalletKey {
        val key = ECKey.fromPrivate(privateKey.hexToBytes())
        val address = LegacyAddress.fromKey(params, key).toString()
        val publicKeyHex = key.pubKey.toHexString()

        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = null, // مسیر استخراج برای کلید خصوصی وارد شده، مشخص نیست
            address = address,
            privateKeyHex = privateKey,
            publicKeyHex = publicKeyHex
        )
    }
}

// توابع کمکی برای تبدیل هگز
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()