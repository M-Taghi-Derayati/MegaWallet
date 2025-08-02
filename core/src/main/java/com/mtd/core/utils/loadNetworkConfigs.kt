package com.mtd.core.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.core.model.NetworkConfig

fun loadNetworkConfigs(context: Context, fileName: String = "networks.json"): List<NetworkConfig> {
    val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
    val gson = Gson()
    val listType = object : TypeToken<List<NetworkConfig>>() {}.type
    return gson.fromJson(jsonString, listType)
}