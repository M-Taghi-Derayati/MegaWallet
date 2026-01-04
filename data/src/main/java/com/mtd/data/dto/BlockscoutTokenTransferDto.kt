package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

// ... (تمام مدل‌های DTO قبلی)

// مدل‌های جدید برای پاسخ /token-transfers از Blockscout
data class BlockscoutTokenTransferDto(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("from") val from: AddressDto,
    @SerializedName("to") val to: AddressDto,
    @SerializedName("transaction_hash") val txHash: String, // <<-- نام فیلد اصلاح شد
    @SerializedName("token") val token: TokenDto,
    @SerializedName("total") val total: TotalDto
)

// مدل‌های تودرتو
data class AddressDto(@SerializedName("hash") val hash: String)
data class TokenDto(
    @SerializedName("address_hash") val address: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("decimals") val decimals: String? // <<-- ممکن است null باشد
)
data class TotalDto(@SerializedName("value") val value: String)