package com.mtd.data


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.model.NetworkName
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkModule.Companion.provideGson
import com.mtd.data.di.NetworkModule.Companion.provideOkHttpClient
import com.mtd.data.di.NetworkModule.Companion.provideRetrofitBuilder
import com.mtd.data.repository.IWalletRepository
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.IUserPreferencesRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.Wallet
import com.mtd.domain.wallet.ActiveWalletManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletRepositoryIntegrationTest {

    private lateinit var secureStorage: SecureStorage
    private lateinit var keyManager: KeyManager
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var walletRepository: IWalletRepository


    // یک Mnemonic شناخته شده برای تست
    private val TEST_MNEMONIC = "pattern hello embark avocado another banner chest vital ill exercise material pistol"
    // آدرس اتریوم متناظر با این Mnemonic (در مسیر پیش‌فرض)
    private val EXPECTED_ETH_ADDRESS = "0x17b51d4928668B50065C589bAfBC32736f196216"

    @Before
    fun setUp() {
        // از Context واقعی اپلیکیشن در محیط تست استفاده می‌کنیم
        val context = ApplicationProvider.getApplicationContext<Context>()

        // نمونه‌های واقعی از کلاس‌های :core را می‌سازیم، نه Mock
        secureStorage = SecureStorage(context)
        blockchainRegistry = BlockchainRegistry()
        blockchainRegistry.loadNetworksFromAssets(context)
        val okHttpClient = OkHttpClient()

        val gson = provideGson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient, gson)
        val chainDataSourceFactory= ChainDataSourceFactory(blockchainRegistry,retrofitBuilder,okHttpClient)
        val activeWalletManager= ActiveWalletManager()
        keyManager = KeyManager(blockchainRegistry)

        // نمونه واقعی از Repository را با وابستگی‌های واقعی می‌سازیم
        walletRepository = WalletRepositoryImpl(keyManager, secureStorage,activeWalletManager,blockchainRegistry,chainDataSourceFactory)

        // قبل از هر تست، مطمئن می‌شویم که هیچ کیف پولی از تست قبلی باقی نمانده
        runTest { walletRepository.deleteWallet() }
    }

    @Test
    fun fullWalletLifecycle_importAndLoad_shouldWorkCorrectly() = runTest {
        // --- مرحله ۱: وارد کردن کیف پول با یک Mnemonic شناخته شده ---
        val importResult = walletRepository.importWalletFromMnemonic(TEST_MNEMONIC)

        // بررسی می‌کنیم که وارد کردن موفقیت‌آمیز بوده
        assertTrue(importResult is ResultResponse.Success)
        val importedWallet = (importResult as ResultResponse.Success<Wallet>).data

        // بررسی می‌کنیم که آدرس اتریوم تولید شده صحیح است
        val ethKey = importedWallet.keys.find { it.networkName == NetworkName.ETHEREUM && it.chainId == 1L }
        assertNotNull(ethKey)

        assertTrue(EXPECTED_ETH_ADDRESS.equals(ethKey?.address, ignoreCase = true))
        // --- مرحله ۲: بارگذاری کیف پول (شبیه‌سازی بستن و باز کردن برنامه) ---
        val loadResult = walletRepository.loadExistingWallet()

        // بررسی می‌کنیم که بارگذاری موفقیت‌آمیز بوده
        assertTrue(loadResult is ResultResponse.Success)
        val loadedWallet = (loadResult as ResultResponse.Success<Wallet?>).data
        assertNotNull(loadedWallet)

        // --- مرحله ۳: اعتبارسنجی داده‌های بارگذاری شده ---
        // بررسی می‌کنیم که Mnemonic ذخیره شده و بازیابی شده صحیح است
        val savedMnemonicResult = walletRepository.getSavedMnemonic()
        assertTrue(savedMnemonicResult is ResultResponse.Success)
        assertEquals(TEST_MNEMONIC, (savedMnemonicResult as ResultResponse.Success<String?>).data)

        // بررسی می‌کنیم که آدرس اتریوم در کیف پول بارگذاری شده نیز صحیح است
        val loadedEthKey = loadedWallet!!.keys.find { it.networkName == NetworkName.ETHEREUM && it.chainId == 1L }
        assertNotNull(loadedEthKey)
        assertTrue(EXPECTED_ETH_ADDRESS.equals(loadedEthKey?.address, ignoreCase = true))
        // بررسی می‌کنیم که کلیدهای خصوصی هم یکسان هستند
        assertEquals(ethKey?.privateKeyHex, loadedEthKey?.privateKeyHex)
    }
}