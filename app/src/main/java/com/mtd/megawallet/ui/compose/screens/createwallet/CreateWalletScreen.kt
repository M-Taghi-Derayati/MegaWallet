package com.mtd.megawallet.ui.compose.screens.createwallet

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.megawallet.event.CreateWalletStep
import com.mtd.megawallet.event.GoogleSignInEvent
import com.mtd.megawallet.event.ImportData
import com.mtd.megawallet.ui.compose.components.ErrorSnackbarHandler
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.CloudBackupPasswordScreen
import com.mtd.megawallet.viewmodel.news.CreateWalletViewModel

/**
 * Main screen for creating a new wallet.
 * Handles navigation between different steps of wallet creation.
 */
@Composable
fun CreateWalletScreen(
    onBack: (CreateWalletStep) -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: CreateWalletViewModel = hiltViewModel(),
    importData: ImportData? = null
) {
    LaunchedEffect(importData) {
        // فقط اگر importData از خارج آمده باشد (نه از restore mode)
        if (importData != null && !viewModel.isRestoreMode) {
            viewModel.setPendingImportData(importData)
        }
    }

    val step = viewModel.currentStep

    // Google Sign-In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    // Observe Google Sign-In Events
    LaunchedEffect(Unit) {
        viewModel.googleSignInEvent.collect { event ->
            when (event) {
                is GoogleSignInEvent.LaunchIntent -> googleSignInLauncher.launch(event.intent)
            }
        }
    }

    BackHandler {
        if (!viewModel.prevStep()) {
            onBack(step)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Error Snackbar Handler (در بالای همه چیز)
        ErrorSnackbarHandler(
            uiEvents = viewModel.uiEvents,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(100f)
        )

        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // دکمه بازگشت را فقط زمانی نشان می‌دهیم که در مرحله انیمیشن نهایی نباشیم
            val canGoBack = when (step) {
                CreateWalletStep.SEED_PHRASE_GENERATION -> false 
                CreateWalletStep.CLOUD_BACKUP_PASSWORD -> true
                else -> true
            }

            if (canGoBack) {
                IconButton(
                    onClick = { if (!viewModel.prevStep()) onBack(step) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (step == CreateWalletStep.NAME_INPUT) {
                            Icons.Default.Close
                        } else {
                            Icons.Default.ArrowBackIosNew
                        },
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // اگر در صفحه انیمیشن تمام شده هستیم، دکمه ضربدر یا بک نشان نمی‌دهیم تا زمانی که کاربر اکشن نهایی را انجام دهد
                Box(modifier = Modifier.size(48.dp))
            }
        }

        // Step content
        AnimatedContent(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
            targetState = step,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "StepTransition"
        ) { currentStep ->
            when (currentStep) {
                CreateWalletStep.NAME_INPUT -> NameInputPart(viewModel = viewModel)
                CreateWalletStep.COLOR_SELECTION -> ColorSelectionPart(viewModel = viewModel)
                CreateWalletStep.TERMS_ACCEPTANCE -> TermsPart(viewModel = viewModel)
                CreateWalletStep.SEED_PHRASE_GENERATION -> SeedPhrasePart(
                    viewModel = viewModel,
                    isImportMode = importData != null,
                    onNavigateToHome = onNavigateToHome
                )
                CreateWalletStep.CLOUD_BACKUP_PASSWORD -> {
                    CloudBackupPasswordScreen(
                        onBack = { viewModel.prevStep() },
                        targetColor = viewModel.selectedColor,
                        isRecoveryMode = false,
                        horizontalPadding = 0.dp,
                        onPasswordSubmit = { password -> 
                            viewModel.onCloudPasswordSubmit(password)
                        }
                    )
                }
            }
        }
    }
}


@Preview
@Composable
fun CreateWalletScreenPreview(){
    MaterialTheme {
        CreateWalletScreen({ _ -> }, {})
    }
}
