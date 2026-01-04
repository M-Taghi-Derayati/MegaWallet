package com.mtd.core.model

import com.google.gson.annotations.SerializedName


// پاسخ کامل از /native/quote
data class NativeQuoteResponse(
    @SerializedName("quote") val quote: QuoteResponse,
)


// مدل‌های داخلی برای EIP-712
data class TypeDto(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String
)
data class DomainDto(
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String,
    @SerializedName("chainId") val chainId: Long,
    @SerializedName("verifyingContract") val verifyingContract: String
)
data class ForwardRequestDto(
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("value") val value: String,
    @SerializedName("gas") val gas: String,
    @SerializedName("nonce") val nonce: String,
    @SerializedName("data") val data: String
)

// درخواست برای /native/execute
data class ExecuteNativeRequest(
    @SerializedName("quoteId") val quoteId: String,
    @SerializedName("request") val request: ForwardRequestDto,
    @SerializedName("signature") val signature: String // امضای کامل به صورت هگز
)