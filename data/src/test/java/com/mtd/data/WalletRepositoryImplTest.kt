package com.mtd.data

import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.datasource.RemoteDataSource
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.Wallet
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.wallet.ActiveWalletManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WalletRepositoryImplTest {
    // ۱. ساختن نمونه‌های ساختگی (Mock) از وابستگی‌ها
    private lateinit var keyManager: KeyManager
    private lateinit var secureStorage: SecureStorage
    private lateinit var remoteDataSource: RemoteDataSource
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var activeWalletManager: ActiveWalletManager
    private lateinit var chainDataSourceFactory: ChainDataSourceFactory

    // این کلاسی است که می‌خواهیم تست کنیم
    private lateinit var walletRepository: WalletRepositoryImpl

    @Before
    fun setUp() {
        // قبل از هر تست، Mock ها را مقداردهی اولیه می‌کنیم
        keyManager = mockk()
        secureStorage = mockk()
        remoteDataSource = mockk()
        activeWalletManager = mockk()
        blockchainRegistry = mockk()
        chainDataSourceFactory=mockk()

        // نمونه واقعی از Repository را با Mock ها می‌سازیم
        walletRepository = WalletRepositoryImpl(keyManager, secureStorage,activeWalletManager,blockchainRegistry,chainDataSourceFactory)

        // Mock کردن MnemonicHelper (چون object است، باید از mockkObject استفاده کنیم)
        mockkObject(MnemonicHelper)
    }

    @Test
    fun `createNewWallet should generate mnemonic, save it, and return wallet`() = runTest {
        // Arrange (آماده‌سازی شرایط تست)
        val fakeMnemonic = "test word ".repeat(11) + "test"
        val fakeKeys = listOf(mockk<WalletKey>()) // یک لیست ساختگی از کلیدها

        // تعریف رفتار Mock ها:
        // هر زمان MnemonicHelper.generateMnemonic فراخوانی شد، mnemonic ساختگی ما را برگردان
        every { MnemonicHelper.generateMnemonic(any()) } returns fakeMnemonic
        // هر زمان secureStorage.putEncrypted فراخوانی شد، هیچ کاری نکن (فقط تایید کن که فراخوانی شده)
        every { secureStorage.putEncrypted(any(), any()) } just runs
        // هر زمان keyManager.generateWalletKeysFromMnemonic فراخوانی شد، لیست کلیدهای ساختگی را برگردان
        every { keyManager.generateWalletKeysFromMnemonic(fakeMnemonic) } returns fakeKeys
        // متد deleteWallet را هم Mock می‌کنیم
        every { secureStorage.putEncrypted(any(), "") } just runs


        // Act (اجرای متدی که می‌خواهیم تست کنیم)
        val result = walletRepository.createNewWallet()

        // Assert (بررسی نتایج)
        assertTrue(result is ResultResponse.Success)
        val wallet = (result as ResultResponse.Success<Wallet>).data
        assertEquals(fakeMnemonic, wallet.mnemonic)
        assertEquals(fakeKeys, wallet.keys)

        // تایید می‌کنیم که متدهای مورد نظر دقیقاً یک بار فراخوانی شده‌اند
        verify(exactly = 1) { secureStorage.putEncrypted("wallet_mnemonic_phrase", fakeMnemonic) }
        verify(exactly = 1) { keyManager.generateWalletKeysFromMnemonic(fakeMnemonic) }
    }

    @Test
    fun `importWalletFromMnemonic with invalid mnemonic should return error`() = runTest {
        // Arrange
        val invalidMnemonic = "this is not valid"
        every { MnemonicHelper.isValidMnemonic(invalidMnemonic) } returns false

        // Act
        val result = walletRepository.importWalletFromMnemonic(invalidMnemonic)

        // Assert
        assertTrue(result is ResultResponse.Error)
    }

    @Test
    fun `loadExistingWallet should return wallet when mnemonic is stored`() = runTest {
        // Arrange
        val storedMnemonic = "test word ".repeat(11) + "test"
        val fakeKeys = listOf(mockk<WalletKey>())

        // به Mock ها می‌گوییم وقتی getDecrypted فراخوانی شد، چه چیزی برگردانند
        every { secureStorage.getDecrypted("wallet_mnemonic_phrase") } returns storedMnemonic
        every { secureStorage.getDecrypted("wallet_private_key") } returns null // کلید خصوصی وجود ندارد
        every { keyManager.generateWalletKeysFromMnemonic(storedMnemonic) } returns fakeKeys

        // Act
        val result = walletRepository.loadExistingWallet()

        // Assert
        assertTrue(result is ResultResponse.Success)
        val wallet = (result as ResultResponse.Success<Wallet?>).data
        assertNotNull(wallet)
        assertEquals(storedMnemonic, wallet?.mnemonic)
    }
}