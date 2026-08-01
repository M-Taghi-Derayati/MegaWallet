package com.mtd.data.config

import com.google.gson.Gson
import com.mtd.data.dto.ConfigBundleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The relayer's wire shape differs from the bundled asset files: it emits `id`/`networkType` where
 * `networks.json` uses `networkId`/`type`. Both must decode, because the local seed and the server
 * bundle share one DTO.
 */
class ConfigBundleDecodingTest {

    private val gson = Gson()

    /** Trimmed to the fields that decide whether an entry survives the mapper. */
    private val relayerShape = """
        {
          "version": "1.0.18",
          "networks": [
            {
              "id": "ethereum_mainnet",
              "name": "ETHEREUM",
              "networkType": "EVM",
              "chainId": 1,
              "derivationPath": "m/44'/60'/0'/0/0",
              "currencySymbol": "ETH",
              "decimals": 18,
              "isTestnet": false
            }
          ],
          "assets": [
            {
              "id": "ETH-ETHEREUM",
              "networkId": "ethereum_mainnet",
              "symbol": "ETH",
              "name": "ETHEREUM",
              "decimals": 18
            }
          ],
          "signature": "deadbeef"
        }
    """.trimIndent()

    private val localShape = """
        {
          "version": "0.0.0-local",
          "networks": [
            {
              "networkId": "ethereum_mainnet",
              "name": "ETHEREUM",
              "type": "EVM",
              "chainId": 1,
              "derivationPath": "m/44'/60'/0'/0/0",
              "currencySymbol": "ETH",
              "decimals": 18,
              "isTestnet": false
            }
          ],
          "assets": [
            {
              "assetId": "ETH-ETHEREUM",
              "networkId": "ethereum_mainnet",
              "symbol": "ETH",
              "name": "ETHEREUM",
              "decimals": 18
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `relayer field names survive the round trip to domain configs`() {
        val bundle = gson.fromJson(relayerShape, ConfigBundleDto::class.java)

        val networks = ConfigBundleMapper.toNetworkConfigs(bundle)
        assertEquals(1, networks.size)
        assertEquals("ethereum_mainnet", networks.single().id)
        assertEquals("EVM", networks.single().networkType)

        val assets = ConfigBundleMapper.toAssetConfigs(bundle)
        assertEquals(1, assets.size)
        assertEquals("ETH-ETHEREUM", assets.single().id)
    }

    @Test
    fun `bundled asset file field names still decode`() {
        val bundle = gson.fromJson(localShape, ConfigBundleDto::class.java)

        assertEquals("ethereum_mainnet", ConfigBundleMapper.toNetworkConfigs(bundle).single().id)
        assertEquals("ETH-ETHEREUM", ConfigBundleMapper.toAssetConfigs(bundle).single().id)
    }

    @Test
    fun `an entry with no identifier under either name is dropped, not repaired`() {
        val bundle = gson.fromJson(
            """{"version":"9","networks":[{"name":"X","networkType":"EVM","derivationPath":"m/44'/60'/0'/0/0"}],"assets":[]}""",
            ConfigBundleDto::class.java
        )

        assertTrue(ConfigBundleMapper.toNetworkConfigs(bundle).isEmpty())
    }
}
