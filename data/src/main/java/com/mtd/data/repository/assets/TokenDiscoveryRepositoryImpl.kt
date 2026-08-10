package com.mtd.data.repository.assets

import com.mtd.data.dto.DiscoveredTokenDto
import com.mtd.data.dto.HeldTokensRequestDto
import com.mtd.data.network.proxyCall
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.ITokenDiscoveryRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.DiscoveredToken
import com.mtd.domain.model.assets.TokenSource
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class TokenDiscoveryRepositoryImpl @Inject constructor(
    private val proxyService: MobileProxyApiService,
    private val networkCatalog: INetworkCatalog
) : ITokenDiscoveryRepository {

    override suspend fun getDefaultTokens(
        networkId: String,
        address: String?,
        limit: Int
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        // آدرس عیناً پاس داده می‌شود؛ سرور از تاریخچهٔ از قبل ایندکس‌شده می‌خواند، پس این پارامتر
        // فراخوانیِ اضافه‌ای به provider نمی‌زند و نبودنش فقط توکن‌های خودِ کاربر را حذف می‌کند.
        call = { proxyService.defaultTokens(networkId, address?.takeIf { it.isNotBlank() }, limit) },
        map = { dto -> dto.items.mapNotNull { it.toDomain(fallbackNetworkId = networkId) }.knownNetworksOnly() }
    )

    override suspend fun getHeldTokens(
        networkId: String,
        address: String
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        call = { proxyService.heldTokens(networkId, HeldTokensRequestDto(address = address)) },
        map = { dto -> dto.items.mapNotNull { it.toDomain(fallbackNetworkId = networkId) }.knownNetworksOnly() }
    )

    override suspend fun searchTokens(
        networkId: String,
        query: String,
        limit: Int
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        call = { proxyService.searchTokens(networkId, query, limit) },
        map = { dto -> dto.items.mapNotNull { it.toDomain(fallbackNetworkId = networkId) }.knownNetworksOnly() }
    )

    override suspend fun searchTokensAllNetworks(
        query: String,
        limit: Int
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        call = { proxyService.searchTokensAllNetworks(query, limit) },
        // بدونِ fallback: این‌جا شبکه‌ای برای قرض‌گرفتن وجود ندارد. نتیجه‌ای که `networkId` نداشته
        // باشد غیرقابلِ استفاده است و باید بیفتد، نه اینکه به یک شبکهٔ دلخواه نسبت داده شود.
        map = { dto -> dto.items.mapNotNull { it.toDomain(fallbackNetworkId = null) }.knownNetworksOnly() }
    )

    override suspend fun resolveTokenByAddress(
        address: String
    ): ResultResponse<List<DiscoveredToken>> = proxyCall(
        call = { proxyService.resolveToken(address) },
        map = { dto -> dto.items.mapNotNull { it.toDomain(fallbackNetworkId = null) }.knownNetworksOnly() }
    )

    override suspend fun resolveTokenOnNetwork(
        networkId: String,
        contractAddress: String,
        walletAddress: String?
    ): ResultResponse<DiscoveredToken?> = proxyCall(
        call = {
            proxyService.resolveTokenOnNetwork(
                networkId = networkId,
                // ⚠️ عیناً همان چیزی که کاربر paste کرده. lowercase کردنش تا وقتی توکن در فهرستِ
                // آینه‌ای هست کار می‌کند و دقیقاً از لحظه‌ای می‌شکند که کسی چیزی خارج از فهرست
                // import کند — چون آن‌وقت باید on-chain خوانده شود و base58ِ ترون بازسازی‌شدنی نیست.
                contractAddress = contractAddress,
                walletAddress = walletAddress?.takeIf { it.isNotBlank() }
            )
        },
        map = { dto ->
            dto.item?.toDomain(fallbackNetworkId = networkId)
                ?.takeIf { networkCatalog.getNetworkInfoById(it.networkId) != null }
        }
    )

    /**
     * سرور زنجیره‌هایی را می‌شناسد که این نسخهٔ اپ ثبت نکرده. افزودنِ چنین توکنی یک ردیفِ مرده
     * می‌سازد: نه موجودی‌اش خوانده می‌شود نه قابلِ ارسال است.
     *
     * فیلتر است و نه مرتب‌سازی — ترتیبِ سرور (verified اول، بعد رتبهٔ ارزشِ بازار) دست‌نخورده
     * می‌ماند.
     */
    private fun List<DiscoveredToken>.knownNetworksOnly(): List<DiscoveredToken> =
        filter { token ->
            val known = networkCatalog.getNetworkInfoById(token.networkId) != null
            if (!known) {
                Timber.d("Dropping discovered token %s on unregistered network %s", token.symbol, token.networkId)
            }
            known
        }
}

/**
 * @param fallbackNetworkId فقط برای مسیرهای per-network که شبکه از قبل در URL بوده و سرور ممکن
 *   است در بدنه تکرارش نکند. برای مسیرهای cross-network باید `null` باشد.
 */
private fun DiscoveredTokenDto.toDomain(fallbackNetworkId: String?): DiscoveredToken? {
    val network = networkId?.takeIf { it.isNotBlank() } ?: fallbackNetworkId
    val contract = contractAddress?.takeIf { it.isNotBlank() }
    val decimalsValue = decimals
    val symbolValue = symbol?.takeIf { it.isNotBlank() }

    if (network == null || contract == null || decimalsValue == null || decimalsValue < 0 || symbolValue == null) {
        Timber.w(
            "Dropping token from discovery: network=%s contract=%s decimals=%s symbol=%s",
            network, contractAddress, decimals, symbol
        )
        return null
    }

    return DiscoveredToken(
        catalogId = id?.takeIf { it.isNotBlank() },
        networkId = network,
        symbol = symbolValue,
        name = faName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: symbolValue,
        decimals = decimalsValue,
        contractAddress = contract,
        iconUrl = iconUrl?.takeIf { it.isNotBlank() },
        featured = featured ?: false,
        verified = verified,
        marketCapRank = marketCapRank,
        priceUsd = priceUsd.toPriceOrNull(),
        curated = curated,
        source = source.toTokenSource()
    )
}

/**
 * قیمت به‌صورت رشتهٔ decimal پارس می‌شود و نه float — و رشتهٔ خراب `null` می‌دهد، نه صفر.
 * صفر یعنی «این دارایی بی‌ارزش است» و از مجموعِ سبد کم می‌شود؛ `null` یعنی «نمی‌دانیم» و «—»
 * نشان داده می‌شود.
 */
private fun String?.toPriceOrNull(): BigDecimal? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { BigDecimal(raw) }
        .onFailure { Timber.w("Unparsable priceUsd '%s' from discovery; treating as unknown", raw) }
        .getOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
}

private fun String?.toTokenSource(): TokenSource = when (this?.lowercase()) {
    "catalog" -> TokenSource.CATALOG
    "mirror" -> TokenSource.MIRROR
    "onchain" -> TokenSource.ONCHAIN
    else -> TokenSource.UNKNOWN
}
