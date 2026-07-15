package com.mtd.domain.usecase.history

import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.HistoryAddress
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject

/**
 * Fetches one cursor page of unified multi-network history (§1.7). PROXY-only; a DIRECT-mode
 * data source surfaces [com.mtd.domain.model.error.ApiError.UnsupportedOperation], which the
 * caller (ViewModel) treats as the signal to fall back to per-network aggregation.
 */
class GetUnifiedHistoryPageUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(
        addresses: List<HistoryAddress>,
        cursor: String? = null,
        limit: Int? = null
    ): ResultResponse<HistoryPage> {
        return walletRepository.get().getUnifiedHistory(addresses, cursor, limit)
    }
}
