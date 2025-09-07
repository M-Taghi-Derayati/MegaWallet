package com.mtd.domain.model

// این مدل یک جفت ارز قابل معامله را نمایش می‌دهد
data class SwapPair(
    val fromAssetId: String,       // شناسه کامل دارایی مبدا، e.g., "USDT-11155111" (symbol-chainId)
    val fromAssetName: String,     // "Tether USD"
    val fromAssetSymbol: String,   // "USDT"
    val fromAssetIconUrl: String,
    val fromNetworkName: String,   // "Sepolia"
    val fromNetworkIconUrl: String,

    val toAssetId: String,         // شناسه کامل دارایی مقصد، e.g., "MATIC-80001"
    val toAssetName: String,       // "Polygon"
    val toAssetSymbol: String,     // "MATIC"
    val toAssetIconUrl: String,
    val toNetworkName: String,     // "Mumbai"
    val toNetworkIconUrl: String
)