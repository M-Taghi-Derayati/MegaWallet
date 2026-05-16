package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigInteger


// NodeReal API Models
data class BSCscanResponse(
    @SerializedName("data") val data: NodeRealData,
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String
)

data class NodeRealData(
    @SerializedName("total") val total: Int,
    @SerializedName("list") val list: List<NodeRealTransactionDto>,
    @SerializedName("pageNum") val pageNum: Int,
    @SerializedName("total_num") val totalNum: Int
)

data class NodeRealTransactionDto(
    @SerializedName("id") val id: Long,
    @SerializedName("category") val category: String, // "transaction" for native, "20" for BEP20
    @SerializedName("blockNum") val blockNum: String,
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("value") val value: String, // Hex string
    @SerializedName("asset") val asset: String,
    @SerializedName("name") val name: String,
    @SerializedName("hash") val hash: String,
    @SerializedName("contractAddress") val contractAddress: String,
    @SerializedName("decimal") val decimal: String?,
    @SerializedName("blockTimeStamp") val blockTimeStamp: Long,
    @SerializedName("gasPrice") val gasPrice: BigInteger?,
    @SerializedName("gasUsed") val gasUsed: BigInteger?,
    @SerializedName("receiptsStatus") val receiptsStatus: Int,
    @SerializedName("totalFee") val totalFee: String?
)