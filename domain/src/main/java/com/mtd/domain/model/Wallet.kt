package com.mtd.domain.model

import com.mtd.core.model.WalletKey

data class Wallet(
    val id: String = "",
    val mnemonic: String?, // ممکن است با کلید خصوصی وارد شده باشد، پس nullable است
    val keys: List<WalletKey>,
    val name: String = "My Wallet",
    val color: Int = -13908642 // 0xFF22C55E (Green) as Int
)
