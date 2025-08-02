package com.mtd.core.encryption


import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.core.content.edit

class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    private val key = KeyStoreManager.generateOrGetSecretKey()

    fun putEncrypted(keyName: String, value: String) {
        val encrypted = AESGCMCipher.encrypt(value, key)
        val base64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit() { putString(keyName, base64) }
    }

    fun getDecrypted(keyName: String): String? {
        val base64 = prefs.getString(keyName, null) ?: return null
        val encrypted = Base64.decode(base64, Base64.NO_WRAP)
        return AESGCMCipher.decrypt(encrypted, key)
    }
}
