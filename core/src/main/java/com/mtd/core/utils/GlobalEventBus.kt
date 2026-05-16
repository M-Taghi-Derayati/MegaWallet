package com.mtd.core.utils

import com.mtd.domain.interfaceRepository.IAppEventBus
import com.mtd.domain.model.AppEvent
import com.mtd.domain.model.PendingTransactionHint as DomainPendingTransactionHint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

typealias GlobalEvent = AppEvent
typealias PendingTransactionHint = DomainPendingTransactionHint

@Singleton
class GlobalEventBus @Inject constructor() : IAppEventBus {

    private val _events = MutableSharedFlow<AppEvent>(replay = 1)
    override val events = _events.asSharedFlow()

    override suspend fun postEvent(event: AppEvent) {
        _events.emit(event)
    }
}
