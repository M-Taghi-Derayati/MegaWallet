package com.mtd.domain.usecase.receive

import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.ReceiveUiState
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.core.Wallet
import javax.inject.Inject

class BuildReceiveAddressGroupsUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(activeWallet: Wallet): List<ReceiveUiState.AddressGroup> {
        val addressGroups = mutableListOf<ReceiveUiState.AddressGroup>()

        val evmKey = activeWallet.keys.find { it.networkType == NetworkType.EVM }
        if (evmKey != null) {
            val evmNetworks = networkCatalog.getAllNetworkInfos()
                .filter { it.networkType == NetworkType.EVM }

            val item = ReceiveUiState.AddressItem(
                id = "EVM",
                symbol = "ETH",
                networkName = "EVM Networks",
                networkFaName = "شبکه‌های اتریومی",
                address = evmKey.address,
                iconUrl = networkCatalog.getNetworkInfoByName(NetworkName.SEPOLIA)?.iconUrl,
                supportedNetworkIcons = evmNetworks.mapNotNull { it.iconUrl },
                supportedNetworkIds = evmNetworks.map { it.id }
            )

            addressGroups.add(
                ReceiveUiState.AddressGroup(
                    title = "آدرس اتریوم",
                    subtitle = "Supported Networks",
                    items = listOf(item)
                )
            )
        }

        val otherKeys = activeWallet.keys.filter { it.networkType != NetworkType.EVM }
        if (otherKeys.isNotEmpty()) {
            val items = otherKeys.map { key ->
                val networkInfo = networkCatalog.getNetworkInfoByName(key.networkName)
                ReceiveUiState.AddressItem(
                    id = networkInfo?.id ?: key.networkName.name,
                    symbol = networkInfo?.currencySymbol ?: key.networkName.name,
                    networkName = networkInfo?.name?.name?.replaceFirstChar { it.titlecase() }
                        ?: "Unknown",
                    networkFaName = networkInfo?.faName,
                    address = key.address,
                    iconUrl = networkInfo?.iconUrl
                )
            }

            addressGroups.add(
                ReceiveUiState.AddressGroup(
                    title = "سایر شبکه‌ها",
                    subtitle = "",
                    items = items
                )
            )
        }

        return addressGroups
    }
}
