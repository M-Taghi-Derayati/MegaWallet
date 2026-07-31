package com.mtd.core.registry

import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import com.mtd.domain.model.core.NetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-53 — ثبت در برابر نمایش.
 *
 * فیلترِ قدیمیِ `isTestnet == true` در زمانِ **ثبت** اعمال می‌شد، پس هر شبکهٔ mainnet حتی از فایل
 * محلی هم حذف می‌شد و اصلاً قابل resolve نبود. حالا همه ثبت می‌شوند و انتخابِ کاربر فقط روی
 * فهرست‌های UI اثر دارد. این تست همان مرز را قفل می‌کند: خاموش‌کردنِ تست‌نت‌ها هرگز نباید یک
 * شبکه را از دسترسِ هویتی خارج کند، چون کلیدسازی و مسیریابیِ ارسال از همان مسیر می‌آیند.
 */
class TestnetVisibilityTest {

    private class FakeTestnetVisibility(var show: Boolean) : ITestnetVisibilityProvider {
        override fun showTestnets(): Boolean = show
    }

    private fun evm(id: String, name: String, chainId: Long, isTestnet: Boolean) =
        GenericEvmNetwork(
            NetworkConfig(
                id = id,
                name = name,
                networkType = "EVM",
                chainId = chainId,
                derivationPath = "m/44'/60'/0'/0/0",
                rpcUrlsEvm = listOf("https://rpc.example"),
                rpcUrls = emptyList(),
                currencySymbol = "ETH",
                webSocketUrl = null,
                decimals = 18,
                iconUrl = "https://cdn.example/icon.png",
                explorers = emptyList(),
                isTestnet = isTestnet
            )
        )

    private fun registryWith(show: Boolean): Pair<BlockchainRegistry, FakeTestnetVisibility> {
        val toggle = FakeTestnetVisibility(show)
        val registry = BlockchainRegistry(toggle)
        registry.registerNetwork(evm("ethereum_mainnet", "ETHEREUM", 1L, isTestnet = false))
        registry.registerNetwork(evm("sepolia", "SEPOLIA", 11155111L, isTestnet = true))
        return registry to toggle
    }

    // --- ثبت: همیشه کامل، مستقل از کلید -------------------------------------

    @Test
    fun `every config is registered regardless of the toggle`() {
        listOf(true, false).forEach { show ->
            val (registry, _) = registryWith(show)
            assertEquals("showTestnets=$show", 2, registry.getAllNetworks().size)
        }
    }

    @Test
    fun `getNetworkById resolves a testnet even when the toggle is off`() {
        val (registry, _) = registryWith(show = false)

        assertNotNull(registry.getNetworkById("sepolia"))
        assertEquals("sepolia", registry.getNetworkById("sepolia")?.id)
        assertEquals("sepolia", registry.getNetworkInfoById("sepolia")?.id)
    }

    @Test
    fun `getNetworkByChainId resolves a testnet even when the toggle is off`() {
        val (registry, _) = registryWith(show = false)
        assertEquals("sepolia", registry.getNetworkByChainId(11155111L)?.id)
    }

    // --- فهرست: تابعِ کلید ---------------------------------------------------

    @Test
    fun `the listing API hides testnets when the toggle is off`() {
        val (registry, _) = registryWith(show = false)
        val listed = registry.getAllNetworkInfos().map { it.id }

        assertEquals(listOf("ethereum_mainnet"), listed)
    }

    @Test
    fun `the listing API shows both when the toggle is on`() {
        val (registry, _) = registryWith(show = true)
        val listed = registry.getAllNetworkInfos().map { it.id }.toSet()

        assertEquals(setOf("ethereum_mainnet", "sepolia"), listed)
    }

    @Test
    fun `flipping the toggle changes the listing without re-registering anything`() {
        val (registry, toggle) = registryWith(show = false)
        assertEquals(1, registry.getAllNetworkInfos().size)

        toggle.show = true
        assertEquals("must react without reloading networks.json", 2, registry.getAllNetworkInfos().size)

        toggle.show = false
        assertEquals(1, registry.getAllNetworkInfos().size)

        // و ثبت در هیچ‌کدام از حالت‌ها دست نخورده است.
        assertEquals(2, registry.getAllNetworks().size)
    }

    @Test
    fun `mainnets are listed at all, which the old registration-time filter prevented`() {
        val (registry, _) = registryWith(show = true)
        assertTrue(registry.getAllNetworkInfos().any { it.id == "ethereum_mainnet" })
    }

    // --- تصادفِ chainId -----------------------------------------------------

    @Test
    fun `a chainId collision keeps the first registration and never silently overwrites`() {
        // doge_mainnet و doge_testnet در networks.json هر دو chainId=3 دارند. فیلترِ قدیمی این را
        // پنهان می‌کرد؛ حالا که هر دو ثبت می‌شوند، مسیریابیِ chainId نباید بی‌صدا عوض شود.
        val registry = BlockchainRegistry(FakeTestnetVisibility(true))
        registry.registerNetwork(evm("first_chain", "ETHEREUM", 3L, isTestnet = false))
        registry.registerNetwork(evm("second_chain", "SEPOLIA", 3L, isTestnet = true))

        assertEquals("first registration wins", "first_chain", registry.getNetworkByChainId(3L)?.id)
        // هر دو همچنان با networkId قابل resolve هستند.
        assertNotNull(registry.getNetworkById("first_chain"))
        assertNotNull(registry.getNetworkById("second_chain"))
    }
}
