package com.mtd.data.repository.growth

import com.mtd.data.dto.BoxDto
import com.mtd.data.dto.BoxOddsTierDto
import com.mtd.data.dto.BoxOpenResponseDto
import com.mtd.data.dto.BoxPrizeDto
import com.mtd.data.dto.BoxPrizeOddsDto
import com.mtd.data.dto.GasCreditBalanceDto
import com.mtd.data.dto.ReferralStatsDto
import com.mtd.data.network.relayApiError
import com.mtd.data.service.GrowthApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.interfaceRepository.IGrowthRepository
import com.mtd.domain.model.BoxOddsTier
import com.mtd.domain.model.BoxOpenResult
import com.mtd.domain.model.BoxPrize
import com.mtd.domain.model.BoxPrizeOdds
import com.mtd.domain.model.GasCreditBalance
import com.mtd.domain.model.MysteryBox
import com.mtd.domain.model.ReferralStats
import com.mtd.domain.model.ResultResponse
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4 — Growth & Engagement (`/api/v1/growth`). Unwraps the relayer's `{ ok, … }` responses and
 * maps failures to the Phase 1 typed [com.mtd.domain.model.error.ApiException] via [relayApiError]
 * (branch on the machine `error.code`, never the Persian `message`).
 *
 * Mystery Box open and gas-credit balance are **device-bound**: the impl sends no `deviceId` — the
 * attested `deviceId` JWT claim is carried by the bearer token the AuthInterceptor attaches. A
 * missing/invalid device JWT surfaces as [com.mtd.domain.model.error.ApiError.DeviceRequired].
 */
@Singleton
class GrowthRepositoryImpl @Inject constructor(
    private val growthApiService: GrowthApiService
) : IGrowthRepository {

    override suspend fun getReferralStats(address: String): ResultResponse<ReferralStats> = safeApiCall {
        val response = growthApiService.getReferralStats(address)
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "growth referral stats failed (${response.code()})")
        body.toDomain(address)
    }

    override suspend fun getBoxes(owner: String, status: String?): ResultResponse<List<MysteryBox>> = safeApiCall {
        val response = growthApiService.getBoxes(owner, status)
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "growth boxes failed (${response.code()})")
        body.boxes.orEmpty().mapNotNull { it.toDomainOrNull() }
    }

    override suspend fun getBoxOdds(): ResultResponse<List<BoxOddsTier>> = safeApiCall {
        val response = growthApiService.getBoxOdds()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "growth box odds failed (${response.code()})")
        body.tiers.orEmpty().map { it.toDomain() }
    }

    override suspend fun getGasCreditBalance(): ResultResponse<GasCreditBalance> = safeApiCall {
        val response = growthApiService.getGasCreditBalance()
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "growth gas-credit balance failed (${response.code()})")
        body.toDomain()
    }

    override suspend fun openBox(boxId: String): ResultResponse<BoxOpenResult> = safeApiCall {
        val response = growthApiService.openBox(boxId)
        val body = response.body()
        if (!response.isSuccessful || body == null) throw relayApiError(response, "growth box open failed (${response.code()})")
        body.toDomain(boxId)
    }

    private fun ReferralStatsDto.toDomain(fallbackAddress: String) = ReferralStats(
        address = address ?: fallbackAddress,
        totalEarnedRaw = totalEarnedRaw ?: BigInteger.ZERO,
        pendingRaw = pendingRaw ?: BigInteger.ZERO,
        inviteeCount = inviteeCount ?: 0,
        feeShareBps = feeShareBps
    )

    private fun BoxDto.toDomainOrNull(): MysteryBox? {
        val boxId = id?.takeIf { it.isNotBlank() } ?: return null
        return MysteryBox(
            id = boxId,
            tier = tier.orEmpty(),
            status = status.orEmpty(),
            createdAtMs = createdAt,
            openedAtMs = openedAt
        )
    }

    private fun BoxOddsTierDto.toDomain() = BoxOddsTier(
        tier = tier.orEmpty(),
        prizes = prizes.orEmpty().map { it.toDomain() }
    )

    private fun BoxPrizeOddsDto.toDomain() = BoxPrizeOdds(
        type = type.orEmpty(),
        amountRaw = amountRaw ?: BigInteger.ZERO,
        label = label,
        probabilityBps = probabilityBps
    )

    private fun GasCreditBalanceDto.toDomain(): GasCreditBalance {
        val total = totalBalance ?: BigInteger.ZERO
        val pend = pending ?: BigInteger.ZERO
        // Prefer the server's computed effective; otherwise derive total − pending (never negative).
        val eff = effective ?: (total - pend).max(BigInteger.ZERO)
        return GasCreditBalance(totalBalance = total, pending = pend, effective = eff)
    }

    private fun BoxOpenResponseDto.toDomain(fallbackBoxId: String) = BoxOpenResult(
        boxId = boxId ?: fallbackBoxId,
        tier = tier,
        deviceId = deviceId,
        credited = credited ?: false,
        prize = prize?.toDomain(),
        openedAtMs = openedAt
    )

    private fun BoxPrizeDto.toDomain() = BoxPrize(
        type = type.orEmpty(),
        amountRaw = amountRaw ?: BigInteger.ZERO,
        label = label,
        token = token
    )
}
