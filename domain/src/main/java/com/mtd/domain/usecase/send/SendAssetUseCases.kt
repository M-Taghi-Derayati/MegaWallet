package com.mtd.domain.usecase.send

import com.mtd.domain.interfaceRepository.ISendAssetDataSource
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.send.SendFeeQuote
import java.math.BigDecimal
import javax.inject.Inject

class RefreshSelectedAssetBalanceUseCase @Inject constructor(
    private val sendAssetDataSource: ISendAssetDataSource
) {
    suspend operator fun invoke(
        wallet: Wallet,
        asset: AssetItem,
        irrRate: BigDecimal
    ): ResultResponse<AssetItem?> {
        return sendAssetDataSource.refreshAssetBalance(wallet, asset, irrRate)
    }
}

class EstimateSendFeesUseCase @Inject constructor(
    private val sendAssetDataSource: ISendAssetDataSource
) {
    suspend operator fun invoke(
        wallet: Wallet,
        asset: AssetItem,
        recipientAddress: String
    ): ResultResponse<SendFeeQuote> {
        return sendAssetDataSource.estimateFees(wallet, asset, recipientAddress)
    }
}
