package com.mtd.data.device

import com.mtd.core.encryption.SecureStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ResilientDeviceIdProviderTest {

    private val androidId = "android-id-abc123"
    private val packageName = "com.mtd.megawallet"

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `Play Integrity failure falls back to deterministic SHA-256 device id`() = runTest {
        // Attempt 1 fails (simulating GMS missing / network restriction).
        val integrity = mockk<PlayIntegrityTokenProvider> {
            coEvery { requestIntegrityToken() } throws PlayIntegrityUnavailableException()
        }
        // A previously-persisted installation UUID makes the fallback deterministic.
        val installUuid = "11111111-2222-3333-4444-555555555555"
        val secureStorage = mockk<SecureStorage>(relaxed = true) {
            every { getDecrypted("device_installation_uuid") } returns installUuid
        }

        val provider = ResilientDeviceIdProvider(integrity, secureStorage, androidId, packageName)
        val deviceId = provider.getDeviceId()

        val expected = sha256Hex(androidId + installUuid + packageName)
        assertEquals(expected, deviceId)
        assertEquals(64, deviceId.length) // hex SHA-256
    }

    @Test
    fun `installation UUID is generated and persisted exactly once on first launch`() = runTest {
        val integrity = mockk<PlayIntegrityTokenProvider> {
            coEvery { requestIntegrityToken() } throws PlayIntegrityUnavailableException()
        }
        val stored = slot<String>()
        val secureStorage = mockk<SecureStorage>(relaxed = true) {
            every { getDecrypted("device_installation_uuid") } returns null // nothing yet → first launch
            every { putEncrypted("device_installation_uuid", capture(stored)) } returns Unit
        }

        val provider = ResilientDeviceIdProvider(integrity, secureStorage, androidId, packageName)
        val deviceId = provider.getDeviceId()

        // A UUID was generated and written back securely...
        verify(exactly = 1) { secureStorage.putEncrypted("device_installation_uuid", any()) }
        assertTrue(stored.isCaptured)
        // ...and the device id is the SHA-256 over that exact generated UUID.
        assertEquals(sha256Hex(androidId + stored.captured + packageName), deviceId)
    }

    @Test
    fun `successful Play Integrity token is used verbatim without touching the fallback`() = runTest {
        val integrity = mockk<PlayIntegrityTokenProvider> {
            coEvery { requestIntegrityToken() } returns "PI_TOKEN_XYZ"
        }
        val secureStorage = mockk<SecureStorage>(relaxed = true)

        val provider = ResilientDeviceIdProvider(integrity, secureStorage, androidId, packageName)

        assertEquals("PI_TOKEN_XYZ", provider.getDeviceId())
        verify(exactly = 0) { secureStorage.getDecrypted(any()) }
    }
}
