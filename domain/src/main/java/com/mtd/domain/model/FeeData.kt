package com.mtd.domain.model

import java.math.BigDecimal
import java.math.BigInteger

data class FeeData(
    val level: String, // "Normal", "Fast", "Urgent"
    val feeInSmallestUnit: BigDecimal,
    val estimatedTime: String,
        // فیلدهای مخصوص EVM
    val gasPrice: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val feeInCoin: BigDecimal?=null,
    val feeInUsd: BigDecimal?=null,
    val feeRateInSatsPerByte: Long? = null,
    )
