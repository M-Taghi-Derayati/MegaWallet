package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.CloudWalletMetadata

interface ICloudWalletBackupCodec {
    fun encode(items: List<CloudWalletMetadata>): String
    fun decode(raw: String): List<CloudWalletMetadata>
}
