package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.BlockchainConnectionMode

interface IBlockchainConnectionModeProvider {
    fun currentMode(): BlockchainConnectionMode
}
