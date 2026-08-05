package com.mtd.data.repository.assets

import com.mtd.data.dto.DiscoveredTokenDto
import com.mtd.data.dto.HeldTokensRequestDto
import com.mtd.data.network.proxyCall
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.interfaceRepository.ITokenDiscoveryRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.DiscoveredToken
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

///**
// * §5 — پیاده‌سازیِ کشفِ توکن روی `/api/mobile/v1/networks/:networkId/tokens/*`.
// *
// * هر دو اندپوینت پاکتِ BM-33 برمی‌گردانند، پس از همان [proxyCall] بقیهٔ مسیرهای proxy استفاده
// * می‌کنند و خطاها همان‌جا به [com.mtd.domain.model.error.ApiException] تایپ‌دار تبدیل می‌شوند.
//*/





@Singleton
class TokenDiscoveryRepositoryImpl @Inject constructor(
    private val proxyService: MobileProxyApiService
) : ITokenDiscoveryRepository {

    override suspend fun getHeldTokens(
        networkId: String,
        address: String
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        call = { proxyService.heldTokens(networkId, HeldTokensRequestDto(address = address)) },
        map = { dto -> dto.tokens.orEmpty().mapNotNull { it.toDomain(networkId) } }
    )

    override suspend fun searchTokens(
        networkId: String,
        query: String,
        limit: Int
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        call = { proxyService.searchTokens(networkId, query, limit) },
        map = { dto -> dto.tokens.orEmpty().mapNotNull { it.toDomain(networkId) } }
    )
}


private fun DiscoveredTokenDto.toDomain(networkId: String): DiscoveredToken? {
    val contract = contractAddress?.takeIf { it.isNotBlank() }
    val decimalsValue = decimals
    val symbolValue = symbol?.takeIf { it.isNotBlank() }

    if (contract == null || decimalsValue == null || decimalsValue < 0 || symbolValue == null) {
        Timber.w(
            "Dropping token from %s discovery: contract=%s decimals=%s symbol=%s",
            networkId, contractAddress, decimals, symbol
        )
        return null
    }

    return DiscoveredToken(
        catalogId = id?.takeIf { it.isNotBlank() },
        networkId = networkId,
        symbol = symbolValue,
        name = name?.takeIf { it.isNotBlank() } ?: symbolValue,
        decimals = decimalsValue,
        contractAddress = contract,
        iconUrl = iconUrl?.takeIf { it.isNotBlank() },
        featured = featured ?: false
    )
}
