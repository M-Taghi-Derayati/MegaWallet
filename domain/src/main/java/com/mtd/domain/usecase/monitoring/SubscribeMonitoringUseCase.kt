package com.mtd.domain.usecase.monitoring

import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IMonitoringRepository
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.model.MonitoringSubscription
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject

/**
 * TASK-32 — enrolls the **active** wallet's `(address, networkId)` pairs into the server's
 * realtime/deposit monitoring set (`/monitoring/subscribe`).
 *
 * Enrollment is **once per wallet**, not per app-start or per wallet switch:
 *  - the active wallet id is checked against a persisted "already-subscribed" set; if present this is a
 *    no-op (no network call), so switching back and forth between wallets never re-sends;
 *  - a wallet whose id is *not* yet recorded (freshly created/imported → it becomes the active wallet)
 *    is enrolled once, then recorded. A pre-existing wallet is likewise enrolled the first time it is
 *    active. Deletion prunes the id (see the delete flow) so a re-import re-enrolls.
 *
 * Uses the active wallet's real derived `keys` (the metadata-only `getAllWallets()` carries no keys),
 * mapping each key's [com.mtd.domain.model.core.NetworkName] to its bundle `networkId` and chunking to
 * the server's 25-pair bound. A partial failure is not recorded, so it retries next time the wallet is
 * active.
 */
class SubscribeMonitoringUseCase @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider,
    private val monitoringRepository: dagger.Lazy<IMonitoringRepository>,
    private val networkCatalog: INetworkCatalog,
    private val userPreferences: IUserPreferencesRepository
) {
    /** @return the pairs submitted for enrollment (empty when nothing new to enroll). */
    suspend operator fun invoke(): ResultResponse<List<MonitoringSubscription>> {
        val wallet = activeWalletProvider.activeWallet.value
            ?: return ResultResponse.Success(emptyList())

        val alreadySubscribed = userPreferences.getMonitoringSubscribedWalletIds()
        if (wallet.id in alreadySubscribed) return ResultResponse.Success(emptyList())

        val pairs = wallet.keys
            .mapNotNull { key ->
                // TASK-53 — هویت مستقیماً روی کلید است؛ فقط بررسی می‌کنیم شبکه واقعاً ثبت شده باشد.
                val networkId = networkCatalog.getNetworkInfoById(key.networkId)?.id
                    ?: return@mapNotNull null
                MonitoringSubscription(address = key.address, networkId = networkId)
            }
            .distinct()

        if (pairs.isEmpty()) return ResultResponse.Success(emptyList())

        // Enroll in chunks of ≤25 (the server's per-call bound). Record the wallet as subscribed only
        // when every chunk succeeded — a partial failure leaves it unrecorded so the next activation
        // retries (enrollment is idempotent server-side).
        var allSucceeded = true
        pairs.chunked(MAX_PAIRS_PER_CALL).forEach { chunk ->
            if (monitoringRepository.get().subscribe(chunk) is ResultResponse.Error) {
                allSucceeded = false
            }
        }
        if (allSucceeded) {
            userPreferences.setMonitoringSubscribedWalletIds(alreadySubscribed + wallet.id)
        }
        return ResultResponse.Success(pairs)
    }

    private companion object {
        const val MAX_PAIRS_PER_CALL = 25
    }
}
