package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.ICachedWalletBalanceReader
import java.math.BigDecimal
import javax.inject.Inject

/** A wallet's cached total in **USD**. The caller formats it in the selected currency (TASK-56). */
class GetCachedWalletBalanceUseCase @Inject constructor(
    private val cachedWalletBalanceReader: ICachedWalletBalanceReader
) {
    suspend operator fun invoke(walletId: String): BigDecimal {
        return cachedWalletBalanceReader.getCachedTotalUsd(walletId)
    }
}
