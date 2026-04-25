package com.mtd.domain.model

import java.math.BigInteger

enum class GaslessChain(val apiPath: String) {
    EVM("evm"),
    TRON("tron")
}

enum class GaslessServiceType(val apiValue: String) {
    GASLESS("gasless"),
    SPONSOR("sponsor")
}

data class GaslessSupportedToken(
    val chain: GaslessChain,
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
    val chain: GaslessChain,
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
    val displayPolicy: GaslessDisplayPolicyBundle? = null
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
    val chain: GaslessChain,
    val quoteToken: String,
    val params: GaslessRelayParams,
    val permitSignature: String? = null,
    val megaSignature: String? = null,
    val signature: String? = null
)

data class GaslessQueuedTx(
    val id: String,
    val stage: String?
)

data class GaslessTxStatus(
    val id: String,
    val status: String,
    val txHash: String?,
    val lastError: String?,
    val rawStatus: String? = null
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

data class TronApproveQuoteResult(
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

data class TronSponsorApproveResult(
    val funded: Boolean,
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
