package com.mtd.data

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.model.core.NetworkName
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkConnectionInterceptor
import com.mtd.data.di.NetworkModule.httpLoggingInterceptorProvider
import com.mtd.data.di.NetworkModule.provideOkHttpClient
import com.mtd.data.di.NetworkModule.provideRetrofitBuilder
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.ResultResponse
import com.mtd.core.wallet.ActiveWalletManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // اجرای تست‌ها به ترتیب نام
class BitcoinIntegrationTest {

    private lateinit var walletRepository: IWalletRepository
    private lateinit var userBitcoinAddress: String

    // همان mnemonic قبلی. مطمئن شوید در شبکه Testnet3 مقداری tBTC دارد.
    private val TEST_MNEMONIC = "pattern hello embark avocado another banner chest vital ill exercise material pistol"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // --- ساخت وابستگی‌ها به صورت دستی برای محیط تست ---

        // ۱. ساخت وابستگی‌های پایه از :core
        val secureStorage = SecureStorage(context)
        val blockchainRegistry = BlockchainRegistry().apply { loadNetworksFromAssets(context) }
        val keyManager = KeyManager(blockchainRegistry)

        // ۲. ساخت وابستگی‌های شبکه (منطق مشابه DataModule)
        val okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider(),NetworkConnectionInterceptor(context))
        val gson = Gson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient,gson)

        // ۳. ساخت وابستگی‌های اصلی :data
        val assetRegistry=AssetRegistry(blockchainRegistry)
        val dataSourceFactory = ChainDataSourceFactory(blockchainRegistry, retrofitBuilder,assetRegistry,okHttpClient)

        val activeWalletManager = ActiveWalletManager(keyManager)

        walletRepository = WalletRepositoryImpl(
            activeWalletManager = activeWalletManager,
            keyManager = keyManager,
            secureStorage = secureStorage,
            blockchainRegistry = blockchainRegistry,
            dataSourceFactory = dataSourceFactory
        )
//f2449cde31e0e3960132206f9fac3fe50c250d403a11f610dedfb72e0594a253
        // وارد کردن کیف پول و استخراج آدرس بیت‌کوین
        runTest {
            val result = walletRepository.importWalletFromPrivateKey("f2449cde31e0e3960132206f9fac3fe50c250d403a11f610dedfb72e0594a253","test",Color.RED)
            val wallet = (result as ResultResponse.Success).data
            val bitcoinKey = wallet.keys.find { it.networkName == NetworkName.BITCOINTESTNET }
            assertNotNull("Bitcoin Testnet key should be generated ${wallet}", bitcoinKey)
            userBitcoinAddress = bitcoinKey!!.address
            println("🔑 Bitcoin Test Address: $userBitcoinAddress")
            //keyManager.loadKeysIntoCache((result).data.keys)
        }
    }

    @Test
    fun test1_GetBitcoinBalance_shouldReturnCorrectBalance() = runTest {
        println("--- 1. Testing Bitcoin Balance ---")

        // چون getAssets برای بیت‌کوین پیاده‌سازی نشده، مستقیماً getBalance از DataSource را تست می‌کنیم
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(-1) // -1 for Bitcoin Testnet
        val result = dataSource.getBalance(userBitcoinAddress)

        assertTrue("Fetching balance should be successful, but was $result", result is ResultResponse.Success)
        val balance = (result as ResultResponse.Success).data

        // مطمئن شوید به آدرس خود مقداری tBTC از یک Faucet واریز کرده‌اید
        assertTrue("Bitcoin balance should be greater than zero. Fund your address: $userBitcoinAddress", balance > BigDecimal.ZERO)
        println("✅ Bitcoin Balance: $balance Satoshis")
    }

    @Test
    fun test2_GetBitcoinTransactionHistory_shouldReturnHistory() = runTest {
        println("--- 2. Testing Bitcoin Transaction History ---")
        val result = walletRepository.getTransactionHistory(NetworkName.BITCOINTESTNET, userBitcoinAddress)

        assertTrue("Fetching history should be successful. Result: $result", result is ResultResponse.Success)
        val history = (result as ResultResponse.Success).data
        assertNotNull("History list should not be null", history)

        // اگر تراکنشی داشته باشید، این باید true باشد
        assertTrue("History list should not be empty for a funded address.", history.isNotEmpty())
        println("✅ Bitcoin Transactions Found: ${history.size}")
        println("Last transaction: ${history.firstOrNull()}")
    }
   /*  var recommendedFeeRate: Long=2L
    @Test
    fun test3_EstimateBitcoinFee_shouldReturnFee() = runTest {
        println("--- 3. Testing Bitcoin Fee Estimation ---")
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(-1)
        val feeResult = dataSource.estimateFee()

        assertTrue("Fee estimation must be successful to proceed. Result: $feeResult", feeResult is ResultResponse.Success)
         recommendedFeeRate = (feeResult as ResultResponse.Success).data.toLong()
        println("ℹ️ Using recommended fee rate: $recommendedFeeRate sats/byte")

    }

    @Test
    fun test4_SendBitcoinTransaction_shouldSucceed() = runTest {
        println("--- 4. Testing Bitcoin Send Transaction ---")

        // !مهم: این آدرس را با یک آدرس تست‌نت دیگر که به آن دسترسی دارید، جایگزین کنید
        val recipientAddress = "tb1qzdvzanscaq9e22drfwp4pewfrmg2ssg35tzyhs"

        // مطمئن شوید آدرس گیرنده با آدرس فرستنده یکی نباشد
        assertNotEquals("Recipient address cannot be the same as sender address", userBitcoinAddress, recipientAddress)

        val amountInSatoshi = 1500L // 0.000015 tBTC, یک مقدار کوچک برای تست
        val feeRateInSatsPerByte = 2L // یک کارمزد معقول برای تست‌نت

        val params = TransactionParams.Utxo(
            chainId = -1, // برای UTXO استفاده نمی‌شود
            toAddress = recipientAddress,
            amountInSatoshi = amountInSatoshi,
            feeRateInSatsPerByte = recommendedFeeRate
        )

        val result = walletRepository.sendTransaction(params)
        assertTrue("Sending transaction should be successful. Result: $result", result is ResultResponse.Success)
        val txHash = (result as ResultResponse.Success).data
        assertNotNull("Transaction hash should not be null", txHash)
        println("✅ Bitcoin Transaction Sent! Hash: https://mempool.space/testnet/tx/$txHash")
    }*/
}