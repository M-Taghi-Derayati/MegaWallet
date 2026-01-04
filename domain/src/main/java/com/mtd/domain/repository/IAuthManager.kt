package com.mtd.domain.repository

import android.content.Intent
import com.mtd.domain.model.ResultResponse

interface IAuthManager {
    /**
     * یک Intent برای شروع فرآیند ورود می‌سازد.
     */
    fun getSignInIntent(): Intent

    /**
     * نتیجه Intent را پردازش کرده و در صورت موفقیت، AuthCode را برمی‌گرداند.
     */
    suspend fun processSignInResult(data: Intent?): ResultResponse<String> // String -> AuthCode

    fun signOut()
}