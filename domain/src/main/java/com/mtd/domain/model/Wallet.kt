package com.mtd.domain.model

import com.mtd.core.model.WalletKey

data class Wallet(
    val id: String = "",
    val hasMnemonic: Boolean, // فقط نشان می‌دهد که ولت عبارت بازیابی دارد یا با کلید خصوصی است
    val keys: List<WalletKey>,
    val name: String = "My Wallet",
    val color: Int = -13908642, // 0xFF22C55E (Green) as Int
    val isManualBackedUp: Boolean = false,
    val isCloudBackedUp: Boolean = false
)
