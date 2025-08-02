package com.mtd.core.network.evm

import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.network.BlockchainNetwork

class GenericEvmNetwork(private val config: NetworkConfig) : BlockchainNetwork {
    override val networkType = NetworkType.valueOf(config.networkType)
    override val name = config.name
    private val chainId = config.chainId
    private val derivationPath = config.derivationPath

    override fun deriveKeyFromMnemonic(mnemonic: String): WalletKey {
        val keyPair = EvmKeyDerivation.deriveKeyPairFromMnemonic(mnemonic, derivationPath)
        val credentials = EvmKeyDerivation.getCredentialsFromKeyPair(keyPair)
        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = derivationPath,
            address = credentials.address,
            privateKeyHex = EvmKeyDerivation.getPrivateKeyHex(credentials),
            publicKeyHex = EvmKeyDerivation.getPublicKeyHex(credentials)
        )
    }

    override fun deriveKeyFromPrivateKey(privateKey: String): WalletKey {
        val credentials = org.web3j.crypto.Credentials.create(privateKey)
        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = derivationPath,
            address = credentials.address,
            privateKeyHex = privateKey,
            publicKeyHex = credentials.ecKeyPair.publicKey.toString(16)
        )
    }
}