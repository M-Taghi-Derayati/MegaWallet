package com.mtd.data.network

import com.mtd.data.network.interceptor.AuthInterceptor
import com.mtd.domain.interfaceRepository.ITokenStore
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {

    private fun store(token: String?, valid: Boolean) = mockk<ITokenStore> {
        every { getTokenDevice() } returns token
        every { isTokenValid(any()) } returns valid
    }

    private fun chain(url: String, authHeader: String? = null) = RecordingChain(
        Request.Builder().url(url).apply { authHeader?.let { header("Authorization", it) } }.build()
    )

    @Test
    fun `adds bearer when a valid token exists and host matches the relayer`() {
        val c = chain("http://relayer.test/api/evm/quote")
        AuthInterceptor(store("JWT123", valid = true), "relayer.test") { 100L }.intercept(c)
        assertEquals("Bearer JWT123", c.proceeded.header("Authorization"))
    }

    @Test
    fun `no header when there is no token`() {
        val c = chain("http://relayer.test/api/evm/quote")
        AuthInterceptor(store(null, valid = false), "relayer.test").intercept(c)
        assertNull(c.proceeded.header("Authorization"))
    }

    @Test
    fun `no header when the token is expired`() {
        val c = chain("http://relayer.test/x")
        AuthInterceptor(store("JWT", valid = false), "relayer.test").intercept(c)
        assertNull(c.proceeded.header("Authorization"))
    }

    @Test
    fun `SECURITY - never attaches the JWT to a third-party host`() {
        val c = chain("https://data-api.coindesk.com/v1/prices")
        AuthInterceptor(store("JWT", valid = true), "relayer.test").intercept(c)
        assertNull(c.proceeded.header("Authorization"))
    }

    @Test
    fun `does not clobber an explicitly supplied Authorization header`() {
        val c = chain("http://relayer.test/x", authHeader = "Bearer EXPLICIT")
        AuthInterceptor(store("JWT", valid = true), "relayer.test").intercept(c)
        assertEquals("Bearer EXPLICIT", c.proceeded.header("Authorization"))
    }
}
