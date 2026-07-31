package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.send.SendFeeQuote
import java.math.BigDecimal
import java.math.BigInteger

interface ISendAssetDataSource {
    /**
     * Re-reads the on-chain balance for [asset].
     *
     * TASK-56 — the `irrRate` parameter is gone. This layer used to bake fiat display strings into
     * the returned item, in a different shape from the wallet list's (`"$12.34"` / `"1,234 تومان "`
     * vs bare numbers) and with no idea which currency the user had selected. It now returns the
     * balance only; the caller formats, so there is one fiat-formatting policy instead of two.
     */
    suspend fun refreshAssetBalance(
        wallet: Wallet,
        asset: AssetItem
    ): ResultResponse<AssetItem?>

    suspend fun estimateFees(
        wallet: Wallet,
        asset: AssetItem,
        recipientAddress: String,
        amount: BigInteger
    ): ResultResponse<SendFeeQuote>
}
