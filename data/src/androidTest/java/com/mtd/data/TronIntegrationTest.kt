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
import com.mtd.domain.model.Asset
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
class TronIntegrationTest {

    private lateinit var walletRepository: IWalletRepository
    private lateinit var userTronAddress: String

    // همان mnemonic قبلی. مطمئن شوید در شبکه Testnet3 مقداری tBTC دارد.
    private val TEST_MNEMONIC = "pattern hello embark avocado another banner chest vital ill exercise material pistol"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // --- ساخت وابستگی‌ها به صورت دستی برای محیط تست ---

        // ۱. ساخت وابستگی‌های پایه از :core
        val secureStorage = SecureStorage(context)
        val blockchainRegistry = BlockchainRegistry().apply {


            loadNetworksFromAssets(context)


        }
        val keyManager = KeyManager(blockchainRegistry)

        // ۲. ساخت وابستگی‌های شبکه (منطق مشابه DataModule)
        val okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider(),NetworkConnectionInterceptor(context))
        val gson = Gson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient,gson)

        // ۳. ساخت وابستگی‌های اصلی :data
        val assetRegistry=AssetRegistry(blockchainRegistry).apply {
            loadAssetsFromAssets(context)
        }
        val dataSourceFactory = ChainDataSourceFactory(blockchainRegistry, retrofitBuilder,assetRegistry,okHttpClient)

        val activeWalletManager = ActiveWalletManager(keyManager)

        walletRepository = WalletRepositoryImpl(
            activeWalletManager = activeWalletManager,
            keyManager = keyManager,
            secureStorage = secureStorage,
            blockchainRegistry = blockchainRegistry,
            dataSourceFactory = dataSourceFactory
        )

        // وارد کردن کیف پول و استخراج آدرس ترون
        runTest {
            val result = walletRepository.importWalletFromPrivateKey("08538ae0e9430058c40e6ea20290bc14efc8768c51e989d804224d2ef42b62ae","test",Color.RED)
            val wallet = (result as ResultResponse.Success).data
            val tronKey = wallet.keys.find { it.networkName == NetworkName.TRON }
            assertNotNull("Tron Testnet key should be generated ${wallet}", tronKey)
            userTronAddress = tronKey!!.address
            println("🔑 Tron Test Address: $userTronAddress")
//            keyManager.loadKeysIntoCache((result).data.keys)
        }
    }

    @Test
    fun getbalance() = runTest {
        println("--- 1. Testing Bitcoin Balance ---")
        userTronAddress
        // چون getAssets برای بیت‌کوین پیاده‌سازی نشده، مستقیماً getBalance از DataSource را تست می‌کنیم
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(728126428)
//        val result = dataSource.getBalancesForMultipleAddresses(listOf(userTronAddress))
        val result = dataSource.getFeeOptions("TTxKNiut92mkmURVvS7GB1o6Zjso6jrUnB","TNf1CXrskRfH72FheqszAY8TQ7qxdhrPeD", Asset(
            name = "",
            symbol = "USDT",
            decimals = 6,
            contractAddress = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", // آدرس رسمی تتر در شبکه ترون
            balance = BigDecimal("10.000000")
        ))

        assertTrue("Fetching balance should be successful, but was $result", result is ResultResponse.Success)
        val balance = (result as ResultResponse.Success).data

        // مطمئن شوید به آدرس خود مقداری tBTC از یک Faucet واریز کرده‌اید
        //assertTrue("Tron balance should be greater than zero. Fund your address: $userTronAddress", balance. > BigDecimal.ZERO)
        println("✅ Tron Balance: $balance")
    }
}