package com.mtd.data.repository.auth

import com.google.gson.Gson
import com.mtd.data.dto.AuthVerifyRequestDto
import com.mtd.data.service.AuthApiService
import com.mtd.domain.interfaceRepository.IAuthMessageSigner
import com.mtd.domain.interfaceRepository.IDeviceIdProvider
import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.AuthAccount
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.usecase.auth.EnsureAuthenticatedUseCase
import com.mtd.domain.usecase.auth.SignInWithWalletUseCase
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Phase 2 E2E — proves the previously-dormant Web3 auth chain now runs end-to-end:
 * `challenge → personal_sign → verify → JWT storage → WS connect`, and that baseline login yields
 * `deviceVerified = false` (device-bound features stay gated — see BACKEND_GAP_DEVICE_KEY_PROVISIONING).
 */
class SignInWithWalletUseCaseTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApiService
    private val gson = Gson()

    /** In-memory [ITokenStore] so we can assert the JWT is actually persisted. */
    private class FakeTokenStore : ITokenStore {
       private var _token: String? = null
        private  var _expiresAt: Long? = null
        private var _deviceId: String? = null
        private var _cleared = false
        override fun getTokenDevice(): String? = _token
        override fun getDeviceId(): String? = _deviceId
        override fun isTokenValid(nowEpochSec: Long): Boolean =
            _token != null && (_expiresAt ?: 0L) > nowEpochSec
        override fun getExpiresAtEpochSec(): Long? = _expiresAt
        override fun save(token: String, expiresAtEpochSec: Long, deviceId: String?) {
            this._token = token; this._expiresAt = expiresAtEpochSec; this._deviceId = deviceId
        }
        override fun clear() { _token = null; _expiresAt = null; _deviceId = null; _cleared = true }
    }

    /** Deterministic signer — no real key material needed for the orchestration contract. */
    private class FakeSigner(
        private val account: AuthAccount? = AuthAccount("0xWALLET", "EVM"),
        private val signature: String? = "0xSIGNATURE"
    ) : IAuthMessageSigner {
        var signedMessage: String? = null
        override fun activeEvmAccount(): AuthAccount? = account

        override suspend fun signEvmMessage(message: String): String? {
            signedMessage = message
            return signature
        }
    }

    private val deviceIdProvider = object : IDeviceIdProvider {
        override suspend fun getDeviceId(): String = "f".repeat(64)
    }

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApiService::class.java)
    }

    @After fun tearDown() = server.shutdown()

    private fun authRepo(tokenStore: ITokenStore) = AuthRepositoryImpl(
        authApiService = api,
        tokenStore = tokenStore,
        deviceIdProvider = deviceIdProvider,
        clockEpochSec = { 1_000L }
    )

    @Test
    fun `full chain - challenge sign verify stores JWT and connects the socket`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"address":"0xWALLET","chain":"EVM","nonce":"NONCE123","message":"SIGN THIS NONCE123"}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"token":"JWT_ABC","expiresInSec":3600,"scope":["proxy:read","proxy:write"],"deviceId":"dev-1","deviceVerified":false}"""
            )
        )

        val tokenStore = FakeTokenStore()
        val signer = FakeSigner()
        val gateway = mockk<IRealtimeConnectionGateway>(relaxed = true)

        val signIn = SignInWithWalletUseCase(authRepo(tokenStore), signer)
        // EnsureAuthenticated wraps sign-in AND connects the realtime socket on success.
        val ensure = EnsureAuthenticatedUseCase(
            tokenStore = tokenStore,
            signInWithWallet = signIn,
            realtimeGateway = gateway,
            clockEpochSec = { 1_000L }
        )

        val result = ensure(forceFresh = false)

        // → session minted
        assertTrue(result is ResultResponse.Success)
        val session = (result as ResultResponse.Success).data
        assertNotNull(session)
        assertEquals("JWT_ABC", session!!.token)

        // → baseline login is NOT device-attested
        assertFalse("baseline login must not be device-verified", session.deviceVerified)

        // → the wallet actually signed the challenge message
        assertEquals("SIGN THIS NONCE123", signer.signedMessage)

        // → JWT persisted with absolute expiry (clock 1000 + 3600)
        assertEquals("JWT_ABC", tokenStore.getTokenDevice())
        assertEquals(4_600L, tokenStore.getExpiresAtEpochSec())

        // → WS connect requested after a successful sign-in
        verify(exactly = 1) { gateway.connect() }

        // → exact request order: challenge then verify
        assertEquals("/api/auth/challenge", server.takeRequest().path)
        assertEquals("/api/auth/verify", server.takeRequest().path)
    }

    @Test
    fun `verify request is baseline - carries signature plus deviceId but no attestation fields`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"address":"0xWALLET","chain":"EVM","nonce":"N","message":"MSG"}"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """{"token":"JWT_ABC","expiresInSec":3600,"scope":["proxy:read"],"deviceId":null}"""
            )
        )

        val tokenStore = FakeTokenStore()
        val signIn = SignInWithWalletUseCase(authRepo(tokenStore), FakeSigner())

        val result = signIn()
        assertTrue(result is ResultResponse.Success)
        // deviceVerified defaults to false when the backend omits it.
        assertFalse((result as ResultResponse.Success).data.deviceVerified)

        server.takeRequest() // challenge
        val verifyBody = gson.fromJson(server.takeRequest().body.readUtf8(), AuthVerifyRequestDto::class.java)
        assertEquals("0xWALLET", verifyBody.address)
        assertEquals("EVM", verifyBody.chain)
        assertEquals("0xSIGNATURE", verifyBody.signature)
        assertEquals("f".repeat(64), verifyBody.deviceId) // resilient fallback deviceId
        assertNull("baseline must not send a device-challenge nonce", verifyBody.nonce)
        assertNull("baseline must not send an attestation signature", verifyBody.attestationSignature)
    }

    @Test
    fun `no unlocked EVM account yields an error and no network call`() = runTest {
        val tokenStore = FakeTokenStore()
        val signIn = SignInWithWalletUseCase(authRepo(tokenStore), FakeSigner(account = null))

        val result = signIn()

        assertTrue(result is ResultResponse.Error)
        assertNull(tokenStore.getTokenDevice())
        assertEquals(0, server.requestCount)
    }
}
