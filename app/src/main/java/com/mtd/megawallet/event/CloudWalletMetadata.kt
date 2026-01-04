package com.mtd.megawallet.event

data class CloudWalletMetadata(
    val id: String,
    val name: String,
    val key: String,
    val colorHex: String,
    val isMnemonic: Boolean
)

data class CloudWalletItem(
    val id: String,
    val name: String,
    val key: String,
    val colorHex: String,
    val isMnemonic: Boolean,
    val balanceUsdt: String=""
)