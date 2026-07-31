package com.mtd.data.config

import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.data.di.NetworkModule.provideGson
import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.dto.ConfigNetworkDto
import com.mtd.data.service.ConfigApiService
import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * TASK-53 — the apply step that TASK-38 was missing: the verified bundle actually reaching the
 * registries, and a tampered one reaching nothing.
 *
 * This closes the loop end-to-end through the real ConfigManager (only the HTTP service, the
 * signature verifier and the cache are faked), so a regression in the trust gate shows up here
 * rather than on a device.
 */
class ConfigCatalogBootstrapperTest {

    private lateinit var configApiService: ConfigApiService
    private lateinit var signatureVerifier: ConfigSignatureVerifier
    private lateinit var cacheStore: ConfigCacheStore
    private lateinit var localAssetProvider: LocalConfigAssetProvider
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var assetRegistry: AssetRegistry
    private lateinit var appEventBus: IAppEventBus

    /** The APK-shipped seed: one known EVM chain. */
    private val localSeed = ConfigBundleDto(
        version = LocalConfigAssetProvider.LOCAL_VERSION,
        networks = listOf(sepoliaDto()),
        assets = emptyList(),
        signature = null
    )

    @Before
    fun setUp() {
        configApiService = mockk(relaxed = true)
        signatureVerifier = mockk(relaxed = true)
        cacheStore = mockk(relaxed = true)
        localAssetProvider = mockk(relaxed = true)
        appEventBus = mockk(relaxed = true)

        blockchainRegistry = BlockchainRegistry(ITestnetVisibilityProvider { true })
        assetRegistry = AssetRegistry(blockchainRegistry)

        every { localAssetProvider.load() } returns localSeed
        every { cacheStore.read() } returns null

        // Seed the registry the way CryptoModule does at DI time.
        blockchainRegistry.applyConfig(ConfigBundleMapper.toNetworkConfigs(localSeed))
    }

    private fun bootstrapper() = ConfigCatalogBootstrapper(
        configManager = ConfigManager(
            configApiService = configApiService,
            signatureVerifier = signatureVerifier,
            cacheStore = cacheStore,
            localAssetProvider = localAssetProvider,
            gson = provideGson()
        ),
        localAssetProvider = localAssetProvider,
        blockchainRegistry = blockchainRegistry,
        assetRegistry = assetRegistry,
        appEventBus = appEventBus
    )

    private fun serveBundle(json: String, signatureValid: Boolean) {
        coEvery { configApiService.getConfigBundleRaw() } returns
            Response.success(json.toResponseBody("application/json".toMediaType()))
        every { signatureVerifier.verify(any()) } returns signatureValid
    }

    // --- the happy path this whole task exists for ---------------------------

    @Test
    fun `a verified bundle registers a network that ships in no local file`() = runTest {
        serveBundle(BUNDLE_WITH_NEW_CHAIN, signatureValid = true)

        val applied = bootstrapper().bootstrap()

        assertTrue("a verified bundle must be applied", applied)
        val added = blockchainRegistry.getNetworkById("newchain_mainnet")
        assertNotNull("the bundle-only chain must be registered", added)
        assertEquals(777777L, added?.chainId)
        // and it must be usable, not merely present
        assertEquals(18, added?.decimals)
        assertEquals(listOf("https://rpc.newchain.example"), added?.RpcUrlsEvm)
    }

    // --- the trust gate ------------------------------------------------------

    @Test
    fun `a tampered signature registers nothing and leaves the local seed intact`() = runTest {
        serveBundle(BUNDLE_WITH_NEW_CHAIN, signatureValid = false)

        val applied = bootstrapper().bootstrap()

        assertFalse("an unverified bundle must never be applied", applied)
        assertNull(
            "the tampered bundle's network must not exist",
            blockchainRegistry.getNetworkById("newchain_mainnet")
        )
        assertNotNull("the local seed must survive", blockchainRegistry.getNetworkById("sepolia"))
    }

    @Test
    fun `an offline device keeps the local seed`() = runTest {
        coEvery { configApiService.getConfigBundleRaw() } throws java.io.IOException("offline")

        val applied = bootstrapper().bootstrap()

        assertFalse(applied)
        assertNotNull(blockchainRegistry.getNetworkById("sepolia"))
    }

    @Test
    fun `a verified but empty bundle does not wipe the catalog`() = runTest {
        serveBundle("""{"version":"9.9.9","networks":[],"assets":[]}""", signatureValid = true)

        val applied = bootstrapper().bootstrap()

        assertFalse("an empty bundle must be refused, not applied", applied)
        assertNotNull(blockchainRegistry.getNetworkById("sepolia"))
    }

    @Test
    fun `a verified bundle cannot repoint a locally shipped chainId`() = runTest {
        // sepolia is in the local seed with chainId 11155111; the bundle claims 1 (ETH mainnet).
        serveBundle(BUNDLE_WITH_TAMPERED_SEPOLIA, signatureValid = true)

        bootstrapper().bootstrap()

        assertEquals(
            "signature-verified is not the same as allowed to rewrite identity",
            11155111L,
            blockchainRegistry.getNetworkById("sepolia")?.chainId
        )
    }

    @Test
    fun `bootstrap is idempotent and awaitReady reports the outcome`() = runTest {
        serveBundle(BUNDLE_WITH_NEW_CHAIN, signatureValid = true)
        val boot = bootstrapper()

        assertTrue(boot.bootstrap())
        assertTrue(boot.bootstrap())
        assertTrue(boot.awaitReady())
        assertNotNull(blockchainRegistry.getNetworkById("newchain_mainnet"))
    }

    private companion object {
        fun sepoliaDto() = ConfigNetworkDto(
            networkId = "sepolia",
            name = "SEPOLIA",
            type = "EVM",
            chainId = 11155111L,
            isTestnet = true,
            derivationPath = "m/44'/60'/0'/0/0",
            rpcUrlsEvm = listOf("https://sepolia.example"),
            currencySymbol = "ETH",
            decimals = 18,
            regex = "/^0x[a-fA-F0-9]{40}$/",
            iconUrl = "https://cdn.example/eth.png"
        )

        const val BUNDLE_WITH_NEW_CHAIN = """
        {
          "version": "2.0.0",
          "signature": "deadbeef",
          "networks": [
            {
              "networkId": "sepolia", "name": "SEPOLIA", "type": "EVM", "chainId": 11155111,
              "isTestnet": true, "derivationPath": "m/44'/60'/0'/0/0",
              "rpcUrlsEvm": ["https://sepolia.example"], "currencySymbol": "ETH", "decimals": 18,
              "regex": "/^0x[a-fA-F0-9]{40}${'$'}/", "iconUrl": "https://cdn.example/eth.png"
            },
            {
              "networkId": "newchain_mainnet", "name": "NEWCHAIN", "type": "EVM", "chainId": 777777,
              "isTestnet": false, "derivationPath": "m/44'/60'/0'/0/0",
              "rpcUrlsEvm": ["https://rpc.newchain.example"], "currencySymbol": "NEW",
              "decimals": 18, "regex": "/^0x[a-fA-F0-9]{40}${'$'}/",
              "iconUrl": "https://cdn.example/new.png", "explorerApi": "etherscan"
            }
          ],
          "assets": []
        }
        """

        const val BUNDLE_WITH_TAMPERED_SEPOLIA = """
        {
          "version": "2.0.1",
          "signature": "deadbeef",
          "networks": [
            {
              "networkId": "sepolia", "name": "SEPOLIA", "type": "EVM", "chainId": 1,
              "isTestnet": true, "derivationPath": "m/44'/60'/0'/0/0",
              "rpcUrlsEvm": ["https://sepolia.example"], "currencySymbol": "ETH", "decimals": 18,
              "regex": "/^0x[a-fA-F0-9]{40}${'$'}/", "iconUrl": "https://cdn.example/eth.png"
            }
          ],
          "assets": []
        }
        """
    }
}
