package com.mtd.megawallet.event

sealed class GoogleSignInEvent {
        data class LaunchIntent(val intent: android.content.Intent) : GoogleSignInEvent()
    }