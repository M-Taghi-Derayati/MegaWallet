package com.mtd.domain.usecase.wallet

import com.mtd.domain.interfaceRepository.IBackupRepository
import com.mtd.domain.interfaceRepository.ICloudWalletBackupCodec
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.Asset
import com.mtd.domain.interfaceRepository.IUserPreferencesRepository
import com.mtd.domain.model.CloudWalletMetadata
import com.mtd.domain.model.ImportData
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.Wallet
import javax.inject.Inject

class GetAllWalletsUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(): ResultResponse<List<Wallet>> = walletRepository.get().getAllWallets()
}

class LoadExistingWalletUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(): ResultResponse<Wallet?> {
        return walletRepository.get().loadExistingWallet()
    }
}

class GetBalancesForMultipleWalletsUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(
        networkName: NetworkName,
        walletIds: List<String>
    ): ResultResponse<Map<String, List<Asset>>> {
        return walletRepository.get().getBalancesForMultipleWallets(networkName, walletIds)
    }
}

class SwitchActiveWalletUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(walletId: String): ResultResponse<Unit> {
        return walletRepository.get().switchActiveWallet(walletId)
    }
}

class DeleteWalletUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>,
    private val userPreferences: dagger.Lazy<IUserPreferencesRepository>
) {
    suspend operator fun invoke(walletId: String): ResultResponse<Unit> {
        val result = walletRepository.get().deleteWallet(walletId)
        // TASK-32 — forget this wallet's monitoring enrollment so a later re-import re-enrolls it.
        if (result is ResultResponse.Success) {
            val prefs = userPreferences.get()
            val subscribed = prefs.getMonitoringSubscribedWalletIds()
            if (walletId in subscribed) {
                prefs.setMonitoringSubscribedWalletIds(subscribed - walletId)
            }
        }
        return result
    }
}

class UpdateWalletBackupStatusUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(
        walletId: String,
        manual: Boolean? = null,
        cloud: Boolean? = null
    ): ResultResponse<Unit> {
        return walletRepository.get().updateBackupStatus(walletId, manual, cloud)
    }
}

class UpdateWalletNameUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(walletId: String, newName: String): ResultResponse<Unit> {
        return walletRepository.get().updateWalletName(walletId, newName)
    }
}

class UpdateWalletColorUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(walletId: String, newColor: Int): ResultResponse<Unit> {
        return walletRepository.get().updateWalletColor(walletId, newColor)
    }
}

class GetWalletMnemonicUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(walletId: String): String? {
        return when (val result = walletRepository.get().getMnemonic(walletId)) {
            is ResultResponse.Success -> result.data
            is ResultResponse.Error -> null
        }
    }
}

class HasCloudBackupUseCase @Inject constructor(
    private val backupRepository: IBackupRepository
) {
    suspend operator fun invoke(): Boolean {
        return try {
            backupRepository.hasCloudBackup()
        } catch (_: Exception) {
            false
        }
    }
}

class CreateOrImportWalletUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>
) {
    suspend operator fun invoke(
        name: String,
        color: Int,
        importData: ImportData?,
        restoreId: String? = null,
        isCloudRestoreFlow: Boolean = false
    ): ResultResponse<Wallet> {
        return when (importData) {
            is ImportData.Mnemonic -> walletRepository.get().importWalletFromMnemonic(
                mnemonic = importData.words.joinToString(" "),
                name = name,
                color = color,
                id = restoreId,
                isManualBackedUp = !isCloudRestoreFlow,
                isCloudBackedUp = isCloudRestoreFlow
            )
            is ImportData.PrivateKey -> walletRepository.get().importWalletFromPrivateKey(
                privateKey = importData.key,
                name = name,
                color = color,
                id = restoreId,
                isManualBackedUp = !isCloudRestoreFlow,
                isCloudBackedUp = isCloudRestoreFlow
            )
            null -> walletRepository.get().createNewWallet(
                name = name,
                color = color
            )
        }
    }
}

class BackupCloudWalletMetadataUseCase @Inject constructor(
    private val backupRepository: IBackupRepository,
    private val cloudWalletBackupCodec: ICloudWalletBackupCodec
) {
    suspend operator fun invoke(
        walletMetadata: CloudWalletMetadata,
        password: String
    ): ResultResponse<Unit> {
        val existingBackup = when (val hasBackup = hasExistingBackup()) {
            is ResultResponse.Success -> {
                if (!hasBackup.data) {
                    emptyList()
                } else {
                    when (val restoreResult = backupRepository.restoreData(password)) {
                        is ResultResponse.Success -> parseBackup(restoreResult.data)
                        is ResultResponse.Error -> {
                            return ResultResponse.Error(
                                IllegalArgumentException(
                                    "Incorrect cloud backup password",
                                    restoreResult.exception
                                )
                            )
                        }
                    }
                }
            }
            is ResultResponse.Error -> return ResultResponse.Error(hasBackup.exception)
        }

        val jsonData = cloudWalletBackupCodec.encode(
            existingBackup
                .filterNot { it.id == walletMetadata.id }
                .plus(walletMetadata)
        )
        return backupRepository.backupData(jsonData, password)
    }

    private suspend fun hasExistingBackup(): ResultResponse<Boolean> {
        return try {
            ResultResponse.Success(backupRepository.hasCloudBackup())
        } catch (e: Exception) {
            ResultResponse.Error(e)
        }
    }

    private fun parseBackup(jsonData: String): List<CloudWalletMetadata> {
        return try {
            cloudWalletBackupCodec.decode(jsonData)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class DeleteCloudBackupUseCase @Inject constructor(
    private val backupRepository: IBackupRepository
) {
    suspend operator fun invoke(): ResultResponse<Unit> {
        return backupRepository.deleteBackup()
    }
}

class BackupWalletToCloudUseCase @Inject constructor(
    private val walletRepository: dagger.Lazy<IWalletRepository>,
    private val backupCloudWalletMetadataUseCase: BackupCloudWalletMetadataUseCase
) {
    suspend operator fun invoke(walletId: String, password: String): ResultResponse<Unit> {
        val wallet = when (val walletsResult = walletRepository.get().getAllWallets()) {
            is ResultResponse.Success -> walletsResult.data.firstOrNull { it.id == walletId }
                ?: return ResultResponse.Error(IllegalStateException("Wallet not found"))

            is ResultResponse.Error -> return ResultResponse.Error(walletsResult.exception)
        }

        val secret = when (val secretResult = walletRepository.get().getMnemonic(walletId)) {
            is ResultResponse.Success -> secretResult.data
                ?: return ResultResponse.Error(IllegalStateException("Wallet secret not found"))

            is ResultResponse.Error -> return ResultResponse.Error(secretResult.exception)
        }

        val walletMetadata = CloudWalletMetadata(
            id = wallet.id,
            name = wallet.name,
            key = secret,
            colorHex = String.format("#%06X", 0xFFFFFF and wallet.color),
            isMnemonic = wallet.hasMnemonic
        )

        return backupCloudWalletMetadataUseCase(walletMetadata, password)
    }
}
