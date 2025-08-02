package com.mtd.core.network.evm

import org.web3j.crypto.*
import org.web3j.utils.Numeric

object EvmKeyDerivation {

    // تبدیل mnemonic به seed و ساخت master key pair
    fun deriveKeyPairFromMnemonic(mnemonic: String, derivationPath: String = "m/44'/60'/0'/0/0"): Bip32ECKeyPair {
        val seed = MnemonicUtils.generateSeed(mnemonic, null)
        val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)

        val path = parseDerivationPath(derivationPath)
        return Bip32ECKeyPair.deriveKeyPair(masterKeypair, path)
    }

    // گرفتن Credentials از Bip32ECKeyPair
    fun getCredentialsFromKeyPair(keyPair: Bip32ECKeyPair): Credentials {
        return Credentials.create(keyPair)
    }

    fun getPrivateKeyHex(credentials: Credentials): String {
        return Numeric.toHexStringNoPrefix(credentials.ecKeyPair.privateKey)
    }

    fun getPublicKeyHex(credentials: Credentials): String {
        return Numeric.toHexStringNoPrefix(credentials.ecKeyPair.publicKey)
    }

    fun getAddressFromCredentials(credentials: Credentials): String {
        return credentials.address
    }

    // تبدیل مسیر "m/44'/60'/0'/0/0" به آرایه Int
    private fun parseDerivationPath(path: String): IntArray {
        return path.split("/")
            .drop(1) // حذف "m"
            .map { level ->
                var hardened = false
                var numberStr = level
                if (level.endsWith("'")) {
                    hardened = true
                    numberStr = level.dropLast(1)
                }
                var number = numberStr.toInt()
                if (hardened) {
                    number = number or Bip32ECKeyPair.HARDENED_BIT
                }
                number
            }
            .toIntArray()
    }
}