package com.mtd.megawallet.session

import com.mtd.domain.interfaceRepository.IRealtimeConnectionGateway
import com.mtd.domain.interfaceRepository.ITokenStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * TASK-22 — the socket resumes on foreground only when a session exists, and always drops on background.
 */
class RealtimeLifecycleCoordinatorTest {

    private val gateway = mockk<IRealtimeConnectionGateway>(relaxed = true)
    private val owner = mockk<androidx.lifecycle.LifecycleOwner>(relaxed = true)

    private fun coordinator(token: String?): RealtimeLifecycleCoordinator {
        val tokenStore = mockk<ITokenStore> { every { getTokenDevice() } returns token }
        return RealtimeLifecycleCoordinator(gateway, tokenStore)
    }

    @Test
    fun `foreground with a session connects the socket`() {
        coordinator(token = "JWT").onStart(owner)
        verify(exactly = 1) { gateway.connect() }
    }

    @Test
    fun `foreground without a session does not connect (auth flow owns the first connect)`() {
        coordinator(token = null).onStart(owner)
        verify(exactly = 0) { gateway.connect() }
    }

    @Test
    fun `background always disconnects the socket`() {
        coordinator(token = "JWT").onStop(owner)
        verify(exactly = 1) { gateway.disconnect() }
    }
}
