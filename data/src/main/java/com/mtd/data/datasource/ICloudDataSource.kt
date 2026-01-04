package com.mtd.data.datasource

interface ICloudDataSource {
    suspend fun initializeWithAuthCode(authCode: String)
    fun isInitialized(): Boolean
    fun signOut()
    suspend fun uploadBackup(data: ByteArray)
    suspend fun downloadBackup(): ByteArray?
    suspend fun hasCloudBackup(): Boolean
    suspend fun deleteBackup()
}