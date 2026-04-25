package com.mtd.data.repository.gasless

import com.mtd.domain.model.error.AppError
import com.mtd.domain.model.GaslessApiException
import com.mtd.domain.model.GaslessErrorCategory
import com.mtd.domain.model.GaslessErrorClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class GaslessErrorClassifierTest {

    @Test
    fun `classify maps 400 to VALIDATION`() {
        val info = GaslessErrorClassifier.classify(
            GaslessApiException(
                statusCode = 400,
                responseBody = "invalid payload",
                message = "bad request"
            )
        )

        assertEquals(GaslessErrorCategory.VALIDATION, info.category)
        assertEquals(400, info.httpCode)
    }

    @Test
    fun `classify maps 409 to CONFLICT`() {
        val info = GaslessErrorClassifier.classify(
            GaslessApiException(
                statusCode = 409,
                responseBody = "duplicate nonce",
                message = "conflict"
            )
        )

        assertEquals(GaslessErrorCategory.CONFLICT, info.category)
        assertEquals(409, info.httpCode)
    }

    @Test
    fun `classify maps 401 to AUTH`() {
        val info = GaslessErrorClassifier.classify(
            GaslessApiException(
                statusCode = 401,
                responseBody = "unauthorized",
                message = "auth"
            )
        )

        assertEquals(GaslessErrorCategory.AUTH, info.category)
        assertEquals(401, info.httpCode)
    }

    @Test
    fun `classify maps network app errors to INFRA`() {
        val info = GaslessErrorClassifier.classify(AppError.Network.Timeout)
        assertEquals(GaslessErrorCategory.INFRA, info.category)
    }
}
