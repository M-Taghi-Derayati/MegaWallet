package com.mtd.domain.model

import java.math.BigInteger

/**
 * Phase 4 — Growth & Engagement domain models (`/api/v1/growth`).
 *
 * Two hard contract rules are encoded here:
 *  - **All money is raw base units as [BigInteger]** (`amountRaw`, `totalBalance`, `pending`,
 *    `effective`, referral earnings) — parsed from the wire JSON String via the registered
 *    `BigIntegerStringAdapter`. Never [Long]/[Double].
 *  - Mystery Box open + Gas Credit balance are **device-bound**: identity comes from the attested
 *    `deviceId` JWT claim (attached by the AuthInterceptor), not from any wallet/request field.
 */

/** `GET /api/v1/growth/referral/stats?address=` — inviter earnings + invitee counts. */
data class ReferralStats(
    val address: String,
    val totalEarnedRaw: BigInteger,
    val pendingRaw: BigInteger,
    val inviteeCount: Int,
    val feeShareBps: Int?
)

/** A single Mystery Box from `GET /api/v1/growth/boxes`. */
data class MysteryBox(
    val id: String,
    val tier: String,          // BRONZE | SILVER | GOLD
    val status: String,        // UNOPENED | OPENED
    val createdAtMs: Long?,
    val openedAtMs: Long?
)

/** One prize row in a tier's odds table (`GET /api/v1/growth/boxes/odds`). */
data class BoxPrizeOdds(
    val type: String,          // always GAS_CREDIT (internal-only prize)
    val amountRaw: BigInteger,
    val label: String?,
    val probabilityBps: Int?
)

/** Public prize table + probabilities, grouped per tier. */
data class BoxOddsTier(
    val tier: String,
    val prizes: List<BoxPrizeOdds>
)

/** Device-bound gas credit ledger (`GET /api/v1/growth/gas-credit/balance`). `effective = total − Σpending`. */
data class GasCreditBalance(
    val totalBalance: BigInteger,
    val pending: BigInteger,
    val effective: BigInteger
)

/** The prize drawn when a box is opened — internal GAS_CREDIT only. */
data class BoxPrize(
    val type: String,
    val amountRaw: BigInteger,
    val label: String?,
    val token: String?
)

/** Result of `POST /api/v1/growth/boxes/:id/open` — atomic open + draw, minted to the device. */
data class BoxOpenResult(
    val boxId: String,
    val tier: String?,
    val deviceId: String?,
    val credited: Boolean,
    val prize: BoxPrize?,
    val openedAtMs: Long?
)
