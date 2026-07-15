package com.mtd.data.dto

data class BatchBalanceRequestDto(
    val wallets: List<BatchBalanceWalletDto>
)

data class BatchBalanceWalletDto(
    val networkId: String,
    val address: String,
    val assetIds: List<String>? = null
)

data class BatchNetworkBalanceDto(
    val status: String? = null,                 // "ok" | "error"
    val balances: List<ProxyBalanceDto>? = null,
    val error: ProxyErrorDto? = null
)
