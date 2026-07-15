package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.BoxOddsTier
import com.mtd.domain.model.BoxOpenResult
import com.mtd.domain.model.GasCreditBalance
import com.mtd.domain.model.MysteryBox
import com.mtd.domain.model.ReferralStats
import com.mtd.domain.model.ResultResponse

/**
 * Phase 4 — Growth & Engagement (`/api/v1/growth`). Mystery Box open and Gas Credit balance are
 * **device-bound**: the impl relies on the attested `deviceId` JWT claim (attached by the
 * AuthInterceptor), NOT on a wallet/request field. All failures surface as the typed
 * [com.mtd.domain.model.error.ApiException]; control flow branches on [com.mtd.domain.model.error.ApiError].
 */
interface IGrowthRepository {

    /** Inviter earnings + invitee counts for [address]. */
    suspend fun getReferralStats(address: String): ResultResponse<ReferralStats>

    /** A wallet's Mystery Boxes, optionally filtered by [status] (UNOPENED/OPENED). */
    suspend fun getBoxes(owner: String, status: String? = null): ResultResponse<List<MysteryBox>>

    /** Public prize table + probabilities per tier (no auth). */
    suspend fun getBoxOdds(): ResultResponse<List<BoxOddsTier>>

    /**
     * Device-bound gas credit ledger. Identity is the `deviceId` JWT claim; the impl relies on the
     * bearer token attached by the interceptor (no `?deviceId=` is sent).
     */
    suspend fun getGasCreditBalance(): ResultResponse<GasCreditBalance>

    /**
     * Atomic open + draw of a box, minting internal GAS_CREDIT to the device. Device-gated:
     * a missing/invalid device JWT → [com.mtd.domain.model.error.ApiError.DeviceRequired]; a second
     * open → [com.mtd.domain.model.error.ApiError.MysteryBoxAlreadyOpened]; >5/hour →
     * [com.mtd.domain.model.error.ApiError.DeviceRewardLimitExceeded].
     */
    suspend fun openBox(boxId: String): ResultResponse<BoxOpenResult>
}
