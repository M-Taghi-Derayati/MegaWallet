// In: core/src/main/java/com/mtd/core/utils/AssetLoader.kt
package com.mtd.core.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtd.core.assets.AssetConfig

fun loadAssets(context: Context, fileName: String = "assets.json"): List<AssetConfig> {
    return try {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val gson = Gson()
        val listType = object : TypeToken<List<AssetConfig>>() {}.type
        gson.fromJson(jsonString, listType)
    } catch (e: Exception) {
        // در صورت بروز خطا (مثلا فایل پیدا نشد)، لیست خالی برگردان
        emptyList()
    }
}