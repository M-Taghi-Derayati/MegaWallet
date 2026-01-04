package com.mtd.data
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.repository.IAuthManager

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) : IAuthManager {

    private val webClientId = "1046615759222-vl9okabqo2a4j8ji9eg496v3s1h38jn4.apps.googleusercontent.com"

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .requestServerAuthCode(webClientId)
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    override fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    override suspend fun processSignInResult(data: Intent?): ResultResponse<String> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.await() // await() for suspend function
            val authCode = account.serverAuthCode
            if (authCode != null) {
                ResultResponse.Success(authCode)
            } else {
                ResultResponse.Error(Exception("Authorization code is null."))
            }
        } catch (e: ApiException) {
            ResultResponse.Error(Exception("Google Sign-In failed with code: ${e.statusCode}", e))
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override fun signOut() {
        googleSignInClient.signOut()
    }


}