package com.mtd.data.repository

import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.json.GsonJsonCodec
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.keymanager.MnemonicHelper.isPrivateKeyValid
import com.mtd.core.network.tron.TronUtils.Base58.hexToBase58
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.wallet.ActiveWalletManager
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.dto.HistoryAddressDto
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.Asset
import com.mtd.domain.model.HistoryAddress
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.WalletStorageMetadata
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.core.WalletKey
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import org.web3j.utils.Numeric
import javax.inject.Inject
import timber.log.Timber
import kotlin.collections.find
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import com.mtd.domain.interfaceRepository.IAddressBookRepository
import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.interfaceRepository.IUserTokenRepository
import kotlin.collections.toMutableList

class WalletRepositoryImpl @Inject constructor(
    private val keyManager: KeyManager,
    private val secureStorage: SecureStorage,
    private val activeWalletManager: ActiveWalletManager,
    private val blockchainRegistry: BlockchainRegistry,
    private val gson: Gson,
    val dataSourceFactory: dagger.Lazy<ChainDataSourceFactory>,
    // هر دو Lazy: فقط در مسیرِ حذفِ آخرین کیف‌پول لازم می‌شوند و نباید گرافِ راه‌اندازی را
    // سنگین‌تر کنند.
    private val appCacheStore: dagger.Lazy<IAppCacheStore>,
    private val userPreferences: dagger.Lazy<IUserPreferencesRepository>,
    private val userTokenRepository: dagger.Lazy<IUserTokenRepository>,
    private val addressBook: dagger.Lazy<IAddressBookRepository>
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

    private fun TransactionFeeDetails.toDomainModel(): TransactionFeeDetails {
        return TransactionFeeDetails(
            fee = fee,
            energyUsed = energyUsed,
            bandwidthUsed = bandwidthUsed,
            energyFee = energyFee,
            networkFee = networkFee
        )
    }



    private fun getWalletsMetadata(): List<WalletStorageMetadata> {
        val json = secureStorage.getDecrypted(WALLETS_METADATA_KEY)
        return if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            GsonJsonCodec.decodeList<WalletStorageMetadata>(json, gson)
        }
    }

    private fun saveWalletsMetadata(list: List<WalletStorageMetadata>) {
        val json = GsonJsonCodec.encode(list, gson)
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

            // KAN-18: erase legacy keys completely (no residual encrypted blanks).
            secureStorage.remove(LEGACY_MNEMONIC_KEY)
            secureStorage.remove(LEGACY_PRIVATE_KEY)
        }
    }

    override suspend fun createNewWallet(
        name: String,
        color: Int,
        id: String?,
        isManualBackedUp: Boolean,
        isCloudBackedUp: Boolean
    ): ResultResponse<Wallet> {
        return safeApiCall {
            val walletId = id ?: java.util.UUID.randomUUID().toString()
            val mnemonic = MnemonicHelper.generateMnemonic()

            // ذخیره سکرت
            secureStorage.putEncrypted(getSecretKey(walletId), mnemonic)

            // بروزرسانی لیست
            val metadata = getWalletsMetadata().toMutableList()
            metadata.add(
                WalletStorageMetadata(
                    walletId,
                    name,
                    color,
                    true,
                    isManualBackedUp,
                    isCloudBackedUp
                )
            )
            saveWalletsMetadata(metadata)

            // تنظیم به عنوان ولت فعال
            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
            val wallet = Wallet(
                id = walletId,
                hasMnemonic = true,
                keys = keys,
                name = name,
                color = color,
                isManualBackedUp = isManualBackedUp,
                isCloudBackedUp = isCloudBackedUp
            )
            activeWalletManager.unlockWallet(wallet, mnemonic)
            wallet
        }
    }

    override suspend fun importWalletFromMnemonic(
        mnemonic: String,
        name: String,
        color: Int,
        id: String?,
        isManualBackedUp: Boolean,
        isCloudBackedUp: Boolean
    ): ResultResponse<Wallet> {
        return safeApiCall {
            if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                throw IllegalArgumentException("Invalid mnemonic phrase.")
            }
            val walletId = id ?: java.util.UUID.randomUUID().toString()
            secureStorage.putEncrypted(getSecretKey(walletId), mnemonic)

            val metadata = getWalletsMetadata().toMutableList()
            metadata.add(
                WalletStorageMetadata(
                    walletId,
                    name,
                    color,
                    true,
                    isManualBackedUp,
                    isCloudBackedUp
                )
            )
            saveWalletsMetadata(metadata)

            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
            val wallet = Wallet(
                id = walletId,
                hasMnemonic = true,
                keys = keys,
                name = name,
                color = color,
                isManualBackedUp = isManualBackedUp,
                isCloudBackedUp = isCloudBackedUp
            )
            activeWalletManager.unlockWallet(wallet, mnemonic)
            wallet
        }
    }

    override suspend fun importWalletFromPrivateKey(
        privateKey: String,
        name: String,
        color: Int,
        id: String?,
        isManualBackedUp: Boolean,
        isCloudBackedUp: Boolean
    ): ResultResponse<Wallet> {
        return safeApiCall {
            if (!isPrivateKeyValid(privateKey)) {
                throw IllegalArgumentException("Invalid private key format.")
            }
            val cleanPrivateKey = Numeric.cleanHexPrefix(privateKey)
            val walletId = id ?: java.util.UUID.randomUUID().toString()
            secureStorage.putEncrypted(getSecretKey(walletId), cleanPrivateKey)

            val metadata = getWalletsMetadata().toMutableList()
            metadata.add(
                WalletStorageMetadata(
                    walletId,
                    name,
                    color,
                    false,
                    isManualBackedUp,
                    isCloudBackedUp
                )
            )
            saveWalletsMetadata(metadata)

            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            val keys = keyManager.generateWalletKeysFromPrivateKey(cleanPrivateKey)
            val wallet = Wallet(
                id = walletId,
                hasMnemonic = false,
                keys = keys,
                name = name,
                color = color,
                isManualBackedUp = isManualBackedUp,
                isCloudBackedUp = isCloudBackedUp
            )
            activeWalletManager.unlockWallet(wallet, cleanPrivateKey)
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
                Wallet(
                    id = activeMeta.id,
                    hasMnemonic = true,
                    keys = keys,
                    name = activeMeta.name,
                    color = activeMeta.color,
                    isManualBackedUp = activeMeta.isManualBackedUp,
                    isCloudBackedUp = activeMeta.isCloudBackedUp
                )
            } else {
                val keys = keyManager.generateWalletKeysFromPrivateKey(secret)
                Wallet(
                    id = activeMeta.id,
                    hasMnemonic = false,
                    keys = keys,
                    name = activeMeta.name,
                    color = activeMeta.color,
                    isManualBackedUp = activeMeta.isManualBackedUp,
                    isCloudBackedUp = activeMeta.isCloudBackedUp
                )
            }

            activeWalletManager.unlockWallet(wallet, secret)
            wallet
        }
    }

    override suspend fun getAllWallets(): ResultResponse<List<Wallet>> {
        return safeApiCall {
            migrateLegacyWalletIfNeeded()
            val metadataList = getWalletsMetadata()
            metadataList.map { meta ->
                // برای نمایش لیست، فقط متادیتا کافی است و کلمات بازیابی هرگز اینجا لود نمی‌شوند.
                Wallet(
                    id = meta.id,
                    hasMnemonic = meta.isMnemonic,
                    keys = emptyList(),
                    name = meta.name,
                    color = meta.color,
                    isManualBackedUp = meta.isManualBackedUp,
                    isCloudBackedUp = meta.isCloudBackedUp
                )
            }
        }
    }

    override suspend fun getWalletKeysForWallets(
        walletIds: List<String>
    ): ResultResponse<Map<String, List<WalletKey>>> {
        if (walletIds.isEmpty()) return ResultResponse.Success(emptyMap())

        return safeApiCall {
            // مشتق‌کردن کلید برای هر کیف‌پول سنگین است (BIP32 به ازای هر شبکه)، پس مثل
            // getBalancesForMultipleWallets کل بلاک روی IO می‌رود.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val metadataList = getWalletsMetadata()

                walletIds.distinct().mapNotNull { walletId ->
                    val metadata = metadataList.find { it.id == walletId } ?: return@mapNotNull null
                    val secret = secureStorage.getDecrypted(getSecretKey(walletId))
                        ?: return@mapNotNull null

                    val keys = if (metadata.isMnemonic) {
                        keyManager.generateWalletKeysFromMnemonic(secret)
                    } else {
                        keyManager.generateWalletKeysFromPrivateKey(secret)
                    }

                    walletId to keys
                }.toMap()
                // ⚠️ عمداً activeWalletManager.unlockWallet صدا زده نمی‌شود: این‌جا فقط آدرس خوانده
                // می‌شود و باز کردن یک کیف‌پول به‌عنوان فعال، حالتِ کلِ اپ را عوض می‌کند.
            }
        }
    }

    override suspend fun switchActiveWallet(walletId: String): ResultResponse<Unit> {
        return safeApiCall {
            val metadataList = getWalletsMetadata()
            val meta =
                metadataList.find { it.id == walletId } ?: throw Exception("Wallet not found")

            secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, walletId)

            // لود کامل ولت برای آنلاک کردن
            val secret =
                secureStorage.getDecrypted(getSecretKey(walletId)) ?: throw Exception("Secret lost")
            val wallet = if (meta.isMnemonic) {
                val keys = keyManager.generateWalletKeysFromMnemonic(secret)
                Wallet(
                    id = meta.id,
                    hasMnemonic = true,
                    keys = keys,
                    name = meta.name,
                    color = meta.color,
                    isManualBackedUp = meta.isManualBackedUp,
                    isCloudBackedUp = meta.isCloudBackedUp
                )
            } else {
                val keys = keyManager.generateWalletKeysFromPrivateKey(secret)
                Wallet(
                    id = meta.id,
                    hasMnemonic = false,
                    keys = keys,
                    name = meta.name,
                    color = meta.color,
                    isManualBackedUp = meta.isManualBackedUp,
                    isCloudBackedUp = meta.isCloudBackedUp
                )
            }
            activeWalletManager.unlockWallet(wallet, secret)
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

            val itemToRemove =
                metadata.find { it.id == walletId } ?: throw Exception("Wallet not found")
            metadata.remove(itemToRemove)
            saveWalletsMetadata(metadata)

            // KAN-18: completely erase the wallet secret from secure_prefs (no blank ciphertext
            // residue) and immediately drop any in-memory derived keys for this wallet.
            secureStorage.remove(getSecretKey(walletId))
            activeWalletManager.clearCacheForWallet(walletId)

            // If it was active, switch to another wallet
            val currentActive = secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
            if (currentActive == walletId) {
                val nextActive = metadata.firstOrNull()?.id
                if (nextActive != null) {
                    switchActiveWallet(nextActive)
                } else {
                    secureStorage.remove(ACTIVE_WALLET_ID_KEY)
                    activeWalletManager.lockWallet()
                }
            }

            // ردِ خودِ این کیف‌پول، چه آخرین باشد چه نه. کش‌های موجودی با پیشوندِ شناسه کلید
            // می‌خورند و بدونِ این، روی دیسک می‌مانند و کیفِ هم‌نامِ بعدی آن‌ها را برمی‌دارد.
            // هیچ‌کدام نباید حذف را برگردانند: کارِ اصلی انجام شده و پاک‌سازی از آن مهم‌تر نیست.
            runCatching {
                appCacheStore.get().removeByPrefix("${CachedWalletBalanceReaderImpl.CACHE_KEY_PREFIX}${walletId}_")
            }.onFailure { Timber.w(it, "Balance-cache wipe for %s failed", walletId) }
            runCatching { userTokenRepository.get().forgetWallet(walletId) }
                .onFailure { Timber.w(it, "Token-selection wipe for %s failed", walletId) }

            // آخرین کیف‌پول که رفت، هرچه مانده به کیف‌پولی اشاره می‌کند که دیگر وجود ندارد.
            // پس از این نقطه، برنامه باید مثل نصبِ تازه رفتار کند.
            if (metadata.isEmpty()) {
                runCatching { appCacheStore.get().clear() }
                    .onFailure { Timber.w(it, "Cache wipe after last-wallet removal failed") }
                runCatching { userPreferences.get().clearAll() }
                    .onFailure { Timber.w(it, "Preference wipe after last-wallet removal failed") }
                runCatching { addressBook.get().clear() }
                    .onFailure { Timber.w(it, "Address-book wipe after last-wallet removal failed") }
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
                    activeWalletManager.updateWalletMetadata(updatedWallet)
                }
                Unit
            }
        }
    }
        override suspend fun updateWalletColor(
            walletId: String,
            newColor: Int
        ): ResultResponse<Unit> {
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
                        activeWalletManager.updateWalletMetadata(updatedWallet)
                    }
                }
                Unit
            }
        }



        override suspend fun hasWallet(): Boolean {
            return getWalletsMetadata().isNotEmpty()
        }

        override suspend fun getMnemonic(walletId: String): ResultResponse<String?> {
            return safeApiCall {
                // برگرداندن سکرت چه عبارت بازیابی باشد چه کلید خصوصی
                secureStorage.getDecrypted(getSecretKey(walletId))
            }
        }

        override suspend fun deleteWallet() {
            val activeId = getActiveWalletId() ?: return
            val metadata = getWalletsMetadata().toMutableList()
            metadata.removeAll { it.id == activeId }
            saveWalletsMetadata(metadata)
            // KAN-18: erase rather than blank, and clear the in-memory cache for the removed wallet.
            secureStorage.remove(getSecretKey(activeId))
            activeWalletManager.clearCacheForWallet(activeId)
            val nextActive = metadata.firstOrNull()?.id
            if (nextActive != null) {
                secureStorage.putEncrypted(ACTIVE_WALLET_ID_KEY, nextActive)
            } else {
                secureStorage.remove(ACTIVE_WALLET_ID_KEY)
                activeWalletManager.lockWallet()
            }
        }

        override suspend fun sendTransaction(
            params: TransactionParams
        ): ResultResponse<String> {
            return safeApiCall {
                val chainId = when (params) {
                    is TransactionParams.Evm -> blockchainRegistry.getNetworkById(params.networkId)?.chainId
                    is TransactionParams.Utxo -> params.chainId
                    is TransactionParams.Tvm -> blockchainRegistry.getNetworkById(params.networkId)?.chainId
                    is TransactionParams.TvmPrepared -> blockchainRegistry.getNetworkById(params.networkId)?.chainId
                }
                    ?: throw IllegalStateException("Network chain id is missing for transaction params")
                val privateKey =
                    keyManager.getCredentialsForChain(chainId)?.ecKeyPair?.privateKey?.toString(
                        16
                    )
                        ?: throw IllegalStateException("Wallet is locked or key not found for this chain.")
                val dataSource = dataSourceFactory.get().create(chainId)
                val result = dataSource.sendTransaction(params, privateKey)
                return@safeApiCall when (result) {
                    is ResultResponse.Success -> result.data
                    is ResultResponse.Error -> throw Exception(result.exception)
                }
            }
        }

        override suspend fun getAssets(
            networkId: String
        ): ResultResponse<List<Asset>> {
            return safeApiCall {
                val network = blockchainRegistry.getNetworkById(networkId)
                    ?: throw IllegalStateException("Network $networkId not found")
                val userAddress = activeWalletManager.getAddressForNetwork(network.chainId!!)
                    ?: throw IllegalStateException("Active wallet or address not found for network.")
                val dataSource = dataSourceFactory.get().create(network)
                val result = dataSource.getBalanceAssets(userAddress)
                if (result is ResultResponse.Success) result.data else throw Exception("Failed to fetch assets")
            }
        }

        override suspend fun getTransactionHistory(
            networkId: String,
            userAddress: String
        ): ResultResponse<List<TransactionRecord>> {
            return safeApiCall {
                val network = blockchainRegistry.getNetworkById(networkId)
                    ?: throw IllegalStateException("Network $networkId not found")
                val dataSource = dataSourceFactory.get().create(network)
                // خطای واقعیِ منبع را رد کن؛ یک Exception عمومیِ جدید علت را پاک می‌کرد و
                // ErrorManager فقط «Failed to fetch history» را می‌دید.
                when (val result = dataSource.getTransactionHistory(userAddress)) {
                    is ResultResponse.Success -> result.data
                    is ResultResponse.Error -> throw result.exception
                }
            }
        }

        override suspend fun getUnifiedHistory(
            addresses: List<HistoryAddress>,
            cursor: String?,
            limit: Int?
        ): ResultResponse<HistoryPage> {
            if (addresses.isEmpty()) {
                return ResultResponse.Success(HistoryPage(emptyList(), nextCursor = null, hasMore = false))
            }
            return safeApiCall {
                // PROXY is the source of truth — do NOT pre-filter on local config/bundle state. The
                // carrier only exists to build the data source (whose transport matches the active
                // mode); ProxyChainDataSource.getHistory ignores its own network and posts every
                // (networkId,address) pair. Fall back to ANY registered network so a locally-unknown
                // networkId still reaches the server, which authoritatively answers 404/UNSUPPORTED.
                val carrier = addresses.firstNotNullOfOrNull { blockchainRegistry.getNetworkById(it.networkId) }
                    ?: blockchainRegistry.getAllNetworks().firstOrNull()
                    ?: throw ApiException(
                        ApiError.NetworkNotFound,
                        reasonFa = "No registered networks available to route the history request"
                    )
                val dataSource = dataSourceFactory.get().create(carrier)
                val dtoAddresses = addresses.map { HistoryAddressDto(it.networkId, it.address) }
                when (val result = dataSource.getHistory(dtoAddresses, cursor, limit)) {
                    is ResultResponse.Success -> result.data
                    is ResultResponse.Error -> throw result.exception
                }
            }
        }

        override suspend fun getTransactionFeeDetails(
            networkId: String,
            txId: String
        ): ResultResponse<TransactionFeeDetails> {
            return safeApiCall {
                val network = blockchainRegistry.getNetworkById(networkId)
                    ?: throw IllegalStateException("Network $networkId not found")
                val dataSource = dataSourceFactory.get().create(network)
                when (val result = dataSource.getTransactionFeeDetails(txId)) {
                    is ResultResponse.Success -> result.data.toDomainModel()
                    is ResultResponse.Error -> throw result.exception
                }
            }
        }


        /**
         * آدرسِ کیف‌پولِ فعال روی یک شبکه.
         *
         * اول از کلیدهای ذخیره‌شده خوانده می‌شود (مسیرِ سریع و رفتارِ قبلی، بدون تماس با
         * SecureStorage). اگر نبود، آدرس همان‌جا از روی سکرت مشتق می‌شود: `wallet.keys` لحظهٔ ساختِ
         * کیف‌پول تولید شده و برای شبکه‌هایی که بعداً از باندلِ امضاشده آمده‌اند ورودی ندارد. بدون
         * این fallback، `null` برمی‌گشت و ارسال/تبدیل روی آن شبکه اصلاً شروع نمی‌شد —
         * [com.mtd.data.repository.transfer.UnifiedTransferCoordinator] و هر دو هماهنگ‌کنندهٔ
         * gasless از همین‌جا آدرس می‌گیرند.
         *
         * مشتق‌کردن با `networkId` انجام می‌شود نه `NetworkType`: دوج‌کوین و لایت‌کوین هر دو `UTXO`
         * هستند و آدرسشان یکی نیست.
         */
        override suspend fun getActiveAddressForNetwork(networkId: String): String? {
            val activeWallet = activeWalletManager.activeWallet.value ?: return null
            val networkInfo = blockchainRegistry.getNetworkById(networkId) ?: return null

            activeWallet.keys.find { it.networkId == networkInfo.id }?.address?.let { return it }

            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val metadata = getWalletsMetadata().find { it.id == activeWallet.id }
                        ?: return@runCatching null
                    val secret = secureStorage.getDecrypted(getSecretKey(activeWallet.id))
                        ?: return@runCatching null

                    if (metadata.isMnemonic) {
                        keyManager.generateKeyForNetworkId(secret, networkInfo.id)?.address
                    } else {
                        keyManager.generateWalletKeysFromPrivateKey(secret)
                            .find { it.networkId == networkInfo.id }?.address
                    }
                }.onFailure {
                    Timber.w(it, "Could not derive an address for %s", networkInfo.id)
                }.getOrNull()
            }
        }

        override suspend fun getBalancesForMultipleWallets(
            networkId: String,
            walletIds: List<String>
        ): ResultResponse<Map<String, List<Asset>>> {
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val chainConfig = blockchainRegistry.getNetworkById(networkId)
                        ?: return@withContext ResultResponse.Error(Exception("Network $networkId not found"))
                    val dataSource = dataSourceFactory.get().create(chainConfig)

                    // Map<WalletId, Address>
                    val walletAddresses = mutableMapOf<String, String>()
                    val metadataList = getWalletsMetadata()

                    // استخراج آدرس‌ها به صورت امن (عملیات سنگین)
                    walletIds.forEach { walletId ->
                        val metadata = metadataList.find { it.id == walletId } ?: return@forEach
                        val secret =
                            secureStorage.getDecrypted(getSecretKey(walletId)) ?: return@forEach

                        val address = if (metadata.isMnemonic) {
                            // تولید آدرس از کلمات بازیابی برای شبکه خاص
                            keyManager.generateKeyForNetworkId(secret, chainConfig.id)?.address
                        } else {
                            // تولید از کلید خصوصی
                            keyManager.generateWalletKeysFromPrivateKey(secret)
                                .find { it.networkId == chainConfig.id }?.address
                        }

                        if (address != null) {
                            walletAddresses[walletId] = address
                        }
                    }

                    if (walletAddresses.isEmpty()) return@withContext ResultResponse.Success(
                        emptyMap()
                    )

                    // ارسال درخواست به شبکه (قبلاً در IO بود، ولی حالا کل بلاک در IO است)
                    val result =
                        dataSource.getBalancesForMultipleAddresses(walletAddresses.values.toList())

                    if (result is ResultResponse.Success) {
                        // تبدیل <Address, Assets> به <WalletId, Assets>

                        // نقشه معکوس: آدرس -> لیست کیف پول‌ها
                        val addressToWalletIds = mutableMapOf<String, MutableList<String>>()
                        walletAddresses.forEach { (walletId, address) ->
                            val normalizedAddress = normalizeAddress(address, chainConfig.networkType)
                            addressToWalletIds.getOrPut(normalizedAddress) { mutableListOf() }
                                .add(walletId)
                        }

                        val finalMap = mutableMapOf<String, List<Asset>>()

                        result.data.forEach { (address, assets) ->
                            val normalizedAddress = normalizeAddress(address, chainConfig.networkType)
                            addressToWalletIds[normalizedAddress]?.forEach { walletId ->
                                finalMap[walletId] = assets
                            }
                        }

                        ResultResponse.Success(finalMap)
                    } else {
                        ResultResponse.Error((result as ResultResponse.Error).exception)
                    }
                } catch (e: Exception) {
                    ResultResponse.Error(e)
                }
            }
        }

        override suspend fun updateBackupStatus(
            walletId: String,
            manual: Boolean?,
            cloud: Boolean?
        ): ResultResponse<Unit> {
            return safeApiCall {
                val metadata = getWalletsMetadata().toMutableList()
                val index = metadata.indexOfFirst { it.id == walletId }
                if (index == -1) throw Exception("Wallet not found")

                val current = metadata[index]
                metadata[index] = current.copy(
                    isManualBackedUp = manual ?: current.isManualBackedUp,
                    isCloudBackedUp = cloud ?: current.isCloudBackedUp
                )
                saveWalletsMetadata(metadata)

                // اگر ولت فعال است، آن را در منیجر هم بروز کن
                val currentActive = secureStorage.getDecrypted(ACTIVE_WALLET_ID_KEY)
                if (currentActive == walletId) {
                    activeWalletManager.activeWallet.value?.let { wallet ->
                        val updatedWallet = wallet.copy(
                            isManualBackedUp = manual ?: wallet.isManualBackedUp,
                            isCloudBackedUp = cloud ?: wallet.isCloudBackedUp
                        )
                        activeWalletManager.updateWalletMetadata(updatedWallet)
                    }
                }
                Unit
            }
        }

        /**
         * TASK-53 — روی خانوادهٔ شبکه ([NetworkType]) کلید می‌خورد، نه روی نام تک‌تک شبکه‌ها.
         * لیستِ قبلیِ EVM فقط {BSC, ETHEREUM, SEPOLIA, BSCTESTNET} بود، یعنی آدرس‌های
         * Base/Arbitrum اصلاً lowercase نمی‌شدند و در تطبیق آدرس→کیف‌پول از قلم می‌افتادند.
         */
        fun normalizeAddress(address: String, networkType: NetworkType): String {
            return when (networkType) {
                NetworkType.EVM -> address.lowercase()
                NetworkType.TVM -> {
                    if (address.startsWith("0x") || address.startsWith("41")) {
                        hexToBase58(address)
                    } else {
                        address
                    }
                }

                else -> address
            }
        }
    }


