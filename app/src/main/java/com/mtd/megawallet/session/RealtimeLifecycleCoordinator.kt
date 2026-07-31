package com.mtd.megawallet.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import com.mtd.domain.interfaceRepository.ITokenStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class RealtimeLifecycleCoordinator @Inject constructor(
    private val gateway: IRealtimeConnectionGateway,
    private val tokenStore: ITokenStore
) : DefaultLifecycleObserver {

    @Volatile private var started = false

    /** Begin observing the process lifecycle. Must be called on the main thread; idempotent. */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** App-wide foreground: resume the socket, but only if the user actually has a session. */
    override fun onStart(owner: LifecycleOwner) {
        if (tokenStore.getTokenDevice() != null) {
            Timber.d("[Realtime] app foreground → connecting socket")
            gateway.connect()
        } else {
            // No session yet (e.g. cold start before sign-in); the auth coordinator connects once the
            // JWT is minted. Connecting here would only flip the socket's `shouldBeConnected` flag with
            // no token and pre-empt that path.
            Timber.d("[Realtime] app foreground but no session; deferring to auth flow")
        }
    }

    /** App-wide background (backgrounded / screen locked): drop the socket to save battery + data. */
    override fun onStop(owner: LifecycleOwner) {
        Timber.d("[Realtime] app background → disconnecting socket")
        gateway.disconnect()
    }
}
