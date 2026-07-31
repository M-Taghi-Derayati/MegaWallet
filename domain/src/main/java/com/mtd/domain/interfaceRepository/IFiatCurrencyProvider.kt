package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.FiatCurrency
import kotlinx.coroutines.flow.StateFlow

/**
 * TASK-56 — the single, observable owner of the user's selected fiat currency.
 *
 * Mirrors [IUsdToIrrRateProvider], deliberately. The preference itself is a suspend get/set on
 * [IUserPreferencesRepository]; if screens read it *that* way, each one would snapshot the value at
 * some arbitrary moment and never notice a switch — the same defect TASK-54 had to unwind for the
 * Toman rate. Collect [currency] and the whole UI re-renders on one tap.
 *
 * A fiat surface generally needs **both** this and `IUsdToIrrRateProvider.rate`: the currency says
 * which unit to render, the rate says what to multiply by.
 */
interface IFiatCurrencyProvider {

    /** The selected currency. Seeded with [FiatCurrency.DEFAULT] until [ensurePrimed] has run. */
    val currency: StateFlow<FiatCurrency>

    /**
     * Hydrates [currency] from the persisted preference. Suspend, so the disk read stays off the main
     * thread (TD-19); idempotent, so every consumer can call it in `init` without coordinating.
     *
     * Until it completes on a cold start [currency] reports [FiatCurrency.DEFAULT]. That is
     * self-correcting rather than wrong: USD is the unit balances are computed in, so it renders
     * correctly, and the persisted choice takes over as soon as the read lands.
     */
    suspend fun ensurePrimed()

    /** Persists [currency] and publishes it, in that order. */
    suspend fun set(currency: FiatCurrency)

    /** Flips USD ⇄ TOMAN. Convenience for the header toggle; goes through [set], so it persists. */
    suspend fun toggle()
}
