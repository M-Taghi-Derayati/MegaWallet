package com.mtd.data.repository.gasless

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.mtd.core.json.GsonJsonCodec
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
        val withoutSame = current.filterNot { it.queueId == item.queueId && it.networkId == item.networkId }
        saveUnsafe(withoutSame + item)
    }

    fun remove(networkId: String, queueId: String) = synchronized(lock) {
        val current = readUnsafe()
        val filtered = current.filterNot { it.networkId == networkId && it.queueId == queueId }
        saveUnsafe(filtered)
    }

    fun clear() = synchronized(lock) {
        sharedPreferences.edit() { remove(KEY) }
    }

    private fun readUnsafe(): List<PendingGaslessTx> {
        val raw = sharedPreferences.getString(KEY, null) ?: return emptyList()
        return try {
            GsonJsonCodec.decodeList<PendingGaslessTx>(raw, gson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveUnsafe(items: List<PendingGaslessTx>) {
        val encoded = GsonJsonCodec.encode(items, gson)
        sharedPreferences.edit() { putString(KEY, encoded) }
    }

    companion object {
        // Phase 4: bumped to v2 (the persisted shape dropped `chain` and is now keyed by
        // networkId). Old v1 entries are ignored (in-flight trackers expire naturally).
        private const val KEY = "pending_gasless_txs_v2"
    }
}

