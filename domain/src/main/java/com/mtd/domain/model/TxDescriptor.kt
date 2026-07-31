package com.mtd.domain.model

data class TxDescriptor(
    val direction: String? = null,   // "in" (deposit) | "out" (withdraw) | "self"
    val assetKind: String? = null,   // "token" | "native"
    val asset: String? = null,       // token contract; "" for native
    val amountRaw: String? = null,   // RAW base units
    val tokenSymbol: String? = null,
    val tokenDecimal: Int? = null
)