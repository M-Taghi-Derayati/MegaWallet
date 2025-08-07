package com.mtd.core.network.evm

import org.web3j.crypto.*
import org.web3j.utils.Numeric

object EvmKeyDerivation {

    // تبدیل mnemonic به seed و ساخت master key pair
    fun deriveKeyPairFromMnemonic(mnemonic: String, derivationPath: String = "m/44'/60'/0'/0/0"): ECKeyPair {
        // ۱. ابتدا seed را از mnemonic می‌سازیم
        val seed = MnemonicUtils.generateSeed(mnemonic, null) // null for no passphrase

        // ۲. یک master keypair از روی seed می‌سازیم
        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)

        // ۳. مسیر استخراج را پارس می‌کنیم
        val path = parseDerivationPath(derivationPath)

        // ۴. کلید فرزند را با استفاده از مسیر استخراج می‌کنیم
        val childKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path)

        // ۵. آبجکت ECKeyPair را برمی‌گردانیم (به جای Bip32ECKeyPair)
        return childKeyPair
    }


    // گرفتن Credentials از Bip32ECKeyPair
    fun getCredentialsFromKeyPair(keyPair: ECKeyPair): Credentials {
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