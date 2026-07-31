package com.mtd.core.network

import com.google.gson.Gson
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.domain.model.core.NetworkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-53 — دو رفتارِ مخصوصِ زنجیره (گویش اکسپلورر و هزینهٔ دادهٔ L1) از `when (network.name)`
 * به کانفیگ منتقل شدند. این تست نگهبانِ آن انتقال است: مقادیرِ داخل networks.json باید دقیقاً
 * همان چیزی باشند که لیست‌های هاردکدِ قبلی می‌گفتند، وگرنه رفتار زنجیره‌های موجود عوض شده است.
 */
class NetworkBehaviourFlagsTest {

    private val configs: List<NetworkConfig> by lazy {
        val candidates = listOf(
            File("src/main/assets/networks.json"),
            File("core/src/main/assets/networks.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("networks.json not found; looked in ${candidates.map { it.absolutePath }}")
        Gson().fromJson(file.readText(), Array<NetworkConfig>::class.java).toList()
    }

    private fun evmConfigs() = configs.filter { it.networkType.equals("EVM", ignoreCase = true) }

    // --- گویش اکسپلورر -------------------------------------------------------

    /** لیست قبلی در EvmDataSource: BSC, BSCTESTNET -> bscscan */
    @Test
    fun `only the BSC chains use the bscscan dialect`() {
        val bscscan = evmConfigs()
            .filter { it.explorerApi == NetworkConfig.EXPLORER_API_BSCSCAN }
            .map { it.id }
            .toSet()

        assertEquals(setOf("bsc_testnet", "bsc_mainnet"), bscscan)
    }

    /** لیست قبلی: ETHEREUM, SEPOLIA, ARBITRUM, ARBSEPOLIA, BASE, BASESEPOLIA, POLTESTNET -> etherscan */
    @Test
    fun `every other EVM chain uses the etherscan dialect`() {
        val etherscan = evmConfigs()
            .filter { it.explorerApi == NetworkConfig.EXPLORER_API_ETHERSCAN }
            .map { it.id }
            .toSet()

        assertEquals(
            setOf(
                "ethereum_mainnet", "sepolia",
                "base_sepolia", "base_mainnet",
                "arb_sepolia", "arb_mainnet"
            ),
            etherscan
        )
    }

    @Test
    fun `every EVM network declares an explorer dialect explicitly`() {
        evmConfigs().forEach { config ->
            assertNotNull("${config.id} has no explorerApi", config.explorerApi)
            assertTrue(
                "${config.id} has an unknown explorerApi: ${config.explorerApi}",
                config.explorerApi in setOf(
                    NetworkConfig.EXPLORER_API_ETHERSCAN,
                    NetworkConfig.EXPLORER_API_BSCSCAN
                )
            )
        }
    }

    @Test
    fun `non-EVM networks declare no explorer dialect`() {
        configs.filterNot { it.networkType.equals("EVM", ignoreCase = true) }.forEach {
            assertEquals("${it.id} should not declare explorerApi", null, it.explorerApi)
        }
    }

    // --- هزینهٔ دادهٔ L1 (OP-Stack) -------------------------------------------

    /** لیست قبلی در EvmDataSource.isL2StackOptimism(): BASE, BASESEPOLIA */
    @Test
    fun `only the Base chains carry an L1 data fee`() {
        val opStack = configs.filter { it.hasL1DataFee }.map { it.id }.toSet()
        assertEquals(setOf("base_sepolia", "base_mainnet"), opStack)
    }

    @Test
    fun `arbitrum is not treated as OP-Stack`() {
        // آربیتروم L2 هست ولی OP-Stack نیست؛ رفتار قبلی هم همین بود.
        configs.filter { it.id.startsWith("arb_") }.forEach {
            assertFalse("${it.id} must not have an L1 data fee", it.hasL1DataFee)
        }
    }

    // --- عبور مقادیر از کانفیگ به شبکه ---------------------------------------

    @Test
    fun `GenericEvmNetwork surfaces the config flags verbatim`() {
        val config = evmConfig(explorerApi = NetworkConfig.EXPLORER_API_BSCSCAN, hasL1DataFee = true)
        val network = GenericEvmNetwork(config)

        assertEquals(NetworkConfig.EXPLORER_API_BSCSCAN, network.explorerApi)
        assertTrue(network.hasL1DataFee)
    }

    @Test
    fun `a config that omits the flags resolves to etherscan and no L1 fee`() {
        val network = GenericEvmNetwork(evmConfig(explorerApi = null, hasL1DataFee = false))

        assertEquals(null, network.explorerApi)
        assertFalse(network.hasL1DataFee)
        // EvmDataSource این null را به گویش پیش‌فرض تبدیل می‌کند، نه به «تاریخچه‌ای وجود ندارد».
        assertEquals(
            NetworkConfig.EXPLORER_API_ETHERSCAN,
            network.explorerApi ?: NetworkConfig.DEFAULT_EXPLORER_API
        )
    }

    /**
     * نکته: `name` عمداً یک ثابتِ موجودِ NetworkName است. ساختِ شبکه با نامِ ناشناخته الان
     * `NetworkName.valueOf` را می‌ترکاند — آن مسدودکننده در گامِ بعدی (هویت بر پایهٔ networkId)
     * برداشته می‌شود و تستِ «شبکهٔ فقط-در-باندل» آن‌جا اضافه می‌شود.
     */
    private fun evmConfig(explorerApi: String?, hasL1DataFee: Boolean) = NetworkConfig(
        id = "newchain_mainnet",
        name = "ETHEREUM",
        networkType = "EVM",
        chainId = 999999,
        derivationPath = "m/44'/60'/0'/0/0",
        rpcUrlsEvm = listOf("https://rpc.newchain.example"),
        rpcUrls = emptyList(),
        currencySymbol = "NEW",
        webSocketUrl = null,
        decimals = 18,
        iconUrl = "https://cdn.example/newchain.png",
        explorers = listOf("https://scan.newchain.example/"),
        explorerApi = explorerApi,
        hasL1DataFee = hasL1DataFee
    )
}
