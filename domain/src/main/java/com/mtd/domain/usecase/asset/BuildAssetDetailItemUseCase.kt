package com.mtd.domain.usecase.asset

import com.mtd.domain.interfaceRepository.IAssetCatalog
import com.mtd.domain.model.AssetItem
import javax.inject.Inject

class BuildAssetDetailItemUseCase @Inject constructor(
    private val assetCatalog: IAssetCatalog
) {
    operator fun invoke(assetId: String): AssetDetailResult {
        val isGroup = assetId.startsWith("GROUP_")
        val groupSymbol = assetId.removePrefix("GROUP_")
        val resolvedId = if (isGroup) {
            assetCatalog.getAllAssetConfigs()
                .find { it.symbol.equals(groupSymbol, ignoreCase = true) }
                ?.id ?: assetId
        } else {
            assetId
        }

        val config = assetCatalog.getAssetConfigById(resolvedId)
        val fallbackName = if (isGroup) groupSymbol else assetId
        val item = AssetItem(
            id = assetId,
            name = config?.name ?: fallbackName,
            faName = config?.faName,
            symbol = config?.symbol ?: if (isGroup) groupSymbol else "",
            networkName = if (isGroup) "" else config?.networkId.orEmpty(),
            networkId = if (isGroup) "GROUP" else config?.networkId.orEmpty(),
            iconUrl = config?.iconUrl,
            balance = "...",
            balanceUsdt = "...",
            isGroupHeader = isGroup
        )

        val chartSymbol = config?.symbol ?: fallbackName.lowercase()
        return AssetDetailResult(item = item, chartSymbol = chartSymbol)
    }
}

data class AssetDetailResult(
    val item: AssetItem,
    val chartSymbol: String
)
