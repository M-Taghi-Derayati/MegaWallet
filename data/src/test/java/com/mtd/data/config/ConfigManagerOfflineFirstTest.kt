package com.mtd.data.config

import com.mtd.data.di.NetworkModule.provideGson
import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.dto.ConfigVersionDto
import com.mtd.data.service.ConfigApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Phase 3 — verifies the ConfigManager Offline-First + Fallback priority order:
 *  - network failure → bundled local assets (resilience, never an error),
 *  - cache present + version matches latest → cache served without a full bundle fetch,
 *  - cache below the minimum-version floor → discarded, refetch forced (Base USDC decimals fix).
 */
class ConfigManagerOfflineFirstTest {

    private lateinit var configApiService: ConfigApiService
    private lateinit var signatureVerifier: ConfigSignatureVerifier
    private lateinit var cacheStore: ConfigCacheStore
    private lateinit var localAssetProvider: LocalConfigAssetProvider
    private lateinit var configManager: ConfigManager

    @Before
    fun setUp() {
        configApiService = mockk(relaxed = true)
        signatureVerifier = mockk(relaxed = true)
        cacheStore = mockk(relaxed = true)
        localAssetProvider = mockk(relaxed = true)

        configManager = ConfigManager(
            configApiService = configApiService,
            signatureVerifier = signatureVerifier,
            cacheStore = cacheStore,
            localAssetProvider = localAssetProvider,
            gson = provideGson()
        )
    }

    @Test
    fun `falls back to local assets when network fails`() = runTest {
        val localBundle = ConfigBundleDto(version = LocalConfigAssetProvider.LOCAL_VERSION)
        every { cacheStore.read() } returns null
        every { localAssetProvider.load() } returns localBundle
        // Network bundle fetch fails (offline / IOException).
        coEvery { configApiService.getConfigBundleRaw() } throws IOException("offline")

        val result = configManager.getValidatedConfig()

        assertSame(localBundle, result)
        verify(exactly = 1) { localAssetProvider.load() }
    }

    @Test
    fun `uses cache when version matches latest known version`() = runTest {
        val cached = ConfigBundleDto(version = "1.0.41")
        every { cacheStore.read() } returns cached
        coEvery { configApiService.getConfigVersion() } returns
            Response.success(ConfigVersionDto(version = "1.0.41"))

        val result = configManager.getValidatedConfig()

        assertEquals("1.0.41", result.version)
        assertSame(cached, result)
        // Cache is authoritative → no expensive signed-bundle fetch, no local fallback.
        coVerify(exactly = 0) { configApiService.getConfigBundleRaw() }
        verify(exactly = 0) { localAssetProvider.load() }
    }

    @Test
    fun `cache below the version floor is refetched even when the probe agrees with it`() = runTest {
        // The probe agreeing is exactly the case the plain version check would serve from cache —
        // and exactly the case that leaves Base USDC on 18 decimals.
        every { cacheStore.read() } returns ConfigBundleDto(version = "1.0.40")
        coEvery { configApiService.getConfigVersion() } returns
            Response.success(ConfigVersionDto(version = "1.0.40"))
        coEvery { configApiService.getConfigBundleRaw() } returns Response.success(
            """{"version":"1.0.41"}""".toResponseBody("application/json".toMediaType())
        )
        every { signatureVerifier.verify(any()) } returns true

        val result = configManager.getValidatedConfig()

        assertEquals("1.0.41", result.version)
        coVerify(exactly = 1) { configApiService.getConfigBundleRaw() }
        verify(exactly = 1) { cacheStore.write(match { it.version == "1.0.41" }) }
    }

    @Test
    fun `cache below the version floor is not revived when the refetch fails`() = runTest {
        val localBundle = ConfigBundleDto(version = LocalConfigAssetProvider.LOCAL_VERSION)
        every { cacheStore.read() } returns ConfigBundleDto(version = "1.0.40")
        every { localAssetProvider.load() } returns localBundle
        coEvery { configApiService.getConfigBundleRaw() } throws IOException("offline")

        val result = configManager.getValidatedConfig()

        // The APK-local seed carries the corrected decimals; the rejected cache must not come back.
        assertSame(localBundle, result)
    }
}
