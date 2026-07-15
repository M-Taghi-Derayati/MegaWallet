package com.mtd.data.config

import com.mtd.domain.interfaceRepository.ICapabilityProvider
import com.mtd.domain.model.capability.AvailabilitySource
import com.mtd.domain.model.capability.CapabilitySnapshot
import com.mtd.domain.model.capability.FeatureAvailabilityContext
import com.mtd.domain.model.capability.FeatureReasonCodes
import com.mtd.domain.model.capability.GaslessCapability
import com.mtd.domain.model.capability.NetworkCapability
import com.mtd.domain.model.capability.SponsorCapability
import com.mtd.domain.model.core.NetworkType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Capability Platform (Android Migration, Phase A) — verifies the decision matrix of
 * FeatureAvailabilityResolver: capability-authoritative (can hide e.g. BSC), per-token
 * veto, and the legacy fallback that preserves today's behavior when capability is offline.
 */
class FeatureAvailabilityResolverTest {

    private lateinit var capabilityProvider: ICapabilityProvider
    private lateinit var resolver: FeatureAvailabilityResolver

    @Before
    fun setUp() {
        capabilityProvider = mockk()
        resolver = FeatureAvailabilityResolver(capabilityProvider)
    }

    private fun snapshotWith(vararg networks: NetworkCapability) =
        CapabilitySnapshot(version = "1.0.4", etag = "\"e\"", fetchedAtEpochMs = 1L, networks = networks.toList())

    private fun ctx(
        networkId: String = "base_mainnet",
        networkType: NetworkType? = NetworkType.EVM,
        tokenId: String? = "0xToken",
        tokenGaslessEnabled: Boolean? = null,
        tokenSponsorEnabled: Boolean? = null,
        eligibilityAllowed: Boolean? = null
    ) = FeatureAvailabilityContext(
        networkId = networkId, networkType = networkType, tokenId = tokenId,
        tokenGaslessEnabled = tokenGaslessEnabled, tokenSponsorEnabled = tokenSponsorEnabled,
        eligibilityAllowed = eligibilityAllowed
    )

    @Test
    fun `native token is never gasless (client rule)`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns CapabilitySnapshot.EMPTY
        val r = resolver.isGaslessAvailable(ctx(tokenId = null))
        assertFalse(r.available)
        assertEquals(FeatureReasonCodes.NATIVE_TOKEN_NOT_SUPPORTED, r.reasonCode)
        assertEquals(AvailabilitySource.CLIENT_RULE, r.source)
    }

    @Test
    fun `capability-available + token-enabled is available from CAPABILITY`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("base_mainnet", chainId = 8453, relayPrefix = "base", gasless = GaslessCapability(true, true, "OK"))
        )
        val r = resolver.isGaslessAvailable(ctx(tokenGaslessEnabled = true))
        assertTrue(r.available)
        assertEquals(AvailabilitySource.CAPABILITY, r.source)
        assertEquals("base", r.relayPrefix)
    }

    @Test
    fun `capability hides gasless even if token says enabled (BSC-style)`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("bsc_mainnet", chainId = 56, relayPrefix = "bsc", gasless = GaslessCapability(false, true, "SERVICE_DOWN"))
        )
        val r = resolver.isGaslessAvailable(ctx(networkId = "bsc_mainnet", tokenGaslessEnabled = true))
        assertFalse(r.available)
        assertEquals(FeatureReasonCodes.SERVICE_DOWN, r.reasonCode)
        assertEquals(AvailabilitySource.CAPABILITY, r.source)
    }

    @Test
    fun `capability-available but token disabled is vetoed by TOKEN_POLICY`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("base_mainnet", gasless = GaslessCapability(true, true, "OK"))
        )
        val r = resolver.isGaslessAvailable(ctx(tokenGaslessEnabled = false))
        assertFalse(r.available)
        assertEquals(FeatureReasonCodes.TOKEN_DISABLED, r.reasonCode)
        assertEquals(AvailabilitySource.TOKEN_POLICY, r.source)
    }

    @Test
    fun `offline capability falls back to legacy EVM + token-enabled`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns CapabilitySnapshot.EMPTY
        val r = resolver.isGaslessAvailable(ctx(tokenGaslessEnabled = true))
        assertTrue(r.available)
        assertEquals(AvailabilitySource.FALLBACK_LEGACY, r.source)
    }

    @Test
    fun `offline capability on a non-EVM-TVM network is unsupported (legacy rule)`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns CapabilitySnapshot.EMPTY
        val r = resolver.isGaslessAvailable(ctx(networkType = NetworkType.BITCOIN, tokenGaslessEnabled = true))
        assertFalse(r.available)
        assertEquals(FeatureReasonCodes.NETWORK_TYPE_UNSUPPORTED, r.reasonCode)
    }

    @Test
    fun `sponsor is DEPENDENCY_DOWN when gasless unavailable`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("bsc_mainnet", gasless = GaslessCapability(false, true, "SERVICE_DOWN"), sponsor = SponsorCapability(true, true, "OK"))
        )
        val r = resolver.isSponsorAvailable(ctx(networkId = "bsc_mainnet", tokenGaslessEnabled = true, tokenSponsorEnabled = true))
        assertFalse(r.available)
        assertEquals(FeatureReasonCodes.DEPENDENCY_DOWN, r.reasonCode)
    }

    @Test
    fun `sponsor available when gasless+sponsor capability available and token sponsor enabled`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("base_mainnet", relayPrefix = "base", gasless = GaslessCapability(true, true, "OK"), sponsor = SponsorCapability(true, true, "OK"))
        )
        val r = resolver.isSponsorAvailable(ctx(tokenGaslessEnabled = true, tokenSponsorEnabled = true))
        assertTrue(r.available)
        assertEquals(AvailabilitySource.CAPABILITY, r.source)
    }

    @Test
    fun `swap stays available when capability does not model it (legacy)`() = runTest {
        coEvery { capabilityProvider.getCapabilities() } returns CapabilitySnapshot.EMPTY
        val r = resolver.isSwapAvailable(ctx())
        assertTrue(r.available)
        assertEquals(AvailabilitySource.FALLBACK_LEGACY, r.source)
    }

    // --- Phase B wiring contract: the SendViewModel maps matched.note → note and
    //     a missing /tokens match → tokenGaslessEnabled=false. Lock both.
    @Test
    fun `available decision carries the token note through (capability + legacy)`() = runTest {
        val withNote = ctx(tokenGaslessEnabled = true).copy(tokenNote = "نکته")
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("base_mainnet", gasless = GaslessCapability(true, true, "OK"))
        )
        assertEquals("نکته", resolver.isGaslessAvailable(withNote).note)

        coEvery { capabilityProvider.getCapabilities() } returns CapabilitySnapshot.EMPTY
        assertEquals("نکته", resolver.isGaslessAvailable(withNote).note)
    }

    @Test
    fun `no token match (tokenGaslessEnabled false) is unavailable in both modes`() = runTest {
        // capability-available network, but the token isn't enabled → vetoed
        coEvery { capabilityProvider.getCapabilities() } returns snapshotWith(
            NetworkCapability("base_mainnet", gasless = GaslessCapability(true, true, "OK"))
        )
        assertFalse(resolver.isGaslessAvailable(ctx(tokenGaslessEnabled = false)).available)
        // offline → legacy path, same outcome
        coEvery { capabilityProvider.getCapabilities() } returns CapabilitySnapshot.EMPTY
        assertFalse(resolver.isGaslessAvailable(ctx(tokenGaslessEnabled = false)).available)
    }
}
