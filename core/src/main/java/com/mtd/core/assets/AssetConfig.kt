package com.mtd.core.assets

data class AssetConfig(
    val id: String,
    val name: String,
    var symbol: String,
    val decimals: Int,
    val networkId: String,
    val contractAddress: String?,
    val iconUrl: String?,
    val faName: String? = null
)