package com.mtd.domain.model

import com.mtd.domain.model.RpcConfig


interface IUserPreferencesRepository {

    /**
     * لیست اولویت‌بندی شده RPC ها را برای یک شبکه خاص برمی‌گرداند.
     * اگر کاربر هیچ تنظیماتی ذخیره نکرده باشد، لیست خالی برمی‌گرداند.
     */
    suspend fun getRpcListForChain(chainId: Long): List<RpcConfig>

    /**
     * لیست جدید RPC ها را برای یک شبکه خاص ذخیره می‌کند.
     */
    suspend fun saveRpcListForChain(chainId: Long, rpcs: List<RpcConfig>)

    /**
     * شناسه شبکه منتخب فعلی را برمی‌گرداند.
     */
    suspend fun getSelectedNetworkId(): String?

    /**
     * شناسه شبکه منتخب فعلی را ذخیره می‌کند.
     */
    suspend fun setSelectedNetworkId(networkId: String)
}