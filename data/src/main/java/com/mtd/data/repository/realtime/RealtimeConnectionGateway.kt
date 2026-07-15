package com.mtd.data.repository.realtime

import com.mtd.data.socket.NotificationSocketManager
import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — [IRealtimeConnectionGateway] adapter over [NotificationSocketManager].
 *
 * Lets the domain auth use cases (re)connect / tear down the JWT-authenticated `/ws` socket without
 * depending on the concrete data-layer transport. Both calls are idempotent; [NotificationSocketManager]
 * defers the actual upgrade until a session token exists in [com.mtd.domain.interfaceRepository.ITokenStore].
 */
@Singleton
class RealtimeConnectionGateway @Inject constructor(
    private val socketManager: NotificationSocketManager
) : IRealtimeConnectionGateway {

    override fun connect() = socketManager.connect()

    override fun disconnect() = socketManager.disconnect()
}
