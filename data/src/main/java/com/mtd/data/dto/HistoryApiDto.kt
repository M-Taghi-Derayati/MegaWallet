package com.mtd.data.dto

/**
 * Request/response DTOs for the unified history endpoint (`POST /api/mobile/v1/history`).
 * This endpoint is NOT BM-33-enveloped — it returns `{ items, pageInfo, staleSources? }` directly.
 */
data class HistoryAddressDto(
    val networkId: String,
    val address: String
)

data class HistoryRequestDto(
    val addresses: List<HistoryAddressDto>,
    val cursor: String? = null,
    val limit: Int? = null
)

data class HistoryPageInfoDto(
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

data class HistoryStaleSourceDto(
    val networkId: String? = null,
    val address: String? = null,
    val error: String? = null
)

data class HistoryResponseDto(
    val items: List<HistoryItemDto> = emptyList(),
    val pageInfo: HistoryPageInfoDto = HistoryPageInfoDto(),
    val staleSources: List<HistoryStaleSourceDto>? = null
)
