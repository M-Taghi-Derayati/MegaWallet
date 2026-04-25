package com.mtd.data.repository.gasless

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.PendingGaslessTx
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingGaslessTxStore @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {

    private val lock = Any()

    fun getAll(): List<PendingGaslessTx> = synchronized(lock) {
        readUnsafe()
    }

    fun put(item: PendingGaslessTx) = synchronized(lock) {
        val current = readUnsafe().toMutableList()
        val withoutSame = current.filterNot { it.queueId == item.queueId && it.chain == item.chain }
        saveUnsafe(withoutSame + item)
    }

    fun remove(chain: GaslessChain, queueId: String) = synchronized(lock) {
        val current = readUnsafe()
        val filtered = current.filterNot { it.chain == chain && it.queueId == queueId }
        saveUnsafe(filtered)
    }

    fun clear() = synchronized(lock) {
        sharedPreferences.edit() { remove(KEY) }
    }

    private fun readUnsafe(): List<PendingGaslessTx> {
        val raw = sharedPreferences.getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PendingGaslessTx>>() {}.type
            gson.fromJson<List<PendingGaslessTx>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveUnsafe(items: List<PendingGaslessTx>) {
        val encoded = gson.toJson(items)
        sharedPreferences.edit() { putString(KEY, encoded) }
    }

    companion object {
        private const val KEY = "pending_gasless_txs_v1"
    }
}


