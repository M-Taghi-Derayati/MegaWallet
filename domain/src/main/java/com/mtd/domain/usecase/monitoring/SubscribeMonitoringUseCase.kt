package com.mtd.domain.usecase.monitoring

import com.mtd.domain.interfaceRepository.IMonitoringRepository
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.MonitoringSubscription
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject

/**
 * TASK-32 — enrolls EVERY local wallet's `(address, networkId)` pairs into the server's realtime/deposit
 * monitoring set in one shot, instead of relying on the per-wallet `/history` side-effect that only
 * enrolled the currently-open wallet (so non-active wallets got no realtime/deposit signals until the
 * user opened their history).
 *
 * Gathers all pairs across every wallet, maps each key's [com.mtd.domain.model.core.NetworkName] to its
 * bundle `networkId`, dedups, and calls the repo in chunks of the server's 25-pair bound. Registration
 * is durable + idempotent, so this is safe to fire on every login / create / import / wallet switch;
 * a failed chunk just retries on the next trigger (the repo logs per-chunk failures).
 */
class SubscribeMonitoringUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>,
    private val monitoringRepository: dagger.Lazy<IMonitoringRepository>,
    private val networkCatalog: INetworkCatalog
) {
    /** @return the pairs submitted for enrollment, or an Error if the local wallet set can't be read. */
    suspend operator fun invoke(): ResultResponse<List<MonitoringSubscription>> {
        val wallets = when (val result = walletRepository.get().getAllWallets()) {
            is ResultResponse.Success -> result.data
            is ResultResponse.Error -> return ResultResponse.Error(result.exception)
        }

        val pairs = wallets
            .asSequence()
            .flatMap { it.keys.asSequence() }
            .mapNotNull { key ->
                val networkId = networkCatalog.getNetworkInfoByName(key.networkName)?.id
                    ?: return@mapNotNull null
                MonitoringSubscription(address = key.address, networkId = networkId)
            }
            .distinct()
            .toList()

        if (pairs.isEmpty()) return ResultResponse.Success(emptyList())

        // Enroll in chunks of ≤25 (the server's per-call bound, matching `/history`). Per-chunk failures
        // are swallowed here (the repo logs them) — enrollment is idempotent, so the next trigger retries.
        pairs.chunked(MAX_PAIRS_PER_CALL).forEach { chunk ->
            monitoringRepository.get().subscribe(chunk)
        }
        return ResultResponse.Success(pairs)
    }

    private companion object {
        const val MAX_PAIRS_PER_CALL = 25
    }
}
