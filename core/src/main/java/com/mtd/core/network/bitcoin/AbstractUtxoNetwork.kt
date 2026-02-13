package com.mtd.core.network.bitcoin


import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.WalletKey
import com.mtd.core.network.BlockchainNetwork
import org.bitcoinj.base.LegacyAddress
import org.bitcoinj.base.SegwitAddress
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.HDPath
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import java.math.BigInteger
import java.time.Instant

abstract class AbstractUtxoNetwork(
    protected val config: NetworkConfig,
    protected val params: NetworkParameters
) : BlockchainNetwork {

    override val id = config.id
    override val chainId: Long? = config.chainId // شبکه‌های UTXO معمولاً chainId ندارند
    override val RpcUrls = config.rpcUrls
    override val decimals=config.decimals
    override val iconUrl=config.iconUrl
    override val webSocketUrl=config.webSocketUrl
    override val currencySymbol = config.currencySymbol
    override val explorers = config.explorers
    override val color = config.color
    override val faName = config.faName
    override val isTestnet: Boolean = config.isTestnet

    override fun deriveKeyFromMnemonic(mnemonic: String): WalletKey {
        // ۱. ساخت Seed از Mnemonic
        val seed =  DeterministicSeed.ofMnemonic(mnemonic, "", Instant.now())

        // ۲. ساخت زنجیره کلید
        val keyChain = DeterministicKeyChain.builder().seed(seed).build()

        // ۳. پارس کردن مسیر استخراج از کانفیگ
        val compatiblePath = config.derivationPath.replace("'", "H")
        val keyPath = HDPath.parsePath(compatiblePath)

        // ۴. استخراج کلید نهایی
        val deterministicKey = keyChain.getKeyByPath(keyPath, true)

        // ۵. تولید آدرس بر اساس نوع مسیر استخراج (این بخش هوشمند شده)
        val address = when {
            // BIP84: Native SegWit (P2WPKH) -> آدرس‌های bc1... یا tb1...
            config.derivationPath.startsWith("m/84'") -> {
                SegwitAddress.fromKey(params, deterministicKey).toString()
            }
            // BIP49: Nested SegWit (P2SH-P2WPKH) -> آدرس‌های 3... یا 2...
            config.derivationPath.startsWith("m/49'") -> {
                // این نوع آدرس در bitcoinj کمی متفاوت ساخته میشه
                val script = ScriptBuilder.createP2WPKHOutputScript(deterministicKey)
                val p2shScript = ScriptBuilder.createP2SHOutputScript(script)
                LegacyAddress.fromScriptHash(params, p2shScript.program).toString()
            }
            // BIP44: Legacy (P2PKH) -> آدرس‌های 1... یا m.../n...
            else -> {
                LegacyAddress.fromKey(params, deterministicKey).toString()
            }
        }

        val publicKeyHex = deterministicKey.pubKey.toHexString()

        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = config.derivationPath,
            address = address,
            publicKeyHex = publicKeyHex
        )
    }

    override fun deriveKeyFromPrivateKey(privateKey: String): WalletKey {
        val privateKeyHex = if (privateKey.startsWith("0x")) privateKey.substring(2) else privateKey

        if (privateKeyHex.length != 64 || !privateKeyHex.matches(Regex("[a-fA-F0-9]+"))) {
            return throw IllegalStateException("Invalid private key format")
        }
        val privateKeyBigInt = BigInteger(privateKeyHex, 16)
        if (privateKeyBigInt == BigInteger.ZERO) return throw IllegalStateException("Invalid private key format")

        // ۱. ساخت کلید ECKey از هگز کلید خصوصی
        val key = ECKey.fromPrivate(privateKeyBigInt)

        // --- بخش اصلاح شده ---
        // ۲. تولید آدرس بر اساس نوع مسیر استخراج تعریف شده در کانفیگ شبکه
        val address = when {
            // اگر کانفیگ شبکه BIP84 (Native SegWit) را مشخص کرده باشد
            config.derivationPath.startsWith("m/84'") -> {
                SegwitAddress.fromKey(params, key).toString()
            }
            // اگر کانفیگ شبکه BIP49 (Nested SegWit) را مشخص کرده باشد
            config.derivationPath.startsWith("m/49'") -> {
                val script = ScriptBuilder.createP2WPKHOutputScript(key)
                val p2shScript = ScriptBuilder.createP2SHOutputScript(script)
                LegacyAddress.fromScriptHash(params, p2shScript.program).toString()
            }
            // در غیر این صورت (یا اگر کانفیگ BIP44 باشد)، آدرس Legacy بساز
            else -> {
                LegacyAddress.fromKey(params, key).toString()
            }
        }
        val publicKeyHex = key.pubKey.toHexString()

        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = null, // مسیر استخراج برای کلید خصوصی وارد شده، مشخص نیست
            address = address,
            publicKeyHex = publicKeyHex
        )
    }

    override fun getPrivateKeyFromMnemonic(mnemonic: String): String {
        val seed = DeterministicSeed.ofMnemonic(mnemonic, "", Instant.now())
        val keyChain = DeterministicKeyChain.builder().seed(seed).build()
        val compatiblePath = config.derivationPath.replace("'", "H")
        val keyPath = HDPath.parsePath(compatiblePath)
        val deterministicKey = keyChain.getKeyByPath(keyPath, true)
        return deterministicKey.privKeyBytes.toHexString()
    }

    override fun getPrivateKeyFromPrivateKey(privateKey: String): String {
        return if (privateKey.startsWith("0x")) privateKey.substring(2) else privateKey
    }


}

// توابع کمکی برای تبدیل هگز
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()