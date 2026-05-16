package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.assets.AssetConfig

interface IAssetCatalog {
    fun getAllAssetConfigs(): List<AssetConfig>
    fun getAssetConfigById(id: String): AssetConfig?
    fun getAssetConfigsForNetwork(networkId: String): List<AssetConfig>
}
