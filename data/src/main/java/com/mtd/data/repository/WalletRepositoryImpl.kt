package com.mtd.data.repository

import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.keymanager.MnemonicHelper.isPrivateKeyValid
import com.mtd.core.model.NetworkName
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.Wallet
import com.mtd.domain.wallet.ActiveWalletManager
import org.web3j.utils.Numeric
import javax.inject.Inject

class   WalletRepositoryImpl @Inject constructor(
    private val keyManager: KeyManager,
    private val secureStorage: SecureStorage,
    private val activeWalletManager: ActiveWalletManager,
    private val blockchainRegistry: BlockchainRegistry,
     var dataSourceFactory: ChainDataSourceFactory
) : IWalletRepository {
    private companion object {
        const val WALLETS_METADATA_KEY = "wallets_metadata_list"
        const val ACTIVE_WALLET_ID_KEY = "active_wallet_id"
        
        // Legacy keys for migration
        const val LEGACY_MNEMONIC_KEY = "wallet_mnemonic_phrase"
        const val LEGACY_PRIVATE_KEY = "wallet_private_key"
        const val LEGACY_NAME_KEY = "wallet_name"
        const val LEGACY_COLOR_KEY = "wallet_color"
        
        // Keys format for individual secrets: "wallet_secret_{id}"
        private fun getSecretKey(id: String) = "wallet_secret_$id"
    }

    private data class WalletStorageMetadata(
        val id: String,
        val name: String,
        val color: Int,
        val isMnemonic: Boolean
    )

    private fun getWalletsMetadata(): List<WalletStorageMetadata> {
        val json = secureStorage.getDecrypted(WALLETS_METADATA_KEY)
        return if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            val type = object : com.google.gson.reflect.TypeToken<List<WalletStorageMetadata>>() {}.type
            com.google.gson.Gson().fromJson(json, type)
        }
    }

    private fun saveWalletsMetadata(list: List<WalletStorageMetadata>) {
        val json = com.google.gson.Gson().toJson(list)
        secureStorage.putEncrypted(WALLETS_METADATA_KEY, json)
    }

    private fun migrateLegacyWalletIfNeeded() {
        val legacyMnemonic = secureStorage.getDecrypted(LEGACY_MNEMONIC_KEY)
        val legacyPrivateKey = secureStorage.getDecrypted(LEGACY_PRIVATE_KEY)
        
        if ((!legacyMnemonic.isNullOrEmpty() || !legacyPrivateKey.isNullOrEmpty()) && getWalletsMetadata().isEmpty()) {
            val name = secureStorage.getDecrypted(LEGACY_NAME_KEY) ?: "Main Wallet"
            val colorStr = secureStorage.getDecrypted(LEGACY_COLOR_KEY)
            val color = colorStr?.toIntOrNull() ?: -13908642
            val isMnemonic = !legacyMnemonic.isNullOrEmpty()
            val secret = legacyMnemonic ?: legacyPrivateKey ?: return
            
            val id = java.util.UUID.randomUUID().toString()
            
            // Migrate to new structure
            secureStorage.putEncrypted(getSecretKey(id), secret)
            saveWalletsMetadata(listOf(WalletStorageMetadata(id, name, color, isMnemonic)))
            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, id)
            
            // Clear legacy keys
            secureStorage.putEncrypted(LEGACY_MNEMONIC_KEY, "")
            secureStorage.putEncrypted(LEGACY_PRIVATE_KEY, "")
        }
    }

    override suspend fun createNewWallet(name: String, color: Int, id: String?): ResultResponse<Wallet> {
        return safeApiCall {
            val walletId = id ?: java.util.UUID.randomUUID().toString()
            val mnemonic = MnemonicHelper.generateMnemonic()
            
            // ذخیره سکرت
            secureStorage.putEncrypted(getSecretKey(walletId), mnemonic)
            
            // بروزرسانی لیست
            val metadata = getWalletsMetadata().toMutableList()
            metadata.add(WalletStorageMetadata(walletId, name, color, true))
            saveWalletsMetadata(metadata)
            
            // تنظیم به عنوان ولت فعال
            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
            val wallet = Wallet(id = walletId, mnemonic = mnemonic, keys = keys, name = name, color = color)
            activeWalletManager.unlockWallet(wallet)
            wallet
        }
    }

    override suspend fun importWalletFromMnemonic(mnemonic: String, name: String, color: Int, id: String?): ResultResponse<Wallet> {
        return safeApiCall {
            if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                throw IllegalArgumentException("Invalid mnemonic phrase.")
            }
            val walletId = id ?: java.util.UUID.randomUUID().toString()
            secureStorage.putEncrypted(getSecretKey(walletId), mnemonic)

            val metadata = getWalletsMetadata().toMutableList()
            metadata.add(WalletStorageMetadata(walletId, name, color, true))
            saveWalletsMetadata(metadata)
            
            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
            val wallet = Wallet(id = walletId, mnemonic = mnemonic, keys = keys, name = name, color = color)
            activeWalletManager.unlockWallet(wallet)
            wallet
        }
    }

    override suspend fun importWalletFromPrivateKey(privateKey: String, name: String, color: Int, id: String?): ResultResponse<Wallet> {
        return safeApiCall {
            if (!isPrivateKeyValid(privateKey)) {
                throw IllegalArgumentException("Invalid private key format.")
            }
            val cleanPrivateKey = Numeric.cleanHexPrefix(privateKey)
            val walletId = id ?: java.util.UUID.randomUUID().toString()
            secureStorage.putEncrypted(getSecretKey(walletId), cleanPrivateKey)

            val metadata = getWalletsMetadata().toMutableList()
            metadata.add(WalletStorageMetadata(walletId, name, color, false))
            saveWalletsMetadata(metadata)
            
            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            val keys = keyManager.generateWalletKeysFromPrivateKey(cleanPrivateKey)
            val wallet = Wallet(id = walletId, mnemonic = null, keys = keys, name = name, color = color)
            activeWalletManager.unlockWallet(wallet)
            wallet
        }
    }

    override suspend fun loadExistingWallet(): ResultResponse<Wallet?> {
        return safeApiCall {
            migrateLegacyWalletIfNeeded()
            val metadataList = getWalletsMetadata()
            if (metadataList.isEmpty()) throw Exception("No Wallet Found")

            val activeId = secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
            val activeMeta = metadataList.find { it.id == activeId } ?: metadataList.first()
            
            val secret = secureStorage.getDecrypted(getSecretKey(activeMeta.id))
                ?: throw Exception("Secret not found for wallet ${activeMeta.id}")

            val wallet = if (activeMeta.isMnemonic) {
                val keys = keyManager.generateWalletKeysFromMnemonic(secret)
                Wallet(id = activeMeta.id, mnemonic = secret, keys = keys, name = activeMeta.name, color = activeMeta.color)
            } else {
                val keys = keyManager.generateWalletKeysFromPrivateKey(secret)
                Wallet(id = activeMeta.id, mnemonic = null, keys = keys, name = activeMeta.name, color = activeMeta.color)
            }
            
            activeWalletManager.unlockWallet(wallet)
            wallet
        }
    }

    override suspend fun getAllWallets(): ResultResponse<List<Wallet>> {
        return safeApiCall {
            migrateLegacyWalletIfNeeded()
            val metadataList = getWalletsMetadata()
            metadataList.map { meta ->
                // ما اینجا کلیدها را فعلا جنریت نمی‌کنیم تا سنگین نشود، فقط متادیتا و آبجکت Wallet با لیست کلید خالی (یا لود شده در صورت نیاز)
                // برای نمایش لیست، فقط نام و رنگ و ایدی کافی است.
                Wallet(id = meta.id, mnemonic = null, keys = emptyList(), name = meta.name, color = meta.color)
            }
        }
    }

    override suspend fun switchActiveWallet(walletId: String): ResultResponse<Unit> {
        return safeApiCall {
            val metadataList = getWalletsMetadata()
            val meta = metadataList.find { it.id == walletId } ?: throw Exception("Wallet not found")
            
            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)
            
            // لود کامل ولت برای آنلاک کردن
            val secret = secureStorage.getDecrypted(getSecretKey(walletId)) ?: throw Exception("Secret lost")
            val wallet = if (meta.isMnemonic) {
                val keys = keyManager.generateWalletKeysFromMnemonic(secret)
                Wallet(id = meta.id, mnemonic = secret, keys = keys, name = meta.name, color = meta.color)
            } else {
                val keys = keyManager.generateWalletKeysFromPrivateKey(secret)
                Wallet(id = meta.id, mnemonic = null, keys = keys, name = meta.name, color = meta.color)
            }
            activeWalletManager.unlockWallet(wallet)
            Unit
        }
    }

    override suspend fun getActiveWalletId(): String? {
        migrateLegacyWalletIfNeeded()
        return secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
    }

    override suspend fun deleteWallet(walletId: String): ResultResponse<Unit> {
        return safeApiCall {
            val metadata = getWalletsMetadata().toMutableList()
            if (metadata.size <= 1) throw Exception("Cannot delete the last wallet")
            
            val itemToRemove = metadata.find { it.id == walletId } ?: throw Exception("Wallet not found")
            metadata.remove(itemToRemove)
            saveWalletsMetadata(metadata)
            
            // Clear secret
            secureStorage.putEncrypted(getSecretKey(walletId), "")
            
            // If it was active, switch to another wallet
            val currentActive = secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
            if (currentActive == walletId) {
                val nextActive = metadata.firstOrNull()?.id
                if (nextActive != null) {
                    switchActiveWallet(nextActive)
                } else {
                    secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, "")
                    activeWalletManager.lockWallet()
                }
            }
            Unit
        }
    }

    override suspend fun updateWalletName(walletId: String, newName: String): ResultResponse<Unit> {
        return safeApiCall {
            val metadata = getWalletsMetadata().toMutableList()
            val index = metadata.indexOfFirst { it.id == walletId }
            if (index == -1) throw Exception("Wallet not found")
            
            metadata[index] = metadata[index].copy(name = newName)
            saveWalletsMetadata(metadata)
            
            // If this is the active wallet, update it in ActiveWalletManager
            val currentActive = secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
            if (currentActive == walletId) {
                activeWalletManager.activeWallet.value?.let { wallet ->
                    val updatedWallet = wallet.copy(name = newName)
                    activeWalletManager.unlockWallet(updatedWallet)
                }
            }
            Unit
        }
    }

    override suspend fun updateWalletColor(walletId: String, newColor: Int): ResultResponse<Unit> {
        return safeApiCall {
            val metadata = getWalletsMetadata().toMutableList()
            val index = metadata.indexOfFirst { it.id == walletId }
            if (index == -1) throw Exception("Wallet not found")
            
            metadata[index] = metadata[index].copy(color = newColor)
            saveWalletsMetadata(metadata)
            
            // If this is the active wallet, update it in ActiveWalletManager
            val currentActive = secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
            if (currentActive == walletId) {
                activeWalletManager.activeWallet.value?.let { wallet ->
                    val updatedWallet = wallet.copy(color = newColor)
                    activeWalletManager.unlockWallet(updatedWallet)
                }
            }
            Unit
        }
    }

    override suspend fun getActiveAddressForNetwork(networkId: String): String? {
        val walletResult = loadExistingWallet()
        if (walletResult is ResultResponse.Success && walletResult.data != null) {
            val wallet = walletResult.data
            val networkInfo = blockchainRegistry.getNetworkById(networkId)
            if (networkInfo != null) {
                return wallet?.keys?.find { it.networkName == networkInfo.name }?.address
            }
        }
       return null
    }

    override suspend fun hasWallet(): Boolean {
        return getWalletsMetadata().isNotEmpty()
    }

    override suspend fun getSavedMnemonic(): ResultResponse<String?> {
        return safeApiCall {
            val activeId = getActiveWalletId() ?: return@safeApiCall null
            val metadata = getWalletsMetadata().find { it.id == activeId }
            if (metadata?.isMnemonic == true) {
                secureStorage.getDecrypted(getSecretKey(activeId))
            } else null
        }
    }

    override suspend fun deleteWallet() {
        val activeId = getActiveWalletId() ?: return
        val metadata = getWalletsMetadata().toMutableList()
        metadata.removeAll { it.id == activeId }
        saveWalletsMetadata(metadata)
        secureStorage.putEncrypted(getSecretKey(activeId), "")
        secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, metadata.firstOrNull()?.id ?: "")
    }

    override suspend fun sendTransaction(
        params: TransactionParams
    ): ResultResponse<String> {
        return safeApiCall {
            val chainId = when(params) {
                is TransactionParams.Evm -> blockchainRegistry.getNetworkByName(params.networkName)?.chainId!!
                is TransactionParams.Utxo -> params.chainId
            }
            val network = blockchainRegistry.getNetworkByChainId(chainId)
                ?: throw IllegalStateException("Network not found")
            val privateKey = keyManager.getCredentialsForChain(network.chainId!!)?.ecKeyPair?.privateKey?.toString(16)
                ?: throw IllegalStateException("Wallet is locked or key not found for this chain.")
            val dataSource = dataSourceFactory.create(chainId)
            val result = dataSource.sendTransaction(params, privateKey)
            if (result is ResultResponse.Success) result.data else throw Exception("Transaction failed")
        }
    }

    override suspend fun getAssets(
        networkName: NetworkName
    ): ResultResponse<List<Asset>> {
        return safeApiCall {
            val chainId=blockchainRegistry.getNetworkByName(networkName)
            val userAddress = activeWalletManager.getAddressForNetwork(chainId?.chainId!!)
                ?: throw IllegalStateException("Active wallet or address not found for network.")
            val dataSource = dataSourceFactory.create(chainId.chainId!!)
            val result = dataSource.getBalanceEVM(userAddress)
            if (result is ResultResponse.Success) result.data else throw Exception("Failed to fetch assets")
        }
    }

    override suspend fun getTransactionHistory(
        networkName: NetworkName,
        userAddress: String
    ): ResultResponse<List<TransactionRecord>> {
        return safeApiCall {
            val chainId=blockchainRegistry.getNetworkByName(networkName)
            val dataSource = dataSourceFactory.create(chainId?.chainId!!)
            val result = dataSource.getTransactionHistory(userAddress)
            if (result is ResultResponse.Success) result.data else throw Exception("Failed to fetch history")
        }
    }


}







