package com.mtd.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.domain.model.IUserPreferencesRepository
import com.mtd.domain.model.RpcConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson // برای تبدیل لیست به JSON و برعکس
) : IUserPreferencesRepository {

    private companion object {
        const val KEY_RPC_LIST_PREFIX = "rpc_list_"
        const val KEY_SELECTED_NETWORK = "selected_network_id"
    }

    override suspend fun getRpcListForChain(chainId: Long): List<RpcConfig> {
        val json = prefs.getString("$KEY_RPC_LIST_PREFIX$chainId", null) ?: return emptyList()
        val type = object : TypeToken<List<RpcConfig>>() {}.type
        return gson.fromJson(json, type)
    }

    override suspend fun saveRpcListForChain(chainId: Long, rpcs: List<RpcConfig>) {
        val json = gson.toJson(rpcs)
        prefs.edit { putString("$KEY_RPC_LIST_PREFIX$chainId", json) }
    }

    override suspend fun getSelectedNetworkId(): String? {
        return prefs.getString(KEY_SELECTED_NETWORK, null)
    }

    override suspend fun setSelectedNetworkId(networkId: String) {
        prefs.edit { putString(KEY_SELECTED_NETWORK, networkId) }
    }
}