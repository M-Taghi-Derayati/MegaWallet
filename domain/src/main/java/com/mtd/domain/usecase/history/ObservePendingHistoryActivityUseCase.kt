package com.mtd.domain.usecase.history

import com.mtd.domain.interfaceRepository.IPendingHistoryActivityObserver
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePendingHistoryActivityUseCase @Inject constructor(
    private val pendingHistoryActivityObserver: IPendingHistoryActivityObserver
) {
    operator fun invoke(): Flow<Boolean> {
        return pendingHistoryActivityObserver.observePendingHistoryActivity()
    }
}
