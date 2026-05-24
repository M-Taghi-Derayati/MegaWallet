package com.mtd.domain.model

const val HISTORY_ALL_NETWORKS_OPTION_ID = "history_all_networks"

enum class HistoryNetworkOptionType {
    ALL,
    NETWORK
}

data class HistoryNetworkOption(
    val id: String,
    val type: HistoryNetworkOptionType = HistoryNetworkOptionType.NETWORK,
    val networkName: String,
    val networkFaName: String?,
    val networkId: String,
    val symbol: String,
    val address: String,
    val iconUrl: String?,
    val isSelected: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val lastUpdatedAt: Long? = null
) {
    val isAllNetworks: Boolean
        get() = type == HistoryNetworkOptionType.ALL
}
