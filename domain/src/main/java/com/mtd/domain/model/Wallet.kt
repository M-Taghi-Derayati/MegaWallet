package com.mtd.domain.model

import com.mtd.core.model.WalletKey

data class Wallet(
    val mnemonic: String?, // ممکن است با کلید خصوصی وارد شده باشد، پس nullable است
    val keys: List<WalletKey>
)
