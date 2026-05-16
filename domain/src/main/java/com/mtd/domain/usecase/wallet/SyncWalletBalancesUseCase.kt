package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.IWalletBalanceSynchronizer
import com.mtd.domain.model.core.Wallet
import javax.inject.Inject

class SyncWalletBalancesUseCase @Inject constructor(
    private val walletBalanceSynchronizer: IWalletBalanceSynchronizer
) {
    suspend operator fun invoke(
        wallets: List<Wallet>,
        activeWalletId: String?,
        forceResync: Boolean
    ) {
        walletBalanceSynchronizer.syncWalletBalances(wallets, activeWalletId, forceResync)
    }
}
