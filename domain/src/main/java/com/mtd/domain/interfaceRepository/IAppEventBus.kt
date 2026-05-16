package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.AppEvent
import kotlinx.coroutines.flow.SharedFlow

interface IAppEventBus {
    val events: SharedFlow<AppEvent>
    suspend fun postEvent(event: AppEvent)
}
