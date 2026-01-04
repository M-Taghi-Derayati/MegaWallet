package com.mtd.domain.model

import com.google.gson.annotations.SerializedName

data class QuoteRequest(
    @SerializedName("fromAssetSymbol") val fromAssetSymbol: String,
    @SerializedName("fromNetworkId") val fromNetworkId: String,
    @SerializedName("toAssetSymbol") val toAssetSymbol: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("recipientAddress") val recipientAddress: String? = null,
    @SerializedName("userAddress") val userAddress: String? = null,
    @SerializedName("toNetworkId") val toNetworkId: String? = null
)