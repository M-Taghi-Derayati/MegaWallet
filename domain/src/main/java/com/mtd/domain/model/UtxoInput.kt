package com.mtd.domain.model
/** A spendable output the wallet controls (value in raw satoshis). */
data class UtxoInput(
    val txid: String,
    val vout: Int,
    val valueSat: Long
)