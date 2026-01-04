package com.mtd.core.utils// در یک پکیج جدید مثل com.mtd.megawallet.core یا com.mtd.megawallet.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// این کلاس به صورت Singleton در کل اپ در دسترس خواهد بود
@Singleton
class GlobalEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<GlobalEvent>(replay = 1)
    val events = _events.asSharedFlow()

    suspend fun postEvent(event: GlobalEvent) {
        _events.emit(event)
    }
}

// یک sealed class برای تعریف انواع رویدادهای ممکن
sealed class GlobalEvent {
    // رویداد برای زمانی که کیف پول نیاز به رفرش دارد
    data object WalletNeedsRefresh : GlobalEvent()
    
    // در آینده می‌تونی رویدادهای دیگری هم اینجا اضافه کنی
    // data class SomeOtherEvent(val data: String) : GlobalEvent()
}