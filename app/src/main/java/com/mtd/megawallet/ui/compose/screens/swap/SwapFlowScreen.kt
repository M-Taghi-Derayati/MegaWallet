package com.mtd.megawallet.ui.compose.screens.swap

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blankj.utilcode.util.ClipboardUtils
import com.mtd.domain.model.AssetItem
import com.mtd.domain.model.HomeUiState
import com.mtd.megawallet.ui.compose.animations.constants.WalletScreenConstants
import com.mtd.megawallet.ui.compose.components.ChooseBalanceBottomSheet
import com.mtd.megawallet.ui.compose.components.NumericKeypad
import com.mtd.megawallet.ui.compose.components.PrimaryButton
import com.mtd.megawallet.ui.compose.components.UnifiedHeader
import com.mtd.megawallet.ui.compose.components.buildSendableAssetList
import com.mtd.megawallet.ui.compose.screens.scanner.QrScannerScreen
import com.mtd.megawallet.viewmodel.HomeViewModel
import com.mtd.megawallet.viewmodel.wallet.WalletAddressPickerViewModel
import com.mtd.megawallet.viewmodel.swap.SwapEvent
import com.mtd.megawallet.viewmodel.swap.SwapPhase
import com.mtd.megawallet.viewmodel.swap.SwapQuoteState
import com.mtd.megawallet.viewmodel.swap.SwapUiState
import com.mtd.megawallet.viewmodel.swap.SwapViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * فلوی تبدیل: **یک** ستونِ اسکرول‌شونده که سکشن‌هایش بین فازها باز و بسته می‌شوند، نه چند صفحهٔ
 * جدا. آیکونِ هر توکن در کلِ فلو یک عنصرِ واحد است که بین جایگاه‌ها پرواز می‌کند
 * ([SwapMorphIcon])؛ همان چیزی که «مورف» را از «صفحهٔ بعد» متمایز می‌کند.
 */
@Composable
fun SwapFlowScreen(
    homeViewModel: HomeViewModel,
    walletName: String,
    onDismiss: () -> Unit,
    swapViewModel: SwapViewModel = hiltViewModel(),
    walletAddressPickerViewModel: WalletAddressPickerViewModel = hiltViewModel()
) {
    val state by swapViewModel.uiState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val motion = rememberSwapMotion()

    LaunchedEffect(homeState) {
        (homeState as? HomeUiState.Success)?.let { swapViewModel.onAssetsAvailable(it.assets) }
    }

    // فهرستِ شبکه‌های تبدیل‌پذیر موقعِ برگشت به اپ تازه می‌شود؛ ریپازیتوری خودش کش دارد، پس
    // این فقط یک درخواست در هر resume است، نه در هر بازسازیِ ترکیب.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) swapViewModel.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { swapViewModel.reset() }
    }

    val payIcon = remember { SwapMorphIcon() }
    val receiveIcon = remember { SwapMorphIcon() }
    val amountIntro = remember { Animatable(0f) }
    val confirmIntro = remember { Animatable(0f) }
    var previousPhase by remember { mutableStateOf(state.phase) }

    // ── هماهنگیِ فازها ────────────────────────────────────────────────────────
    LaunchedEffect(state.phase, state.payToken?.option?.id) {
        val from = previousPhase
        previousPhase = state.phase

        when (state.phase) {
            SwapPhase.PAY_TOKEN -> {
                amountIntro.snapTo(0f)
                confirmIntro.snapTo(0f)
                receiveIcon.slot = null
                // ردیف‌های فهرست حالا کامپوننتِ مشترکِ ارسال‌اند و مستطیلِ لوگو را بیرون نمی‌دهند،
                // پس مبدأی برای پرواز وجود ندارد؛ آیکون از فازِ بعد شروع می‌شود.
                payIcon.slot = null
            }

            SwapPhase.AMOUNT -> {
                confirmIntro.snapTo(0f)
                if (from == SwapPhase.PAY_TOKEN) {
                    amountIntro.snapTo(0f)
                    launch { amountIntro.animateTo(1f, motion.morph()) }
                } else {
                    amountIntro.snapTo(1f)
                }
                launch { payIcon.flyTo(SwapMorphSlot.PAY_PILL, motion) }
                if (state.receiveToken != null) {
                    launch { receiveIcon.flyTo(SwapMorphSlot.RECEIVE_CARD, motion) }
                }
            }

            SwapPhase.CONFIRM -> {
                confirmIntro.snapTo(0f)
                launch { payIcon.flyTo(SwapMorphSlot.COIN_PAY, motion) }
                launch { receiveIcon.flyTo(SwapMorphSlot.COIN_RECEIVE, motion) }
                launch { confirmIntro.animateTo(1f, motion.morph(delayMs = 120)) }
            }

            SwapPhase.EXECUTING, SwapPhase.RESULT -> {
                payIcon.slot = null
                receiveIcon.slot = null
            }
        }
    }

    // آیکون‌ها استیتِ خارج از ترکیب‌اند؛ باید با انتخابِ کاربر هم‌گام بمانند. نماد هم کنارِ URL
    // می‌آید چون آیکونِ نبوده با آواتارِ حرفیِ همان نماد پر می‌شود، نه با یک درِیوبلِ عمومی.
    LaunchedEffect(state.payToken?.option?.id) {
        payIcon.iconUrl = state.payToken?.option?.iconUrl
        payIcon.symbol = state.payToken?.option?.symbol.orEmpty()
        payIcon.networkIconUrl = state.payToken?.option?.networkIconUrl
    }

    LaunchedEffect(state.receiveToken?.id, state.phase) {
        val token = state.receiveToken
        receiveIcon.iconUrl = token?.iconUrl
        receiveIcon.symbol = token?.symbol.orEmpty()
        receiveIcon.networkIconUrl = token?.networkIconUrl
        when {
            token == null -> receiveIcon.slot = null
            // انتخابِ ارزِ دریافت وسطِ فازِ مبلغ اتفاق می‌افتد: پروازی در کار نیست، پس با یک پاپ می‌آید.
            state.phase == SwapPhase.AMOUNT && receiveIcon.slot == null -> {
                receiveIcon.slot = SwapMorphSlot.RECEIVE_CARD
                if (motion.animated) {
                    receiveIcon.scale.snapTo(0.55f)
                    receiveIcon.scale.animateTo(1f, motion.emphasis())
                }
            }
        }
    }

    val handleBack = {
        when (state.phase) {
            SwapPhase.PAY_TOKEN -> onDismiss()
            SwapPhase.AMOUNT -> swapViewModel.onEvent(SwapEvent.BackToPaySelect)
            SwapPhase.CONFIRM -> swapViewModel.onEvent(SwapEvent.BackFromConfirm)
            // اجرا شروع شده و روی زنجیره است؛ برگشت آن را متوقف نمی‌کند.
            SwapPhase.EXECUTING -> Unit
            SwapPhase.RESULT -> {
                swapViewModel.onEvent(SwapEvent.ResultDismissed)
                onDismiss()
            }
        }
    }

    var chooseBalanceAsset by remember { mutableStateOf<AssetItem?>(null) }

    /**
     * شیتِ انتخابِ شبکه برای سمتِ **دریافت**. جدا از [chooseBalanceAsset] است چون رفتارش فرق
     * دارد: آن‌جا فقط شبکه‌های دارای موجودی معنی دارند، این‌جا شبکهٔ خالی هم مقصدِ معتبرِ پل است.
     */
    var chooseReceiveNetworkAsset by remember { mutableStateOf<AssetItem?>(null) }

    /**
     * شیتِ انتخابِ گیرنده. ویزیبیلیتی‌اش استیتِ خالصِ UI است و در [SwapUiState] نمی‌نشیند؛ باز و
     * بستهٔ یک شیت چیزی نیست که ViewModel لازم باشد بداند.
     */
    var destinationPickerVisible by remember { mutableStateOf(false) }
    var destinationScannerVisible by remember { mutableStateOf(false) }
    var destinationSnapshot by remember { mutableStateOf("") }

    val dismissDestinationPicker = {
        // انصراف = برگشت به همان گیرنده‌ای که قبلِ باز شدنِ شیت انتخاب شده بود.
        if (state.destinationInput != destinationSnapshot) {
            swapViewModel.onEvent(SwapEvent.DestinationAddressChanged(destinationSnapshot))
        }
        destinationPickerVisible = false
    }

    BackHandler {
        when {
            destinationScannerVisible -> destinationScannerVisible = false
            destinationPickerVisible -> dismissDestinationPicker()
            chooseReceiveNetworkAsset != null -> chooseReceiveNetworkAsset = null
            state.receiveSheetVisible -> swapViewModel.onEvent(SwapEvent.DismissReceiveSheet)
            chooseBalanceAsset != null -> chooseBalanceAsset = null
            else -> handleBack()
        }
    }

    // همان سازندهٔ فهرستِ صفحهٔ ارسال. فیلترِ نوعِ شبکه اینجا اعمال نمی‌شود چون تبدیل آدرسِ مقصدی
    // ندارد که فهرست باید با آن سازگار باشد؛ تنها معیار، موجودیِ بزرگ‌تر از صفر است.
    //
    // شبکه‌ای که سرور تبدیل‌پذیر اعلام نکرده اصلاً نمایش داده نمی‌شود: استعلامش همیشه ۴۲۲ برمی‌گردد
    // و آن خطا برای کاربر مثلِ «الان نقدینگی نیست» خوانده می‌شود، نه «این شبکه اصلاً پشتیبانی
    // نمی‌شود». این فقط ورودیِ تبدیل را می‌بندد؛ تست‌نت‌ها در بقیهٔ اپ سرِ جایشان هستند.
    val payAssets = remember(
        homeState,
        state.fiatCurrency,
        state.usdToTomanRate,
        state.swappableNetworkIds,
        state.swapChainsLoaded
    ) {
        buildSendableAssetList(
            fiatCurrency = state.fiatCurrency,
            usdToIrrRate = state.usdToTomanRate,
            source = ((homeState as? HomeUiState.Success)?.assets ?: emptyList())
                .retainSwappableNetworks(state),
            networkType = null,
            networkTypeResolver = { homeViewModel.getNetworkTypeForNetworkId(it) }
        )
    }

    val visiblePayAssets = remember(payAssets, state.payQuery) {
        payAssets.filterByPayQuery(state.payQuery)
    }

    // کیف‌پول‌هایی که روی **شبکهٔ مقصد** آدرس دارند. منبعش یک خواندنِ محلی است و هیچ موجودی یا
    // درخواستِ شبکه‌ای با خودش نمی‌آورد.
    val walletAddresses by walletAddressPickerViewModel.wallets.collectAsStateWithLifecycle()
    val walletDestinationOptions = remember(walletAddresses, state.receiveToken?.networkId) {
        walletAddresses.destinationOptionsFor(state.receiveToken?.networkId)
    }

    val addressBook by walletAddressPickerViewModel.savedAddresses.collectAsStateWithLifecycle()
    val savedDestinationAddresses = remember(addressBook, state.receiveToken?.networkId) {
        addressBook.savedAddressesFor(state.receiveToken?.networkId)
    }

    // نامِ گیرنده از روی همان دو فهرست پیدا می‌شود، نه از یک متنِ ثابت: کیف‌پولِ فعال هم یکی از
    // همین گزینه‌هاست، پس اسمی که کاربر خودش رویش گذاشته همان‌جا پیدا می‌شود. [walletName] فقط
    // وقتی به کار می‌آید که کیف‌پول روی شبکهٔ مقصد در فهرست نباشد ولی آدرسِ مقصد همان آدرسِ خودش باشد.
    val destinationName = remember(
        state.usableDestinationAddress,
        state.destinationIsOwnWallet,
        walletDestinationOptions,
        savedDestinationAddresses,
        walletName
    ) {
        resolveDestinationName(
            address = state.usableDestinationAddress,
            walletOptions = walletDestinationOptions,
            savedAddresses = savedDestinationAddresses
        ) ?: walletName.takeIf { state.destinationIsOwnWallet }
    }

    CompositionLocalProvider(LocalSwapMotion provides motion) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {

                // حاشیهٔ افقیِ هر فاز، همان چیزی که همتایش در فلوی ارسال دارد: فهرست و مبلغ ۱۶،
                // تأیید و بعدش ۲۰. سرصفحه با محتوای زیرِ خودش هم‌تراز می‌ماند.
                val contentPadding = when (state.phase) {
                    SwapPhase.PAY_TOKEN, SwapPhase.AMOUNT -> 16.dp
                    else -> 20.dp
                }

                UnifiedHeader(
                    onBack = { handleBack() },
                    // فازِ تأیید عنوان ندارد؛ عنوانِ صفحه آن‌جا خودِ جفتِ ارز است، مثلِ صفحهٔ
                    // تأییدِ ارسال که گیرنده را بزرگ می‌نویسد.
                    title = if (state.phase == SwapPhase.CONFIRM) null else "تبدیل",
                    isClose = when (state.phase) {
                        SwapPhase.PAY_TOKEN, SwapPhase.RESULT -> true
                        // اجرا شروع شده و روی زنجیره است؛ راهِ خروجی نشان داده نمی‌شود.
                        SwapPhase.EXECUTING -> null
                        else -> false
                    },
                    modifier = Modifier.padding(horizontal = contentPadding)
                )

                Box(Modifier.weight(1f)) {
                    // فهرستِ دارایی lazy است و داخلِ ستونِ اسکرول با ارتفاعِ بی‌کران اندازه‌گیری
                    // می‌شد؛ بیرونِ آن می‌نشیند تا ارتفاعِ کران‌دار بگیرد.
                    SwapMorphSection(
                        visible = state.phase == SwapPhase.PAY_TOKEN,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SwapPaySection(
                            state = state,
                            assets = visiblePayAssets,
                            hasAnyAsset = payAssets.isNotEmpty(),
                            onQueryChange = { swapViewModel.onEvent(SwapEvent.PayQueryChanged(it)) },
                            onAssetSelected = { asset ->
                                if (asset.isGroupHeader && asset.groupAssets.size > 1) {
                                    chooseBalanceAsset = asset
                                } else {
                                    swapViewModel.onEvent(SwapEvent.PayAssetSelected(asset))
                                }
                            }
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        SwapMorphSection(visible = state.phase == SwapPhase.AMOUNT) {
                            SwapPayCardSection(
                                state = state,
                                intro = amountIntro.value,
                                onUseMax = { swapViewModel.onEvent(SwapEvent.UseMax) },
                                onToggleFiat = { swapViewModel.onEvent(SwapEvent.ToggleFiatInput) },
                                // هم‌اندازهٔ آیکونِ کارتِ داراییِ فازِ مبلغِ ارسال.
                                tokenSlot = {
                                    SwapMorphSlotHost(
                                        model = payIcon,
                                        slot = SwapMorphSlot.PAY_PILL,
                                        size = WalletScreenConstants.ASSET_ICON_MAIN_SIZE
                                    )
                                }
                            )
                        }

                        SwapMorphSection(visible = state.phase == SwapPhase.AMOUNT) {
                            Column {
                                Spacer(Modifier.height(12.dp))
                                SwapReceiveCardSection(
                                    state = state,
                                    intro = amountIntro.value,
                                    onOpenSheet = { swapViewModel.onEvent(SwapEvent.OpenReceiveSheet) },
                                    leading = {
                                        if (state.receiveToken == null) {
                                            SwapReceivePlaceholder(
                                                size = WalletScreenConstants.ASSET_ICON_MAIN_SIZE
                                            )
                                        } else {
                                            SwapMorphSlotHost(
                                                model = receiveIcon,
                                                slot = SwapMorphSlot.RECEIVE_CARD,
                                                size = WalletScreenConstants.ASSET_ICON_MAIN_SIZE
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        // آدرسِ مقصد قبل از استعلام جمع می‌شود، نه بعدش: کشِ ۱۵ ثانیه‌ایِ سرور به
                        // ازای هر مقصد جداست و جفتِ بین‌خانوادگی بدونِ آن اصلاً استعلام نمی‌شود.
                        SwapMorphSection(
                            visible = state.phase == SwapPhase.AMOUNT && state.receiveToken != null
                        ) {
                            Column {
                                Spacer(Modifier.height(14.dp))
                                SwapDestinationSection(
                                    state = state,
                                    destinationName = destinationName,
                                    onOpenPicker = {
                                        // مقدارِ فعلی قبل از باز شدنِ شیت نگه داشته می‌شود: تایپ
                                        // داخلِ شیت زنده اعتبارسنجی می‌شود، پس انصراف باید بتواند
                                        // دقیقاً همان چیزی را برگرداند که کاربر داشت.
                                        destinationSnapshot = state.destinationInput
                                        destinationPickerVisible = true
                                    }
                                )
                            }
                        }

                        SwapMorphSection(visible = state.phase == SwapPhase.CONFIRM) {
                            val timer = rememberSwapQuoteCountdown(state)
                            SwapConfirmSection(
                                state = state,
                                walletName = walletName,
                                intro = confirmIntro.value,
                                quoteFraction = timer.fraction,
                                quoteSecondsRemaining = timer.seconds,
                                onSlippageSelected = { swapViewModel.onEvent(SwapEvent.SlippageChanged(it)) },
                                onFeeLevelSelected = { swapViewModel.onEvent(SwapEvent.FeeLevelSelected(it)) },
                                // ۵۶dp: همان قطرِ آواتارِ گیرنده در صفحهٔ تأییدِ ارسال.
                                payCoin = {
                                    SwapMorphSlotHost(
                                        model = payIcon,
                                        slot = SwapMorphSlot.COIN_PAY,
                                        size = 56.dp
                                    )
                                },
                                receiveCoin = {
                                    SwapMorphSlotHost(
                                        model = receiveIcon,
                                        slot = SwapMorphSlot.COIN_RECEIVE,
                                        size = 56.dp
                                    )
                                }
                            )
                        }

                        SwapMorphSection(visible = state.phase == SwapPhase.EXECUTING) {
                            state.execution?.let { progress ->
                                Column {
                                    Spacer(Modifier.height(24.dp))
                                    SwapExecutionSection(state = state, progress = progress)
                                }
                            }
                        }

                        SwapMorphSection(visible = state.phase == SwapPhase.RESULT) {
                            state.execution?.outcome?.let { outcome ->
                                Column {
                                    Spacer(Modifier.height(32.dp))
                                    SwapResultSection(
                                        state = state,
                                        outcome = outcome,
                                        onDone = {
                                            swapViewModel.onEvent(SwapEvent.ResultDismissed)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ── نوارِ پایین، مخصوصِ هر فاز ────────────────────────────────
                SwapMorphSection(visible = state.phase == SwapPhase.AMOUNT) {
                    Column(Modifier.navigationBarsPadding()) {
                        NumericKeypad(
                            onKeyPress = { swapViewModel.onEvent(SwapEvent.AmountKeyPressed(it)) },
                            modifier = Modifier.graphicsLayer {
                                alpha = segment(amountIntro.value, 0.45f, 0.8f)
                            }
                        )
                        SwapContinueButton(
                            state = state,
                            intro = amountIntro.value,
                            onClick = { swapViewModel.onEvent(SwapEvent.ContinuePressed) }
                        )
                    }
                }

                SwapMorphSection(visible = state.phase == SwapPhase.CONFIRM) {
                    Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                        SwapConfirmCta(
                            state = state,
                            intro = confirmIntro.value,
                            onConfirm = { swapViewModel.onEvent(SwapEvent.ConfirmPressed) }
                        )
                    }
                }
            }

            ChooseBalanceBottomSheet(
                asset = chooseBalanceAsset,
                onDismiss = { chooseBalanceAsset = null },
                onNetworkSelected = { selected ->
                    chooseBalanceAsset = null
                    swapViewModel.onEvent(SwapEvent.PayAssetSelected(selected))
                }
            )

            SwapReceiveTokenSheet(
                state = state,
                onQueryChange = { swapViewModel.onEvent(SwapEvent.ReceiveQueryChanged(it)) },
                onAssetSelected = { asset ->
                    // یک ردیف = یک توکن. اگر روی چند شبکه قابلِ مسیریابی است، شبکه در شیتِ بعدی
                    // انتخاب می‌شود؛ همان الگویی که صفحهٔ ارسال دارد.
                    if (asset.isGroupHeader && asset.groupAssets.size > 1) {
                        chooseReceiveNetworkAsset = asset
                    } else {
                        swapViewModel.onEvent(SwapEvent.ReceiveAssetSelected(asset))
                    }
                },
                onImportQueryChange = { swapViewModel.onEvent(SwapEvent.ImportQueryChanged(it)) },
                onImportSubmit = { swapViewModel.onEvent(SwapEvent.ImportSubmitted) },
                onDismiss = { swapViewModel.onEvent(SwapEvent.DismissReceiveSheet) }
            )

            ChooseBalanceBottomSheet(
                asset = chooseReceiveNetworkAsset,
                onDismiss = { chooseReceiveNetworkAsset = null },
                onNetworkSelected = { selected ->
                    chooseReceiveNetworkAsset = null
                    swapViewModel.onEvent(SwapEvent.ReceiveAssetSelected(selected))
                },
                // مقصدِ پل معمولاً همان شبکه‌ای است که کاربر رویش چیزی ندارد.
                includeZeroBalance = true
            )

            SwapDestinationSheet(
                visible = destinationPickerVisible && !destinationScannerVisible,
                networkName = state.receiveToken?.networkName.orEmpty(),
                walletOptions = walletDestinationOptions,
                savedAddresses = savedDestinationAddresses,
                selectedAddress = state.usableDestinationAddress,
                manualInput = state.destinationInput,
                manualError = state.destinationError,
                // اسکن، انتخابِ کیف‌پول، مدخلِ دفترچه و تایپِ دستی همه از یک در می‌روند، پس
                // اعتبارسنجیِ شبکهٔ مقصد برای همه یکی است و هیچ مسیرِ دومی برای ساختِ گیرنده نیست.
                onAddressPicked = { swapViewModel.onEvent(SwapEvent.DestinationAddressChanged(it)) },
                onScanRequested = { destinationScannerVisible = true },
                onPaste = {
                    val pasted = ClipboardUtils.getText().toString()
                    if (pasted.isNotBlank()) {
                        swapViewModel.onEvent(SwapEvent.DestinationAddressChanged(pasted))
                    }
                },
                onConfirm = { destinationPickerVisible = false },
                onDismiss = { dismissDestinationPicker() }
            )

            // اسکنر روی شیت می‌نشیند و بعدِ خواندن به همان شیت برمی‌گردد؛ کاربر وسطِ انتخابِ
            // گیرنده از فلو پرت نمی‌شود.
            AnimatedVisibility(
                visible = destinationScannerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(10000f)
            ) {
                QrScannerScreen(
                    onClose = { destinationScannerVisible = false },
                    onAddressScanned = { scanned ->
                        swapViewModel.onEvent(SwapEvent.DestinationAddressChanged(scanned))
                        destinationScannerVisible = false
                    }
                )
            }
        }
    }
}

/**
 * دکمهٔ ادامهٔ فازِ مبلغ — همان [PrimaryButton] که فازِ مبلغِ ارسال زیرِ صفحه‌کلید دارد.
 * وقتی استعلام در جریان است متنش عوض می‌شود، نه اینکه بی‌صدا کار نکند.
 */
@Composable
private fun SwapContinueButton(
    state: SwapUiState,
    intro: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .graphicsLayer {
                val a = segment(intro, 0.5f, 0.85f)
                alpha = a
                translationY = (1f - a) * 20f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryButton(
            text = if (state.quoteState is SwapQuoteState.Loading) "در حال استعلام…" else "ادامه",
            onClick = onClick,
            enabled = state.canContinueFromAmount
        )
    }
}

/** شمارشِ معکوسِ اعتبارِ استعلام. */
private data class SwapCountdown(val fraction: Float, val seconds: Int)

/**
 * تیکِ ثانیه‌ایِ اعتبارِ استعلام، **بیرون از** [SwapUiState].
 *
 * اگر باقی‌مانده در خودِ state می‌نشست، هر ثانیه کلِ استیت دوباره منتشر می‌شد و همهٔ سکشن‌ها
 * بازسازی می‌شدند؛ اینجا فقط همین نشانگر بازسازی می‌شود.
 */
@Composable
private fun rememberSwapQuoteCountdown(state: SwapUiState): SwapCountdown {
    val expiresAt = state.quoteExpiresAtElapsed
    val ttl = state.quoteTtlMs.coerceAtLeast(1L)

    val countdown by produceState(
        initialValue = SwapCountdown(0f, 0),
        expiresAt,
        ttl
    ) {
        if (expiresAt == null) {
            value = SwapCountdown(0f, 0)
            return@produceState
        }
        while (true) {
            val remaining = expiresAt - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                value = SwapCountdown(0f, 0)
                break
            }
            value = SwapCountdown(
                fraction = (remaining.toFloat() / ttl.toFloat()).coerceIn(0f, 1f),
                seconds = ((remaining + 999L) / 1000L).toInt()
            )
            delay(COUNTDOWN_TICK_MS)
        }
    }

    return countdown
}

private const val COUNTDOWN_TICK_MS = 250L
