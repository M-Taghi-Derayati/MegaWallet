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
class DogecoinIntegrationTest {

    private lateinit var walletRepository: IWalletRepository
    private lateinit var userDogecoinAddress: String

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
            val result = walletRepository.importWalletFromMnemonic(
                "argue shop nerve proof chuckle convince normal state luxury caught kind swift",
                "doge_test",
                Color.RED
            )
            val wallet = (result as ResultResponse.Success).data
            val dogeKey = wallet.keys.find { it.networkName == NetworkName.DOGE }
            assertNotNull("Dogecoin Testnet key should be generated: $wallet", dogeKey)
            userDogecoinAddress = dogeKey!!.address
            println("🔑 Dogecoin Test Address: $userDogecoinAddress")
        }
    }

    @Test
    fun test1_GetDogecoinBalance_shouldReturnCorrectBalance() = runTest {
        println("--- 1. Testing Dogecoin Balance ---")
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(3)
        val result = dataSource.getBalance(userDogecoinAddress)

        assertTrue("Fetching DOGE balance should be successful, result=$result", result is ResultResponse.Success)
        val balance = (result as ResultResponse.Success).data

        assertTrue(
            "Dogecoin balance should be > 0. Fund this address in DOGETESTNET: $userDogecoinAddress",
            balance > BigDecimal.ZERO
        )
        println("✅ Dogecoin Balance: $balance")
    }

    @Test
    fun test2_GetDogecoinTransactionHistory_shouldReturnHistory() = runTest {
        println("--- 2. Testing Dogecoin Transaction History ---")
        val result = walletRepository.getTransactionHistory(NetworkName.DOGE, userDogecoinAddress)

        assertTrue("Fetching DOGE history should be successful. Result: $result", result is ResultResponse.Success)
        val history = (result as ResultResponse.Success).data
        assertNotNull("DOGE history list should not be null", history)
        assertTrue("DOGE history should not be empty for a funded address.", history.isNotEmpty())
        println("✅ Dogecoin Transactions Found: ${history.size}")
        println("Last transaction: ${history.firstOrNull()}")
    }

    @Test
    fun test3_EstimateDogecoinFee_shouldReturnFeeOptions() = runTest {
        println("--- 3. Testing Dogecoin Fee Estimation ---")
        val dataSource = (walletRepository as WalletRepositoryImpl).dataSourceFactory.create(3)
        val result = dataSource.getFeeOptions()

        assertTrue("Fee options should be fetched successfully. Result: $result", result is ResultResponse.Success)
        val options = (result as ResultResponse.Success).data
        assertTrue("DOGE fee options should not be empty", options.isNotEmpty())
        assertTrue(
            "DOGE fee rate should be present and > 0",
            (options.firstOrNull()?.feeRateInSatsPerByte ?: 0L) > 0L
        )
        println("✅ Dogecoin fee options: $options")
    }
}

