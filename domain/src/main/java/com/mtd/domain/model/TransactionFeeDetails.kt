package com.mtd.domain.model

import java.math.BigInteger

data class TransactionFeeDetails(
    val fee: BigInteger,
    val energyUsed: Long? = null,
    val bandwidthUsed: Long? = null,
    val energyFee: BigInteger? = null,
    val networkFee: BigInteger? = null
)
