package com.mtd.core.assets

data class AssetConfig(
    val id: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val networkId: String,
    val contractAddress: String?,
    val coinGeckoId: String?,
    val iconUrl: String?
)