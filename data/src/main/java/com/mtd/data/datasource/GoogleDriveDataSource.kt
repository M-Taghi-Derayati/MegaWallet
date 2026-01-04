package com.mtd.data.datasource


import android.content.Context
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : ICloudDataSource {

    private var drive: Drive? = null
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val androidClientId =
        "1046615759222-r16ths1csmqc0jtf3hi1421ghauoa8ff.apps.googleusercontent.com"
    private val webClientId =
        "1046615759222-vl9okabqo2a4j8ji9eg496v3s1h38jn4.apps.googleusercontent.com"

    private val clientSecret = "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"

    private companion object {
        private const val BACKUP_FILE_NAME = "megawallet_backup.dat"
        private const val APP_DATA_FOLDER = "appDataFolder"
    }

    override suspend fun initializeWithAuthCode(authCode: String): Unit = withContext(Dispatchers.IO) {


        val tokenResponse = GoogleAuthorizationCodeTokenRequest(
            transport, jsonFactory, webClientId, clientSecret, authCode, "urn:ietf:wg:oauth:2.0:oob"
        ).execute()
        val credential = GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(transport)
            .setClientSecrets(androidClientId, clientSecret)
            .build()
            .setFromTokenResponse(tokenResponse)
        drive = Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName("MegaWallet")
            .build()
    }

    override fun isInitialized(): Boolean = drive != null

    override fun signOut() { drive = null }

    private suspend fun findBackupFileId(): String? = withContext(Dispatchers.IO) {
        val driveService = drive ?: throw IllegalStateException("Drive service not initialized.")
        val files = driveService.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setFields("files(id, name)")
            .execute()
        return@withContext files.files?.find { it.name == BACKUP_FILE_NAME }?.id
    }

    override suspend fun uploadBackup(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val driveService = drive ?: throw IllegalStateException("Drive service not initialized.")
        val fileId = findBackupFileId()
        val fileMetadata = File().apply { name = BACKUP_FILE_NAME }
        val mediaContent = ByteArrayContent("application/octet-stream", data)
        if (fileId == null) {
            fileMetadata.parents = listOf(APP_DATA_FOLDER)
            driveService.files().create(fileMetadata, mediaContent).execute()
        } else {
            driveService.files().update(fileId, fileMetadata, mediaContent).execute()
        }
    }

    override suspend fun downloadBackup(): ByteArray? = withContext(Dispatchers.IO) {
        val driveService = drive ?: throw IllegalStateException("Drive service not initialized.")
        val fileId = findBackupFileId() ?: return@withContext null
        val outputStream = ByteArrayOutputStream()
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        return@withContext outputStream.toByteArray()
    }

    override suspend fun hasCloudBackup(): Boolean = findBackupFileId() != null

    override suspend fun deleteBackup(): Unit = withContext(Dispatchers.IO) {
        val driveService = drive ?: throw IllegalStateException("Drive service not initialized.")
        val fileId = findBackupFileId() ?: return@withContext
        driveService.files().delete(fileId).execute()
    }
}

