package com.mtd.data.datasource

import com.mtd.domain.model.Asset
import com.mtd.domain.model.BlockchainConnectionMode
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

class TransportFailoverTest {

    // ── classifier ──────────────────────────────────────────────────────────────────────────────
    @Test
    fun `server and transport errors are failover-worthy`() {
        assertTrue(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.UpstreamUnavailable)))
        assertTrue(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.InternalError)))
        assertTrue(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.ServiceUnavailable)))
        assertTrue(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.RateLimited(null))))
        assertTrue(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.UnsupportedOperation)))
        assertTrue(TransportErrorClassifier.isFailoverWorthy(IOException("timeout")))
        // cause chain is walked
        assertTrue(TransportErrorClassifier.isFailoverWorthy(RuntimeException(IOException("reset"))))
    }

    @Test
    fun `business errors are NOT failover-worthy`() {
        assertFalse(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.ValidationError)))
        assertFalse(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.InvalidAddress)))
        assertFalse(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.InsufficientGasCredit)))
        assertFalse(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.SimulationReverted)))
        assertFalse(TransportErrorClassifier.isFailoverWorthy(ApiException(ApiError.BroadcastRejected)))
        assertFalse(TransportErrorClassifier.isFailoverWorthy(IllegalArgumentException("bad input")))
    }

    // ── decorator ───────────────────────────────────────────────────────────────────────────────
    private fun decorator(
        preferred: IChainDataSource,
        alternate: IChainDataSource,
        health: TransportHealthTracker = TransportHealthTracker()
    ) = TransportFailoverChainDataSource(
        preferred = preferred,
        alternateFactory = { alternate },
        preferredMode = BlockchainConnectionMode.PROXY,
        networkId = "sepolia",
        health = health
    )

    private val ok = ResultResponse.Success(BigDecimal.ONE)

    @Test
    fun `preferred success never touches the alternate`() = runTest {
        val preferred = mockk<IChainDataSource>()
        val alternate = mockk<IChainDataSource>()
        coEvery { preferred.getBalance(any()) } returns ok

        val result = decorator(preferred, alternate).getBalance("addr")

        assertSame(ok, result)
        coVerify(exactly = 0) { alternate.getBalance(any()) }
    }

    @Test
    fun `transient preferred error fails over to the alternate`() = runTest {
        val preferred = mockk<IChainDataSource>()
        val alternate = mockk<IChainDataSource>()
        val altOk = ResultResponse.Success(BigDecimal.TEN)
        coEvery { preferred.getBalance(any()) } returns
            ResultResponse.Error(ApiException(ApiError.UpstreamUnavailable))
        coEvery { alternate.getBalance(any()) } returns altOk

        val result = decorator(preferred, alternate).getBalance("addr")

        assertSame(altOk, result)
        coVerify(exactly = 1) { alternate.getBalance(any()) }
    }

    @Test
    fun `business preferred error is surfaced without failover`() = runTest {
        val preferred = mockk<IChainDataSource>()
        val alternate = mockk<IChainDataSource>()
        val err = ResultResponse.Error(ApiException(ApiError.ValidationError))
        coEvery { preferred.getBalance(any()) } returns err

        val result = decorator(preferred, alternate).getBalance("addr")

        assertSame(err, result)
        coVerify(exactly = 0) { alternate.getBalance(any()) }
    }

    @Test
    fun `when both transports fail the preferred error is surfaced`() = runTest {
        val preferred = mockk<IChainDataSource>()
        val alternate = mockk<IChainDataSource>()
        val preferredErr = ResultResponse.Error(ApiException(ApiError.InternalError))
        coEvery { preferred.getBalance(any()) } returns preferredErr
        coEvery { alternate.getBalance(any()) } returns
            ResultResponse.Error(IOException("also down"))

        val result = decorator(preferred, alternate).getBalance("addr")

        assertSame(preferredErr, result)
    }

    @Test
    fun `broadcast never fails over`() = runTest {
        val preferred = mockk<IChainDataSource>()
        val alternate = mockk<IChainDataSource>()
        coEvery { preferred.sendTransaction(any(), any()) } returns
            ResultResponse.Error(ApiException(ApiError.UpstreamUnavailable))
        val params = mockk<TransactionParams>()

        val result = decorator(preferred, alternate).sendTransaction(params, "key")

        assertTrue(result is ResultResponse.Error)
        coVerify(exactly = 0) { alternate.sendTransaction(any(), any()) }
    }

    @Test
    fun `open circuit tries the alternate first`() = runTest {
        val preferred = mockk<IChainDataSource>()
        val alternate = mockk<IChainDataSource>()
        // Threshold 1: a single preferred failure opens the circuit.
        val health = TransportHealthTracker(failureThreshold = 1, cooldownMs = 60_000L)
        coEvery { preferred.getBalance(any()) } returns
            ResultResponse.Error(ApiException(ApiError.UpstreamUnavailable))
        coEvery { alternate.getBalance(any()) } returns ResultResponse.Success(BigDecimal.TEN)
        val sut = decorator(preferred, alternate, health)

        // 1st call: preferred fails (opens circuit) → alternate serves.
        sut.getBalance("addr")
        // 2nd call: circuit open → alternate is tried FIRST, preferred not consulted again.
        val second = sut.getBalance("addr")

        assertEquals(BigDecimal.TEN, (second as ResultResponse.Success).data)
        coVerify(exactly = 1) { preferred.getBalance(any()) }
        coVerify(exactly = 2) { alternate.getBalance(any()) }
    }

    @Test
    fun `unused param keeps Asset import honest`() {
        // getBalanceAssets path returns List<Asset>; ensure the type is referenced (compile guard).
        val asset: Asset? = null
        assertFalse(asset != null)
    }
}
