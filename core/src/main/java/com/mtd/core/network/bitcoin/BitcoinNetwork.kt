package com.mtd.core.network.bitcoin



import com.mtd.domain.model.core.NetworkConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import org.bitcoinj.core.NetworkParameters

class BitcoinNetwork(
    config: NetworkConfig,
    params: NetworkParameters
) : AbstractUtxoNetwork(config, params) {
    override val explorers=config.explorers
    override val networkType = NetworkType.valueOf(config.networkType)
    override val name = NetworkName.valueOf(config.name)

}