package com.mtd.data.dto

import com.google.gson.annotations.SerializedName

data class GaslessPrepareResponseDto(
    @SerializedName("user") val user: String?,
    @SerializedName("nonce") val nonce: String?,
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
    @SerializedName("amount") val amount: String
)

data class GaslessQuoteRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("prepareToken") val prepareToken: String,
    @SerializedName("params") val params: GaslessQuoteParamsDto,
    @SerializedName("clientFeeAmount") val clientFeeAmount: String? = null
)

data class GaslessCanonicalParamsDto(
    @SerializedName("user") val user: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("target") val target: String?,
    @SerializedName("amount") val amount: String?,
    @SerializedName("feeAmount") val feeAmount: String?,
    @SerializedName("nonce") val nonce: String?,
    @SerializedName("deadline") val deadline: Long?,
    @SerializedName("treasury") val treasury: String?
)

data class GaslessServerQuoteDto(
    @SerializedName("feeAmount") val feeAmount: String?
)

data class GaslessQuoteResponseDto(
    @SerializedName("quoteToken") val quoteToken: String?,
    @SerializedName("canonicalParams") val canonicalParams: GaslessCanonicalParamsDto?,
    @SerializedName("serverQuote") val serverQuote: GaslessServerQuoteDto?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?
)

data class GaslessRelayParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String,
    @SerializedName("target") val target: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("feeAmount") val feeAmount: String,
    @SerializedName("nonce") val nonce: String,
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
    @SerializedName("stage") val stage: String?
)

data class GaslessTxStatusDto(
    @SerializedName(value = "_id", alternate = ["id"]) val id: Any?,
    @SerializedName("status") val status: String?,
    @SerializedName("txHash") val txHash: String?,
    @SerializedName("lastError") val lastError: String?
)

data class TronSponsorApproveParamsDto(
    @SerializedName("user") val user: String,
    @SerializedName("token") val token: String
)

data class TronApproveQuoteRequestDto(
    @SerializedName("chain") val chain: String,
    @SerializedName("params") val params: TronSponsorApproveParamsDto
)

data class TronApproveQuoteResponseDto(
    @SerializedName("chain") val chain: String?,
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

data class EvmSponsorApproveResponseDto(
    @SerializedName("funded") val funded: Boolean?,
    @SerializedName("mode") val mode: String?,
    @SerializedName(value = "amount", alternate = ["sponsorAmountWei", "sponsorAmountEth"]) val amount: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("txHash") val txHash: String?,
    @SerializedName("displayPolicy") val displayPolicy: GaslessDisplayPolicyDto?
)
