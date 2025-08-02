package com.mtd.core

import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.network.evm.EvmKeyDerivation
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
      val mnemonic= MnemonicHelper.generateMnemonic(128)
        val keypair= EvmKeyDerivation.deriveKeyPairFromMnemonic(mnemonic)
        val credentials= EvmKeyDerivation.getCredentialsFromKeyPair(keypair)
        println("🔑 Private Key:\n${EvmKeyDerivation.getPrivateKeyHex(credentials)}\n")
        println("📬 Public Key:\n${EvmKeyDerivation.getPublicKeyHex(credentials)}\n")
        println("🏦 Address:\n${EvmKeyDerivation.getAddressFromCredentials(credentials)}")
    }
}