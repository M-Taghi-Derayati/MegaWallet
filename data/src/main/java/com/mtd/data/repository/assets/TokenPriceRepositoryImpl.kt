package com.mtd.data.repository.assets

import com.mtd.data.dto.TokenPriceQueryDto
import com.mtd.data.dto.TokenPricesRequestDto
import com.mtd.data.network.proxyCall
import com.mtd.data.service.MobileProxyApiService
import com.mtd.domain.interfaceRepository.ITokenPriceRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.TokenPriceQuery
import com.mtd.domain.model.assets.TokenPriceResult
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `POST /api/mobile/v1/tokens/prices` — قیمت بر اساسِ آدرسِ قرارداد.
 *
 * chunk کردن این‌جاست چون سقفِ ۱۰۰تاییِ سرور جزئیاتِ ترابری است و فراخوان نباید بشناسدش. یک
 * chunkِ شکست‌خورده کلِ نتیجه را از بین نمی‌برد: قیمت‌هایی که آمده‌اند برمی‌گردند و بقیه در عمل
 * مثلِ `missing` رفتار می‌کنند — که همان مسیرِ «—» است، نه صفر.
 */
@Singleton
class TokenPriceRepositoryImpl @Inject constructor(
    private val proxyService: MobileProxyApiService
) : ITokenPriceRepository {

    override suspend fun getPricesByContract(
        queries: List<TokenPriceQuery>
    ): ResultResponse<TokenPriceResult> {
        if (queries.isEmpty()) return ResultResponse.Success(TokenPriceResult.EMPTY)

        val prices = mutableMapOf<String, BigDecimal>()
        val missing = mutableListOf<String>()
        val sources = linkedSetOf<String>()
        var lastError: ResultResponse.Error? = null

        // یکتاسازی روی جفتِ (شبکه، آدرسِ **عینی**). lowercase نمی‌کنیم چون خودِ آدرس است که
        // فرستاده می‌شود و برای ترون شکلش معنا دارد.
        val distinct = queries.distinctBy { "${it.networkId}:${it.address}" }

        distinct.chunked(TokenPriceResult.MAX_TOKENS_PER_REQUEST).forEach { chunk ->
            val body = TokenPricesRequestDto(
                tokens = chunk.map {
                    TokenPriceQueryDto(
                        networkId = it.networkId,
                        // ⚠️ عیناً. base58ِ ترون حساس به حروف است؛ EVM بی‌تفاوت است، پس عینی‌فرستادن
                        // هیچ‌وقت ضرر ندارد و lowercase گاهی همه‌چیز را می‌شکند.
                        address = it.address,
                        symbol = it.symbol?.takeIf { s -> s.isNotBlank() }
                    )
                }
            )

            val result = proxyCall(
                call = { proxyService.tokenPrices(body) },
                map = { dto -> dto }
            )

            when (result) {
                is ResultResponse.Success -> {
                    result.data.prices?.forEach { (key, raw) ->
                        raw.toPositivePriceOrNull()?.let { prices[key] = it }
                            ?: Timber.w("Unusable price '%s' for %s; treating as missing", raw, key)
                    }
                    result.data.missing?.let { missing += it }
                    result.data.sources?.let { sources += it }
                }

                is ResultResponse.Error -> {
                    Timber.w(result.exception, "Contract price chunk failed (%d tokens)", chunk.size)
                    lastError = result
                }
            }
        }

        // فقط وقتی خطا برمی‌گردانیم که **هیچ** قیمتی نگرفته باشیم. پاسخِ ناقص عادی است و
        // برگرداندنِ خطا برای آن، قیمت‌های سالم را هم دور می‌ریزد.
        val error = lastError
        if (prices.isEmpty() && error != null) return error

        return ResultResponse.Success(
            TokenPriceResult(
                prices = prices,
                missing = missing.distinct(),
                sources = sources.toList()
            )
        )
    }
}

/** قیمتِ نامعتبر یا ناصفر-نشدنی «نامعلوم» است، نه صفر. */
private fun String?.toPositivePriceOrNull(): BigDecimal? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { BigDecimal(raw) }.getOrNull()?.takeIf { it > BigDecimal.ZERO }
}
