package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

data class GaslessPrepareResponseDto(
    @SerializedName("user") val user: String?,
    // Raw nonce — decoded from a JSON string via BigIntegerStringAdapter (Phase 1).
    @SerializedName("nonce") val nonce: BigInteger?,
    @SerializedName("deadline") val deadline: Long?,
    @SerializedName("chainId") val chainId: Long?,
    @SerializedName("relayerContract") val relayerContract: String?,
    @SerializedName("treasury") val treasury: String?,
    @SerializedName("prepareToken") val prepareToken: String?,
    @SerializedName("prepareExpiresAt") val prepareExpiresAt: Long?
)

data class GaslessSupportedTokenDto(
    @SerializedName("chain") val chain: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("symbol") val symbol: String?,
    @SerializedName("gaslessEnabled") val gaslessEnabled: Boolean?,
    @SerializedName("sponsorEnabled") val sponsorEnabled: Boolean?,
    @SerializedName("note") val note: String?
)

data class GaslessEligibilityParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String
)

data class GaslessEligibilityRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("service") val service: String,
    @SerializedName("params") val params: GaslessEligibilityParamsDto
)

data class GaslessEligibilityReasonDto(
    @SerializedName("allowed") val allowed: Boolean?,
    @SerializedName("reasonCode") val reasonCode: String?,
    @SerializedName("reasonFa") val reasonFa: String?
)

data class GaslessEligibilityResponseDto(
    @SerializedName("chain") val chain: String?,
    @SerializedName("service") val service: String?,
    @SerializedName("user") val user: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("allowed") val allowed: Boolean?,
    @SerializedName("rollout") val rollout: GaslessEligibilityReasonDto?,
    @SerializedName("tokenPolicy") val tokenPolicy: GaslessEligibilityReasonDto?
)

data class GaslessDisplayPolicyItemDto(
    @SerializedName("required") val required: Boolean?,
    @SerializedName("mode") val mode: String?,
    @SerializedName("displayAmount") val displayAmount: String?,
    @SerializedName("displayToken") val displayToken: String?,
    @SerializedName("displayUsd") val displayUsd: String?,
    @SerializedName("displayIrr") val displayIrr: String?,
    @SerializedName("willDeductFromUser") val willDeductFromUser: Boolean?,
    @SerializedName("deductSource") val deductSource: String?,
    @SerializedName("reasonFa") val reasonFa: String?
)

data class GaslessDisplayPolicyDto(
    @SerializedName("gasless") val gasless: GaslessDisplayPolicyItemDto?,
    @SerializedName("sponsorApprove") val sponsorApprove: GaslessDisplayPolicyItemDto?
)

data class GaslessQuoteParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String,
    @SerializedName("target") val target: String,
    // Raw base-unit amount — encoded as a JSON string via BigIntegerStringAdapter.
    @SerializedName("amount") val amount: BigInteger
)

data class GaslessQuoteRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("prepareToken") val prepareToken: String,
    @SerializedName("params") val params: GaslessQuoteParamsDto,
    // "GAS_CREDIT" | "WALLET" | "SPONSOR" — who covers the relayer fee.
    @SerializedName("feeFundingSource") val feeFundingSource: String? = null,
    @SerializedName("clientFeeAmount") val clientFeeAmount: BigInteger? = null
)

data class GaslessCanonicalParamsDto(
    @SerializedName("user") val user: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("target") val target: String?,
    // Raw base-unit values — decoded from JSON strings via BigIntegerStringAdapter (Phase 1).
    @SerializedName("amount") val amount: BigInteger?,
    @SerializedName("feeAmount") val feeAmount: BigInteger?,
    @SerializedName("nonce") val nonce: BigInteger?,
    @SerializedName("deadline") val deadline: Long?,
    @SerializedName("treasury") val treasury: String?
)

data class GaslessServerQuoteDto(
    @SerializedName("feeAmount") val feeAmount: BigInteger?
)

data class GaslessSmartFeeDto(
    @SerializedName("decision") val decision: String?,
    @SerializedName("reasonFa") val reasonFa: String?,
    @SerializedName("feeAmount") val feeAmount: BigInteger?,
    @SerializedName("feeUsd") val feeUsd: String?,
    @SerializedName("directUserCostUsd") val directUserCostUsd: String?,
    @SerializedName("moreExpensiveThanDirect") val moreExpensiveThanDirect: Boolean?
)

data class GaslessQuoteResponseDto(
    @SerializedName("quoteToken") val quoteToken: String?,
    @SerializedName("canonicalParams") val canonicalParams: GaslessCanonicalParamsDto?,
    @SerializedName("serverQuote") val serverQuote: GaslessServerQuoteDto?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?,
    @SerializedName("smartFee") val smartFee: GaslessSmartFeeDto?,
    // New fee-funding fields (latest backend contract). All nullable for backward compatibility.
    @SerializedName("accepted") val accepted: Boolean? = null,
    @SerializedName("quoteId") val quoteId: String? = null,
    // "GAS_CREDIT" | "WALLET" | "SPONSOR" — who actually covers the relayer fee for this quote.
    @SerializedName("feeFundingSource") val feeFundingSource: String? = null,
    @SerializedName("gasCreditApplied") val gasCreditApplied: Boolean? = null,
    // Raw base-unit values — decoded from JSON strings via BigIntegerStringAdapter (Phase 1).
    @SerializedName("gasCredit") val gasCredit: BigInteger? = null,
    @SerializedName("totalFee") val totalFee: BigInteger? = null,
    @SerializedName("finalFee") val finalFee: BigInteger? = null
)

data class GaslessRelayParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String,
    @SerializedName("target") val target: String,
    // Raw base-unit values — encoded as JSON strings via BigIntegerStringAdapter.
    @SerializedName("amount") val amount: BigInteger,
    @SerializedName("feeAmount") val feeAmount: BigInteger,
    @SerializedName("nonce") val nonce: BigInteger,
    @SerializedName("deadline") val deadline: Long
)

data class GaslessRelayRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("quoteToken") val quoteToken: String,
    @SerializedName("params") val params: GaslessRelayParamsDto,
    @SerializedName("permitSignature") val permitSignature: String? = null,
    @SerializedName("megaSignature") val megaSignature: String? = null,
    @SerializedName("signature") val signature: String? = null
)

data class GaslessRelayResponseDto(
    @SerializedName("status") val status: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("stage") val stage: String?,
    // true when the relayer replayed a prior submission for the same x-idempotency-key (no new tx).
    @SerializedName("idempotent") val idempotent: Boolean? = null
)

data class GaslessTxStatusDto(
    @SerializedName("_id") val objectId: Any?,
    @SerializedName("id") val id: Any?,
    @SerializedName("chain") val chain: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("publicStatus") val publicStatus: String?,
    @SerializedName("txHash") val txHash: String?,
    @SerializedName("lastError") val lastError: String?,
    @SerializedName("requestId") val requestId: String?,
    @SerializedName("createdAt") val createdAt: Any?,
    @SerializedName("updatedAt") val updatedAt: Any?
)

data class TronSponsorApproveParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String
)

data class TronApproveQuoteRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("params") val params: TronSponsorApproveParamsDto
)

data class TronApproveTxTemplateDto(
    @SerializedName("approvalAmount") val approvalAmount: String?,
    @SerializedName("approvalAmountMode") val approvalAmountMode: String?
)

data class TronApproveQuoteResponseDto(
    @SerializedName("chain") val chain: String?,
    @SerializedName("approveRequired") val approveRequired: Boolean?,
    @SerializedName("approvalAmount") val approvalAmount: String?,
    @SerializedName("approvalAmountMode") val approvalAmountMode: String?,
    @SerializedName("approveTxTemplate") val approveTxTemplate: TronApproveTxTemplateDto?,
    @SerializedName("requiredAllowance") val requiredAllowance: String?,
    @SerializedName("estimatedEnergy") val estimatedEnergy: String?,
    @SerializedName("estimatedBandwidthBytes") val estimatedBandwidthBytes: String?,
    @SerializedName("energyFeeSun") val energyFeeSun: String?,
    @SerializedName("bandwidthFeeSun") val bandwidthFeeSun: String?,
    @SerializedName("requiredSun") val requiredSun: String?,
    @SerializedName("requiredTrx") val requiredTrx: String?,
    @SerializedName("requiredUsdApprox") val requiredUsdApprox: Double?,
    @SerializedName("source") val source: String?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?
)

data class TronSponsorApproveRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("params") val params: TronSponsorApproveParamsDto,
    @SerializedName("mode") val mode: String
)

data class TronSponsorApproveResponseDto(
    @SerializedName("funded") val funded: Boolean?,
    @SerializedName("approveRequired") val approveRequired: Boolean?,
    @SerializedName("skipReason") val skipReason: String?,
    @SerializedName("mode") val mode: String?,
    @SerializedName(value = "amount", alternate = ["sponsorAmountSun", "sponsorAmountWei"]) val amount: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("txHash") val txHash: String?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?
)

data class EvmSponsorApproveParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String
)

data class EvmSponsorApproveRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("params") val params: EvmSponsorApproveParamsDto,
    @SerializedName("mode") val mode: String
)

data class EvmApproveQuoteRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("params") val params: EvmSponsorApproveParamsDto
)

data class EvmApproveTxTemplateDto(
    @SerializedName("to") val to: String?,
    @SerializedName("spender") val spender: String?,
    @SerializedName("data") val data: String?,
    @SerializedName("approvalAmount") val approvalAmount: String?,
    @SerializedName("approvalAmountMode") val approvalAmountMode: String?,
    @SerializedName("gasLimit") val gasLimit: String?,
    @SerializedName("gasPriceWei") val gasPriceWei: String?,
    @SerializedName("maxFeePerGasWei") val maxFeePerGasWei: String?,
    @SerializedName("maxPriorityFeePerGasWei") val maxPriorityFeePerGasWei: String?,
    @SerializedName("valueWei") val valueWei: String?
)

data class EvmApproveQuoteResponseDto(
    @SerializedName("chain") val chain: String?,
    @SerializedName("approveRequired") val approveRequired: Boolean?,
    @SerializedName("approvalAmount") val approvalAmount: String?,
    @SerializedName("approvalAmountMode") val approvalAmountMode: String?,
    @SerializedName("approveTxTemplate") val approveTxTemplate: EvmApproveTxTemplateDto?,
    @SerializedName("requiredAllowance") val requiredAllowance: String?,
    @SerializedName("estimatedApproveGasLimit") val estimatedApproveGasLimit: String?,
    @SerializedName("gasPriceWei") val gasPriceWei: String?,
    @SerializedName("maxFeePerGasWei") val maxFeePerGasWei: String?,
    @SerializedName("maxPriorityFeePerGasWei") val maxPriorityFeePerGasWei: String?,
    @SerializedName("requiredApproveWei") val requiredApproveWei: String?,
    @SerializedName("requiredWithBufferWei") val requiredWithBufferWei: String?,
    @SerializedName("source") val source: String?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?
)

data class EvmSponsorApproveResponseDto(
    @SerializedName("funded") val funded: Boolean?,
    @SerializedName("mode") val mode: String?,
    @SerializedName(value = "amount", alternate = ["sponsorAmountWei", "sponsorAmountEth"]) val amount: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("txHash") val txHash: String?,
    @SerializedName("approveTxTemplate") val approveTxTemplate: EvmApproveTxTemplateDto?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?
)
