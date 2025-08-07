package com.mtd.domain.model

data class RpcConfig(
    val url: String,
    val isUserAdded: Boolean,
    val priority: Int // برای مرتب‌سازی
)