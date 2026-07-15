package com.mtd.data.device

import android.content.Context
import android.provider.Settings
import com.mtd.core.encryption.SecureStorage
import com.mtd.domain.interfaceRepository.IDeviceIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — resilient [IDeviceIdProvider] with a Play-Integrity-first, deterministic-fallback strategy.
 *
 * **Attempt 1:** request a Play Integrity token via [PlayIntegrityTokenProvider].
 * **Attempt 2 (fallback):** on ANY failure (timeout, GMS missing, network error, exception) derive a
 * stable local id = `SHA-256( ANDROID_ID + installationUuid + packageName )`.
 *
 * The installation UUID is generated exactly once on first use and persisted in the AndroidKeystore-
 * encrypted [SecureStorage], so the fallback id is stable across launches for a given install.
 *
 * The non-Hilt primary constructor takes [androidId]/[packageName] directly so the whole class is
 * pure-JVM unit-testable (no Robolectric); the [Inject] constructor resolves them from the [Context].
 */
@Singleton
class ResilientDeviceIdProvider(
    private val integrityTokenProvider: PlayIntegrityTokenProvider,
    private val secureStorage: SecureStorage,
    private val androidId: String,
    private val packageName: String
) : IDeviceIdProvider {

    @Inject
    constructor(
        integrityTokenProvider: PlayIntegrityTokenProvider,
        secureStorage: SecureStorage,
        @ApplicationContext context: Context
    ) : this(
        integrityTokenProvider = integrityTokenProvider,
        secureStorage = secureStorage,
        androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
        packageName = context.packageName
    )

    override suspend fun getDeviceId(): String {
        // Attempt 1 — strong attestation.
        runCatching { integrityTokenProvider.requestIntegrityToken() }
            .onFailure { Timber.w(it, "Play Integrity unavailable — falling back to local deviceId") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        // Attempt 2 — deterministic local fallback.
        return buildLocalDeviceId()
    }

    private fun buildLocalDeviceId(): String {
        val material = androidId + installationUuid() + packageName
        return sha256Hex(material)
    }

    /** Generated exactly once on first call, then read back from secure storage on every later call. */
    private fun installationUuid(): String {
        secureStorage.getDecrypted(KEY_INSTALL_UUID)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        secureStorage.putEncrypted(KEY_INSTALL_UUID, generated)
        return generated
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_INSTALL_UUID = "device_installation_uuid"
    }
}
