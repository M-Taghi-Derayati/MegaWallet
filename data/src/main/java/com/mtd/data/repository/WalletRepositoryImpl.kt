package com.mtd.data.repository

import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.RemoteDataSource
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.domain.model.Asset
import com.mtd.domain.model.Wallet
import javax.inject.Inject
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.wallet.ActiveWalletManager
import java.math.BigInteger

class   WalletRepositoryImpl @Inject constructor(
    private val keyManager: KeyManager,
    private val secureStorage: SecureStorage,
    private val activeWalletManager: ActiveWalletManager,
    private val blockchainRegistry: BlockchainRegistry,
    private val dataSourceFactory: ChainDataSourceFactory
) : IWalletRepository {
    private companion object {
        const val MNEMONIC_STORAGE_KEY = "wallet_mnemonic_phrase"
        const val PRIVATE_KEY_STORAGE_KEY =
            "wallet_private_key" // برای حالتی که با کلید خصوصی وارد می‌شود
    }

    override suspend fun createNewWallet(): ResultResponse<Wallet> {
        return try {
            val mnemonic = MnemonicHelper.generateMnemonic()
            // قبل از هر کاری، اطلاعات قبلی را پاک کن
            deleteWallet()
            // کلمات بازیابی را ذخیره کن
            secureStorage.putEncrypted(MNEMONIC_STORAGE_KEY, mnemonic)

            val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
            val wallet = Wallet(mnemonic = mnemonic, keys = keys)
            activeWalletManager.setActiveWallet(wallet)
            ResultResponse.Success(wallet)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun importWalletFromMnemonic(mnemonic: String): ResultResponse<Wallet> {
        return try {
            if (!MnemonicHelper.isValidMnemonic(mnemonic)) {
                throw IllegalArgumentException("Invalid mnemonic phrase.")
            }
            deleteWallet()
            secureStorage.putEncrypted(MNEMONIC_STORAGE_KEY, mnemonic)

            val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
            val wallet = Wallet(mnemonic = mnemonic, keys = keys)
            activeWalletManager.setActiveWallet(wallet)
            ResultResponse.Success(wallet)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun importWalletFromPrivateKey(privateKey: String): ResultResponse<Wallet> {
        return try {
            // یک اعتبارسنجی ساده برای کلید خصوصی (64 کاراکتر هگز)
            if (privateKey.length != 64 || !privateKey.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                throw IllegalArgumentException("Invalid private key format.")
            }
            deleteWallet()
            secureStorage.putEncrypted(PRIVATE_KEY_STORAGE_KEY, privateKey)

            val keys = keyManager.generateWalletKeysFromPrivateKey(privateKey)
            // در این حالت mnemonic نداریم
            val wallet = Wallet(mnemonic = null, keys = keys)
            ResultResponse.Success(wallet)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun loadExistingWallet(): ResultResponse<Wallet?> {
        return try {
            val mnemonic = secureStorage.getDecrypted(MNEMONIC_STORAGE_KEY)
            val privateKey = secureStorage.getDecrypted(PRIVATE_KEY_STORAGE_KEY)

            val wallet = when {
                mnemonic != null -> {
                    val keys = keyManager.generateWalletKeysFromMnemonic(mnemonic)
                    Wallet(mnemonic = mnemonic, keys = keys)
                }

                privateKey != null -> {
                    val keys = keyManager.generateWalletKeysFromPrivateKey(privateKey)
                    Wallet(mnemonic = null, keys = keys)
                }

                else -> null
            }
            wallet.let {
                if (it!=null){
                    activeWalletManager.setActiveWallet(it)
                    ResultResponse.Success(wallet)
                }else{
                    ResultResponse.Error(Exception( "Not Wallet Found"))
                }

            }

        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun hasWallet(): Boolean {
        // اگر هر کدام از کلیدهای ذخیره‌سازی وجود داشته باشد، یعنی کیف پول داریم
        return secureStorage.getDecrypted(MNEMONIC_STORAGE_KEY) != null ||
                secureStorage.getDecrypted(PRIVATE_KEY_STORAGE_KEY) != null
    }

    override suspend fun getSavedMnemonic(): ResultResponse<String?> {
        return try {
            val mnemonic = secureStorage.getDecrypted(MNEMONIC_STORAGE_KEY)
            ResultResponse.Success(mnemonic)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun deleteWallet() {
        // هر دو کلید ممکن را پاک می‌کنیم تا حالت برنامه تمیز بماند
        secureStorage.putEncrypted(MNEMONIC_STORAGE_KEY, "")
        secureStorage.putEncrypted(PRIVATE_KEY_STORAGE_KEY, "")
        // یا اگر متد remove دارید:
        // secureStorage.remove(MNEMONIC_STORAGE_KEY)
        // secureStorage.remove(PRIVATE_KEY_STORAGE_KEY)
    }

    override suspend fun sendTransaction(
        params: TransactionParams
    ): ResultResponse<String> {
        return try {
            val chainId = when(params) {
                is TransactionParams.Evm -> blockchainRegistry.getNetworkByName(params.networkName)?.chainId!!
                is TransactionParams.Utxo -> params.chainId
            }

            // ۱. پیدا کردن شبکه و کلید خصوصی
            val network = blockchainRegistry.getNetworkByChainId(chainId)
                ?: throw IllegalStateException("Network not found")

            val privateKey = keyManager.getCredentialsForChain(network.chainId!!)?.ecKeyPair?.privateKey?.toString(16)
                ?: throw IllegalStateException("Wallet is locked or key not found for this chain.")

            // ۲. ساخت استراتژی مناسب از فکتوری
            val dataSource = dataSourceFactory.create(chainId)

            // ۳. واگذاری کامل کار به استراتژی (DataSource)
            dataSource.sendTransaction(params, privateKey)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun getAssets(
        networkName: NetworkName
    ): ResultResponse<List<Asset>> {
        return try {
            // ۱. آدرس کاربر را به راحتی از ActiveWalletManager بگیر

            val chainId=blockchainRegistry.getNetworkByName(networkName)
            val userAddress = activeWalletManager.getAddressForNetwork(chainId?.chainId!!)
                ?: throw IllegalStateException("Active wallet or address not found for network.")

            // ۲. از کارخانه، استراتژی مناسب را درخواست کن
            val dataSource = dataSourceFactory.create(chainId.chainId!!)

            // ۳. موجودی را با آدرس به دست آمده، دریافت کن

            dataSource.getBalanceEVM(userAddress)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun getTransactionHistory(
        networkName: NetworkName,
        userAddress: String
    ): ResultResponse<List<TransactionRecord>> {
        return try {
            val chainId=blockchainRegistry.getNetworkByName(networkName)
            val dataSource = dataSourceFactory.create(chainId?.chainId!!)
            dataSource.getTransactionHistory(userAddress)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }


}







