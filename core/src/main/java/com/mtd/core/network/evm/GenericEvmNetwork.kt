package com.mtd.core.network.evm

import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.network.BlockchainNetwork
import org.web3j.crypto.ECKeyPair

class GenericEvmNetwork(config: NetworkConfig) : BlockchainNetwork {
    override val id: String=config.id
    override val networkType = NetworkType.valueOf(config.networkType)
    override val name = NetworkName.valueOf(config.name)
    override val chainId = config.chainId
    override val defaultRpcUrls=config.rpcUrls
    override val currencySymbol= config.currencySymbol
    override val blockExplorerUrl=config.blockExplorerUrl
    override val explorers=config.explorers
    private val derivationPath = config.derivationPath

    override fun deriveKeyFromMnemonic(mnemonic: String): WalletKey {
        val keyPair: ECKeyPair = EvmKeyDerivation.deriveKeyPairFromMnemonic(mnemonic, derivationPath)
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