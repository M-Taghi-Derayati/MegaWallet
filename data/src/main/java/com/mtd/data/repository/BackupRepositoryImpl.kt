package com.mtd.data.repository


import com.mtd.core.encryption.PasswordBasedCipher
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val cloudDataSource: ICloudDataSource
) : IBackupRepository {

    override suspend fun backupData(jsonData: String, password: String): ResultResponse<Unit> {
        return safeApiCall {
            val encryptedData = PasswordBasedCipher.encrypt(jsonData, password.toCharArray())
            cloudDataSource.uploadBackup(encryptedData)
        }
    }

    override suspend fun restoreData(password: String): ResultResponse<String> {
        return safeApiCall {
            val encryptedData = cloudDataSource.downloadBackup()
                ?: throw IllegalStateException("No backup found in cloud.")
            PasswordBasedCipher.decrypt(encryptedData, password.toCharArray())
        }
    }

    override suspend fun hasCloudBackup(): Boolean {
        return cloudDataSource.hasCloudBackup()
    }

    override suspend fun deleteBackup(): ResultResponse<Unit> {
        return safeApiCall {
            cloudDataSource.deleteBackup()
        }
    }
}