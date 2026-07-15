package com.mtd.data.repository.gasless

import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.domain.interfaceRepository.ICapabilityProvider
import com.mtd.domain.model.capability.NetworkCapability
import com.mtd.domain.model.core.NetworkType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Gasless Routing Migration (Phase 1) — verifies routing is data-driven (relayPrefix
 * from capability, family from the registry), with no hardcoded chain assumptions, and
 * that unroutable cases (no relayer / non-gasless family / unknown network / errors)
 * resolve to null without throwing.
 */
class GaslessRouteResolverTest {

    private lateinit var capabilityProvider: ICapabilityProvider
    private lateinit var registry: BlockchainRegistry
    private lateinit var resolver: GaslessRouteResolver

    @Before
    fun setUp() {
        capabilityProvider = mockk()
        registry = mockk()
        resolver = GaslessRouteResolver(capabilityProvider, registry)
    }

    private fun stubNetwork(networkId: String, type: NetworkType?) {
        if (type == null) {
            every { registry.getNetworkById(networkId) } returns null
        } else {
            val net = mockk<BlockchainNetwork>()
            every { net.networkType } returns type
            every { registry.getNetworkById(networkId) } returns net
        }
    }

    private fun stubCapability(networkId: String, relayPrefix: String?) {
        coEvery { capabilityProvider.getNetworkCapability(networkId) } returns
            NetworkCapability(networkId = networkId, relayPrefix = relayPrefix)
    }

    @Test
    fun `ethereum EVM resolves to evm`() = runTest {
        stubNetwork("ethereum_mainnet", NetworkType.EVM)
        stubCapability("ethereum_mainnet", "evm")
        val route = resolver.resolve("ethereum_mainnet")!!
        assertEquals("evm", route.relayPrefix)
        assertEquals(NetworkType.EVM, route.networkType)
    }

    @Test
    fun `BSC resolves to bsc (data-driven, not enum)`() = runTest {
        stubNetwork("bsc_mainnet", NetworkType.EVM)
        stubCapability("bsc_mainnet", "bsc")
        val route = resolver.resolve("bsc_mainnet")!!
        assertEquals("bsc", route.relayPrefix)
        assertEquals(NetworkType.EVM, route.networkType)
    }

    @Test
    fun `TRON TVM resolves to tron`() = runTest {
        stubNetwork("tron_mainnet", NetworkType.TVM)
        stubCapability("tron_mainnet", "tron")
        val route = resolver.resolve("tron_mainnet")!!
        assertEquals("tron", route.relayPrefix)
        assertEquals(NetworkType.TVM, route.networkType)
    }

    @Test
    fun `no relayer (null relayPrefix) yields no route`() = runTest {
        stubNetwork("base_mainnet", NetworkType.EVM)
        stubCapability("base_mainnet", null)
        assertNull(resolver.resolve("base_mainnet"))
    }

    @Test
    fun `blank relayPrefix yields no route`() = runTest {
        stubNetwork("base_mainnet", NetworkType.EVM)
        stubCapability("base_mainnet", "   ")
        assertNull(resolver.resolve("base_mainnet"))
    }

    @Test
    fun `non-gasless family (BITCOIN) yields no route even with a relayPrefix`() = runTest {
        stubNetwork("bitcoin_mainnet", NetworkType.BITCOIN)
        stubCapability("bitcoin_mainnet", "btc")
        assertNull(resolver.resolve("bitcoin_mainnet"))
    }

    @Test
    fun `unknown network (registry null) yields no route`() = runTest {
        stubNetwork("nope", null)
        assertNull(resolver.resolve("nope"))
    }

    @Test
    fun `capability error is swallowed and yields no route (never throws)`() = runTest {
        stubNetwork("ethereum_mainnet", NetworkType.EVM)
        coEvery { capabilityProvider.getNetworkCapability("ethereum_mainnet") } throws RuntimeException("boom")
        assertNull(resolver.resolve("ethereum_mainnet"))
    }
}
