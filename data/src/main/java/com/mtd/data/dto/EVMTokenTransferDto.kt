package com.mtd.data.dto

import com.google.gson.annotations.SerializedName


// مدل‌های جدید برای پاسخ /token-transfers از Blockscout
data class EVMTokenTransferDto(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("from") val fromAddress: AddressDto,
    @SerializedName("to") val toAddress: AddressDto,
    @SerializedName("transaction_hash") val txHash: String, // <<-- نام فیلد اصلاح شد
    @SerializedName("token") val token: TokenDto,
    @SerializedName("total") val total: TotalDto
){
    data class AddressDto(
        @SerializedName("hash")
        val hash: String
    )
    data class TokenDto(
        @SerializedName("address_hash") val address: String,
        @SerializedName("symbol") val symbol: String,
        @SerializedName("decimals") val decimals: String? // <<-- ممکن است null باشد
    )
    data class TotalDto(@SerializedName("value") val value: String)
}

