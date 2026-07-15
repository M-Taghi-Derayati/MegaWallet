package com.mtd.data.repository.auth

import com.google.gson.Gson
import com.mtd.core.crypto.HmacUtils
import com.mtd.data.dto.AuthVerifyRequestDto
import com.mtd.data.dto.DeviceChallengeRequest
import com.mtd.data.service.AuthApiService
import com.mtd.domain.interfaceRepository.IDeviceIdProvider
import com.mtd.domain.interfaceRepository.ITokenStore
import com.mtd.domain.model.ResultResponse
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Phase 2 — verifies the `/api/auth/verify` request actually carries the resolved deviceId in the
 * EXISTING [AuthVerifyRequestDto] shape (`{address, chain, signature, deviceId}`), and that when the
 * caller supplies no deviceId the resilient [IDeviceIdProvider] fallback fills it in.
 */
class AuthVerifyDeviceIdIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApiService
    private val gson = Gson()

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

    @Test
    fun `verify payload contains the fallback deviceId and maps to the existing AuthVerifyRequestDto`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"token":"JWT_ABC","expiresInSec":3600,"scope":["proxy:read","proxy:write"],"deviceId":null}"""
            )
        )

        // Fallback deviceId (simulating Play Integrity failure → deterministic local hash).
        val fallbackDeviceId = "a".repeat(64)
        val deviceIdProvider = object : IDeviceIdProvider {
            override suspend fun getDeviceId(): String = fallbackDeviceId
        }
        val savedDeviceId = slot<String>()
        val tokenStore = mockk<ITokenStore>(relaxed = true)

        val repo = AuthRepositoryImpl(
            authApiService = api,
            tokenStore = tokenStore,
            deviceIdProvider = deviceIdProvider,
            clockEpochSec = { 1_000L }
        )

        // Caller passes no deviceId → repository must resolve it via the provider.
        val result = repo.verify(
            address = "0xWALLET",
            chain = "evm",
            signature = "0xSIG",
            deviceId = null
        )

        assertTrue(result is ResultResponse.Success)

        // The recorded request body decodes into the EXISTING DTO (no extra fields).
        val recorded = server.takeRequest()
        assertEquals("/api/auth/verify", recorded.path)
        val sent = gson.fromJson(recorded.body.readUtf8(), AuthVerifyRequestDto::class.java)
        assertEquals("0xWALLET", sent.address)
        assertEquals("evm", sent.chain)
        assertEquals("0xSIG", sent.signature)
        assertEquals(fallbackDeviceId, sent.deviceId)

        // And the resolved deviceId is persisted alongside the minted JWT.
        verify { tokenStore.save(token = "JWT_ABC", expiresAtEpochSec = 1_000L + 3600L, deviceId = capture(savedDeviceId)) }
        assertEquals(fallbackDeviceId, savedDeviceId.captured)
    }

    @Test
    fun `caller-supplied deviceId wins over the provider`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"token":"JWT_ABC","expiresInSec":3600,"scope":["proxy:read"],"deviceId":null}"""
            )
        )

        var providerCalled = false
        val deviceIdProvider = object : IDeviceIdProvider {
            override suspend fun getDeviceId(): String {
                providerCalled = true
                return "SHOULD_NOT_BE_USED"
            }
        }

        val repo = AuthRepositoryImpl(
            authApiService = api,
            tokenStore = mockk(relaxed = true),
            deviceIdProvider = deviceIdProvider,
            clockEpochSec = { 1_000L }
        )

        repo.verify(address = "0xW", chain = "evm", signature = "0xS", deviceId = "EXPLICIT_PI_TOKEN")

        val sent = gson.fromJson(server.takeRequest().body.readUtf8(), AuthVerifyRequestDto::class.java)
        assertEquals("EXPLICIT_PI_TOKEN", sent.deviceId)
        assertFalse("provider must not be consulted when the caller supplies a deviceId", providerCalled)
    }

    @Test
    fun `TASK-04a - attested verify fetches a device-challenge and sends nonce plus attestationSignature`() = runTest {
        val secret = "6a15371d3f05309c9e8a52b893f23f3cb58027d1f2db70d58a0b57ad1c204533"
        val deviceId = "device-xyz"
        val nonce = "a1b2c3"

        // Order matters: /device-challenge is called first, then /verify.
        server.enqueue(MockResponse().setBody("""{"nonce":"$nonce","attestationMessage":"sign me"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"token":"JWT","expiresInSec":3600,"scope":["proxy:write"],"deviceId":null,"deviceVerified":true}"""
            )
        )

        val repo = AuthRepositoryImpl(
            authApiService = api,
            tokenStore = mockk(relaxed = true),
            deviceIdProvider = object : IDeviceIdProvider {
                override suspend fun getDeviceId(): String = deviceId
            },
            deviceAttestSecret = secret,
            clockEpochSec = { 1_000L }
        )

        val result = repo.verify(address = "0xW", chain = "evm", signature = "0xSIG", deviceId = deviceId)
        assertTrue(result is ResultResponse.Success)
        assertTrue((result as ResultResponse.Success).data.deviceVerified)

        // 1) device-challenge carries the deviceId.
        val challengeReq = server.takeRequest()
        assertEquals("/api/auth/device-challenge", challengeReq.path)
        assertEquals(
            deviceId,
            gson.fromJson(challengeReq.body.readUtf8(), DeviceChallengeRequest::class.java).deviceId
        )

        // 2) verify carries the nonce + the exact two-level HMAC attestation signature.
        val verifyReq = server.takeRequest()
        assertEquals("/api/auth/verify", verifyReq.path)
        val verifyBody = gson.fromJson(verifyReq.body.readUtf8(), AuthVerifyRequestDto::class.java)
        assertEquals(nonce, verifyBody.nonce)
        assertEquals(deviceId, verifyBody.deviceId)
        assertEquals(
            HmacUtils.generateDeviceAttestation(secret, deviceId, nonce),
            verifyBody.attestationSignature
        )
    }
}
