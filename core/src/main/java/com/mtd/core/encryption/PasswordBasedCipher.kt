package com.mtd.core.encryption


import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PasswordBasedCipher {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val TAG_LENGTH_BIT = 128
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH_BIT = 256
    private const val VERSION: Byte = 1

    fun encrypt(plaintext: String, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_SIZE_BYTES).apply { SecureRandom().nextBytes(this) }

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // ترکیب نسخه + salt + iv + ciphertext
        val buffer = ByteBuffer.allocate(1 + salt.size + iv.size + ciphertext.size)
        buffer.put(VERSION).put(salt).put(iv).put(ciphertext)

        return buffer.array()
    }

    fun decrypt(encryptedData: ByteArray, password: CharArray): String {
        val buffer = ByteBuffer.wrap(encryptedData)
        val version = buffer.get()
        if (version != VERSION) throw IllegalArgumentException("Unsupported cipher version: $version")

        val salt = ByteArray(SALT_SIZE_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_SIZE_BYTES).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return try {
            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext, Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("رمز عبور نادرست یا داده خراب شده است.")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val key = factory.generateSecret(spec).encoded
        // پاک‌سازی حافظه رمز عبور
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }
}
