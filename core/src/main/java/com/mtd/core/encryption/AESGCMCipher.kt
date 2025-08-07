package com.mtd.core.encryption


import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AESGCMCipher {
    // سایز تگ احراز هویت GCM (128 بیت)
    private const val TAG_LENGTH_BIT = 128
    // سایز IV (بردار مقداردهی اولیه) که Keystore معمولاً استفاده می‌کند (12 بایت)
    private const val IV_SIZE_BYTES = 12
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plaintext: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)

        // مرحله ۱: به جای ساخت IV، فقط حالت رمزنگاری را مشخص می‌کنیم.
        // Keystore خودش IV را تولید و مدیریت می‌کند.
        cipher.init(Cipher.ENCRYPT_MODE, key)

        // مرحله ۲: بعد از رمزنگاری، IV استفاده شده را از خود Cipher می‌گیریم.
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // مرحله ۳: IV را به ابتدای ciphertext می‌چسبانیم تا برای رمزگشایی در دسترس باشد.
        // اندازه IV باید مشخص باشد، Keystore معمولاً از 12 بایت استفاده می‌کند.
        // assert(iv.size == IV_SIZE_BYTES) // می‌توانید برای اطمینان این خط را اضافه کنید.
        return iv + ciphertext
    }

    fun decrypt(cipherData: ByteArray, key: SecretKey): String {
        // اطمینان حاصل می‌کنیم که داده ورودی به اندازه کافی بزرگ است تا شامل IV باشد.
        if (cipherData.size <= IV_SIZE_BYTES) {
            throw IllegalArgumentException("Invalid cipher data format")
        }

        // مرحله ۱: IV را از ابتدای داده رمزنگاری شده جدا می‌کنیم.
        val iv = cipherData.sliceArray(0 until IV_SIZE_BYTES)
        val ciphertext = cipherData.sliceArray(IV_SIZE_BYTES until cipherData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)

        // مرحله ۲: GCMParameterSpec را با IV استخراج شده و سایز تگ استاندارد می‌سازیم.
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

        // مرحله ۳: Cipher را با کلید و مشخصات GCM برای رمزگشایی مقداردهی اولیه می‌کنیم.
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }
}

