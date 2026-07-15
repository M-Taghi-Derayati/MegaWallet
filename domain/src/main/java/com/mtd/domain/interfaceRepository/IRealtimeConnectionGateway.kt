package com.mtd.domain.interfaceRepository

/**
 * Phase 2 seam — decouples the auth use cases from the concrete WebSocket transport (data layer).
 *
 * The realtime socket requires a JWT at its upgrade handshake, so it must be (re)connected only after
 * a successful sign-in and torn down on sign-out. The implementation delegates to
 * `NotificationSocketManager`; both calls are idempotent.
 */
interface IRealtimeConnectionGateway {

    /** Open (or keep) the authenticated realtime connection. Safe to call repeatedly. */
    fun connect()

    /** Close the realtime connection and stop auto-reconnect (e.g. on logout / wallet switch). */
    fun disconnect()
}
