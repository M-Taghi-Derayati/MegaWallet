package com.mtd.core.utils

import com.mtd.domain.interfaceRepository.IPendingHistoryActivityObserver
import com.mtd.domain.model.AppEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class PendingHistoryActivityObserver @Inject constructor(
    private val globalEventBus: GlobalEventBus
) : IPendingHistoryActivityObserver {

    override fun observePendingHistoryActivity(): Flow<Boolean> {
        return globalEventBus.events.mapNotNull { event ->
            (event as? AppEvent.TransactionHistoryNeedsRefresh)
                ?.let { it.pendingTransaction != null }
        }
    }
}
