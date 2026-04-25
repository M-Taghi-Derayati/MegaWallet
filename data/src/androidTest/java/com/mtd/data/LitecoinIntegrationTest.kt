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
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LitecoinIntegrationTest {

    private lateinit var walletRepository: IWalletRepository
    private lateinit var userLitecoinAddress: String

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val secureStorage = SecureStorage(context)
        val blockchainRegistry = BlockchainRegistry().apply { loadNetworksFromAssets(context) }
        val keyManager = KeyManager(blockchainRegistry)

        val okHttpClient = provideOkHttpClient(
            httpLoggingInterceptorProvider(),
            NetworkConnectionInterceptor(context)
        )
        val gson = Gson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient, gson)

        val assetRegistry = AssetRegistry(blockchainRegistry)
        val dataSourceFactory = ChainDataSourceFactory(
            blockchainRegistry,
            retrofitBuilder,
            assetRegistry,
            okHttpClient
        )

        val activeWalletManager = ActiveWalletManager(keyManager)

        walletRepository = WalletRepositoryImpl(
            activeWalletManager = activeWalletManager,
            keyManager = keyManager,
            secureStorage = secureStorage,
            blockchainRegistry = blockchainRegistry,
            dataSourceFactory = dataSourceFactory
        )

        runTest {
            val result = walletRepository.importWalletFromPrivateKey(
                "f2449cde31e0e3960132206f9fac3fe50c250d403a11f610dedfb72e0594a253",
                "ltc_test",
                Color.RED
            )
            val wallet = (result as ResultResponse.Success).data
            val litecoinKey = wallet.keys.find { it.networkName == NetworkName.LTCTESTNET }
            assertNotNull("Litecoin Testnet key should be generated: $wallet", litecoinKey)
            userLitecoinAddress = litecoinKey!!.address
            println("🔑 Litecoin Test Address: $userLitecoinAddress")
        }
    }

    @Test
    fun test1_GetLitecoinBalance_shouldReturnCorrectBalance() = runTest {
        println("--- 1. Testing Litecoin Balance ---")
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(-2)
        val result = dataSource.getBalance(userLitecoinAddress)

        assertTrue("Fetching LTC balance should be successful, result=$result", result is ResultResponse.Success)
        val balance = (result as ResultResponse.Success).data

        assertTrue(
            "Litecoin balance should be > 0. Fund this address in LTCTESTNET: $userLitecoinAddress",
            balance > BigDecimal.ZERO
        )
        println("✅ Litecoin Balance: $balance")
    }

    @Test
    fun test2_GetLitecoinTransactionHistory_shouldReturnHistory() = runTest {
        println("--- 2. Testing Litecoin Transaction History ---")
        val result = walletRepository.getTransactionHistory(NetworkName.LTCTESTNET, userLitecoinAddress)

        assertTrue("Fetching LTC history should be successful. Result: $result", result is ResultResponse.Success)
        val history = (result as ResultResponse.Success).data
        assertNotNull("LTC history list should not be null", history)
        assertTrue("LTC history should not be empty for a funded address.", history.isNotEmpty())
        println("✅ Litecoin Transactions Found: ${history.size}")
        println("Last transaction: ${history.firstOrNull()}")
    }

    @Test
    fun test3_EstimateLitecoinFee_shouldReturnFeeOptions() = runTest {
        println("--- 3. Testing Litecoin Fee Estimation ---")
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(-2)
        val result = dataSource.getFeeOptions()

        assertTrue("Fee options should be fetched successfully. Result: $result", result is ResultResponse.Success)
        val options = (result as ResultResponse.Success).data
        assertTrue("LTC fee options should not be empty", options.isNotEmpty())
        assertTrue(
            "LTC fee rate should be present and > 0",
            (options.firstOrNull()?.feeRateInSatsPerByte ?: 0L) > 0L
        )
        println("✅ Litecoin fee options: $options")
    }
}

