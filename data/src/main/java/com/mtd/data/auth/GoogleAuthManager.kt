package com.mtd.data.auth
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.interfaceRepository.IAuthManager

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) : IAuthManager {

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
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
            val accountName = account.email
            if (accountName != null) {
                ResultResponse.Success(accountName)
            } else {
                ResultResponse.Error(Exception("Account email is null."))
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