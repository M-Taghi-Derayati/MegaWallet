package com.mtd.data.socket

import com.mtd.domain.model.AppEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TASK-22 — verifies the thin-signal → refresh-event targeting rules of [SocketRefreshMapper]:
 * `balance.invalidated` refreshes just the affected asset, `tx.new`/`tx.status.updated` refresh
 * history scoped to the reverse-mapped network, missing ids degrade to a broader refresh, and legacy
 * events stay coarse.
 */
class SocketRefreshMapperTest {

    // Fake reverse map: only `sepolia` is a known network.
    private val resolve: (String) -> String? = { networkId ->
        if (networkId == "sepolia") "SEPOLIA" else null
    }

    private fun map(event: SocketEvent) = SocketRefreshMapper.refreshEventsFor(event, resolve)

    @Test
    fun `balance invalidated targets the affected asset`() {
        val events = map(
            SocketEvent.BalanceInvalidated(
                id = "f1", eventId = "e1", walletId = "w1",
                networkId = "sepolia", assetId = "sepolia-usdc", cursor = "c1"
            )
        )
        assertEquals(
            listOf(AppEvent.WalletAssetNeedsRefresh(assetId = "sepolia-usdc", networkId = "sepolia")),
            events
        )
    }

    @Test
    fun `balance invalidated without assetId falls back to a whole-wallet refresh`() {
        val events = map(
            SocketEvent.BalanceInvalidated(
                id = "f1", eventId = "e1", walletId = "w1",
                networkId = "sepolia", assetId = null, cursor = null
            )
        )
        assertEquals(listOf(AppEvent.WalletNeedsRefresh), events)
    }

    @Test
    fun `tx new refreshes history (scoped) and the wallet balance`() {
        val events = map(
            SocketEvent.TxNew(
                id = "f2", eventId = "e2", txHash = "0xabc",
                networkId = "sepolia", addressIdentityId = "aid", cursor = "c2"
            )
        )
        // A new tx moves the balance too, so tx.new also triggers a wallet refresh (safety net for a
        // missing/late balance.invalidated).
        assertEquals(
            listOf(
                AppEvent.TransactionHistoryNeedsRefresh(networkName = "SEPOLIA"),
                AppEvent.WalletNeedsRefresh
            ),
            events
        )
    }

    @Test
    fun `tx status updated with an unknown network yields an unscoped history refresh`() {
        val events = map(
            SocketEvent.TxStatusUpdated(
                id = "f3", eventId = "e3", txHash = "0xdef",
                networkId = "unknown_net", status = "SUCCESS", cursor = "c3"
            )
        )
        assertEquals(listOf(AppEvent.TransactionHistoryNeedsRefresh(networkName = null)), events)
    }

    @Test
    fun `legacy tx-status-changed stays coarse (wallet + history)`() {
        val events = map(
            SocketEvent.TxStatusChanged(
                id = "f4", txId = "t", txHash = "0x1", chain = "evm",
                status = "SUCCESS", token = "USDC", amount = "1"
            )
        )
        assertEquals(
            listOf(AppEvent.WalletNeedsRefresh, AppEvent.TransactionHistoryNeedsRefresh()),
            events
        )
    }

    @Test
    fun `connection ready and unknown trigger no refresh`() {
        assertEquals(emptyList<AppEvent>(), map(SocketEvent.ConnectionReady(id = "welcome", heartbeatMs = 30_000L)))
        assertEquals(emptyList<AppEvent>(), map(SocketEvent.Unknown))
    }
}
