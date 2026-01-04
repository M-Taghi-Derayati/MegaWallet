package com.mtd.data.repository
import com.mtd.domain.model.ResultResponse

interface IBackupRepository {
    suspend fun backupData(jsonData: String, password: String): ResultResponse<Unit>
    suspend fun restoreData(password: String): ResultResponse<String>
    suspend fun hasCloudBackup(): Boolean
    suspend fun deleteBackup(): ResultResponse<Unit>
}