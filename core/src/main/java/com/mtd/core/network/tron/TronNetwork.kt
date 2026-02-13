package com.mtd.core.network.tron

import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.tron.TronUtils.Base58.hexDecode
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.MnemonicUtils
import org.web3j.utils.Numeric.cleanHexPrefix
import org.web3j.utils.Numeric.toHexStringNoPrefix

class TronNetwork(config: NetworkConfig) : BlockchainNetwork {
    override val id: String = config.id
    override val networkType: NetworkType = NetworkType.valueOf(config.networkType)
    override val name: NetworkName = NetworkName.valueOf(config.name)
    override val chainId: Long? = config.chainId
    override val decimals: Int = config.decimals
    override val iconUrl: String = config.iconUrl
    override val webSocketUrl: String? = config.webSocketUrl
    override val RpcUrls: List<String> = config.rpcUrls
    override val currencySymbol: String = config.currencySymbol
    override val explorers: List<String> = config.explorers
    override val color: String? = config.color
    override val faName: String? = config.faName
    override val isTestnet: Boolean = config.isTestnet

    private val derivationPath = config.derivationPath // "m/44'/195'/0'/0/0"

    override fun deriveKeyFromMnemonic(mnemonic: String): WalletKey {
        // 1. Generate seed from mnemonic
        val seed = MnemonicUtils.generateSeed(mnemonic, null)
        
        // 2. Derive master key pair and child key pair using BIP44 path
        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val path = parseDerivationPath(derivationPath)
        val childKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path)
        
        // 3. Generate TRON address from public key
        val address = TronUtils.getAddressFromPublicKey(childKeyPair.publicKey)
        
        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = derivationPath,
            address = address,
            publicKeyHex = toHexStringNoPrefix(childKeyPair.publicKey)
        )
    }

    override fun deriveKeyFromPrivateKey(privateKey: String): WalletKey {
        val cleanPrivateKey = cleanHexPrefix(privateKey)
        val keyPair = ECKeyPair.create(hexDecode(cleanPrivateKey))
        
        val address = TronUtils.getAddressFromPublicKey(keyPair.publicKey)

        return WalletKey(
            networkName = name,
            networkType = networkType,
            chainId = chainId,
            derivationPath = null,
            address = address,
            publicKeyHex = toHexStringNoPrefix(keyPair.publicKey)
        )
    }

    override fun getPrivateKeyFromMnemonic(mnemonic: String): String {
        val seed = MnemonicUtils.generateSeed(mnemonic, null)
        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val path = parseDerivationPath(derivationPath)
        val childKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path)
        return toHexStringNoPrefix(childKeyPair.privateKey)
    }

    override fun getPrivateKeyFromPrivateKey(privateKey: String): String {
        return cleanHexPrefix(privateKey)
    }

    // Helper to parse derivation path
    private fun parseDerivationPath(path: String): IntArray {
        return path.split("/")
            .drop(1)
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
