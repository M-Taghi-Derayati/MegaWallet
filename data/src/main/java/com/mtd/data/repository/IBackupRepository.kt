package com.mtd.data.repository
import com.mtd.domain.model.ResultResponse

interface IBackupRepository {
    suspend fun backupMnemonic(mnemonic: String, password: String): ResultResponse<Unit>
    suspend fun restoreMnemonic(password: String): ResultResponse<String>
    suspend fun hasCloudBackup(): Boolean
}