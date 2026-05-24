package com.mtd.data.datasource

import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.model.BlockchainConnectionMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBlockchainConnectionModeProvider @Inject constructor() : IBlockchainConnectionModeProvider {
    override fun currentMode(): BlockchainConnectionMode {
        return BlockchainConnectionMode.DIRECT
    }
}
