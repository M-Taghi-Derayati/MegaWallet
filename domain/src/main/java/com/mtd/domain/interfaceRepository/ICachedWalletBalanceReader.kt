package com.mtd.domain.interfaceRepository

import java.math.BigDecimal

interface ICachedWalletBalanceReader {

    /**
     * A wallet's cached total, in **USD** — the unit balances are computed in.
     *
     * TASK-56 — this used to return a formatted `"$1,234"` string, built in the data layer. That made
     * the wallet switcher the one fiat surface the USD⇄تومان toggle could not reach: the currency was
     * already baked into the string before any ViewModel saw it. Formatting belongs to the caller,
     * which knows the selected currency and the rate.
     *
     * Returns [BigDecimal.ZERO] when nothing is cached — a genuinely empty wallet and an unsynced one
     * are indistinguishable here, and both correctly render as zero.
     */
    suspend fun getCachedTotalUsd(walletId: String): BigDecimal
}
