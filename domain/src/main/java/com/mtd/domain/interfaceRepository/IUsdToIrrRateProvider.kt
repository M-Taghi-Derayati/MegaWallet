package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.CurrencyRate
import kotlinx.coroutines.flow.StateFlow

/**
 * TASK-54 — the single, observable source of the USD→IRR/Toman rate (Wallex).
 *
 * Before this existed the rate was a value each consumer *pulled* on its own — a suspend getter behind a
 * private cache in `HomeViewModel`, a one-shot `init` fetch in `SendViewModel`, and a
 * `LaunchedEffect(homeViewModel)` in `AssetDetailScreen` whose key never changes. A newly fetched rate
 * therefore reached the log but not the screen, and the three copies could disagree. Collect [rate]
 * instead; the value updates in place when a refresh lands.
 *
 * [rate] is `null` when the rate is **not yet known**. Render that as a placeholder — never as `0`, and
 * never as a hardcoded guess. A failed refresh keeps the last known good value rather than inventing one.
 */
interface IUsdToIrrRateProvider {

    /** Latest known rate, or `null` if none has been obtained yet. Never a fabricated value. */
    val rate: StateFlow<CurrencyRate?>

    /**
     * Publishes the last known rate from local storage if nothing is known yet. **Never touches the
     * network**, so it is safe on a path that must not wait on Wallex — a cold start can render a
     * sensible Toman value immediately instead of blanking until the first round-trip returns.
     */
    suspend fun ensureSeeded()

    /**
     * Refreshes if the cached value is older than the provider's TTL.
     *
     * @param force refresh regardless of the TTL (pull-to-refresh).
     */
    suspend fun refresh(force: Boolean = false)
}
