package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import javax.inject.Inject

class GetCachedWalletBalanceUseCase @Inject constructor(
    private val cachedWalletBalanceReader: ICachedWalletBalanceReader
) {
    suspend operator fun invoke(walletId: String): String {
        return cachedWalletBalanceReader.getCachedTotalBalance(walletId)
    }
}
