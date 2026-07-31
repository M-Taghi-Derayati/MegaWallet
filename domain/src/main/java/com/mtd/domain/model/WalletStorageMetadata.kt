package com.mtd.domain.model

 data class WalletStorageMetadata(
        val id: String,
        val name: String,
        val color: Int,
        val isMnemonic: Boolean,
        val isManualBackedUp: Boolean = false,
        val isCloudBackedUp: Boolean = false
    )