package com.mtd.core.model

// مدل‌های داده برای ارتباط با ریپازیتوری
data class Eip712Signature(val v: Int, val r: String, val s: String)

data class ExecuteSwapRequest(
    val quoteId: String,
    val fromChainId: Long,
    val tokenAddress: String,
    val ownerAddress: String,
    val amount: String, // مقدار به صورت Wei
    val deadline: Long,
    val signature: Eip712Signature,
    val destinationAddress: String
)