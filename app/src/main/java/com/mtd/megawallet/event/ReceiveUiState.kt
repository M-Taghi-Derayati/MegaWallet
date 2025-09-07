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
    val networkName: String,
    val address: String,
    val iconUrl: String?
)
}