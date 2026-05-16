package com.mtd.domain.interfaceRepository

interface ICachedWalletBalanceReader {
    suspend fun getCachedTotalBalance(walletId: String): String
}
