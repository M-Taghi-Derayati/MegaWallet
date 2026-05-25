package com.mtd.core.json

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GsonJsonCodec {
    inline fun <reified T> decode(json: String, gson: Gson): T? {
        return runCatching {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson<T>(json, type)
        }.getOrNull()
    }

    inline fun <reified T> decodeList(json: String, gson: Gson): List<T> {
        return decode<List<T>>(json, gson).orEmpty()
    }

    fun encode(value: Any, gson: Gson): String {
        return gson.toJson(value)
    }
}
