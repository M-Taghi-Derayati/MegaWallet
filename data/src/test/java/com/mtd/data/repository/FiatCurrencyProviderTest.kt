package com.mtd.data.repository

import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.model.FiatCurrency
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * TASK-56 — the observable fiat-currency preference.
 *
 * The behaviours under test are the ones the acceptance criteria hang on: the choice survives process
 * death (it is read back from the preference), and one write is seen by every observer.
 */
class FiatCurrencyProviderTest {

    private lateinit var prefs: IUserPreferencesRepository
    private lateinit var provider: FiatCurrencyProvider

    @Before
    fun setUp() {
        prefs = mockk()
        coEvery { prefs.setFiatCurrency(any()) } just Runs
        provider = FiatCurrencyProvider(prefs)
    }

    @Test
    fun `starts on USD before the preference has been read`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.TOMAN

        // No ensurePrimed() yet: USD is the safe initial value because it needs no rate to render.
        assertEquals(FiatCurrency.USD, provider.currency.value)
    }

    @Test
    fun `ensurePrimed publishes the persisted choice - survives process death`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.TOMAN

        provider.ensurePrimed()

        assertEquals(FiatCurrency.TOMAN, provider.currency.value)
    }

    @Test
    fun `ensurePrimed reads the preference only once`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.TOMAN

        provider.ensurePrimed()
        provider.ensurePrimed()
        provider.ensurePrimed()

        coVerify(exactly = 1) { prefs.getFiatCurrency() }
    }

    @Test
    fun `concurrent ensurePrimed callers collapse into one read`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.TOMAN

        // Several ViewModels prime in their init blocks at roughly the same moment.
        (1..8).map { async { provider.ensurePrimed() } }.awaitAll()

        coVerify(exactly = 1) { prefs.getFiatCurrency() }
        assertEquals(FiatCurrency.TOMAN, provider.currency.value)
    }

    @Test
    fun `set persists and publishes`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.USD

        provider.set(FiatCurrency.TOMAN)

        coVerify(exactly = 1) { prefs.setFiatCurrency(FiatCurrency.TOMAN) }
        assertEquals(FiatCurrency.TOMAN, provider.currency.value)
    }

    @Test
    fun `toggle flips both ways and persists each flip`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.USD

        provider.toggle()
        assertEquals(FiatCurrency.TOMAN, provider.currency.value)

        provider.toggle()
        assertEquals(FiatCurrency.USD, provider.currency.value)

        coVerify(exactly = 1) { prefs.setFiatCurrency(FiatCurrency.TOMAN) }
        coVerify(exactly = 1) { prefs.setFiatCurrency(FiatCurrency.USD) }
    }

    @Test
    fun `toggle honours the persisted value rather than the USD default`() = runTest {
        // The user last chose تومان; the very first tap must go back to USD, not to تومان again.
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.TOMAN

        provider.toggle()

        assertEquals(FiatCurrency.USD, provider.currency.value)
    }

    @Test
    fun `a later ensurePrimed cannot revert a choice the user just made`() = runTest {
        coEvery { prefs.getFiatCurrency() } returns FiatCurrency.USD

        provider.set(FiatCurrency.TOMAN)
        // A ViewModel created after the tap still calls ensurePrimed in its init.
        provider.ensurePrimed()

        assertEquals(FiatCurrency.TOMAN, provider.currency.value)
        coVerify(exactly = 0) { prefs.getFiatCurrency() }
    }
}
