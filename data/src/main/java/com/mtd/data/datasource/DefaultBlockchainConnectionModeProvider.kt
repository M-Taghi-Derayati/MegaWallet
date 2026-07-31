package com.mtd.data.datasource

import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KAN-9 / KAN-19 — resolves the active DIRECT/PROXY transport from the persisted user preference
 * ([IUserPreferencesRepository]) instead of hardcoding DIRECT.
 *
 * [currentMode] is non-suspending (it is consumed synchronously by [ChainDataSourceFactory] at
 * data-source creation time). TASK-05 / TD-19: it must NEVER block — the previous implementation
 * did `runBlocking { getConnectionMode() }` on the caller's thread, which stalled the **main
 * thread** when `MainScreenViewModel` seeded its state during composition. It now returns the
 * in-memory cache (or the DIRECT default) without touching disk. The cache is hydrated off the main
 * thread via [prime] (called at app start and from the ViewModel's init) and refreshed on [setMode].
 *
 * Until [prime] completes on a cold start, [currentMode] returns DIRECT (the default). This is
 * self-correcting: once the cache is primed, subsequent reads reflect the persisted choice, and the
 * factory selects the correct per-mode data source from then on.
 */
@Singleton
class DefaultBlockchainConnectionModeProvider @Inject constructor(
    private val userPreferencesRepository: IUserPreferencesRepository
) : IBlockchainConnectionModeProvider {

    @Volatile
    private var cachedMode: BlockchainConnectionMode? = null

    /** Non-blocking. Returns the cached mode, or DIRECT until [prime] has hydrated the cache. */
    override fun currentMode(): BlockchainConnectionMode =
        cachedMode ?: BlockchainConnectionMode.DIRECT

    /**
     * Hydrates the in-memory cache from the persisted preference. Suspend — call off the main thread
     * (app warm-up, ViewModel init). Idempotent and safe to call repeatedly.
     */
    suspend fun prime() {
        cachedMode = userPreferencesRepository.getConnectionMode()
    }

    /** Persists [mode] and refreshes the in-memory cache so the next [currentMode] reflects it. */
    suspend fun setMode(mode: BlockchainConnectionMode) {
        userPreferencesRepository.setConnectionMode(mode)
        cachedMode = mode
    }
}
