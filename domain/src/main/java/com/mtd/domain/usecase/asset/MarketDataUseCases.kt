package com.mtd.domain.usecase.asset

import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.AssetPriceDto
import javax.inject.Inject

class GetLatestAssetPricesUseCase @Inject constructor(
    private val marketDataRepository: IMarketDataRepository
) {
    suspend operator fun invoke(symbols: Pair<List<String>,List<String>>): ResultResponse<List<AssetPriceDto>> {
        return marketDataRepository.getLatestPrices(symbols)
    }
}

// TASK-54 — `GetUsdToIrrRateUseCase` removed. It returned the rate as a one-shot value, which is what
// let callers snapshot it into a field and stop noticing updates. Observe
// `IUsdToIrrRateProvider.rate` instead; the provider is the only thing that should call
// `IMarketDataRepository.getUsdToIrrRate()`.
