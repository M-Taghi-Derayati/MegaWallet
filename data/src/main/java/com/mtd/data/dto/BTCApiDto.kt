package com.mtd.data.dto

data class UtxoDto(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: StatusDto
)
data class StatusDto(
    val confirmed: Boolean,
    val block_height: Long?,
    val block_hash: String?,
    val block_time: Long?
)
data class AddressDto(
    val address: String,
    val chain_stats: AddressStatsDto,
    val mempool_stats: AddressStatsDto
)
data class AddressStatsDto(
    val funded_txo_count: Int,
    val funded_txo_sum: Long,
    val spent_txo_count: Int,
    val spent_txo_sum: Long,
    val tx_count: Int
)
data class TxDto(
    val txid: String,
    val fee: Long?,
    val status: StatusDto,
    val vin: List<VinDto>,
    val vout: List<VoutDto>
)
data class VinDto(
    val prevout: VoutDto?
)
data class VoutDto(
    val scriptpubkey_address: String?,
    val value: Long
)
data class FeeRecommendationDto(
    val fastestFee: Int,
    val halfHourFee: Int,
    val hourFee: Int,
    val economyFee: Int,
    val minimumFee: Int,
)