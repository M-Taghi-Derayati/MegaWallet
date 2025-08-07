package com.mtd.domain.model


import java.math.BigInteger

data class Asset(
    val name: String,         // e.g., "Ethereum"
    val symbol: String,       // e.g., "ETH"
    val decimals: Int,        // e.g., 18
    val contractAddress: String?, // آدرس قرارداد برای توکن‌های ERC20 (برای توکن اصلی null است)
    val balance: BigInteger   // موجودی کاربر در واحد پایه (Wei)
)