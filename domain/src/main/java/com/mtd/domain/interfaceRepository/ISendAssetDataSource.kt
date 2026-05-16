package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.send.SendFeeQuote
import java.math.BigDecimal

interface ISendAssetDataSource {
    suspend fun refreshAssetBalance(
        wallet: Wallet,
        asset: AssetItem,
        irrRate: BigDecimal
    ): ResultResponse<AssetItem?>

    suspend fun estimateFees(
        wallet: Wallet,
        asset: AssetItem,
        recipientAddress: String
    ): ResultResponse<SendFeeQuote>
}
