package com.mtd.data.dto


import com.google.gson.annotations.SerializedName
import java.math.BigInteger

data class BlockscoutTransactionDto(
    @SerializedName("hash") val hash: String,
    @SerializedName("from") val from: FromToDto,
    @SerializedName("to") val to: FromToDto?, // 'to' می‌تواند در تراکنش‌های ساخت قرارداد null باشد
    @SerializedName("value") val value: BigInteger,
    @SerializedName("gas_used") val gasUsed: String?,
    @SerializedName("gas_price") val gasPrice: String?,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("status") val status: String // e.g., "ok" or "error"
)

data class FromToDto(
    @SerializedName("hash") val hash: String,
    @SerializedName("name") val name: String?
)