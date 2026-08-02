package com.mtd.data.datasource


import com.mtd.data.dto.BatchBalanceWalletDto
import com.mtd.data.dto.HistoryAddressDto
import com.mtd.domain.model.Asset
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.FeeData
import com.mtd.domain.model.HistoryPage
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionFeeDetails
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TransactionRecord
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * TASK-36 — smart transport failover. Classifies a failure as **transport/transient** (worth retrying
 * on the other transport) vs **business/final** (must surface verbatim — retrying elsewhere would only
 * mask the real error or, for a send, risk a duplicate). Pure & deterministic → unit-testable.
 */
internal object TransportErrorClassifier {
    private val TRANSIENT_HTTP = setOf(408, 425, 429, 500, 502, 503, 504)

    /** Walk the cause chain (bounded) looking for a transport/server signal. */
    fun isFailoverWorthy(error: Throwable?): Boolean {
        var cur: Throwable? = error
        var depth = 0
        while (cur != null && depth < 8) {
            when (cur) {
                // ApiException is authoritative — key on the typed code, not its cause.
                is ApiException -> return cur.apiError.isFailoverWorthy()
                is HttpException -> return cur.code() in TRANSIENT_HTTP
                is IOException -> return true            // timeout / connect / unknown-host / reset
                is CancellationException -> return true  // a source-captured withTimeout (NOT a live cancel)
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun ApiError.isFailoverWorthy(): Boolean = when (this) {
        // Server/proxy is unhealthy or can't serve this → the other transport may.
        ApiError.UpstreamUnavailable,
        ApiError.InternalError,
        ApiError.ServiceUnavailable,
        // Chain-family routing: this transport cannot serve this network at all → the other may.
        ApiError.NetworkFamilyUnsupported -> true
        is ApiError.RateLimited -> true
        is ApiError.Unknown -> true

        // NOT failover-worthy, despite being a capability signal. UnsupportedOperation is how a
        // transport says "this operation is not part of what I do" — DIRECT for unified history,
        // PROXY for per-network history. Failing over silently sent every address to the proxy while
        // the user had explicitly selected DIRECT, and it also swallowed the signal the caller needs:
        // TransactionHistoryViewModel branches on exactly this error to fall back to the per-network
        // DIRECT path, and that branch could never be reached.
        ApiError.UnsupportedOperation -> false
        // Business/final: validation, bad address/signed-tx, not-found, revert, gas-credit, race,
        // requote, idempotency, broadcast-rejected, mystery-box/device/swap → never fail over.
        else -> false
    }
}

/**
 * Best-effort circuit breaker so we don't pay the known-bad transport's latency on every call. After
 * [failureThreshold] consecutive failures for a key, the circuit is "open" for [cooldownMs] — while
 * open the decorator tries the alternate first. A success closes it immediately.
 */
class TransportHealthTracker(
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 30_000L
) {
    private class Health {
        @Volatile var consecutiveFailures = 0
        @Volatile var openUntilMs = 0L
    }

    private val map = ConcurrentHashMap<String, Health>()

    fun isCircuitOpen(key: String): Boolean {
        val h = map[key] ?: return false
        return System.currentTimeMillis() < h.openUntilMs
    }

    fun recordSuccess(key: String) {
        map[key]?.let { it.consecutiveFailures = 0; it.openUntilMs = 0L }
    }

    fun recordFailure(key: String) {
        val h = map.getOrPut(key) { Health() }
        val n = h.consecutiveFailures + 1
        h.consecutiveFailures = n
        if (n >= failureThreshold) {
            h.openUntilMs = System.currentTimeMillis() + cooldownMs
        }
    }
}

/**
 * TASK-36 — an [IChainDataSource] decorator that makes the user's selected transport *self-healing*:
 * on a classified transport/transient failure of the [preferred] source it transparently retries the
 * same **read** on the [alternate] transport, and remembers which one is healthy (circuit breaker) so
 * a persistently-down primary stops costing latency. Both sources return the same domain types, so the
 * fallback is invisible to callers (the same invariant the DIRECT/PROXY toggle relies on).
 *
 * **Broadcast is intentionally NOT failed over** (v1): the two transports build/sign a send
 * differently, so re-broadcasting on the other could produce a *different* signed tx (duplicate /
 * double-spend risk). [sendTransaction] always goes to [preferred] only.
 *
 * The user's saved mode stays the *preferred* transport — failover is a temporary override, never a
 * silent permanent switch.
 */
internal class TransportFailoverChainDataSource(
    private val preferred: IChainDataSource,
    alternateFactory: () -> IChainDataSource,
    private val preferredMode: BlockchainConnectionMode,
    private val networkId: String,
    private val health: TransportHealthTracker
) : IChainDataSource {

    // Built only on first failover (most calls never touch the alternate transport).
    private val alternate: IChainDataSource by lazy(alternateFactory)
    private val healthKey: String get() = "${preferredMode.name}:$networkId"

    // ── reads: fail over on transport/transient errors ──────────────────────────────────────────
    override suspend fun getBalanceAssets(address: String): ResultResponse<List<Asset>> =
        read("getBalanceAssets") { it.getBalanceAssets(address) }

    override suspend fun getBalance(address: String): ResultResponse<BigDecimal> =
        read("getBalance") { it.getBalance(address) }

    override suspend fun getBalancesForMultipleAddresses(
        addresses: List<String>
    ): ResultResponse<Map<String, List<Asset>>> =
        read("getBalancesForMultipleAddresses") { it.getBalancesForMultipleAddresses(addresses) }

    override suspend fun getFeeOptions(
        fromAddress: String?,
        toAddress: String?,
        asset: Asset?,
        amount: BigInteger?
    ): ResultResponse<List<FeeData>> =
        read("getFeeOptions") { it.getFeeOptions(fromAddress, toAddress, asset, amount) }

    override suspend fun getTransactionHistory(address: String): ResultResponse<List<TransactionRecord>> =
        read("getTransactionHistory") { it.getTransactionHistory(address) }

    override suspend fun getTransactionFeeDetails(txId: String): ResultResponse<TransactionFeeDetails> =
        read("getTransactionFeeDetails") { it.getTransactionFeeDetails(txId) }

    override suspend fun getHistory(
        addresses: List<HistoryAddressDto>,
        cursor: String?,
        limit: Int?
    ): ResultResponse<HistoryPage> =
        read("getHistory") { it.getHistory(addresses, cursor, limit) }

    override suspend fun getBatchBalances(
        wallets: List<BatchBalanceWalletDto>
    ): ResultResponse<Map<String, ResultResponse<List<Asset>>>> =
        read("getBatchBalances") { it.getBatchBalances(wallets) }

    // ── broadcast: preferred only, never failed over (duplicate/double-spend risk) ──────────────
    override suspend fun sendTransaction(
        params: TransactionParams,
        privateKeyHex: String
    ): ResultResponse<String> = preferred.sendTransaction(params, privateKeyHex)

    /**
     * Run [call] on the preferred transport (or the alternate first when the circuit is open), and on
     * a *failover-worthy* error try the other. Health is tracked on the preferred transport only; when
     * both fail, the preferred transport's error is surfaced (most actionable to the user).
     */
    private suspend fun <T> read(
        op: String,
        call: suspend (IChainDataSource) -> ResultResponse<T>
    ): ResultResponse<T> {
        val circuitOpen = health.isCircuitOpen(healthKey)
        val firstIsPreferred = !circuitOpen
        val firstSrc = if (firstIsPreferred) preferred else alternate

        val firstResult = attempt(firstSrc, call)
        if (firstResult is ResultResponse.Success) {
            if (firstIsPreferred) health.recordSuccess(healthKey)
            return firstResult
        }
        val firstError = (firstResult as ResultResponse.Error).exception
        if (firstIsPreferred) health.recordFailure(healthKey)
        if (!TransportErrorClassifier.isFailoverWorthy(firstError)) return firstResult

        val secondIsPreferred = circuitOpen
        val secondSrc = if (secondIsPreferred) preferred else alternate
        Timber.w(
            firstError,
            "[Failover] %s: %s transport failed (transient) -> retrying on the other",
            op,
            if (firstIsPreferred) preferredMode.name else "ALTERNATE"
        )

        val secondResult = attempt(secondSrc, call)
        if (secondResult is ResultResponse.Success) {
            if (secondIsPreferred) health.recordSuccess(healthKey)
            return secondResult
        }
        if (secondIsPreferred) health.recordFailure(healthKey)
        // Both failed — surface the PREFERRED transport's error, not the fallback's.
        return if (firstIsPreferred) firstResult else secondResult
    }

    /** Never swallow a live coroutine cancellation; any other throw becomes a typed error result. */
    private suspend fun <T> attempt(
        src: IChainDataSource,
        call: suspend (IChainDataSource) -> ResultResponse<T>
    ): ResultResponse<T> = try {
        call(src)
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        ResultResponse.Error(e)
    }
}
