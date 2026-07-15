package com.mtd.domain.model

import com.mtd.domain.model.core.NetworkType
import java.math.BigInteger

// Phase 4: `GaslessChain` is removed. Routing is the data-driven `relayPrefix`
// (networkId → capability → relayPrefix); the EVM/TVM execution family is carried
// by `NetworkType`. Nothing in the gasless layer derives an /api path from an enum.

enum class GaslessServiceType(val apiValue: String) {
    GASLESS("gasless"),
    SPONSOR("sponsor")
}

/** Who funds the relayer fee for a gasless quote. Sent verbatim as [apiValue] on the quote request. */
enum class GaslessFeeFundingSource(val apiValue: String) {
    GAS_CREDIT("GAS_CREDIT"),
    WALLET("WALLET"),
    SPONSOR("SPONSOR");

    companion object {
        fun fromApiValue(value: String?): GaslessFeeFundingSource? {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
        }
    }
}

data class GaslessSupportedToken(
    val networkType: NetworkType,
    val token: String,
    val symbol: String?,
    val gaslessEnabled: Boolean,
    val sponsorEnabled: Boolean,
    val note: String?
)

data class GaslessEligibilityReason(
    val allowed: Boolean,
    val reasonCode: String?,
    val reasonFa: String?
)

data class GaslessEligibilityResult(
    val networkType: NetworkType,
    val service: GaslessServiceType,
    val user: String?,
    val token: String,
    val allowed: Boolean,
    val rollout: GaslessEligibilityReason?,
    val tokenPolicy: GaslessEligibilityReason?
) {
    val bestReasonFa: String?
        get() = rollout?.reasonFa?.takeIf { it.isNotBlank() }
            ?: tokenPolicy?.reasonFa?.takeIf { it.isNotBlank() }

    val bestReasonCode: String?
        get() = rollout?.reasonCode?.takeIf { it.isNotBlank() }
            ?: tokenPolicy?.reasonCode?.takeIf { it.isNotBlank() }
}

data class GaslessDisplayPolicy(
    val required: Boolean? = null,
    val mode: String?,
    val displayAmount: String?,
    val displayToken: String?,
    val displayUsd: String?,
    val displayIrr: String?,
    val willDeductFromUser: Boolean?,
    val deductSource: String?,
    val reasonFa: String?
)

data class GaslessDisplayPolicyBundle(
    val gasless: GaslessDisplayPolicy?,
    val sponsorApprove: GaslessDisplayPolicy?
)

data class GaslessSmartFee(
    val decision: String?,
    val reasonFa: String?,
    val feeAmount: BigInteger? = null,
    val feeUsd: String? = null,
    val directUserCostUsd: String? = null,
    val moreExpensiveThanDirect: Boolean? = null
)

data class GaslessPrepareData(
    val userAddress: String,
    val nonce: BigInteger,
    val deadline: Long?,
    val chainId: Long,
    val relayerContract: String,
    val treasuryAddress: String? = null,
    val prepareToken: String,
    val prepareExpiresAt: Long? = null
)

data class GaslessQuoteRequest(
    val prepareToken: String,
    val user: String,
    val token: String,
    val target: String,
    val amount: BigInteger,
    val feeFundingSource: GaslessFeeFundingSource = GaslessFeeFundingSource.WALLET,
    val clientFeeAmount: BigInteger? = null
)

data class GaslessCanonicalParams(
    val user: String,
    val token: String,
    val target: String,
    val amount: BigInteger,
    val feeAmount: BigInteger,
    val nonce: BigInteger,
    val deadline: Long,
    val treasury: String
)

data class GaslessQuoteData(
    val quoteToken: String,
    val canonicalParams: GaslessCanonicalParams,
    val serverFeeAmount: BigInteger? = null,
    val displayPolicy: GaslessDisplayPolicyBundle? = null,
    val smartFee: GaslessSmartFee? = null,
    // Latest backend fee-funding contract. All nullable/defaulted for backward compatibility.
    val accepted: Boolean? = null,
    val quoteId: String? = null,
    /** Who actually covers the relayer fee for this quote, as resolved by the server. */
    val feeFundingSource: GaslessFeeFundingSource? = null,
    val gasCreditApplied: Boolean? = null,
    val gasCredit: BigInteger? = null,
    val totalFee: BigInteger? = null,
    val finalFee: BigInteger? = null
)

data class GaslessRelayParams(
    val user: String,
    val token: String,
    val target: String,
    val amount: BigInteger,
    val feeAmount: BigInteger,
    val nonce: BigInteger,
    val deadline: Long
)

data class GaslessRelayPayload(
    val networkType: NetworkType,
    val quoteToken: String,
    val params: GaslessRelayParams,
    val permitSignature: String? = null,
    val megaSignature: String? = null,
    val signature: String? = null
)

data class GaslessQueuedTx(
    val id: String,
    val stage: String?,
    /** true when the relayer replayed a prior submission for the same idempotency key (no new tx). */
    val idempotent: Boolean = false
)

data class GaslessTxStatus(
    val id: String,
    val chain: String? = null,
    val status: String,
    val txHash: String?,
    val lastError: String?,
    val rawStatus: String? = null,
    val requestId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    val isFinal: Boolean
        get() = status.equals("SUCCESS", ignoreCase = true) ||
            status.equals("FAILED", ignoreCase = true) ||
            status.equals("TIMEOUT", ignoreCase = true)
}

enum class TronSponsorMode(val apiValue: String) {
    GIFT("gift"),
    DEBT("debt");

    companion object {
        fun fromApiValue(value: String?): TronSponsorMode? {
            return entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
        }
    }
}

data class TronSponsorApproveRequest(
    val userAddress: String,
    val tokenAddress: String,
    val mode: TronSponsorMode = TronSponsorMode.GIFT
)

data class TronApproveQuoteRequest(
    val userAddress: String,
    val tokenAddress: String
)

data class TronApproveTxTemplate(
    val approvalAmount: BigInteger?,
    val approvalAmountMode: String?
)

data class TronApproveQuoteResult(
    val approveRequired: Boolean = true,
    val approvalAmount: BigInteger? = null,
    val approvalAmountMode: String? = null,
    val approveTxTemplate: TronApproveTxTemplate? = null,
    val requiredAllowance: BigInteger? = null,
    val estimatedEnergy: BigInteger?,
    val estimatedBandwidthBytes: BigInteger?,
    val energyFeeSun: BigInteger?,
    val bandwidthFeeSun: BigInteger?,
    val requiredSun: BigInteger,
    val requiredTrx: String?,
    val requiredUsdApprox: Double?,
    val source: String?,
    val sponsorDisplayPolicy: GaslessDisplayPolicy? = null
)

data class EvmApproveTxTemplate(
    val to: String?,
    val spender: String?,
    val data: String?,
    val approvalAmount: BigInteger?,
    val approvalAmountMode: String?,
    val gasLimit: BigInteger?,
    val gasPriceWei: BigInteger?,
    val maxFeePerGasWei: BigInteger?,
    val maxPriorityFeePerGasWei: BigInteger?,
    val valueWei: BigInteger?
)

data class EvmApproveQuoteRequest(
    val userAddress: String,
    val tokenAddress: String
)

data class EvmApproveQuoteResult(
    val approveRequired: Boolean = true,
    val approvalAmount: BigInteger? = null,
    val approvalAmountMode: String? = null,
    val approveTxTemplate: EvmApproveTxTemplate? = null,
    val requiredAllowance: BigInteger? = null,
    val estimatedApproveGasLimit: BigInteger? = null,
    val gasPriceWei: BigInteger? = null,
    val maxFeePerGasWei: BigInteger? = null,
    val maxPriorityFeePerGasWei: BigInteger? = null,
    val requiredApproveWei: BigInteger = BigInteger.ZERO,
    val requiredWithBufferWei: BigInteger? = null,
    val source: String? = null,
    val sponsorDisplayPolicy: GaslessDisplayPolicy? = null
)

data class TronSponsorApproveResult(
    val funded: Boolean,
    val approveRequired: Boolean? = null,
    val skipReason: String? = null,
    val mode: TronSponsorMode?,
    val amount: BigInteger?,
    val reason: String?,
    val txHash: String?,
    val sponsorDisplayPolicy: GaslessDisplayPolicy? = null
)

typealias EvmSponsorMode = TronSponsorMode

data class EvmSponsorApproveRequest(
    val userAddress: String,
    val tokenAddress: String,
    val mode: EvmSponsorMode = EvmSponsorMode.GIFT
)

data class EvmSponsorApproveResult(
    val funded: Boolean,
    val mode: EvmSponsorMode?,
    val amount: BigInteger?,
    val reason: String?,
    val txHash: String?,
    val sponsorDisplayPolicy: GaslessDisplayPolicy? = null
)
