package com.mtd.domain.interfaceRepository

import kotlinx.coroutines.flow.Flow

interface IPendingHistoryActivityObserver {
    fun observePendingHistoryActivity(): Flow<Boolean>
}
