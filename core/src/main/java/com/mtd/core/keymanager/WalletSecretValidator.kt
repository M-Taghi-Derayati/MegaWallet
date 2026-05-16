package com.mtd.core.keymanager

import com.mtd.domain.interfaceRepository.IWalletSecretValidator
import javax.inject.Inject

class WalletSecretValidator @Inject constructor() : IWalletSecretValidator {
    override fun isValidMnemonic(mnemonic: String): Boolean {
        return MnemonicHelper.isValidMnemonic(mnemonic)
    }

    override fun isValidPrivateKey(privateKey: String): Boolean {
        return MnemonicHelper.isPrivateKeyValid(privateKey)
    }
}
