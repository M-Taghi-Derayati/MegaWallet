package com.mtd.data.repository

import com.mtd.domain.interfaceRepository.IAppCacheStore
import com.mtd.domain.interfaceRepository.IMarketDataRepository
import com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider
import com.mtd.domain.model.CurrencyRate
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-54 — single observable owner of the USD→IRR/Toman rate (Wallex).
 *
 * Replaces three private copies that were each pulled once and then went stale: `HomeViewModel`'s
 * suspend getter + 3-minute field cache, `SendViewModel`'s one-shot `init` fetch (defaulting to a
 * hardcoded 70000), and `AssetDetailScreen`'s `LaunchedEffect` whose key never changed. Consumers now
 * collect [rate], so a refresh that lands anywhere is seen everywhere.
 *
 * Contract kept deliberately narrow:
 *  - `null` means **unknown**. A failed refresh leaves the previous good value in place and never
 *    substitutes a placeholder number — the UI decides how to render "unknown".
 *  - concurrent [refresh] calls collapse into one network round-trip (several screens observe this).
 *  - the last good value is persisted so a cold start shows something immediately instead of blanking
 *    until the first Wallex round-trip returns.
 */
@Singleton
class UsdToIrrRateProvider @Inject constructor(
    private val marketDataRepository: IMarketDataRepository,
    private val cacheStore: IAppCacheStore
) : IUsdToIrrRateProvider {

    private val _rate = MutableStateFlow<CurrencyRate?>(null)
    override val rate: StateFlow<CurrencyRate?> = _rate.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile
    private var seeded = false

    override suspend fun ensureSeeded() {
        if (seeded) return
        refreshMutex.withLock {
            if (seeded) return
            seedFromCache()
            seeded = true
        }
    }

    override suspend fun refresh(force: Boolean) {
        ensureSeeded()
        // Single-flight: while one refresh is in flight the others wait, then see it already fresh and
        // return without issuing a second call.
        refreshMutex.withLock {
            if (!force && isFresh(_rate.value)) return

            when (val result = marketDataRepository.getUsdToIrrRate()) {
                is ResultResponse.Success -> {
                    _rate.value = result.data
                    persist(result.data)
                }
                is ResultResponse.Error -> {
                    // Keep the last good value; the caller's UI keeps showing it (or the placeholder if
                    // there never was one). Reporting is the consumer's job — a background rate refresh
                    // failing is not worth a snackbar (ErrorSurface.SILENT).
                    Timber.w(result.exception, "[UsdToIrrRate] refresh failed; keeping last known value")
                }
            }
        }
    }

    private fun isFresh(current: CurrencyRate?): Boolean {
        val value = current ?: return false
        return System.currentTimeMillis() - value.lastUpdated < TTL_MS
    }

    /**
     * Cold-start seed. The persisted value carries no timestamp, so it is treated as **stale on
     * purpose**: it is good enough to render immediately, and [isFresh] will still let the very next
     * refresh go through.
     */
    private suspend fun seedFromCache() {
        if (_rate.value != null) return
        val saved = runCatching {
            cacheStore.get(CACHE_KEY, String::class.java)?.toBigDecimalOrNull()
        }.getOrNull()
        if (saved != null && saved > BigDecimal.ZERO) {
            _rate.value = CurrencyRate(
                quoteCurrency = QUOTE_CURRENCY,
                baseCurrency = BASE_CURRENCY,
                rate = saved,
                lastUpdated = 0L
            )
        }
    }

    private suspend fun persist(value: CurrencyRate) {
        runCatching { cacheStore.put(CACHE_KEY, value.rate.toPlainString()) }
            .onFailure { Timber.w(it, "[UsdToIrrRate] failed to persist last known rate") }
    }

    private companion object {
        /** Matches the 3-minute window the HomeViewModel cache used before this existed. */
        const val TTL_MS = 3 * 60 * 1000L

        /** Same key the old ad-hoc HomeViewModel persistence used, so existing installs keep their value. */
        const val CACHE_KEY = "LAST_IRR_RATE"

        /**
         * TASK-56 — must match what [MarketDataRepositoryImpl] emits: the cached number is the same
         * Wallex تومان value, so seeding it as "IRR" would make `FiatConversion` divide it by ten and
         * every cold start would show a تومان balance a tenth of the truth until the first refresh.
         */
        const val QUOTE_CURRENCY = "TMN"
        const val BASE_CURRENCY = "USDT"
    }
}
