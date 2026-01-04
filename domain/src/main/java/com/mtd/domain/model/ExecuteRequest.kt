package com.mtd.domain.model

import com.google.gson.annotations.SerializedName

data class ExecuteRequest(
    @SerializedName("quoteId") val quoteId: String,
    @SerializedName("selectedNetworkId") val selectedNetworkId: String,
    @SerializedName("recipientAddress") val recipientAddress: String,
    @SerializedName("permitParameters") val permitParameters: PermitParametersDto
){
    data class PermitParametersDto(
        @SerializedName("tokenAddress") val tokenAddress: String,
        @SerializedName("userAddress") val userAddress: String,
        @SerializedName("amount") val amount: String, // به صورت Wei
        @SerializedName("deadline") val deadline: Long,
        @SerializedName("v") val v: Int,
        @SerializedName("r") val r: String,
        @SerializedName("s") val s: String
    )

    data class ExecuteResponse(
        @SerializedName("message") val message: String,
        @SerializedName("tradeId") val tradeId: String
    )
}
