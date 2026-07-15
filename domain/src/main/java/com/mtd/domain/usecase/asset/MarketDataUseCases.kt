package com.mtd.domain.usecase.asset

import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.model.CurrencyRate
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

class GetUsdToIrrRateUseCase @Inject constructor(
    private val marketDataRepository: IMarketDataRepository
) {
    suspend operator fun invoke(): ResultResponse<CurrencyRate> {
        return marketDataRepository.getUsdToIrrRate()
    }
}
