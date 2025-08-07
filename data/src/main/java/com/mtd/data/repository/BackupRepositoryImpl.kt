package com.mtd.data.repository


import com.mtd.core.encryption.PasswordBasedCipher
import com.mtd.data.datasource.ICloudDataSource
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val cloudDataSource: ICloudDataSource
) : IBackupRepository {

    override suspend fun backupMnemonic(mnemonic: String, password: String): ResultResponse<Unit> {
        return try {
            val encryptedMnemonic = PasswordBasedCipher.encrypt(mnemonic, password.toCharArray())
            cloudDataSource.uploadBackup(encryptedMnemonic)
            ResultResponse.Success(Unit)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun restoreMnemonic(password: String): ResultResponse<String> {
        return try {
            val encryptedData = cloudDataSource.downloadBackup()
                ?: throw IllegalStateException("No backup found in cloud.")
            val mnemonic = PasswordBasedCipher.decrypt(encryptedData, password.toCharArray())
            ResultResponse.Success(mnemonic)
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    override suspend fun hasCloudBackup(): Boolean {
        return try {
            cloudDataSource.hasCloudBackup()
        } catch (e: Exception) {
            false
        }
    }
}