package com.mtd.domain.usecase.history

import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.HISTORY_ALL_NETWORKS_OPTION_ID
import com.mtd.domain.model.HistoryNetworkOption
import com.mtd.domain.model.HistoryNetworkOptionType
import com.mtd.domain.model.core.WalletKey
import java.util.Locale
import javax.inject.Inject

class BuildHistoryNetworkOptionsUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val assetCatalog: IAssetCatalog
) {
    operator fun invoke(
        keys: List<WalletKey>,
        selectedId: String?,
        refreshingIds: Set<String>,
        lastRefreshTimes: Map<String, Long>,
        now: Long,
        staleAfterMs: Long
    ): List<HistoryNetworkOption> {
        // TASK-53 — گروه‌بندی و شناسهٔ گزینه‌ها با networkId کانونی، نه با alias قدیمی.
        val sources = keys
            .distinctBy { key -> key.networkId to key.address.lowercase(Locale.US) }
        val sourceIds = sources.map { key -> optionId(key.networkId, key.address) }

        val resolvedSelectedId = selectedId
            ?.takeIf { id -> id == HISTORY_ALL_NETWORKS_OPTION_ID || id in sourceIds }
            ?: HISTORY_ALL_NETWORKS_OPTION_ID

        val allLastUpdatedAt = sourceIds
            .mapNotNull { id -> lastRefreshTimes[id] }
            .minOrNull()
        val hasStaleNetwork = sourceIds.any { id ->
            lastRefreshTimes[id]?.let { now - it > staleAfterMs } == true && id !in refreshingIds
        }
        val allOption = HistoryNetworkOption(
            id = HISTORY_ALL_NETWORKS_OPTION_ID,
            type = HistoryNetworkOptionType.ALL,
            networkName = "",
            networkFaName = null,
            networkId = "",
            symbol = "",
            address = "",
            iconUrl = null,
            isSelected = resolvedSelectedId == HISTORY_ALL_NETWORKS_OPTION_ID,
            isRefreshing = HISTORY_ALL_NETWORKS_OPTION_ID in refreshingIds,
            isStale = hasStaleNetwork && HISTORY_ALL_NETWORKS_OPTION_ID !in refreshingIds,
            lastUpdatedAt = allLastUpdatedAt
        )

        val networkOptions = sources.mapNotNull { key ->
            val network = networkCatalog.getNetworkInfoById(key.networkId) ?: return@mapNotNull null
            val nativeAsset = assetCatalog.getAssetConfigsForNetwork(network.id)
                .firstOrNull { it.contractAddress == null }
            val id = optionId(key.networkId, key.address)
            val lastUpdatedAt = lastRefreshTimes[id]

            HistoryNetworkOption(
                id = id,
                // نمایشی است، نه هویت — هویت در [HistoryNetworkOption.networkId] است.
                networkName = network.name?.name ?: network.id,
                networkFaName = network.faName,
                networkId = network.id,
                symbol = key.symbol ?: nativeAsset?.symbol ?: network.currencySymbol,
                address = key.address,
                iconUrl = nativeAsset?.iconUrl ?: network.iconUrl,
                isSelected = id == resolvedSelectedId,
                isRefreshing = id in refreshingIds,
                isStale = lastUpdatedAt?.let { now - it > staleAfterMs } == true &&
                    id !in refreshingIds,
                lastUpdatedAt = lastUpdatedAt
            )
        }

        return listOf(allOption) + networkOptions
    }

    fun optionId(networkName: String, address: String): String {
        return "${networkName.lowercase(Locale.US)}|${address.lowercase(Locale.US)}"
    }
}
