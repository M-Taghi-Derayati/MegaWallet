package com.mtd.domain.interfaceRepository

import android.content.Intent
import com.mtd.domain.model.ResultResponse

interface IAuthManager {
    /**
     * Creates an intent for starting Google Sign-In.
     */
    fun getSignInIntent(): Intent

    /**
     * Processes the sign-in result and returns the selected Google account name.
     */
    suspend fun processSignInResult(data: Intent?): ResultResponse<String>

    fun signOut()
}
