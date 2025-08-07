package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

data class EtherscanResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("result") val result: List<EtherscanTransactionDto>
)