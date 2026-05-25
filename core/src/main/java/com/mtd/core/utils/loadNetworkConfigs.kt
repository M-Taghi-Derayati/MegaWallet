package com.mtd.core.utils

import android.content.Context
import com.google.gson.Gson
import com.mtd.core.json.GsonJsonCodec
import com.mtd.domain.model.core.NetworkConfig

fun loadNetworkConfigs(context: Context, fileName: String = "networks.json"): List<NetworkConfig> {
    val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
    return GsonJsonCodec.decodeList<NetworkConfig>(jsonString, Gson())
}

