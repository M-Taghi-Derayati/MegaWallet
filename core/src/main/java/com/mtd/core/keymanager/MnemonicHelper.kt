package com.mtd.core.keymanager

import org.web3j.crypto.*
import java.security.SecureRandom

object MnemonicHelper {

    fun generateMnemonic(strengthInBits: Int = 128): String {
        require(strengthInBits == 128 || strengthInBits == 256) {
            "فقط 128 (12 کلمه) یا 256 (24 کلمه) بیت قابل پشتیبانیه"
        }
        val entropy = ByteArray(strengthInBits / 8)
        SecureRandom().nextBytes(entropy)
        return MnemonicUtils.generateMnemonic(entropy)
    }

    fun isValidMnemonic(mnemonic: String): Boolean {
        return try {
            MnemonicUtils.validateMnemonic(mnemonic)
        } catch (e: Exception) {
            false
        }
    }

    fun generateSeed(mnemonic: String, passphrase: String? = null): ByteArray {
        return MnemonicUtils.generateSeed(mnemonic, passphrase)
    }

    /**
     * تبدیل mnemonic به private key (hex) با مسیر استاندارد BIP44: m/44'/60'/0'/0/0
     */
    fun mnemonicToPrivateKey(mnemonic: String): String {
        val seed = generateSeed(mnemonic)
        val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)

        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            60 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0,
            0
        )
        val derivedKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeypair, path)

        return derivedKeyPair.privateKey.toString(16).padStart(64, '0')
    }

    /**
     * استخراج آدرس عمومی از کلید خصوصی (hex)
     */
    fun getAddressFromPrivateKey(privateKeyHex: String): String {
        val credentials = Credentials.create(privateKeyHex)
        return credentials.address
    }
}