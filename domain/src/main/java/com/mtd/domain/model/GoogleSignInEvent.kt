package com.mtd.domain.model

import android.content.Intent

sealed class GoogleSignInEvent {
        data class LaunchIntent(val intent: Intent) : GoogleSignInEvent()
    }