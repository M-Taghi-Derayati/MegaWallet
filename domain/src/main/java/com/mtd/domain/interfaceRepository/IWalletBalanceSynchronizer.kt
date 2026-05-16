package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.core.Wallet

interface IWalletBalanceSynchronizer {
    suspend fun syncWalletBalances(
        wallets: List<Wallet>,
        activeWalletId: String?,
        forceResync: Boolean
    )
}
