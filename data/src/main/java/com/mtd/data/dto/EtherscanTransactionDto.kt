package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

// این کلاس، هر آیتم تراکنش در لیست result را مدل می‌کند.
data class EtherscanTransactionDto(
    @SerializedName("hash") val hash: String,
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("value") val value: BigInteger,
    @SerializedName("gasUsed") val gasUsed: String,
    @SerializedName("gasPrice") val gasPrice: String,
    @SerializedName("timeStamp") val timeStamp: String,
    @SerializedName("isError") val isError: String // 0 for success, 1 for error
)