package com.mtd.domain.usecase.network

import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.model.core.NetworkType
import javax.inject.Inject

class GetNetworkTypeForAddressUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(address: String): NetworkType? {
        return networkCatalog.getNetworkTypeForAddress(address)
    }
}

class GetNetworkTypeByIdUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(networkId: String): NetworkType? {
        return networkCatalog.getNetworkTypeForNetworkId(networkId)
    }
}

class ValidateAddressForNetworkUseCase @Inject constructor(
    private val networkCatalog: INetworkCatalog
) {
    operator fun invoke(address: String, networkId: String): Boolean {
        return networkCatalog.isValidAddressForNetworkId(address, networkId)
    }
}
