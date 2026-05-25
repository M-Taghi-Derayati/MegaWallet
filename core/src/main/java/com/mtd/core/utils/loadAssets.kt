// In: core/src/main/java/com/mtd/core/utils/AssetLoader.kt
package com.mtd.core.utils

import android.content.Context
import com.google.gson.Gson
import com.mtd.core.json.GsonJsonCodec
import com.mtd.domain.model.assets.AssetConfig

fun loadAssets(context: Context, fileName: String = "assets.json"): List<AssetConfig> {
    return try {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        GsonJsonCodec.decodeList<AssetConfig>(jsonString, Gson())
    } catch (e: Exception) {
        // در صورت بروز خطا (مثلا فایل پیدا نشد)، لیست خالی برگردان
        emptyList()
    }
}
