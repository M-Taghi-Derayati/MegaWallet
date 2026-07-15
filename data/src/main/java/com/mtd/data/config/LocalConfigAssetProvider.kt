package com.mtd.data.config

import android.content.Context
import com.mtd.core.utils.loadAssets
import com.mtd.core.utils.loadNetworkConfigs
import com.mtd.data.dto.ConfigAssetDto
import com.mtd.data.dto.ConfigBundleDto
import com.mtd.data.dto.ConfigNetworkDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority-3 resilience source for [ConfigManager]: reads the APK-bundled `networks.json` /
 * `assets.json` (shipped under `core/src/main/assets/`) and maps them into a [ConfigBundleDto].
 *
 * Local assets are trusted by default (they ship with the signed APK), so the bundle produced here
 * carries no signature and is never signature-verified. The sentinel [LOCAL_VERSION] guarantees it
 * can never be mistaken for a server bundle when comparing `X-Config-Version`.
 */
@Singleton
class LocalConfigAssetProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun load(): ConfigBundleDto {
        val networks = loadNetworkConfigs(context).map { cfg ->
            ConfigNetworkDto(
                networkId = cfg.id,
                name = cfg.name,
                type = cfg.networkType,
                chainId = cfg.chainId,
                isTestnet = cfg.isTestnet
            )
        }
        val assets = loadAssets(context).map { asset ->
            ConfigAssetDto(
                assetId = asset.id,
                networkId = asset.networkId,
                symbol = asset.symbol,
                name = asset.name,
                decimals = asset.decimals,
                contractAddress = asset.contractAddress
            )
        }
        return ConfigBundleDto(
            version = LOCAL_VERSION,
            networks = networks,
            assets = assets,
            signature = null
        )
    }

    companion object {
        const val LOCAL_VERSION = "0.0.0-local"
    }
}
