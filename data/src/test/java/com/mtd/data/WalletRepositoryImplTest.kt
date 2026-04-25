package com.mtd.data

import android.graphics.Color
import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.domain.model.core.WalletKey
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.core.wallet.ActiveWalletManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletRepositoryImplTest {

    private lateinit var keyManager: KeyManager
    private lateinit var secureStorage: SecureStorage
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var activeWalletManager: ActiveWalletManager
    private lateinit var chainDataSourceFactory: ChainDataSourceFactory
    private lateinit var walletRepository: WalletRepositoryImpl

    @Before
    fun setUp() {
        keyManager = mockk(relaxed = true)
        secureStorage = mockk(relaxed = true)
        activeWalletManager = mockk(relaxed = true)
        blockchainRegistry = mockk(relaxed = true)
        chainDataSourceFactory = mockk(relaxed = true)

        walletRepository = WalletRepositoryImpl(
            keyManager = keyManager,
            secureStorage = secureStorage,
            activeWalletManager = activeWalletManager,
            blockchainRegistry = blockchainRegistry,
            dataSourceFactory = chainDataSourceFactory
        )

        mockkObject(MnemonicHelper)
        every { secureStorage.getDecrypted(any()) } returns null
    }

    @Test
    fun `createNewWallet stores secret and returns mnemonic wallet`() = runTest {
        val fakeMnemonic = "test word ".repeat(11) + "test"
        val fakeKeys = listOf(mockk<WalletKey>())

        every { MnemonicHelper.generateMnemonic() } returns fakeMnemonic
        every { secureStorage.putEncrypted(any(), any()) } just runs
        every { keyManager.generateWalletKeysFromMnemonic(fakeMnemonic) } returns fakeKeys

        val result = walletRepository.createNewWallet("test", Color.RED)

        assertTrue(result is ResultResponse.Success)
        val wallet = (result as ResultResponse.Success<Wallet>).data
        assertTrue(wallet.hasMnemonic)
        assertEquals(fakeKeys, wallet.keys)
        verify(exactly = 1) { secureStorage.putEncrypted(match { it.startsWith("wallet_secret_") }, fakeMnemonic) }
    }

    @Test
    fun `importWalletFromMnemonic with invalid mnemonic returns error`() = runTest {
        val invalidMnemonic = "this is not valid"
        every { MnemonicHelper.isValidMnemonic(invalidMnemonic) } returns false

        val result = walletRepository.importWalletFromMnemonic(invalidMnemonic, "test", Color.RED)

        assertTrue(result is ResultResponse.Error)
    }

    @Test
    fun `loadExistingWallet returns active wallet from metadata structure`() = runTest {
        val walletId = "wallet_1"
        val storedMnemonic = "test word ".repeat(11) + "test"
        val fakeKeys = listOf(mockk<WalletKey>())
        val metadataJson =
            """[{"id":"$walletId","name":"test","color":${Color.RED},"isMnemonic":true,"isManualBackedUp":false,"isCloudBackedUp":false}]"""

        every { secureStorage.getDecrypted("wallets_metadata_list") } returns metadataJson
        every { secureStorage.getDecrypted("active_wallet_id") } returns walletId
        every { secureStorage.getDecrypted("wallet_secret_$walletId") } returns storedMnemonic
        every { keyManager.generateWalletKeysFromMnemonic(storedMnemonic) } returns fakeKeys

        val result = walletRepository.loadExistingWallet()

        assertTrue(result is ResultResponse.Success)
        val wallet = (result as ResultResponse.Success<Wallet?>).data
        assertNotNull(wallet)
        assertTrue(wallet?.hasMnemonic == true)
        assertEquals(fakeKeys, wallet?.keys)
    }
}
