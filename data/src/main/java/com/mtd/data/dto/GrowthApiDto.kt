package com.mtd.data.dto

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

/**
 * Phase 4 — Growth & Engagement DTOs (`/api/v1/growth`). Every raw-money field is [BigInteger],
 * deserialized from the wire JSON String by the registered `BigIntegerStringAdapter`. Responses use
 * the relayer's `{ ok, … }` / `{ ok:false, error:{code,message} }` shape (NOT the BM-33 envelope);
 * the repository unwraps `ok` and maps failures via `relayApiError`.
 */

// ── referral/stats ──────────────────────────────────────────────────────────
data class ReferralStatsDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("address") val address: String? = null,
    @SerializedName("totalEarnedRaw") val totalEarnedRaw: BigInteger? = null,
    @SerializedName("pendingRaw") val pendingRaw: BigInteger? = null,
    @SerializedName("inviteeCount") val inviteeCount: Int? = null,
    @SerializedName("feeShareBps") val feeShareBps: Int? = null
)

// --- boxes ---
data class BoxesResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("boxes") val boxes: List<BoxDto>? = null
)

data class BoxDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("createdAt") val createdAt: Long? = null,
    @SerializedName("openedAt") val openedAt: Long? = null
)

// --- boxes/odds ---
data class BoxOddsResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("tiers") val tiers: List<BoxOddsTierDto>? = null
)

data class BoxOddsTierDto(
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("prizes") val prizes: List<BoxPrizeOddsDto>? = null
)

data class BoxPrizeOddsDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("amountRaw") val amountRaw: BigInteger? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("probabilityBps") val probabilityBps: Int? = null
)

// --- gas-credit/balance ---
data class GasCreditBalanceDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("totalBalance") val totalBalance: BigInteger? = null,
    @SerializedName("pending") val pending: BigInteger? = null,
    @SerializedName("effective") val effective: BigInteger? = null
)

// ── boxes/:id/open ──────────────────────────────────────────────────────────
data class BoxOpenResponseDto(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("boxId") val boxId: String? = null,
    @SerializedName("tier") val tier: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("credited") val credited: Boolean? = null,
    @SerializedName("prize") val prize: BoxPrizeDto? = null,
    @SerializedName("openedAt") val openedAt: Long? = null
)

data class BoxPrizeDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("amountRaw") val amountRaw: BigInteger? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("token") val token: String? = null
)
