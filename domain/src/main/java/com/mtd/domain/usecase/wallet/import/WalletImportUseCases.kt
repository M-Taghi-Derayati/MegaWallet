package com.mtd.domain.usecase.wallet.importwallet

import android.content.Intent
import com.mtd.domain.interfaceRepository.IAuthManager
import com.mtd.domain.interfaceRepository.IBackupRepository
import com.mtd.domain.interfaceRepository.ICloudBackupDataSource
import com.mtd.domain.interfaceRepository.ICloudWalletBackupCodec
import com.mtd.domain.interfaceRepository.ICloudWalletBalanceCalculator
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.interfaceRepository.IWalletSecretValidator
import com.mtd.domain.model.CloudWalletItem
import com.mtd.domain.model.CloudWalletMetadata
import com.mtd.domain.model.DriveBackupState
import com.mtd.domain.model.ImportData
import com.mtd.domain.model.ResultResponse
import javax.inject.Inject

class ValidateImportSecretUseCase @Inject constructor(
    private val walletSecretValidator: IWalletSecretValidator
) {
    operator fun invoke(importData: ImportData): Boolean {
        return when (importData) {
            is ImportData.Mnemonic -> walletSecretValidator.isValidMnemonic(
                importData.words.joinToString(" ")
            )
            is ImportData.PrivateKey -> walletSecretValidator.isValidPrivateKey(importData.key)
        }
    }
}

class GetCloudSignInIntentUseCase @Inject constructor(
    private val authManager: IAuthManager
) {
    operator fun invoke(): Intent = authManager.getSignInIntent()
}

class IsCloudBackupConnectedUseCase @Inject constructor(
    private val cloudBackupDataSource: ICloudBackupDataSource
) {
    operator fun invoke(): Boolean = cloudBackupDataSource.isInitialized()
}

class GetDriveBackupStateUseCase @Inject constructor(
    private val cloudBackupDataSource: ICloudBackupDataSource
) {
    suspend operator fun invoke(): DriveBackupState {
        return try {
            if (!cloudBackupDataSource.isInitialized()) {
                DriveBackupState.NotConnected
            } else if (cloudBackupDataSource.hasCloudBackup()) {
                DriveBackupState.BackupFound
            } else {
                DriveBackupState.NoBackup
            }
        } catch (_: Exception) {
            DriveBackupState.NoBackup
        }
    }
}

class ConnectCloudBackupUseCase @Inject constructor(
    private val authManager: IAuthManager,
    private val cloudBackupDataSource: ICloudBackupDataSource
) {
    suspend operator fun invoke(data: Intent?): ResultResponse<DriveBackupState> {
        return when (val result = authManager.processSignInResult(data)) {
            is ResultResponse.Success -> {
                try {
                    cloudBackupDataSource.initializeWithAuthCode(result.data)
                    ResultResponse.Success(
                        if (cloudBackupDataSource.hasCloudBackup()) {
                            DriveBackupState.BackupFound
                        } else {
                            DriveBackupState.NoBackup
                        }
                    )
                } catch (e: Exception) {
                    ResultResponse.Error(e)
                }
            }
            is ResultResponse.Error -> ResultResponse.Error(result.exception)
        }
    }
}

class RestoreCloudWalletsUseCase @Inject constructor(
    private val backupRepository: IBackupRepository,
    private val cloudWalletBackupCodec: ICloudWalletBackupCodec
) {
    suspend operator fun invoke(password: String): ResultResponse<List<CloudWalletItem>> {
        return when (val result = backupRepository.restoreData(password)) {
            is ResultResponse.Success -> {
                try {
                    val metadataList: List<CloudWalletMetadata> =
                        cloudWalletBackupCodec.decode(result.data)
                    ResultResponse.Success(
                        metadataList.map { metadata ->
                            CloudWalletItem(
                                id = metadata.id,
                                name = metadata.name,
                                key = metadata.key,
                                colorHex = metadata.colorHex,
                                isMnemonic = metadata.isMnemonic
                            )
                        }
                    )
                } catch (e: Exception) {
                    ResultResponse.Error(e)
                }
            }
            is ResultResponse.Error -> ResultResponse.Error(result.exception)
        }
    }
}

class CalculateCloudWalletBalancesUseCase @Inject constructor(
    private val cloudWalletBalanceCalculator: ICloudWalletBalanceCalculator
) {
    suspend operator fun invoke(wallets: List<CloudWalletItem>): List<CloudWalletItem> {
        return cloudWalletBalanceCalculator.calculateBalances(wallets)
    }
}

class ImportCloudWalletsUseCase @Inject constructor(
    private val walletRepository: IWalletRepository
) {
    suspend operator fun invoke(wallets: List<CloudWalletItem>): ImportCloudWalletsResult {
        var failedCount = 0
        wallets.forEach { walletItem ->
            val walletColor = parseColor(walletItem.colorHex) ?: 0xFF22C55E.toInt()
            val importResult = if (walletItem.isMnemonic) {
                walletRepository.importWalletFromMnemonic(
                    mnemonic = walletItem.key,
                    name = walletItem.name,
                    color = walletColor,
                    id = walletItem.id,
                    isManualBackedUp = false,
                    isCloudBackedUp = true
                )
            } else {
                walletRepository.importWalletFromPrivateKey(
                    privateKey = walletItem.key,
                    name = walletItem.name,
                    color = walletColor,
                    id = walletItem.id,
                    isManualBackedUp = false,
                    isCloudBackedUp = true
                )
            }

            if (importResult is ResultResponse.Error) {
                failedCount++
            }
        }
        return ImportCloudWalletsResult(failedCount = failedCount)
    }

    private fun parseColor(colorHex: String): Int? {
        val normalized = colorHex.trim().removePrefix("#")
        if (normalized.length !in setOf(6, 8)) return null
        if (!normalized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val withAlpha = if (normalized.length == 6) "FF$normalized" else normalized
        return withAlpha.toLong(16).toInt()
    }
}

data class ImportCloudWalletsResult(
    val failedCount: Int
)
