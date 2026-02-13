package com.mtd.megawallet.event

sealed class ReceiveUiState {
    object Loading : ReceiveUiState()
    data class Success(val addressGroups: List<AddressGroup>) : ReceiveUiState()
    data class Error(val message: String) : ReceiveUiState()


data class AddressGroup(
    val title: String,
    val subtitle: String,
    val items: List<AddressItem>
)

data class AddressItem(
    val id: String,
    val symbol: String? = null,
    val networkName: String,
    val networkFaName: String? = null,
    val address: String,
    val iconUrl: String?,
    val supportedNetworkIcons: List<String> = emptyList(),
    val supportedNetworkIds: List<String> = emptyList()
)
}