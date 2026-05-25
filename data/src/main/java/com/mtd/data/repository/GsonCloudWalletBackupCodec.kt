package com.mtd.data.repository

import com.google.gson.Gson
import com.mtd.core.json.GsonJsonCodec
import com.mtd.domain.interfaceRepository.ICloudWalletBackupCodec
import com.mtd.domain.model.CloudWalletMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GsonCloudWalletBackupCodec @Inject constructor(
    private val gson: Gson
) : ICloudWalletBackupCodec {

    override fun encode(items: List<CloudWalletMetadata>): String {
        return GsonJsonCodec.encode(items, gson)
    }

    override fun decode(raw: String): List<CloudWalletMetadata> {
        return GsonJsonCodec.decodeList<CloudWalletMetadata>(raw, gson)
    }
}
