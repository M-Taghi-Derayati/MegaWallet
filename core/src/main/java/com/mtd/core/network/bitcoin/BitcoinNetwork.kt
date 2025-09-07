package com.mtd.core.network.bitcoin


import com.mtd.core.model.NetworkConfig
import com.mtd.core.model.NetworkName
import com.mtd.core.model.NetworkType
import org.bitcoinj.core.NetworkParameters

class BitcoinNetwork(
    config: NetworkConfig,
    params: NetworkParameters
) : AbstractUtxoNetwork(config, params) {
    override val explorers=config.explorers
    override val networkType = NetworkType.valueOf(config.networkType)
    override val name = NetworkName.valueOf(config.name)

}