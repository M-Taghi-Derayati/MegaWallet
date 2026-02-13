package com.mtd.core.network.evm

import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.network.BlockchainNetwork
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair

class GenericEvmNetwork(config: NetworkConfig) : BlockchainNetwork {
    override val id: String=config.id
    override val networkType = NetworkType.valueOf(config.networkType)
    override val name = NetworkName.valueOf(config.name)
    override val chainId = config.chainId
    override val decimals=config.decimals
    override val iconUrl=config.iconUrl
    override val webSocketUrl=config.webSocketUrl
    override val RpcUrls=config.rpcUrls
    override val currencySymbol= config.currencySymbol
    override val explorers=config.explorers
    override val color = config.color
    override val faName = config.faName
    override val isTestnet: Boolean = config.isTestnet
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
            publicKeyHex = EvmKeyDerivation.getPublicKeyHex(credentials)
        )
    }

    override fun deriveKeyFromPrivateKey(privateKey: String): WalletKey {
        val credentials = Credentials.create(privateKey)
        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = derivationPath,
            address = credentials.address,
            publicKeyHex = credentials.ecKeyPair.publicKey.toString(16)
        )
    }

    override fun getPrivateKeyFromMnemonic(mnemonic: String): String {
        val keyPair = EvmKeyDerivation.deriveKeyPairFromMnemonic(mnemonic, derivationPath)
        return keyPair.privateKey.toString(16)
    }

    override fun getPrivateKeyFromPrivateKey(privateKey: String): String {
        return privateKey
    }
}