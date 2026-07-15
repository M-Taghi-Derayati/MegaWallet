package com.mtd.domain.usecase.asset

import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject

class GetAssetChartDataUseCase @Inject constructor(
    private val marketDataRepository: IMarketDataRepository
) {
    suspend operator fun invoke(coinName: String, days: String): ResultResponse<List<Pair<Long, String>>> {
        return marketDataRepository.getHistoricalPrices(coinName, days)
    }
}

class LoadAssetChartPointsUseCase @Inject constructor(
    private val getAssetChartDataUseCase: GetAssetChartDataUseCase
) {
    suspend operator fun invoke(baseSymbol: String, days: String): List<Pair<Long, String>> {
        return when (val result = getAssetChartDataUseCase(baseSymbol, days)) {
            is ResultResponse.Success -> result.data
            is ResultResponse.Error -> emptyList()
        }
    }
}
