package com.mtd.domain.model

import java.math.BigDecimal
import java.math.BigInteger

data class UiAsset(
    val name: String,
    val symbol: String,
    val decimals: Int,
    val balance: BigDecimal,
    val networkName: String,
    val iconUrl: String?,
    val contractAddress: String?
)

