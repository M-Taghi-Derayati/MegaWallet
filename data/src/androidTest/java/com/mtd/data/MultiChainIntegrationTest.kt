package com.mtd.data


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkModule.Companion.httpLoggingInterceptorProvider
import com.mtd.data.di.NetworkModule.Companion.provideGson
import com.mtd.data.di.NetworkModule.Companion.provideOkHttpClient
import com.mtd.data.di.NetworkModule.Companion.provideRetrofitBuilder
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.ResultResponse
import com.mtd.data.repository.IWalletRepository
import com.mtd.data.repository.TransactionParams
import com.mtd.domain.wallet.ActiveWalletManager
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.math.BigInteger
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MultiChainIntegrationTest {

    private lateinit var walletRepository: IWalletRepository

    private val TEST_MNEMONIC =
        "pattern hello embark avocado another banner chest vital ill exercise material pistol"

    private val USER_ADDRESS="0x17b51d4928668B50065C589bAfBC32736f196216"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // --- ساخت وابستگی‌ها به صورت دستی برای محیط تست ---

        // ۱. ساخت وابستگی‌های پایه از :core
        val secureStorage = SecureStorage(context)
        val blockchainRegistry = BlockchainRegistry().apply { loadNetworksFromAssets(context) }
        val keyManager = KeyManager(blockchainRegistry)

        // ۲. ساخت وابستگی‌های شبکه (منطق مشابه DataModule)
        val okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider())
        val gson = Gson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient,gson)

        // ۳. ساخت وابستگی‌های اصلی :data
        val dataSourceFactory = ChainDataSourceFactory(blockchainRegistry, retrofitBuilder, okHttpClient)
        val activeWalletManager = ActiveWalletManager()

        // ۴. ساخت کلاس نهایی تحت تست (WalletRepositoryImpl)
        walletRepository = WalletRepositoryImpl(
            activeWalletManager = activeWalletManager,
            keyManager = keyManager,
            secureStorage = secureStorage,
            blockchainRegistry = blockchainRegistry,
            dataSourceFactory = dataSourceFactory
        )

        // ۵. آماده‌سازی وضعیت اولیه برای هر تست
        // این کار تضمین می‌کند که ActiveWalletManager و KeyManager Cache هر دو مقداردهی شده‌اند.
        runTest {
            val result = walletRepository.importWalletFromMnemonic(TEST_MNEMONIC)
            // یک Assert کوچک برای اطمینان از صحت مقداردهی اولیه
            assertTrue("Setup failed: Wallet import was not successful.", result is ResultResponse.Success)
            keyManager.loadKeysIntoCache((result as ResultResponse.Success).data.keys)
        }
    }

    @Test
    fun testFullFlow_onSepoliaNetwork() = runTest {
        val chainId = 11155111L // شناسه Sepolia از JSON
        val netType = NetworkName.SEPOLIA // شناسه Sepolia از JSON
        println("--- Testing on Sepolia Network ---")

        // --- ۱. تست گرفتن موجودی ---
        val assetsResult = walletRepository.getAssets(netType)
        assertTrue("Fetching assets from Sepolia should be ${assetsResult}", assetsResult is ResultResponse.Success)
        val assets = (assetsResult as ResultResponse.Success).data
        val nativeAsset = assets.find { it.contractAddress == null }
        assertNotNull("SepoliaETH should be found", nativeAsset)
        assertTrue("SepoliaETH balance must be greater than zero", nativeAsset!!.balance > BigInteger.ZERO)
        println("✅ Sepolia Balance: ${nativeAsset.balance}")

        // --- ۲. تست گرفتن تاریخچه تراکنش‌ها ---
        val historyResult = walletRepository.getTransactionHistory(netType, USER_ADDRESS)
        assertTrue("Fetching history from Sepolia should be ${historyResult}", historyResult is ResultResponse.Success)
        val history = (historyResult as ResultResponse.Success).data
        assertTrue("Transaction history on Sepolia should not be empty", history.isNotEmpty())
        println("✅ Sepolia Transactions Found: ${history.size}")

        // --- ۳. تست ارسال یک تراکنش کوچک ---
        val recipientAddress = "0x000000000000000000000000000000000000dEaD" // یک آدرس سوخته برای تست
        val amountToSend = BigInteger.valueOf(1000000000) // 0.000000001 ETH

        val txParams = TransactionParams.Evm(netType, recipientAddress, amountToSend)
        val sendResult = walletRepository.sendTransaction(txParams)

        assertTrue("Sending transaction on Sepolia should be ${sendResult}", sendResult is ResultResponse.Success)
        val txHash = (sendResult as ResultResponse.Success).data
        assertNotNull("Transaction hash should not be null", txHash)
        assertTrue("Transaction hash should start with 0x", txHash.startsWith("0x"))
        println("✅ Sepolia Transaction Sent! Hash: $txHash")
    }

    @Test
    fun testFullFlow_onBscTestnet() = runTest {
        val netType = NetworkName.BSCTESTNET // شناسه BSC از JSON
        println("\n--- Testing on BSC Testnet ---")

        // --- ۱. تست گرفتن موجودی ---
        val assetsResult = walletRepository.getAssets(netType)
        assertTrue("Fetching assets from BSC Testnet should be ${assetsResult}", assetsResult is ResultResponse.Success)
        val assets = (assetsResult as ResultResponse.Success).data
        val nativeAsset = assets.find { it.contractAddress == null }
        assertNotNull("tBNB should be found", nativeAsset)
        assertTrue("tBNB balance must be greater than zero", nativeAsset!!.balance > BigInteger.ZERO)
        println("✅ BSC Testnet Balance: ${nativeAsset.balance}")

        // --- ۲. تست گرفتن تاریخچه تراکنش‌ها ---
        val historyResult = walletRepository.getTransactionHistory(netType, USER_ADDRESS)
        assertTrue("Fetching history from BSC Testnet should be ${historyResult}", historyResult is ResultResponse.Success)
        val history = (historyResult as ResultResponse.Success).data
        assertTrue("Transaction history on BSC Testnet should not be empty", history.isNotEmpty())
        println("✅ BSC Testnet Transactions Found: ${history.size}")

        // --- ۳. تست ارسال یک تراکنش کوچک ---
        val recipientAddress = "0x000000000000000000000000000000000000dEaD"
        val amountToSend = BigInteger.valueOf(1000000000) // 0.000000001 tBNB

        val txParams = TransactionParams.Evm(netType, recipientAddress, amountToSend)
        val sendResult = walletRepository.sendTransaction(txParams)

        assertTrue("Sending transaction on BSC Testnet should be ${sendResult}", sendResult is ResultResponse.Success)
        val txHash = (sendResult as ResultResponse.Success).data
        assertNotNull("Transaction hash should not be null", txHash)
        assertTrue("Transaction hash should start with 0x", txHash.startsWith("0x"))
        println("✅ BSC Testnet Transaction Sent! Hash: $txHash")
    }

}