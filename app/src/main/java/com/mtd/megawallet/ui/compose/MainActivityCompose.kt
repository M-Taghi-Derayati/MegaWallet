package com.mtd.megawallet.ui.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mtd.megawallet.ui.compose.screens.main.MainScreen
import com.mtd.megawallet.ui.compose.theme.MegaWalletTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity اصلی اپلیکیشن که MainScreen را نمایش می‌دهد.
 * این صفحه بعد از ایجاد یا بازیابی کیف پول نمایش داده می‌شود.
 */
@AndroidEntryPoint
class MainActivityCompose : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MegaWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onNavigateToWalletManagement = {
                            // TODO: Navigate to wallet management screen
                        },
                        onScanClick = {
                            // TODO: Open QR scanner
                        },
                        onSearchClick = {
                            // TODO: Open search
                        },
                        onMoreOptionsClick = {
                            // TODO: Show more options menu
                        },
                        onFabClick = {
                            // TODO: Handle FAB click (e.g., show send/receive options)
                        },
                        onHistoryClick = {
                            // TODO: Navigate to history screen
                        },
                        onExploreClick = {
                            // TODO: Navigate to explore screen
                        }
                    )
                }
            }
        }
    }
}







