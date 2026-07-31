package com.mtd.domain.model

/**
 * TASK-56 / TASK S §2.2-D — the fiat unit every money value in the UI is rendered in.
 *
 * Replaces the in-memory `HomeUiState.DisplayCurrency`, which only the wallet list honoured and which
 * did not survive process death. This enum is the persisted preference
 * ([com.mtd.domain.interfaceRepository.IUserPreferencesRepository.getFiatCurrency]) and the value
 * every screen observes through [com.mtd.domain.interfaceRepository.IFiatCurrencyProvider].
 *
 * **Units, stated once** (the conversion itself lives in exactly one place — `core/utils/FiatConversion`):
 *  - [USD] — the unit every balance is *computed* in. Asset prices arrive in USD, so USD needs no
 *    conversion and is always available, even offline.
 *  - [TOMAN] — تومان. **Not** rial: 1 تومان = 10 ﷼. Reaching it requires the USD→تومان factor from
 *    [com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider], which may be unknown — in which case
 *    the UI shows a placeholder, never `0` and never a guess.
 */
enum class FiatCurrency {
    USD,
    TOMAN;

    companion object {
        /** USD: it is the computed unit, so it renders correctly before any rate is known. */
        val DEFAULT = USD

        /** Tolerant parse for the persisted value; an unknown/absent name falls back to [DEFAULT]. */
        fun fromNameOrDefault(name: String?): FiatCurrency =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
