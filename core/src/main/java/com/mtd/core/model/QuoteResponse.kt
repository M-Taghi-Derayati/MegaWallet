package com.mtd.core.model

import com.google.gson.annotations.SerializedName

data class QuoteResponse(
    @SerializedName("quoteId") val quoteId: String,
    @SerializedName("fromAmount") val fromAmount: String,
    @SerializedName("fromAssetSymbol") val fromAssetSymbol: String,
    @SerializedName("bestExchange") val bestExchange: String,
    @SerializedName("exchangeRate") val exchangeRate: String,
    @SerializedName("depositAddress") val depositAddress: String? = null, // برای سواپ‌های UTXO
    @SerializedName("finalReceiveAmount") val finalReceiveAmount: String? = null, // برای سواپ‌های UTXO
    @SerializedName("fees") val fees: FeesDto? = null, // برای سواپ‌های UTXO
    @SerializedName("receivingOptions") val receivingOptions: List<ReceivingOptionDto>? = null, // برای سواپ‌های EVM
    @SerializedName("expiresAt") val expiresAt: String
){
    data class ReceivingOptionDto(
        @SerializedName("networkId") val networkId: String,
        @SerializedName("networkName") val networkName: String,
        @SerializedName("fees") val fees: FeesDto,
        @SerializedName("finalAmount") val finalAmount: String,
        @SerializedName("estimatedDeliveryTime") val estimatedDeliveryTime: String
    )

    data class FeesDto(
        @SerializedName("totalFeeInUsd") val totalFeeInUsd: String? = null,
        @SerializedName("details") val details: FeeDetailsDto
    )

    data class FeeDetailsDto(
        @SerializedName("iconUrl") val iconUrl: String,
        @SerializedName("exchangeFee") val exchangeFee: FeeComponentDto,
        @SerializedName("ourFee") val ourFee: FeeComponentDto,
        @SerializedName("sourceNetworkGasFee") val sourceNetworkGasFee: FeeComponentDto? = null,
        @SerializedName("destinationNetworkFee") val destinationNetworkFee: FeeComponentDto? = null
    )

    data class FeeComponentDto(
        @SerializedName("amount") val amount: String,
        @SerializedName("asset") val asset: String
    )

}