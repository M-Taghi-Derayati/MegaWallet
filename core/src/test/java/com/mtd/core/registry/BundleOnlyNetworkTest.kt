package com.mtd.core.registry

import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.domain.model.core.NetworkConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-53 — «زنجیرهٔ فقط-در-باندل»: شبکه‌ای که سرور اضافه کرده و هیچ ثابتی در [NetworkName] ندارد.
 *
 * قبلاً `NetworkName.valueOf(config.name)` روی چنین ورودی‌ای `IllegalArgumentException` پرتاب می‌کرد،
 * یعنی شبکه یا حذف می‌شد یا کلِ بارگذاریِ کاتالوگ می‌ترکید. این تست همان دروازهٔ مکانیکی را می‌بندد.
 */
class BundleOnlyNetworkTest {

    private fun bundleEvmConfig(
        id: String = "newchain_mainnet",
        name: String = "NEWCHAIN",
        chainId: Long = 777_777L
    ) = NetworkConfig(
        id = id,
        name = name,
        networkType = "EVM",
        chainId = chainId,
        derivationPath = "m/44'/60'/0'/0/0",
        rpcUrlsEvm = listOf("https://rpc.newchain.example"),
        rpcUrls = emptyList(),
        currencySymbol = "NEW",
        webSocketUrl = null,
        decimals = 18,
        regex = "/^0x[a-fA-F0-9]{40}$/",
        iconUrl = "https://cdn.example/newchain.png",
        explorers = listOf("https://scan.newchain.example/")
    )

    // --- alias -----------------------------------------------------------

    @Test
    fun `fromConfigName returns null for an unknown name instead of throwing`() {
        assertNull(NetworkName.fromConfigName("NEWCHAIN"))
        assertNull(NetworkName.fromConfigName(null))
        assertNull(NetworkName.fromConfigName("   "))
    }

    @Test
    fun `fromConfigName still resolves known names, case-insensitively`() {
        assertEquals(NetworkName.SEPOLIA, NetworkName.fromConfigName("SEPOLIA"))
        assertEquals(NetworkName.SEPOLIA, NetworkName.fromConfigName("sepolia"))
        assertEquals(NetworkName.BSCTESTNET, NetworkName.fromConfigName(" bscTestnet "))
    }

    // --- construction ----------------------------------------------------

    @Test
    fun `a bundle-only EVM network is constructible and keeps its identity`() {
        val network = GenericEvmNetwork(bundleEvmConfig())

        assertEquals("newchain_mainnet", network.id)
        assertNull("alias must be null, not a fabricated constant", network.name)
        assertEquals(NetworkType.EVM, network.networkType)
        assertEquals(777_777L, network.chainId)
        assertEquals(18, network.decimals)
        assertEquals("NEW", network.currencySymbol)
    }

    @Test
    fun `a bundle-only network derives a real address from a mnemonic`() {
        // همان mnemonic استانداردِ تستی؛ آدرس EVM فقط به derivationPath بستگی دارد، نه به نام شبکه.
        val mnemonic =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        val bundleOnly = GenericEvmNetwork(bundleEvmConfig())
        val known = GenericEvmNetwork(bundleEvmConfig(id = "sepolia", name = "SEPOLIA", chainId = 11155111L))

        val key = bundleOnly.deriveKeyFromMnemonic(mnemonic)

        assertTrue("address must be a 0x EVM address", key.address.startsWith("0x"))
        assertEquals(42, key.address.length)
        assertEquals("newchain_mainnet", key.networkId)
        assertNull(key.networkName)
        // مسیر استخراج یکی است، پس آدرس باید با یک زنجیرهٔ EVM شناخته‌شده یکی باشد.
        assertEquals(known.deriveKeyFromMnemonic(mnemonic).address, key.address)
    }

    // --- registry --------------------------------------------------------

    @Test
    fun `registry indexes a bundle-only network by id and chainId`() {
        val registry = BlockchainRegistry()
        registry.registerNetwork(GenericEvmNetwork(bundleEvmConfig()))

        assertNotNull(registry.getNetworkById("newchain_mainnet"))
        assertNotNull(registry.getNetworkByChainId(777_777L))
        assertEquals(NetworkType.EVM, registry.getNetworkTypeForNetworkId("newchain_mainnet"))
        assertEquals(
            "newchain_mainnet",
            registry.getNetworkInfoById("newchain_mainnet")?.id
        )
    }

    @Test
    fun `a null alias never collides with another aliasless network`() {
        val registry = BlockchainRegistry()
        registry.registerNetwork(GenericEvmNetwork(bundleEvmConfig()))
        registry.registerNetwork(
            GenericEvmNetwork(bundleEvmConfig(id = "othchain_mainnet", name = "OTHCHAIN", chainId = 888_888L))
        )

        // هر دو alias نال دارند؛ اگر جایی با نام کلید می‌خورد، این‌ها روی هم می‌افتادند.
        assertEquals(2, registry.getAllNetworks().count { it.name == null })
        assertEquals("newchain_mainnet", registry.getNetworkById("newchain_mainnet")?.id)
        assertEquals("othchain_mainnet", registry.getNetworkById("othchain_mainnet")?.id)
        assertEquals(777_777L, registry.getNetworkByChainId(777_777L)?.chainId)
        assertEquals(888_888L, registry.getNetworkByChainId(888_888L)?.chainId)
    }

    @Test
    fun `lookup by a known alias still works alongside aliasless networks`() {
        val registry = BlockchainRegistry()
        registry.registerNetwork(GenericEvmNetwork(bundleEvmConfig()))
        registry.registerNetwork(
            GenericEvmNetwork(bundleEvmConfig(id = "sepolia", name = "SEPOLIA", chainId = 11155111L))
        )

        assertEquals("sepolia", registry.getNetworkByName(NetworkName.SEPOLIA)?.id)
        assertEquals("sepolia", registry.getNetworkInfoByName(NetworkName.SEPOLIA)?.id)
    }

    @Test
    fun `address validation works for a bundle-only network via its config regex`() {
        val registry = BlockchainRegistry()
        registry.registerNetwork(GenericEvmNetwork(bundleEvmConfig()))

        // regex ایندکس نشده (indexAddressRegex فقط در مسیر بارگذاری اجرا می‌شود)، پس اعتبارسنجی
        // باید از روی خانوادهٔ شبکه جواب بدهد، نه از روی نامِ آن.
        assertTrue(
            registry.isValidAddressForNetworkId(
                "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
                "newchain_mainnet"
            )
        )
    }
}
