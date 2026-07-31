package com.mtd.domain.model

import com.mtd.domain.model.core.NetworkName
import java.math.BigInteger

sealed class TransactionRecord {
    abstract val hash: String
    abstract val timestamp: Long // Used as 'Completed' or main time
    abstract val submittedAt: Long? // For Stepper UI
    abstract val pendingDurationSeconds: Long? // For Stepper UI
    abstract val fee: BigInteger?
    abstract val status: TransactionStatus
    /**
     * TASK-53 — شناسهٔ کانونیِ شبکه. برای رکوردهای قدیمیِ کش‌شده ممکن است `null` باشد؛
     * در آن حالت [networkName] به‌عنوان fallback استفاده می‌شود.
     */
    abstract val networkId: String?
    /** TASK-53 — alias قدیمی؛ برای شبکه‌های فقط-در-باندل `null` است. برای تطبیق از [networkId] استفاده کنید. */
    abstract val networkName: NetworkName?
    abstract val fromAddress: String?
    abstract val toAddress: String?
    abstract val amount: BigInteger
    abstract val isOutgoing: Boolean
    abstract val fiatValue: Double? // e.g. $1.39
}

data class EvmTransaction(
    override val hash: String,
    override val timestamp: Long,
    override val submittedAt: Long? = null,
    override val pendingDurationSeconds: Long? = null,
    override val fee: BigInteger,
    override val status: TransactionStatus,
    override val networkId: String? = null,
    override val networkName: NetworkName? = null,
    override val fromAddress: String,
    override val toAddress: String,
    override val amount: BigInteger,
    override val isOutgoing: Boolean,
    override val fiatValue: Double? = null,
    
    // EVM Specific
    val gasPrice: BigInteger? = null,
    val gasUsed: BigInteger? = null,
    val nonce: Long? = null,
    val contractAddress: String? = null,
    val tokenTransferDetails: TokenTransferDetails? = null
) : TransactionRecord()

data class TronTransaction(
    override val hash: String,
    override val timestamp: Long,
    override val submittedAt: Long? = null,
    override val pendingDurationSeconds: Long? = null,
    override val fee: BigInteger?, // Often 0 if energy is used, or TRX burned
    override val status: TransactionStatus,
    override val networkId: String? = null,
    override val networkName: NetworkName? = null,
    override val fromAddress: String,
    override val toAddress: String,
    override val amount: BigInteger,
    override val isOutgoing: Boolean,
    override val fiatValue: Double? = null,

    // Tron Specific
    val bandwidthUsed: Long? = null,
    val energyUsed: Long? = null,
    val feeLimit: Long? = null,
    val contractAddress: String? = null,
    val tokenTransferDetails: TokenTransferDetails? = null
) : TransactionRecord()

data class BitcoinTransaction(
    override val hash: String,
    override val timestamp: Long,
    override val submittedAt: Long? = null,
    override val pendingDurationSeconds: Long? = null,
    override val fee: BigInteger,
    override val status: TransactionStatus,
    override val networkId: String? = null,
    override val networkName: NetworkName? = null,
    override val fromAddress: String?,
    override val toAddress: String?,
    override val amount: BigInteger,
    override val isOutgoing: Boolean,
    override val fiatValue: Double? = null,
    
    // Bitcoin Specific
    val feeRateSatsPerByte: Long? = null
) : TransactionRecord()

data class TokenTransferDetails(
    val from: String,
    val to: String,
    val amount: BigInteger,
    val tokenSymbol: String,
    val tokenDecimals: Int,
    val contractAddress: String
)

enum class TransactionStatus { CONFIRMED, PENDING, FAILED }