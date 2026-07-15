package com.mtd.data.repository

import com.mtd.core.encryption.SecureStorage
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.wallet.ActiveWalletManager
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkModule.provideGson
import com.mtd.domain.model.ResultResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * KAN-18 — secure wallet deletion. Verifies the secret is *erased* (`SecureStorage.remove`) rather
 * than blanked with an empty-string ciphertext, and that the in-memory key cache is cleared.
 */
class WalletDeletionSecurityTest {

    private lateinit var keyManager: KeyManager
    private lateinit var secureStorage: SecureStorage
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var activeWalletManager: ActiveWalletManager
    private lateinit var chainDataSourceFactory: ChainDataSourceFactory
    private lateinit var walletRepository: WalletRepositoryImpl

    private val deletedId = "w1"
    private val survivorId = "w2"

    private val metadataJson = """
        [
          {"id":"$deletedId","name":"W1","color":-1,"isMnemonic":true,"isManualBackedUp":false,"isCloudBackedUp":false},
          {"id":"$survivorId","name":"W2","color":-1,"isMnemonic":true,"isManualBackedUp":false,"isCloudBackedUp":false}
        ]
    """.trimIndent()

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
            gson = provideGson(),
            dataSourceFactory = dagger.Lazy { chainDataSourceFactory }
        )

        every { secureStorage.getDecrypted(any()) } returns null
        every { secureStorage.getDecrypted("wallets_metadata_list") } returns metadataJson
        every { secureStorage.getDecrypted("active_wallet_id") } returns deletedId
        every { secureStorage.getDecrypted("wallet_secret_$survivorId") } returns "survivor-secret"
    }

    @Test
    fun `deleteWallet erases the secret instead of blanking it`() = runTest {
        val result = walletRepository.deleteWallet(deletedId)

        assertTrue(result is ResultResponse.Success)
        // The secret must be physically removed from secure_prefs...
        verify(exactly = 1) { secureStorage.remove("wallet_secret_$deletedId") }
        // ...and never overwritten with an empty-string ciphertext (the old, leaky behavior).
        verify(exactly = 0) { secureStorage.putEncrypted("wallet_secret_$deletedId", "") }
        verify(exactly = 0) { secureStorage.putEncrypted("wallet_secret_$deletedId", any()) }
    }

    @Test
    fun `deleteWallet clears the in-memory cache for the deleted wallet`() = runTest {
        walletRepository.deleteWallet(deletedId)

        verify(exactly = 1) { activeWalletManager.clearCacheForWallet(deletedId) }
    }
}
