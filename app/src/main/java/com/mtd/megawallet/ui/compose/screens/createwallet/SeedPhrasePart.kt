package com.mtd.megawallet.ui.compose.screens.createwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mtd.megawallet.ui.compose.animations.GeneratingAnimation
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel

/**
 * Component for seed phrase generation step in create wallet flow.
 * Shows the generating animation and handles wallet creation.
 */
@Composable
fun SeedPhrasePart(
    viewModel: CreateWalletViewModel,
    isImportMode: Boolean = false,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isGenerating by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        if (isGenerating) {
            GeneratingAnimation(
                targetColor = viewModel.selectedColor,
                walletName = viewModel.walletName,
                seedWords = viewModel.seedWords,
                walletAddressEVM = viewModel.walletAddressEVM,
                walletAddressBTC = viewModel.walletAddressBTC,
                isImportMode = isImportMode,
                viewModel = viewModel,
                isFlipped = viewModel.isFlipped,
                onFlippedChange = { viewModel.toggleFlipped(it) },
                backupState = viewModel.backupAnimationState,
                totalBalance = viewModel.totalBalanceUSDT,
                onCloudBackupClick = { viewModel.onCloudBackupClick() },
                onManualBackupClick = { viewModel.onManualBackupClick() },
                onNavigateToHome = onNavigateToHome,
                isRestoreMode = viewModel.isRestoreMode
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "کلمات بازیابی شما آماده است",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

