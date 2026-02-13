package com.mtd.megawallet.ui.compose.screens.wallet

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.mtd.common_ui.R
import com.mtd.megawallet.ui.compose.animations.constants.MainScreenConstants
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.CloudBackupPasswordScreen
import com.mtd.megawallet.ui.compose.screens.addexistingwallet.CloudPasswordMode
import com.mtd.megawallet.ui.compose.screens.wallet.components.SecretRecoveryPromptBottomSheet
import com.mtd.megawallet.ui.compose.screens.wallet.components.SecretRevealOverlay
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletCard
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletManagementMenuContent
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletPersonalizationContent
import com.mtd.megawallet.ui.compose.screens.wallet.components.WalletRecoveryMethodsContent
import com.mtd.megawallet.ui.compose.theme.Green
import com.mtd.megawallet.viewmodel.news.MultiWalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val PremiumSpringInt = spring<IntOffset>(
    dampingRatio = 0.82f,
    stiffness = 380f
)

private enum class BackupFlowStep {
    None,
    Revealing,
    VerifyingManual,
    CloudPassword,
    Success
}



@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MultiWalletScreen(
    onNavigateBack: () -> Unit,
    onAddNewWallet: () -> Unit,
    onImportExisting: () -> Unit,
    viewModel: MultiWalletViewModel = hiltViewModel()
) {
    val wallets by viewModel.wallets.collectAsState()
    val activeWalletId by viewModel.activeWalletId.collectAsState()

    var showAddWalletSheet by remember { mutableStateOf(false) }
    var showSecretPromptSheet by remember { mutableStateOf(false) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectedWalletId by remember { mutableStateOf<String?>(null) }
    val isAnyCardExpanded = selectedWalletId != null

    // Personalization states
    var isPersonalizing by remember { mutableStateOf(false) }
    var isShowingRecovery by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editColor by remember { mutableStateOf(Color.Unspecified) }
    var isEditingNickname by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()
    var backupFlowStep by remember { mutableStateOf(BackupFlowStep.None) }
    var revealMethod by remember { mutableStateOf("") } // "cloud" or "manual"
    var secretData by remember { mutableStateOf("") }
    var isCloudRecoveryMode by remember { mutableStateOf(false) }
    var isCloudBackupLoading by remember { mutableStateOf(false) }
    var pendingCloudSignInFlow by remember { mutableStateOf(false) }
    var cloudPasswordError by remember { mutableStateOf<String?>(null) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!pendingCloudSignInFlow) return@rememberLauncherForActivityResult

        scope.launch {
            isCloudBackupLoading = true
            when (val signInResult = viewModel.handleCloudGoogleSignInResult(result.data)) {
                is com.mtd.domain.model.ResultResponse.Success -> {
                    isCloudRecoveryMode = signInResult.data
                    cloudPasswordError = null
                    backupFlowStep = BackupFlowStep.CloudPassword
                }

                is com.mtd.domain.model.ResultResponse.Error -> {
                    isCloudRecoveryMode = true
                    cloudPasswordError = "اتصال به گوگل درایو برقرار نشد. دوباره تلاش کنید."
                    backupFlowStep = BackupFlowStep.CloudPassword
                }
            }
            isCloudBackupLoading = false
            pendingCloudSignInFlow = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadWallets()
    }

    val handleBack = {
        when{
            backupFlowStep == BackupFlowStep.VerifyingManual -> backupFlowStep = BackupFlowStep.Revealing
            backupFlowStep == BackupFlowStep.CloudPassword -> backupFlowStep = BackupFlowStep.Revealing
            backupFlowStep != BackupFlowStep.None -> backupFlowStep = BackupFlowStep.None
            isShowingRecovery->isShowingRecovery=false
            isEditingNickname->isEditingNickname=false
            isPersonalizing->isPersonalizing=false
            else->{
                selectedWalletId=null
            }
        }
    }

    BackHandler(enabled = isAnyCardExpanded || backupFlowStep != BackupFlowStep.None) {
        handleBack()
    }

    // نرم کردن تغییر آلفای اجزای صفحه
    val contentAlpha by animateFloatAsState(
        targetValue = if (isAnyCardExpanded || backupFlowStep != BackupFlowStep.None) 0f else 1f,
        animationSpec = tween(400),
        label = "content_alpha"
    )

    // انیمیشن خروج لیست والت‌ها به چپ هنگام شروع تست بک‌آپ
    val listOffsetX by animateDpAsState(
        targetValue = if (backupFlowStep == BackupFlowStep.VerifyingManual || backupFlowStep == BackupFlowStep.CloudPassword) (-1000).dp else 0.dp,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "list_offset_x"
    )


        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { rootCoordinates = it }
        ) {
            // ۱. لیست اصلی والت‌ها
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState(), enabled = !isAnyCardExpanded)
                    .padding(bottom = 16.dp)
                    .graphicsLayer { translationX = listOffsetX.toPx() } // Apply offset here
            ) {
                // فضای خالی برای هدر ثابت
                Spacer(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(72.dp)
                )

                Text(
                    text = "کیف پول‌های شما",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold)),
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .graphicsLayer { alpha = contentAlpha }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (wallets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    wallets.chunked(2).forEach { rowWallets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            rowWallets.forEach { item ->
                                val isExpanded = selectedWalletId == item.wallet.id
                                val isAnyOtherExpanded = isAnyCardExpanded && !isExpanded
                                val keepRevealState = backupFlowStep == BackupFlowStep.Revealing || backupFlowStep == BackupFlowStep.VerifyingManual || backupFlowStep == BackupFlowStep.CloudPassword

                                Box(modifier = Modifier.weight(1f)) {
                                    WalletCard(
                                        wallet = item.wallet,
                                        balance = item.totalBalance,
                                        isActive = item.wallet.id == activeWalletId,
                                        isExpanded = isExpanded,
                                        isAnyOtherExpanded = isAnyOtherExpanded,
                                        rootCoordinates = rootCoordinates,
                                        isManualBackedUp = item.isManualBackedUp || (backupFlowStep == BackupFlowStep.Success && selectedWalletId == item.wallet.id && revealMethod == "manual"),
                                        isCloudBackedUp = item.isCloudBackedUp || (backupFlowStep == BackupFlowStep.Success && selectedWalletId == item.wallet.id && revealMethod == "cloud"),
                                        isPersonalizing = isPersonalizing && isExpanded,
                                        isEditingNickname = isEditingNickname && isExpanded,
                                        hideActions = backupFlowStep != BackupFlowStep.None,
                                        editName = editName,
                                        editColor = editColor,
                                        onSelect = {
                                            if (!isAnyCardExpanded) {
                                                viewModel.switchWallet(item.wallet.id)
                                                onNavigateBack()
                                            }
                                        },
                                        onToggleExpand = {
                                            if (!isAnyOtherExpanded) {
                                                if (isExpanded) {
                                                    isPersonalizing = false
                                                    isEditingNickname = false
                                                    selectedWalletId = null
                                                } else {
                                                    selectedWalletId = item.wallet.id
                                                }
                                            }
                                        },
                                        onSettingsClick = {
                                            editName = item.wallet.name
                                            editColor = Color(item.wallet.color)
                                            isPersonalizing = true
                                        },
                                        onDeleteClick = {
                                            selectedWalletId?.let {
                                                scope.launch {
                                                    viewModel.deleteWallet(it)
                                                    selectedWalletId = null
                                                }
                                            }
                                        },
                                        onNameChange = { editName = it },
                                        onEditNicknameToggle = {
                                            isEditingNickname = !isEditingNickname
                                        },
                                        isRevealingSecret = keepRevealState && isExpanded,
                                        isBackupSuccess = backupFlowStep == BackupFlowStep.Success && isExpanded,
                                        secretData = secretData,
                                        focusRequester = focusRequester
                                    )
                                }
                            }
                            if (rowWallets.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(64.dp))
            }

            // ۲. متن توضیحات و هدر در یک ستون ساختاریافته
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(3000f)
            ) {
                MultiWalletHeader(
                    isExpanded = isAnyCardExpanded,
                    onBackClick = onNavigateBack,
                    onAddClick = { showAddWalletSheet = true },
                    onCollapse = handleBack,
                    contentAlpha = contentAlpha
                )

                AnimatedVisibility(
                    visible = isPersonalizing,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { -20 },
                        animationSpec = PremiumSpringInt
                    ),
                    exit = fadeOut(tween(300)) + slideOutVertically(
                        targetOffsetY = { -20 },
                        animationSpec = PremiumSpringInt
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "نام مستعار و رنگ کیف پول شما خصوصی است و فقط برای شما قابل مشاهده است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular)),
                        fontSize = 14.sp
                    )
                }
            }

            // ۳. لایه منوی مدیریت (با موقعیت داینامیک زیر کارت)
            AnimatedVisibility(
                visible = isAnyCardExpanded && backupFlowStep == BackupFlowStep.None,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2000f)
            ) {
                val density = LocalDensity.current
                val screenWidthDp =
                    if (rootCoordinates != null) with(density) { rootCoordinates!!.size.width.toDp() } else 360.dp
                val cardTargetHeight = (screenWidthDp - 48.dp) * 0.61f
                val cardTargetY = if (isPersonalizing) 180.dp else 120.dp
                val menuTopPadding = cardTargetY + cardTargetHeight + 20.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = menuTopPadding, start = 24.dp, end = 24.dp)
                ) {
                    val selectedWalletUiItem = wallets.find { it.wallet.id == selectedWalletId }

                    val menuState = when {
                        isPersonalizing -> "personalize"
                        isShowingRecovery -> "recovery"
                        else -> "main"
                    }

                    AnimatedContent(
                        targetState = menuState,
                        transitionSpec = {
                            if (targetState == "recovery" || (initialState == "main" && targetState == "personalize")) {
                                slideInHorizontally { it } + fadeIn() togetherWith (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith (slideOutHorizontally { it } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "menu_transition"
                    ) { state ->
                        when (state) {
                            "personalize" -> {
                                WalletPersonalizationContent(
                                    selectedColor = editColor,
                                    onColorSelect = { editColor = it },
                                    onSave = {
                                        selectedWalletId?.let { id ->
                                            scope.launch {
                                                viewModel.updateWalletName(id, editName)
                                                viewModel.updateWalletColor(id, editColor.toArgb())
                                                isPersonalizing = false
                                            }
                                        }
                                    }
                                )
                            }

                            "recovery" -> {
                                val selectedWalletUiItem =
                                    wallets.find { it.wallet.id == selectedWalletId }
                                WalletRecoveryMethodsContent(
                                    isManualBackedUp = selectedWalletUiItem?.isManualBackedUp == true,
                                    isCloudBackedUp = selectedWalletUiItem?.isCloudBackedUp == true,
                                    onMethodClick = { type ->
                                        revealMethod = type
                                        showSecretPromptSheet = true
                                    }
                                )
                            }

                            else -> {
                                WalletManagementMenuContent(
                                    isBackedUp = selectedWalletUiItem?.isManualBackedUp == true || selectedWalletUiItem?.isCloudBackedUp == true,
                                    onBackupClick = { isShowingRecovery = true },
                                    onSettings = {
                                        editName = selectedWalletUiItem?.wallet?.name ?: ""
                                        editColor = Color(
                                            selectedWalletUiItem?.wallet?.color
                                                ?: 0xFF00FF00.toInt()
                                        )
                                        isPersonalizing = true
                                    },
                                    onDelete = {
                                        selectedWalletId?.let { id ->
                                            scope.launch {
                                                viewModel.deleteWallet(id)
                                                selectedWalletId = null
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AddWalletBottomSheet(
                visible = showAddWalletSheet,
                onDismiss = { showAddWalletSheet = false },
                onCreateNew = onAddNewWallet,
                onImportExisting = onImportExisting
            )

            val selectedWalletUiItem = wallets.find { it.wallet.id == selectedWalletId }
            val effectiveManualBackedUp =
                selectedWalletUiItem?.isManualBackedUp == true ||
                        (backupFlowStep == BackupFlowStep.Success && revealMethod == "manual")
            val effectiveCloudBackedUp =
                selectedWalletUiItem?.isCloudBackedUp == true ||
                        (backupFlowStep == BackupFlowStep.Success && revealMethod == "cloud")
            SecretRecoveryPromptBottomSheet(
                visible = showSecretPromptSheet,
                isMnemonic = selectedWalletUiItem?.wallet?.hasMnemonic == true,
                onDismiss = { showSecretPromptSheet = false },
                onReveal = {
                    backupFlowStep = BackupFlowStep.Revealing
                    showSecretPromptSheet = false

                    scope.launch {
                        selectedWalletId?.let { id ->
                            val secret = viewModel.getMnemonic(id)
                            if (secret != null) {
                                secretData = secret
                            } else {
                                // اگر به هر دلیلی خالی بود، انیمیشن را برمی‌گردانیم
                                backupFlowStep = BackupFlowStep.None
                            }
                        }
                    }
                }
            )

            // ۴. لایه‌ی نمایش کلمات (Reveal Overlay)
            SecretRevealOverlay(
                visible = backupFlowStep == BackupFlowStep.Revealing ||
                        backupFlowStep == BackupFlowStep.VerifyingManual ||
                        backupFlowStep == BackupFlowStep.Success,
                isMnemonic = selectedWalletUiItem?.wallet?.hasMnemonic == true,
                methodType = revealMethod,
                walletColor = selectedWalletUiItem?.wallet?.color?: MaterialTheme.colorScheme.primary.toArgb(),
                isManualBackedUp = effectiveManualBackedUp,
                isCloudBackedUp = effectiveCloudBackedUp,
                isVerifyingBackup = backupFlowStep == BackupFlowStep.VerifyingManual,
                isBackupSuccess = backupFlowStep == BackupFlowStep.Success,
                isCloudActionLoading = isCloudBackupLoading,
                mnemonic = secretData,
                onStartVerification = { backupFlowStep = BackupFlowStep.VerifyingManual },
                onStartCloudBackup = {
                    selectedWalletId?.let {
                        scope.launch {
                            cloudPasswordError = null
                            isCloudBackupLoading = true
                            val hasLocalCloudHint =
                                selectedWalletUiItem?.isCloudBackedUp == true ||
                                        wallets.any { walletUi -> walletUi.isCloudBackedUp }
                            if (!viewModel.isCloudConnected()) {
                                pendingCloudSignInFlow = true
                                isCloudBackupLoading = false
                                googleSignInLauncher.launch(viewModel.getCloudSignInIntent())
                                return@launch
                            }

                            isCloudRecoveryMode = hasLocalCloudHint || viewModel.hasCloudBackup()
                            isCloudBackupLoading = false
                            backupFlowStep = BackupFlowStep.CloudPassword
                        }
                    }
                },
                onBackupConfirmed = {
                    backupFlowStep = BackupFlowStep.Revealing
                    selectedWalletId?.let { id ->
                        scope.launch {
                            viewModel.updateBackupStatus(id, manual = true)
                            delay(800)
                            backupFlowStep = BackupFlowStep.Success
                        }
                    }
                },
                onClose = {
                    backupFlowStep = BackupFlowStep.None
                }
            )

            AnimatedVisibility(
                visible = backupFlowStep == BackupFlowStep.CloudPassword,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(450)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp)
                    .zIndex(6000f)
            ) {
                CloudBackupPasswordScreen(
                    onBack = {
                        cloudPasswordError = null
                        backupFlowStep = BackupFlowStep.Revealing
                    },
                    targetColor = Color(selectedWalletUiItem?.wallet?.color ?: MaterialTheme.colorScheme.primary.toArgb()),
                    mode = if (isCloudRecoveryMode) {
                        CloudPasswordMode.APPEND_TO_EXISTING_BACKUP
                    } else {
                        CloudPasswordMode.CREATE_NEW_BACKUP
                    },
                    isLoading = isCloudBackupLoading,
                    errorMessage = cloudPasswordError,
                    onPasswordSubmit = { password ->
                        selectedWalletId?.let { walletId ->
                            scope.launch {
                                cloudPasswordError = null
                                isCloudBackupLoading = true
                                when (val result = viewModel.backupWalletToCloud(walletId, password)) {
                                    is com.mtd.domain.model.ResultResponse.Success -> {
                                        viewModel.updateBackupStatus(walletId, cloud = true)
                                        backupFlowStep = BackupFlowStep.Success
                                    }

                                    is com.mtd.domain.model.ResultResponse.Error -> {
                                        val errorText = result.exception.message.orEmpty()
                                        cloudPasswordError = if (errorText.contains("initialized", ignoreCase = true)) {
                                            "ابتدا اتصال گوگل درایو را فعال کنید."
                                        } else if (isCloudRecoveryMode) {
                                            "رمز عبور پشتیبان ابری اشتباه است."
                                        } else {
                                            "خطا در ذخیره نسخه پشتیبان ابری."
                                        }
                                    }
                                }
                                isCloudBackupLoading = false
                            }
                        }
                    }
                )
            }
        }

}



@Composable
private fun MultiWalletHeader(
    isExpanded: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onCollapse: () -> Unit,
    contentAlpha: Float
) {
    val contentColor = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // حالت عادی (Add + Title + Settings)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(25.dp)
                )
            }
            Text(
                text = "کیف پول‌ها",
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_bold))
            )
            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        // حالت گسترده (فقط دکمه بازگشت در سمت چپ/End)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - contentAlpha },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}


@Composable
private fun AddWalletBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onImportExisting: () -> Unit
) {
    // استفاده از یک باکس ریشه برای مدیریت هر دو لایه
    Box(modifier = Modifier.fillMaxSize()) {

        // ۱. لایه مشکی پشت (فقط Fade)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        // ۲. باکس اصلی محتوا (Slide + Fade)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400)) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(300)
                    ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // محتوای اصلی (Floating Card)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 40.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MainScreenConstants.FAB_CORNER_RADIUS_EXPANDED))
                    .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                    .clickable(enabled = false) {} // برای جلوگیری از کلیک روی لایه پشت
                    .padding(bottom = 24.dp)
            ) {
                // هدر
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کیف جدید",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily(
                            Font(
                                R.font.iransansmobile_fa_regular,
                                FontWeight.Medium
                            )
                        )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Divider(
                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.1f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ۱. Create New
                AddWalletItem(
                    title = "ساخت کیف جدید",
                    subtitle = "ایجاد یک کیف پول جدید بدون سابقه",
                    icon = Icons.Default.Add,
                    iconBgColor = Color(0xFF42A5F5),
                    onClick = onCreateNew
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ۲. Add Existing
                AddWalletItem(
                    title = "وارد کردن",
                    subtitle = "افزودن کیف پول موجود با عبارت بازیابی یا کلید خصوصی",
                    icon = Icons.Default.ArrowDownward,
                    iconBgColor = Green,
                    onClick = onImportExisting
                )
            }
        }
    }
}


@Composable
private fun AddWalletItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(MainScreenConstants.FAB_MENU_ITEM_CORNER_RADIUS))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                fontSize = 12.sp,
                fontFamily = FontFamily(Font(R.font.iransansmobile_fa_regular))
            )
        }
    }
}

@Preview
@Composable
fun previewCard() {
    MaterialTheme {
//        ManagementMenuContent(false,{}, {}, {})
//        RecoveryMethodsContent(false,false,{},{})

        SecretRecoveryPromptBottomSheet(true, isMnemonic = true, onDismiss = {}, onReveal = {})

    }
}


