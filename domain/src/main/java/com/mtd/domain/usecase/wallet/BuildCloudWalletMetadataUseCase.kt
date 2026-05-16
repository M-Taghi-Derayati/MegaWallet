package com.mtd.domain.usecase.wallet

import com.mtd.domain.model.CloudWalletMetadata
import com.mtd.domain.model.ImportData
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.usecase.wallet.importwallet.ValidateImportSecretUseCase
import java.util.UUID
import javax.inject.Inject

class BuildCloudWalletMetadataUseCase @Inject constructor(
    private val validateImportSecretUseCase: ValidateImportSecretUseCase
) {
    operator fun invoke(
        seedWords: List<String>,
        importData: ImportData?,
        walletId: String?,
        walletName: String,
        color: Int
    ): ResultResponse<CloudWalletMetadata> {
        val resolvedName = walletName.ifEmpty { "Wallet ${System.currentTimeMillis()}" }
        val colorHex = String.format("#%06X", 0xFFFFFF and color)
        val resolvedId = walletId ?: UUID.randomUUID().toString()

        return when {
            seedWords.isNotEmpty() && importData == null -> {
                val importSecret = ImportData.Mnemonic(seedWords)
                if (!validateImportSecretUseCase(importSecret)) {
                    ResultResponse.Error(IllegalArgumentException("عبارت بازیابی نامعتبر است"))
                } else {
                    ResultResponse.Success(
                        CloudWalletMetadata(
                            id = resolvedId,
                            name = resolvedName,
                            key = seedWords.joinToString(" "),
                            colorHex = colorHex,
                            isMnemonic = true
                        )
                    )
                }
            }

            importData is ImportData.Mnemonic -> {
                if (!validateImportSecretUseCase(importData)) {
                    ResultResponse.Error(IllegalArgumentException("عبارت بازیابی نامعتبر است"))
                } else {
                    ResultResponse.Success(
                        CloudWalletMetadata(
                            id = resolvedId,
                            name = resolvedName,
                            key = importData.words.joinToString(" "),
                            colorHex = colorHex,
                            isMnemonic = true
                        )
                    )
                }
            }

            importData is ImportData.PrivateKey -> {
                if (!validateImportSecretUseCase(importData)) {
                    ResultResponse.Error(IllegalArgumentException("کلید خصوصی نامعتبر است"))
                } else {
                    ResultResponse.Success(
                        CloudWalletMetadata(
                            id = resolvedId,
                            name = resolvedName,
                            key = importData.key,
                            colorHex = colorHex,
                            isMnemonic = false
                        )
                    )
                }
            }

            else -> ResultResponse.Error(IllegalArgumentException("اطلاعات کیف پول یافت نشد"))
        }
    }
}
