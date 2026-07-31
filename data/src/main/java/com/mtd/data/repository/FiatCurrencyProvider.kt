package com.mtd.data.repository

import com.mtd.domain.interfaceRepository.IFiatCurrencyProvider
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.model.FiatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-56 — `@Singleton` so every ViewModel observes the *same* currency and they cannot disagree.
 *
 * The initial value is [FiatCurrency.DEFAULT] rather than a blocking read of the preference: doing the
 * disk read in the constructor would put it on whichever thread first injects this (often the main
 * thread during composition), which is the stall TD-19 removed from
 * [com.mtd.data.datasource.DefaultBlockchainConnectionModeProvider]. [ensurePrimed] does the read from
 * a coroutine and publishes the persisted choice.
 */
@Singleton
class FiatCurrencyProvider @Inject constructor(
    private val userPreferencesRepository: IUserPreferencesRepository
) : IFiatCurrencyProvider {

    private val _currency = MutableStateFlow(FiatCurrency.DEFAULT)
    override val currency: StateFlow<FiatCurrency> = _currency.asStateFlow()

    private val primeMutex = Mutex()

    @Volatile
    private var primed = false

    override suspend fun ensurePrimed() {
        if (primed) return
        primeMutex.withLock {
            if (primed) return
            _currency.value = userPreferencesRepository.getFiatCurrency()
            primed = true
        }
    }

    /**
     * Takes the same lock as [ensurePrimed] on purpose: without it, a `set` landing while a cold-start
     * prime is mid-read would be overwritten by that read's now-stale result, silently reverting the
     * user's tap.
     */
    override suspend fun set(currency: FiatCurrency) = primeMutex.withLock {
        userPreferencesRepository.setFiatCurrency(currency)
        // The in-memory value is authoritative from here on, so a later [ensurePrimed] is a no-op.
        primed = true
        _currency.value = currency
    }

    override suspend fun toggle() {
        ensurePrimed()
        set(
            when (_currency.value) {
                FiatCurrency.USD -> FiatCurrency.TOMAN
                FiatCurrency.TOMAN -> FiatCurrency.USD
            }
        )
    }
}
