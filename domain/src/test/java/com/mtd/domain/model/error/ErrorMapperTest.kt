package com.mtd.domain.model.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * TASK-57 — the error surface contract, pinned.
 *
 * The rules under test:
 *  1. every [ApiError] variant has curated Persian **and** English copy (no blanks, no codes);
 *  2. no raw exception / DTO / server text ever becomes the user-facing message;
 *  3. technical detail is available but PII-scrubbed;
 *  4. the call-site fallback applies only when the taxonomy has nothing better.
 */
class ErrorMapperTest {

    /**
     * Every variant of the sealed [ApiError]. Adding a case to the taxonomy without adding it here
     * fails [allVariantsAreCovered], which is the point.
     */
    private val allApiErrors: List<ApiError> = listOf(
        ApiError.NetworkNotFound,
        ApiError.AssetNotFound,
        ApiError.ValidationError,
        ApiError.InvalidAddress,
        ApiError.InvalidSignedTx,
        ApiError.UnsupportedOperation,
        ApiError.SimulationReverted,
        ApiError.NetworkFamilyUnsupported,
        ApiError.UpstreamUnavailable,
        ApiError.BroadcastRejected,
        ApiError.InternalError,
        ApiError.RaceConditionLock,
        ApiError.InsufficientGasCredit,
        ApiError.RequoteRequired,
        ApiError.IdempotencyKeyConflict,
        ApiError.RateLimited(retryAfterSec = 30),
        ApiError.RateLimited(retryAfterSec = null),
        ApiError.ServiceUnavailable,
        ApiError.GasCreditUnavailable,
        ApiError.MysteryBoxAlreadyOpened,
        ApiError.MysteryBoxNotFound,
        ApiError.DeviceRequired,
        ApiError.DeviceRewardLimitExceeded,
        ApiError.SwapNoRoutes,
        ApiError.Unknown(code = "SOMETHING_NEW", rawMessage = null)
    )

    // ── 1. Coverage of the taxonomy ─────────────────────────────────────────

    /**
     * Compile-time completeness guard. This `when` has no `else`, so adding a variant to
     * [ApiError] stops this file from compiling until the variant is named — and
     * [allVariantsAreCovered] then checks it was also added to the [allApiErrors] fixture.
     * (Reflection is deliberately avoided: `sealedSubclasses` needs kotlin-reflect, which
     * `:domain` does not depend on.)
     */
    private fun label(error: ApiError): String = when (error) {
        ApiError.NetworkNotFound -> "NetworkNotFound"
        ApiError.AssetNotFound -> "AssetNotFound"
        ApiError.ValidationError -> "ValidationError"
        ApiError.InvalidAddress -> "InvalidAddress"
        ApiError.InvalidSignedTx -> "InvalidSignedTx"
        ApiError.UnsupportedOperation -> "UnsupportedOperation"
        ApiError.SimulationReverted -> "SimulationReverted"
        ApiError.NetworkFamilyUnsupported -> "NetworkFamilyUnsupported"
        ApiError.UpstreamUnavailable -> "UpstreamUnavailable"
        ApiError.BroadcastRejected -> "BroadcastRejected"
        ApiError.InternalError -> "InternalError"
        ApiError.RaceConditionLock -> "RaceConditionLock"
        ApiError.InsufficientGasCredit -> "InsufficientGasCredit"
        ApiError.RequoteRequired -> "RequoteRequired"
        ApiError.IdempotencyKeyConflict -> "IdempotencyKeyConflict"
        is ApiError.RateLimited -> "RateLimited"
        ApiError.ServiceUnavailable -> "ServiceUnavailable"
        ApiError.GasCreditUnavailable -> "GasCreditUnavailable"
        ApiError.MysteryBoxAlreadyOpened -> "MysteryBoxAlreadyOpened"
        ApiError.MysteryBoxNotFound -> "MysteryBoxNotFound"
        ApiError.DeviceRequired -> "DeviceRequired"
        ApiError.DeviceRewardLimitExceeded -> "DeviceRewardLimitExceeded"
        ApiError.SwapNoRoutes -> "SwapNoRoutes"
        is ApiError.Unknown -> "Unknown"
    }

    @Test
    fun `allVariantsAreCovered - the fixture exercises every ApiError subtype`() {
        val expected = setOf(
            "NetworkNotFound", "AssetNotFound", "ValidationError", "InvalidAddress",
            "InvalidSignedTx", "UnsupportedOperation", "SimulationReverted",
            "NetworkFamilyUnsupported", "UpstreamUnavailable", "BroadcastRejected",
            "InternalError", "RaceConditionLock", "InsufficientGasCredit", "RequoteRequired",
            "IdempotencyKeyConflict", "RateLimited", "ServiceUnavailable", "GasCreditUnavailable",
            "MysteryBoxAlreadyOpened", "MysteryBoxNotFound", "DeviceRequired",
            "DeviceRewardLimitExceeded", "SwapNoRoutes", "Unknown"
        )
        assertEquals(
            "ApiError gained a variant with no test coverage",
            expected,
            allApiErrors.map(::label).toSet()
        )
    }

    @Test
    fun `farsiMessage - every variant has non-blank Persian copy`() {
        allApiErrors.forEach { error ->
            val message = ApiErrorMessageMapper.farsiMessage(error)
            assertTrue("blank Persian message for $error", message.isNotBlank())
        }
    }

    @Test
    fun `englishMessage - every variant has non-blank English copy`() {
        allApiErrors.forEach { error ->
            val message = ApiErrorMessageMapper.englishMessage(error)
            assertTrue("blank English message for $error", message.isNotBlank())
        }
    }

    @Test
    fun `getUserMessage - every variant produces user-facing copy, never a code`() {
        allApiErrors.forEach { error ->
            val message = ErrorMapper.getUserMessage(ApiException(apiError = error))

            assertTrue("blank message for $error", message.isNotBlank())
            assertFalse("code leaked into the message for $error", message.contains('_'))
            assertFalse("class name leaked for $error", message.contains(error::class.simpleName!!))
            assertFalse("exception text leaked for $error", message.contains("ApiException"))
        }
    }

    @Test
    fun `getUserMessage - each variant carries its curated copy, not the generic fallback`() {
        // Only the deliberately-unknown bucket may fall back to the generic wording.
        allApiErrors
            .filterNot { it is ApiError.Unknown }
            .forEach { error ->
                val message = ErrorMapper.getUserMessage(ApiException(apiError = error))
                assertEquals(
                    "curated copy not used for $error",
                    ApiErrorMessageMapper.farsiMessage(error),
                    message
                )
            }
    }

    @Test
    fun `technicalCode - is stable and never blank`() {
        allApiErrors.forEach { error ->
            assertTrue(ApiErrorMessageMapper.technicalCode(error).isNotBlank())
        }
        assertEquals("RATE_LIMITED", ApiErrorMessageMapper.technicalCode(ApiError.RateLimited(1)))
        assertEquals(
            "SOMETHING_NEW",
            ApiErrorMessageMapper.technicalCode(ApiError.Unknown("SOMETHING_NEW", null))
        )
        assertEquals("UNKNOWN", ApiErrorMessageMapper.technicalCode(ApiError.Unknown(null, null)))
    }

    // ── 2. Taxonomy folding ─────────────────────────────────────────────────

    @Test
    fun `toAppError - infra failures fold onto the retryable Network bucket`() {
        listOf(
            ApiError.UpstreamUnavailable,
            ApiError.ServiceUnavailable,
            ApiError.RateLimited(null)
        ).forEach { error ->
            assertEquals(
                AppError.Network.ServerUnavailable,
                ApiErrorMessageMapper.toAppError(ApiException(apiError = error))
            )
        }
    }

    @Test
    fun `toAppError - business failures fold onto their typed business cases`() {
        assertEquals(
            AppError.Business.InvalidAddress,
            ApiErrorMessageMapper.toAppError(ApiException(apiError = ApiError.InvalidAddress))
        )
        assertEquals(
            AppError.Business.InsufficientFunds,
            ApiErrorMessageMapper.toAppError(ApiException(apiError = ApiError.InsufficientGasCredit))
        )
    }

    @Test
    fun `map - transport exceptions become the network taxonomy`() {
        assertEquals(AppError.Network.NoInternet, ErrorMapper.map(UnknownHostException()))
        assertEquals(AppError.Network.Timeout, ErrorMapper.map(SocketTimeoutException()))
        assertEquals(AppError.Network.NoInternet, ErrorMapper.map(IOException()))
        assertTrue(ErrorMapper.map(IllegalStateException("boom")) is AppError.Unexpected)
    }

    @Test
    fun `map - an AppError passes through untouched`() {
        val original = AppError.Business.General(message = "پیام آماده")
        assertEquals(original, ErrorMapper.map(original))
        assertEquals("پیام آماده", ErrorMapper.getUserMessage(original))
    }

    @Test
    fun `getUserMessage - every AppError case has copy`() {
        listOf(
            AppError.Network.NoInternet,
            AppError.Network.Timeout,
            AppError.Network.ServerUnavailable,
            AppError.Network.Unknown(RuntimeException("x")),
            AppError.Business.InsufficientFunds,
            AppError.Business.InvalidAddress,
            AppError.Business.General(message = "خطای سفارشی"),
            AppError.Unexpected(RuntimeException("x"))
        ).forEach { error ->
            assertTrue("blank message for $error", ErrorMapper.getUserMessage(error).isNotBlank())
        }
    }

    // ── 3. No raw text reaches the user ─────────────────────────────────────

    @Test
    fun `getUserMessage - a raw exception message is never shown`() {
        val raw = "java.net.ConnectException: Failed to connect to /10.0.2.2:8080"
        val message = ErrorMapper.getUserMessage(IllegalStateException(raw))

        assertEquals(ErrorMapper.GENERIC_MESSAGE, message)
        assertFalse(message.contains("ConnectException"))
        assertFalse(message.contains("10.0.2.2"))
    }

    @Test
    fun `getUserMessage - a server reasonFa is only used for the Unknown bucket`() {
        val serverText = "moved 0x1234567890abcdef1234567890abcdef12345678"

        val typed = ApiException(apiError = ApiError.BroadcastRejected, reasonFa = serverText)
        assertEquals(
            ApiErrorMessageMapper.farsiMessage(ApiError.BroadcastRejected),
            ErrorMapper.getUserMessage(typed)
        )

        // Even the Unknown fallback goes through the sanitizer, so no address survives.
        val unknown = ApiException(apiError = ApiError.Unknown("X", null), reasonFa = serverText)
        val message = ErrorMapper.getUserMessage(unknown)
        assertFalse(message.contains("0x1234567890abcdef1234567890abcdef12345678"))
        assertTrue(message.contains(ErrorTextSanitizer.REDACTED))
    }

    // ── 4. Technical detail ─────────────────────────────────────────────────

    @Test
    fun `getTechnicalDetail - carries the machine code and the http status`() {
        val detail = ErrorMapper.getTechnicalDetail(
            ApiException(apiError = ApiError.BroadcastRejected, httpStatus = 502)
        )
        assertTrue(detail.contains("BroadcastRejected"))
        assertTrue(detail.contains("502"))
    }

    @Test
    fun `getTechnicalDetail - redacts addresses and payloads from the server text`() {
        val detail = ErrorMapper.getTechnicalDetail(
            ApiException(
                apiError = ApiError.InvalidSignedTx,
                httpStatus = 400,
                reasonFa = "rejected tx 0xdeadbeefdeadbeefdeadbeef from TN3W4H6rK2ce4vX9YnFQHwKENnHjoxb3m9"
            )
        )
        assertFalse(detail.contains("0xdeadbeefdeadbeefdeadbeef"))
        assertFalse(detail.contains("TN3W4H6rK2ce4vX9YnFQHwKENnHjoxb3m9"))
        assertTrue(detail.contains(ErrorTextSanitizer.REDACTED))
    }

    @Test
    fun `getTechnicalDetail - unwraps AppError so the real cause is described`() {
        val detail = ErrorMapper.getTechnicalDetail(
            AppError.Unexpected(IllegalArgumentException("bad decimals"))
        )
        assertTrue(detail.contains("IllegalArgumentException"))
        assertTrue(detail.contains("bad decimals"))
    }

    @Test
    fun `getTechnicalDetail - never blank for a bare exception`() {
        assertTrue(ErrorMapper.getTechnicalDetail(RuntimeException()).isNotBlank())
    }

    // ── 5. Fallback + presentation ──────────────────────────────────────────

    @Test
    fun `userMessage - the call-site fallback wins only when the taxonomy is silent`() {
        // Nothing known about it → the call site's Persian description of the action.
        assertEquals(
            "خطا در حذف کیف پول",
            ErrorMapper.userMessage(RuntimeException("SQL error 1032"), "خطا در حذف کیف پول")
        )

        // Something known → curated copy wins over the fallback.
        val typed = ApiException(apiError = ApiError.InsufficientGasCredit)
        assertEquals(
            ApiErrorMessageMapper.farsiMessage(ApiError.InsufficientGasCredit),
            ErrorMapper.userMessage(typed, "خطا در ارسال")
        )
        assertNotEquals("خطا در ارسال", ErrorMapper.userMessage(typed, "خطا در ارسال"))
    }

    @Test
    fun `userMessage - a blank fallback does not produce a blank message`() {
        assertEquals(
            ErrorMapper.GENERIC_MESSAGE,
            ErrorMapper.userMessage(RuntimeException("x"), "   ")
        )
        assertEquals(ErrorMapper.GENERIC_MESSAGE, ErrorMapper.userMessage(RuntimeException("x")))
    }

    @Test
    fun `present - keeps the declared surface and splits user copy from technical detail`() {
        val presentation = ErrorMapper.present(
            throwable = ApiException(apiError = ApiError.SimulationReverted, httpStatus = 422),
            surface = ErrorSurface.BLOCKING,
            title = "ارسال ناموفق"
        )

        assertEquals(ErrorSurface.BLOCKING, presentation.surface)
        assertEquals("ارسال ناموفق", presentation.title)
        assertEquals(
            ApiErrorMessageMapper.farsiMessage(ApiError.SimulationReverted),
            presentation.shortMessage
        )
        assertTrue(presentation.technicalDetail.contains("SimulationReverted"))
        // The technical text must not be duplicated into the snackbar line.
        assertFalse(presentation.shortMessage.contains("422"))
    }

    @Test
    fun `present - defaults to a snackbar`() {
        assertEquals(
            ErrorSurface.SNACKBAR,
            ErrorMapper.present(RuntimeException("x")).surface
        )
    }

    @Test
    fun `present - every ApiError variant is renderable`() {
        allApiErrors.forEach { error ->
            val presentation = ErrorMapper.present(ApiException(apiError = error))
            assertTrue("blank short message for $error", presentation.shortMessage.isNotBlank())
            assertTrue("blank detail for $error", presentation.technicalDetail.isNotBlank())
        }
    }

    // ── 6. Code → typed error resolution ────────────────────────────────────

    @Test
    fun `ApiError from - resolves codes and falls back on http status`() {
        assertEquals(ApiError.NetworkNotFound, ApiError.from("NETWORK_NOT_FOUND", 404))
        assertEquals(ApiError.NetworkNotFound, ApiError.from("network_not_found", 404))
        assertEquals(ApiError.RequoteRequired, ApiError.from("QUOTE_EXPIRED", 409))
        assertEquals(ApiError.RequoteRequired, ApiError.from(null, 409))
        assertEquals(ApiError.SimulationReverted, ApiError.from(null, 422))
        assertEquals(ApiError.RateLimited(12), ApiError.from(null, 429, retryAfterSec = 12))
        assertEquals(ApiError.ServiceUnavailable, ApiError.from(null, 503))
        assertEquals(ApiError.InternalError, ApiError.from(null, 500))
        assertEquals(ApiError.Unknown(null, null), ApiError.from(null, 418))
    }
}
