package com.mtd.domain.model

import java.math.BigInteger

data class UiAsset(
    val name: String,
    val symbol: String,
    val decimals: Int,
    val balance: BigInteger,
    val networkName: String,
    val iconUrl: String?,
    val coinGeckoId: String?,
    val contractAddress: String?
)

