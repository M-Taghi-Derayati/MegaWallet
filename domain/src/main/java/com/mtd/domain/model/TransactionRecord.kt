package com.mtd.domain.model


import java.math.BigInteger


sealed class TransactionRecord(
    open var hash: String,
    open var timestamp: Long,
    open var fee: BigInteger,
    open var status: TransactionStatus
)

data class EvmTransaction(
    override var hash: String, override var timestamp: Long, override var fee: BigInteger, override var status: TransactionStatus,
    val fromAddress: String,
    val toAddress: String,
    val amount: BigInteger,
    val isOutgoing: Boolean
) : TransactionRecord(hash, timestamp, fee, status)

data class BitcoinTransaction(
    override var hash: String, override var timestamp: Long, override var fee: BigInteger, override var status: TransactionStatus,
    val amount: BigInteger,
    val fromAddress: String?, // اگر برداشت باشه، آدرس فعلی فرستنده است
    val toAddress: String?
) : TransactionRecord(hash, timestamp, fee, status)

data class TransactionInput(val address: String?, val amount: BigInteger)
data class TransactionOutput(val address: String?, val amount: BigInteger)

enum class TransactionStatus { CONFIRMED, PENDING, FAILED }