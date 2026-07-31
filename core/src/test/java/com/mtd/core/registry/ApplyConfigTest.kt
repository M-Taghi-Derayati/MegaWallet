package com.mtd.core.registry

import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import com.mtd.domain.model.assets.AssetConfig
import com.mtd.domain.model.core.NetworkConfig
import com.mtd.domain.model.core.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-53 — `applyConfig` روی رجیستری‌ها: همان چیزی که «شبکهٔ فقط-در-باندل» را ممکن می‌کند.
 *
 * تأییدِ امضا وظیفهٔ لایهٔ بالاتر (ConfigManager / ConfigCatalogBootstrapper) است؛ این‌جا
 * قراردادِ خودِ رجیستری آزموده می‌شود: چه چیزی پذیرفته می‌شود، چه چیزی رد، و این‌که بازسازی
 * هرگز هویتِ محلی را بی‌صدا عوض نکند.
 */
class ApplyConfigTest {

    private val showAll = ITestnetVisibilityProvider { true }

    private fun evmConfig(
        id: String,
        name: String = "ETHEREUM",
        chainId: Long = 1L,
        derivationPath: String = "m/44'/60'/0'/0/0",
        regex: String? = "/^0x[a-fA-F0-9]{40}$/",
        isTestnet: Boolean = false
    ) = NetworkConfig(
        id = id,
        name = name,
        networkType = "EVM",
        chainId = chainId,
        derivationPath = derivationPath,
        rpcUrlsEvm = listOf("https://rpc.example"),
        rpcUrls = emptyList(),
        currencySymbol = "ETH",
        webSocketUrl = null,
        decimals = 18,
        regex = regex,
        iconUrl = "https://cdn.example/i.png",
        explorers = emptyList(),
        isTestnet = isTestnet
    )

    private fun asset(id: String, networkId: String, contract: String?) = AssetConfig(
        id = id,
        name = "Tether",
        symbol = "USDT",
        decimals = 6,
        networkId = networkId,
        contractAddress = contract,
        iconUrl = null
    )

    // --- شبکهٔ فقط-در-باندل ---------------------------------------------------

    @Test
    fun `applyConfig registers a bundle-only EVM network that is not in the enum`() {
        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(
            listOf(evmConfig(id = "newchain_mainnet", name = "NEWCHAIN", chainId = 777_777L))
        )

        val network = registry.getNetworkById("newchain_mainnet")
        assertNotNull("a chain absent from NetworkName must still register", network)
        assertNull(network?.name)
        assertEquals(NetworkType.EVM, network?.networkType)
        assertEquals(777_777L, registry.getNetworkByChainId(777_777L)?.chainId)
    }

    @Test
    fun `applyConfig replaces rather than merges`() {
        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(evmConfig("old_chain", chainId = 1L)))
        registry.applyConfig(listOf(evmConfig("new_chain", chainId = 2L)))

        assertNull("a network absent from the new bundle must be gone", registry.getNetworkById("old_chain"))
        assertNotNull(registry.getNetworkById("new_chain"))
        assertEquals(1, registry.getAllNetworks().size)
    }

    @Test
    fun `address regex from the bundle is indexed so validation works`() {
        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(evmConfig("newchain_mainnet", name = "NEWCHAIN", chainId = 9L)))

        assertTrue(
            registry.isValidAddressForNetworkId(
                "0x9858EfFD232B4033E47d90003D41EC34EcaEda94",
                "newchain_mainnet"
            )
        )
    }

    // --- محافظِ هویت ---------------------------------------------------------

    @Test
    fun `a bundle cannot change the chainId of a locally shipped network`() {
        val local = evmConfig("sepolia", name = "SEPOLIA", chainId = 11155111L, isTestnet = true)
        val tampered = local.copy(chainId = 1L) // اتریوم mainnet!

        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(tampered), trustedBaseline = listOf(local))

        assertEquals(
            "the locally shipped chainId must win",
            11155111L,
            registry.getNetworkById("sepolia")?.chainId
        )
        assertNull("the tampered chainId must not be routable", registry.getNetworkByChainId(1L))
    }

    @Test
    fun `a bundle cannot change the derivation path of a locally shipped network`() {
        val local = evmConfig("sepolia", name = "SEPOLIA", chainId = 11155111L)
        val tampered = local.copy(derivationPath = "m/44'/0'/0'/0/0")

        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(tampered), trustedBaseline = listOf(local))

        assertEquals("m/44'/60'/0'/0/0", registry.getNetworkById("sepolia")?.derivationPath)
    }

    @Test
    fun `a bundle cannot change the address regex of a locally shipped network`() {
        val local = evmConfig("sepolia", name = "SEPOLIA", chainId = 11155111L)
        val tampered = local.copy(regex = "/^.*$/") // «هر رشته‌ای آدرس معتبر است»

        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(tampered), trustedBaseline = listOf(local))

        assertTrue(
            "a bogus address must still be rejected",
            !registry.isValidAddressForNetworkId("definitely-not-an-address", "sepolia")
        )
    }

    @Test
    fun `non-identity fields of a known network may still be updated`() {
        val local = evmConfig("sepolia", name = "SEPOLIA", chainId = 11155111L)
        val updated = local.copy(
            rpcUrlsEvm = listOf("https://new-rpc.example"),
            iconUrl = "https://cdn.example/new.png"
        )

        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(updated), trustedBaseline = listOf(local))

        val network = registry.getNetworkById("sepolia")
        assertEquals(listOf("https://new-rpc.example"), network?.RpcUrlsEvm)
        assertEquals("https://cdn.example/new.png", network?.iconUrl)
    }

    // --- دارایی‌ها -----------------------------------------------------------

    @Test
    fun `a bundle cannot repoint a locally shipped asset to another contract`() {
        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(evmConfig("sepolia", name = "SEPOLIA", chainId = 11155111L)))
        val assets = AssetRegistry(registry)

        val local = asset("USDT-SEPOLIA", "sepolia", "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        val tampered = local.copy(contractAddress = "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")

        assets.applyConfig(listOf(tampered), trustedBaseline = listOf(local))

        assertEquals(
            "the locally shipped contract must win",
            "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            assets.getAssetById("USDT-SEPOLIA")?.contractAddress
        )
    }

    @Test
    fun `a brand-new asset from the bundle is accepted`() {
        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(evmConfig("newchain_mainnet", name = "NEWCHAIN", chainId = 5L)))
        val assets = AssetRegistry(registry)

        assets.applyConfig(listOf(asset("NEW-TOKEN", "newchain_mainnet", "0xCCCC")))

        assertNotNull(assets.getAssetById("NEW-TOKEN"))
        assertEquals(1, assets.getAssetsForNetwork("newchain_mainnet").size)
    }

    @Test
    fun `assets on unregistered networks are dropped`() {
        val registry = BlockchainRegistry(showAll)
        registry.applyConfig(listOf(evmConfig("sepolia", name = "SEPOLIA", chainId = 11155111L)))
        val assets = AssetRegistry(registry)

        assets.applyConfig(listOf(asset("GHOST", "no_such_network", null)))

        assertNull(assets.getAssetById("GHOST"))
    }
}
