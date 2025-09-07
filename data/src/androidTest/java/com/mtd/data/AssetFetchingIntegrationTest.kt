package com.mtd.data


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.model.NetworkName
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkConnectionInterceptor
import com.mtd.data.di.NetworkModule.httpLoggingInterceptorProvider
import com.mtd.data.di.NetworkModule.provideGson
import com.mtd.data.di.NetworkModule.provideOkHttpClient
import com.mtd.data.di.NetworkModule.provideRetrofitBuilder
import com.mtd.data.repository.IWalletRepository
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.wallet.ActiveWalletManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class AssetFetchingIntegrationTest {

    private lateinit var walletRepository: IWalletRepository

    // Mnemonic کیف پول تستی ما در شبکه Sepolia.
    // این کیف پول باید مقداری SepoliaETH داشته باشد.
    private val TEST_MNEMONIC = "pattern hello embark avocado another banner chest vital ill exercise material pistol"
    private val EXPECTED_ADDRESS = "0x17b51d4928668B50065C589bAfBC32736f196216"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // ساخت تمام وابستگی‌ها به صورت واقعی
        val secureStorage = SecureStorage(context)
        val blockchainRegistry = BlockchainRegistry().apply { loadNetworksFromAssets(context) }
        val keyManager = KeyManager(blockchainRegistry)

        // این کار باعث می‌شود کلیدهای کیف پول در حافظه کش شوند
        val walletKeys = keyManager.generateWalletKeysFromMnemonic(TEST_MNEMONIC)
        keyManager.loadKeysIntoCache(walletKeys)

        val okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider(),NetworkConnectionInterceptor(context))
        val gson = provideGson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient, gson)
        val activeWalletManager= ActiveWalletManager(keyManager)
        val assetRegistry=AssetRegistry()
        val dataSourceFactory = ChainDataSourceFactory(blockchainRegistry, retrofitBuilder,assetRegistry,okHttpClient)

        walletRepository = WalletRepositoryImpl(
            keyManager,
            secureStorage,
            activeWalletManager,
            blockchainRegistry,
            dataSourceFactory // استفاده از فکتوری
        )

        // قبل از تست، کیف پول را با Mnemonic تستی خودمان وارد می‌کنیم تا در SecureStorage ذخیره شود
        runTest {
            walletRepository.importWalletFromMnemonic(TEST_MNEMONIC)
        }
    }

    @Test
    fun getAssets_forSepoliaNetwork_shouldFetchAndReturnCorrectData() = runTest {
        // --- آماده‌سازی ---
        // ما به NetworkType شبکه Sepolia نیاز داریم.
        // فرض می‌کنیم در فایل json ما، فقط یک شبکه EVM برای تست وجود دارد یا باید آن را پیدا کنیم.
        // برای این تست، به صورت دستی از NetworkType.EVM استفاده می‌کنیم و انتظار داریم به Sepolia متصل شود.
        "evm_11155111" // این باید با کانفیگ شما مطابقت داشته باشد

        // --- اجرا ---
        val result = walletRepository.getAssets(NetworkName.SEPOLIA)

        // --- بررسی نتایج ---
        assertTrue("Result should be Success, but was $result", result is ResultResponse.Success)
        val assets = (result as ResultResponse.Success).data

        // اطمینان از اینکه حداقل دارایی‌های مورد انتظار ما وجود دارند
        assertTrue("Asset list should not be empty", assets.isNotEmpty())

        // ۱. بررسی توکن اصلی شبکه (SepoliaETH)
        val nativeAsset = assets.find { it.contractAddress == null }
        assertNotNull("Native asset (ETH) should not be null", nativeAsset)

        // بررسی صحت اطلاعات توکن اصلی
        assertEquals("ETH", nativeAsset!!.symbol)
        assertEquals(18, nativeAsset.decimals)

        // بررسی موجودی توکن اصلی
        // شما باید قبل از اجرای تست، به آدرس تستی مقداری ETH واریز کرده باشید.
        assertTrue("Native asset balance should be greater than zero", nativeAsset.balance > BigInteger.ZERO)

        // ۲. بررسی یک توکن ERC20 شناخته شده (مثلاً USDC در Sepolia)
        val usdcContractAddress = "0x94a9D9AC8a22534E3FaCa422de466b95853443aD" // آدرس USDC در Sepolia
        val erc20Asset = assets.find { it.contractAddress.equals(usdcContractAddress, ignoreCase = true) }
        assertNotNull("ERC20 asset (USDC) should not be null", erc20Asset)

        // بررسی صحت اطلاعات توکن ERC20
        assertEquals("USD Coin", erc20Asset!!.name)
        assertEquals("USDC", erc20Asset.symbol)
        assertEquals(6, erc20Asset.decimals)

        // بررسی موجودی توکن ERC20
        // اگر به آدرس تستی خود USDC واریز نکرده‌اید، این تست باید برای موجودی صفر پاس شود.
        // اگر واریز کرده‌اید، می‌توانید این خط را به `> BigInteger.ZERO` تغییر دهید.
        assertEquals("ERC20 asset balance should be zero (unless manually funded)", BigInteger.ZERO, erc20Asset.balance)

        // برای اطمینان، می‌توانیم لاگ هم بگیریم
        println("Test successful!")
        println("Native Asset (${nativeAsset.symbol}) Balance: ${nativeAsset.balance}")
        println("ERC20 Asset (${erc20Asset.symbol}) Balance: ${erc20Asset.balance}")
    }
}