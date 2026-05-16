package com.mtd.domain.interfaceRepository

interface IWalletSecretValidator {
    fun isValidMnemonic(mnemonic: String): Boolean
    fun isValidPrivateKey(privateKey: String): Boolean
}
