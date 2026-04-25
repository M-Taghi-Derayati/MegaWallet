package com.mtd.data.dto

import java.math.BigInteger

data class BSCscanResponse(val status: String, val result: List<BSCscanTransactionDto>)
data class BSCscanTransactionDto(
    val hash: String, val from: String, val to: String, val value: BigInteger,
    val gasUsed: String, val gasPrice: String, val timeStamp: String, val isError: String
)