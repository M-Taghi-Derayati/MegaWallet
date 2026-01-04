package com.mtd.domain.model

data class USDTMarketStatsResponse(
    val status: String,
    val stats: Map<String, USDTStats>,
    val global: Any?
)

data class USDTStats(
    val isClosed: Boolean?,
    val bestSell: String?,
    val bestBuy: String?,
    val volumeSrc: String?,
    val volumeDst: String?,
    val latest: String?,
    val dayLow: String?,
    val dayHigh: String?,
    val dayOpen: String?,
    val dayClose: String?,
    val dayChange: String?
)
