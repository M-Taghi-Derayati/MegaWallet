package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType

interface INetworkCatalog {
    fun getAllNetworkInfos(): List<NetworkInfo>
    fun getNetworkInfoByName(name: NetworkName): NetworkInfo?
    fun getNetworkInfoById(id: String): NetworkInfo?
    fun getNetworkTypeForAddress(address: String): NetworkType?
    fun getNetworkTypeForNetworkId(networkId: String): NetworkType?
    fun isValidAddressForNetworkId(address: String, networkId: String): Boolean
}

data class NetworkInfo(
    /** TASK-53 — هویتِ کانونی. */
    val id: String,
    val networkType: NetworkType,
    /** TASK-53 — alias قدیمی؛ برای شبکه‌های فقط-در-باندل `null`. */
    val name: NetworkName?,
    val currencySymbol: String,
    val iconUrl: String?,
    val faName: String?,
    val decimals: Int = 0,
    val explorers: List<String> = emptyList(),
    val color: String? = null,
    /** TASK-51 — web-explorer transaction page template with a `{hash}` placeholder; see [NetworkConfig]. */
    val explorerTxUrl: String? = null
)
